package sy.medpro.watani.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Typed signal that the gateway rejected this device's activation
 * (HTTP 401/403). Repository callers return it unwrapped (never folded
 * into a generic "download failed" message) so the UI can clear the
 * activation, wipe restricted downloads and show the activation prompt.
 */
class ActivationRevokedException(message: String) : Exception(message)

/** Outcome of one token-refresh attempt (see [WorkerActivationApi.tryRefresh]). */
enum class RefreshOutcome {
    /** A fresh token was stored. */
    REFRESHED,

    /** The server answered 401/403: the token is dead or access was
     * revoked (expired code, reset device, banned). Activation is lost. */
    REVOKED,

    /** No answer (no network, no user, no token): keep the old state,
     * never wipe on this outcome. */
    UNAVAILABLE
}

/**
 * Team 1 — Worker activation client (entry-activation branch).
 *
 * REPLACES Firestore code validation for activation
 * ([WataniFirestoreSync.validateAndBindActivation] was removed).
 * Firestore user/progress sync ([WataniFirestoreSync.ensureAndSyncUser])
 * is untouched.
 *
 * Single switch point for restricted content: every fetch of a restricted
 * path MUST go through [contentRequest] / [contentRequestForUrl] below.
 * Free paths keep fetching directly from Pages unchanged.
 *
 * No secret is hardcoded here: the only credential is the activation token
 * returned by POST /v1/activate, stored in prefs `watani_activation`
 * (is_activated + activated_code + activation_token +
 * activation_expires_at). The code itself is stored; no code secret exists
 * beyond the code.
 */
object WorkerActivationApi {

    const val WORKER_BASE = "https://watani-api.drbasharmohammad.workers.dev"

    /** Prefs file shared with the old activation screen (same keys kept). */
    const val PREFS_NAME = "watani_activation"

    private const val KEY_ACTIVATED = "is_activated"
    private const val KEY_CODE = "activated_code"
    private const val KEY_TOKEN = "activation_token"
    private const val KEY_EXPIRES_AT = "activation_expires_at"
    private const val KEY_DEVICE_UUID = "device_uuid_fallback"

    /** Restricted prefixes under v1/ (contract) + the exact library.json file. */
    private val RESTRICTED_PREFIXES = listOf(
        "questions/",
        "dawrat/",
        "dawrat_jami3at/",
        "uworld/",
        "explanations/",
        "books/"
    )

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------
    // Device id.
    // ------------------------------------------------------------------

    /**
     * Stable per-device id. ANDROID_ID when available; otherwise a random
     * UUID generated once and stored in prefs (M7). NEVER Build.MODEL:
     * a model string is shared by every phone of that model, so two
     * different users' devices would collide on the same "device" and
     * break the 2-device limit in both directions.
     *
     * Migration note: devices previously bound under a Build.MODEL
     * fallback present a new UUID after this change and count as a new
     * device until the admin resets the old binding. The app is
     * unreleased, so no real user is affected.
     */
    fun deviceId(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        if (!androidId.isNullOrBlank()) return androidId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var uuid = prefs.getString(KEY_DEVICE_UUID, null)
        if (uuid.isNullOrBlank()) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }
        return uuid
    }

    // ------------------------------------------------------------------
    // Activation state (prefs only — safe to call from the UI thread).
    // ------------------------------------------------------------------

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVATED, false)) return false
        val token = prefs.getString(KEY_TOKEN, null)
        if (token.isNullOrBlank()) return false
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        // expiresAt == 0 means the server gave no expiry: treat as valid.
        return expiresAt == 0L || expiresAt > System.currentTimeMillis()
    }

    /** True when the account was activated before but the token has expired. */
    fun hasExpiredToken(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVATED, false)) return false
        if (prefs.getString(KEY_TOKEN, null).isNullOrBlank()) return false
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return expiresAt != 0L && expiresAt <= System.currentTimeMillis()
    }

    fun activatedCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CODE, null)
    }

    fun clearLocalActivation(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVATED, false)
            .remove(KEY_CODE)
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    // ------------------------------------------------------------------
    // Activation loss (M2/M3/M4): one path for every way activation ends.
    // ------------------------------------------------------------------

    /**
     * Clears the local activation AND wipes restricted downloads (M4:
     * decided by the doctor — ended/cancelled activation must not keep
     * working offline). Silent: no UI event. Used by sign-out (M2: a
     * second user on this device must not inherit anything) and by the
     * deliberate deactivation button.
     */
    fun clearActivationAndWipe(context: Context) {
        clearLocalActivation(context)
        try {
            RestrictedInventory.wipeRestrictedDownloads(context)
        } catch (_: Exception) {
        }
    }

    private val _activationLostEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Collected by MainActivity: a gateway 401/403 (or a revoked refresh)
     * means "show the activation prompt now". */
    val activationLostEvents: SharedFlow<Unit> = _activationLostEvents.asSharedFlow()

    /**
     * Gateway 401/403 path (M3): clear + wipe (M4) + emit the prompt
     * event. Repository fetch paths call this the moment the gateway
     * answers 401/403 — never a generic HTTP message, never a cached
     * fallback for restricted content.
     */
    fun notifyActivationLost(context: Context) {
        clearActivationAndWipe(context)
        _activationLostEvents.tryEmit(Unit)
    }

    /**
     * Arabic user text for a server activation error (M14). The raw
     * server string is logged only (never shown); the user sees Arabic.
     * Unknown strings fall back to a generic Arabic message, never to
     * the raw string.
     */
    fun arabicActivationError(serverError: String, httpCode: Int = 0): String {
        Log.d("WorkerActivationApi", "activation error raw: $serverError (http=$httpCode)")
        return when (serverError.trim().lowercase()) {
            "invalid code", "invalid_code" -> "كود التفعيل غير صحيح أو غير موجود."
            "code bound to another account" -> "هذا الكود مستخدم بالفعل ومسجل بحساب آخر."
            "device limit reached", "device_limit" -> "تم تجاوز الحد الأقصى للأجهزة المسموحة (جهازان كحد أقصى للحساب الواحد)."
            "expired", "code_expired", "access_expired" -> "انتهت مدة كود التفعيل — جدّد الكود من لوحة الإدارة أو أدخل كودًا جديدًا."
            "access_revoked", "invalid_token" -> "انتهى التفعيل على هذا الجهاز — أعد إدخال كود التفعيل."
            "rate_limited" -> "محاولات كثيرة جدًا — انتظر ساعة ثم أعد المحاولة."
            "bad_request" -> "طلب تفعيل غير صالح — أعد المحاولة."
            "server_misconfigured" -> "خطأ في إعدادات الخادم — أبلغ الإدارة."
            "auth" -> "تعذر التحقق من الحساب — سجّل الدخول ثم أعد المحاولة."
            else -> if (httpCode == 401 || httpCode == 403) {
                "انتهى التفعيل على هذا الجهاز — أعد إدخال كود التفعيل."
            } else {
                "تعذر التفعيل — تحقق من الكود والاتصال ثم أعد المحاولة."
            }
        }
    }

    private fun storeActivation(context: Context, code: String, token: String, expiresAt: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_CODE, code)
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    // ------------------------------------------------------------------
    // POST /v1/activate and /v1/activate/refresh (contract).
    // ------------------------------------------------------------------

    /**
     * Activates [code] for [user] on this device.
     * Returns Pair(success, message) where message is ARABIC user text
     * (M14); the raw server `error` string is only logged, never shown.
     */
    suspend fun activate(context: Context, user: FirebaseUser, code: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val trimmed = code.trim()
            if (trimmed.isBlank()) return@withContext Pair(false, "يرجى إدخال كود تفعيل صالح.")
            try {
                val bodyJson = JSONObject().apply {
                    put("code", trimmed)
                    put("uid", user.uid)
                    put("email", user.email ?: "")
                    put("displayName", user.displayName ?: "")
                    put("deviceId", deviceId(context))
                }.toString()
                val request = Request.Builder()
                    .url("$WORKER_BASE/v1/activate")
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: "{}"
                    val json = try {
                        JSONObject(text)
                    } catch (_: Exception) {
                        JSONObject()
                    }
                    if (!resp.isSuccessful || !json.optBoolean("ok", false)) {
                        // Arabic user text (M14); raw server string only logged.
                        val serverError = json.optString("error", "").takeIf { it.isNotBlank() } ?: ""
                        return@withContext Pair(false, arabicActivationError(serverError, resp.code))
                    }
                    val token = json.optString("token", "")
                    if (token.isBlank()) {
                        return@withContext Pair(false, "استجابة التفعيل بلا رمز — أعد المحاولة.")
                    }
                    storeActivation(context, trimmed, token, parseExpiresAt(json))
                    Pair(true, "تم تفعيل الحساب والجهاز بنجاح!")
                }
            } catch (e: Exception) {
                Log.d("WorkerActivationApi", "activate network failure: ${e.localizedMessage}")
                Pair(false, "تعذر الاتصال بخادم التفعيل — تحقق من الشبكة وحاول مجددًا.")
            }
        }

    /**
     * Tries a token refresh when the stored token expired. Distinguishes
     * REVOKED (server 401/403: activation is lost — caller must clear
     * and wipe) from UNAVAILABLE (no network/user/token: keep the old
     * state, never wipe on this outcome). Callers on the UI thread must
     * call this from a coroutine (it performs network I/O).
     */
    suspend fun tryRefresh(context: Context): RefreshOutcome = withContext(Dispatchers.IO) {
        val (token, revoked) = refreshInternal(context)
        when {
            token != null -> RefreshOutcome.REFRESHED
            revoked -> RefreshOutcome.REVOKED
            else -> RefreshOutcome.UNAVAILABLE
        }
    }

    // (No blocking-refresh wrapper: validTokenOrRefresh below calls
    // refreshInternal directly so it can see the revocation flag.)

    /**
     * Returns (fresh token or null, serverRejected401_403).
     * STRICT ok check: a response WITHOUT `ok: true` is rejected even
     * when it carries a token — a missing `ok` must never be read as
     * success (the old `optBoolean("ok", true)` default did exactly that).
     */
    private fun refreshInternal(context: Context): Pair<String?, Boolean> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldToken = prefs.getString(KEY_TOKEN, null) ?: return Pair(null, false)
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return Pair(null, false)
        return try {
            val bodyJson = JSONObject().apply {
                put("uid", uid)
                put("deviceId", deviceId(context))
                put("token", oldToken)
            }.toString()
            val request = Request.Builder()
                .url("$WORKER_BASE/v1/activate/refresh")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                val json = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    JSONObject()
                }
                if (!resp.isSuccessful || !json.optBoolean("ok", false)) {
                    return Pair(null, resp.code == 401 || resp.code == 403)
                }
                val token = json.optString("token", "")
                if (token.isBlank()) return Pair(null, false)
                val code = prefs.getString(KEY_CODE, null) ?: ""
                storeActivation(context, code, token, parseExpiresAt(json))
                Pair(token, false)
            }
        } catch (_: Exception) {
            Pair(null, false)
        }
    }

    /**
     * A usable token for the fetch layer: the stored one when still valid,
     * otherwise one blocking refresh attempt. Null = no usable token
     * (caller must surface the activation prompt, not fetch restricted
     * content directly).
     *
     * Overseer fix (fetch-layer revocation): when the refresh attempt
     * proves revocation (server 401/403), this notifies activation loss
     * immediately (wipe + prompt event) instead of returning a silent
     * null that would leave downloaded content usable offline.
     */
    fun validTokenOrRefresh(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        if (token.isNullOrBlank()) return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt == 0L || expiresAt > System.currentTimeMillis() + 60_000L) return token
        val (fresh, revoked) = refreshInternal(context)
        if (revoked) notifyActivationLost(context)
        return fresh
    }

    private fun parseExpiresAt(json: JSONObject): Long {
        // expiresAt may be epoch millis, epoch seconds, or an ISO string.
        if (!json.has("expiresAt") || json.isNull("expiresAt")) return 0L
        return try {
            val raw = json.opt("expiresAt")
            when (raw) {
                is Number -> {
                    val v = raw.toLong()
                    when {
                        v > 1_000_000_000_000L -> v
                        v > 1_000_000_000L -> v * 1000L
                        else -> v
                    }
                }
                is String -> {
                    val s = raw.trim()
                    s.toLongOrNull()?.let { v ->
                        when {
                            v > 1_000_000_000_000L -> v
                            v > 1_000_000_000L -> v * 1000L
                            else -> v
                        }
                    } ?: try {
                        java.time.Instant.parse(s).toEpochMilli()
                    } catch (_: Exception) {
                        0L
                    }
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    // ------------------------------------------------------------------
    // THE switch point: restricted paths -> Worker gateway, free -> Pages.
    // ------------------------------------------------------------------

    /** Relative path under v1/ (leading "v1/" or "/" stripped). */
    fun normalizeRelativePath(path: String): String {
        var p = path.trim().trimStart('/')
        if (p.startsWith("v1/")) p = p.removePrefix("v1/")
        return p
    }

    fun isRestrictedPath(path: String): Boolean {
        val rel = normalizeRelativePath(path)
        if (rel == "library.json") return true
        return RESTRICTED_PREFIXES.any { rel.startsWith(it) }
    }

    private fun encodeRelativePath(rel: String): String =
        rel.split("/").joinToString("/") { seg ->
            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }

    /**
     * Pure gateway URL builder (M1, unit-tested).
     *
     * The gateway REJECTS %2F before decoding (encoded-slash smuggling
     * guard), so the `path` query value MUST carry literal '/' separators
     * with only each SEGMENT percent-encoded. OkHttp's
     * `addQueryParameter("path", rel)` encodes '/' as %2F and is therefore
     * unusable here — every restricted path (all contain '/') would be
     * rejected 400. This builder concatenates the encoded segments
     * manually; see WorkerActivationApiUrlTest for the pinned behaviour.
     */
    fun buildGatewayUrl(rel: String): String {
        return "$WORKER_BASE/v1/content?path=${encodeRelativePath(rel)}"
    }

    /**
     * Pure double-encoding guard (M9): after one decode, no segment may
     * still carry '%'. A remaining '%' means the input was double-encoded
     * (%252F -> %2F) or smuggles an encoded byte — reject, never fetch.
     */
    fun validateDecodedPath(decoded: String): Boolean {
        return decoded.split("/").none { it.contains('%') }
    }

    /**
     * Builds the request for a content file given its RAW relative path
     * under v1/ (e.g. "questions/foo.json", "books/x.pdf", "library.json").
     * Restricted paths go to GET {WORKER_BASE}/v1/content?path=... with
     * `Authorization: Bearer <token>`; free paths go directly to Pages.
     * Returns null ONLY when the path is restricted and no usable token
     * exists (expired + refresh failed, or never activated) — callers
     * must then show the activation prompt instead of fetching.
     *
     * Call only from a background thread: a restricted fetch with an
     * expired token performs one blocking refresh here.
     */
    fun contentRequest(context: Context, rawRelativePath: String): Request? {
        val rel = normalizeRelativePath(rawRelativePath)
        if (!isRestrictedPath(rel)) {
            return Request.Builder().url("${WataniApi.BASE_URL}${encodeRelativePath(rel)}").build()
        }
        val token = validTokenOrRefresh(context) ?: return null
        return Request.Builder()
            .url(buildGatewayUrl(rel))
            .header("Authorization", "Bearer $token")
            .build()
    }

    /**
     * Same switch point for absolute content URLs (e.g. LibraryItem.fileUrl
     * values such as https://watani-content.pages.dev/v1/books/...).
     * Pages /v1/ URLs are mapped to their relative path and routed above;
     * any other host is fetched directly, unchanged.
     */
    /**
     * Same switch point for absolute content URLs (e.g. LibraryItem.fileUrl
     * values such as https://watani-content.pages.dev/v1/books/...).
     * Pages /v1/ URLs are mapped to their relative path and routed above;
     * any other host is fetched directly, unchanged.
     *
     * @throws IllegalArgumentException with an Arabic message when the
     * decoded path still carries '%' in any segment (M9 double-encoding
     * guard). Callers must NOT turn this into an activation prompt: it
     * is a malformed URL, not a missing token.
     */
    fun contentRequestForUrl(context: Context, absoluteUrl: String): Request? {
        if (!absoluteUrl.startsWith(WataniApi.BASE_URL)) {
            return Request.Builder().url(absoluteUrl).build()
        }
        val remainder = absoluteUrl.removePrefix(WataniApi.BASE_URL)
        val decoded = try {
            URLDecoder.decode(remainder.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            remainder
        }
        if (!validateDecodedPath(decoded)) {
            throw IllegalArgumentException("رابط المحتوى غير صالح")
        }
        return contentRequest(context, decoded)
    }
}
