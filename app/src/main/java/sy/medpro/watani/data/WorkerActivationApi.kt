package sy.medpro.watani.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
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
import java.util.concurrent.atomic.AtomicBoolean

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
 * (the old Firestore validator was removed).
 * User/progress sync was migrated to the gateway in phase 3
 * ([WataniGatewaySync.ensureAndSyncUser] over /v1/user/me, using this
 * object's getFromWorker / putToWorker / postToWorker transport);
 * the Firestore sync object (WataniFirestoreSync) was deleted from
 * the tree in the shutdown round — the gateway is the only path.
 *
 * Single switch point for restricted content: every fetch of a restricted
 * path MUST go through [contentRequest] / [contentRequestForUrl] below.
 * Free paths keep fetching directly from Pages unchanged.
 *
 * No secret is hardcoded here: the only credential is the activation token
 * returned by POST /v1/activate, stored per account (Nov 2026: the
 * activation is saved WITH the Google account, so signing out and back
 * in with the same account keeps it, while a different account finds
 * no token and must activate itself). Per-account files are
 * `watani_activation_<uid>` (is_activated + activated_code +
 * activation_token + activation_expires_at); the legacy global file
 * `watani_activation` is only a one-time migration source (see
 * adoptLegacyTokenIfOwned). Device-level keys (device uuid, last FCM
 * token) stay in the global file. The code itself is stored; no code
 * secret exists beyond the code.
 */
object WorkerActivationApi {

    const val WORKER_BASE = "https://watani-api.drbasharmohammad.workers.dev"

    /** Legacy global prefs file (pre-per-account storage; kept as the
     * migration source and for device-level keys). Per-account files
     * are `watani_activation_<uid>` (see activationPrefs). */
    const val PREFS_NAME = "watani_activation"

    private const val KEY_ACTIVATED = "is_activated"
    private const val KEY_CODE = "activated_code"
    private const val KEY_TOKEN = "activation_token"
    private const val KEY_EXPIRES_AT = "activation_expires_at"
    private const val KEY_DEVICE_UUID = "device_uuid_fallback"

    /** Last FCM token uploaded to /v1/devices/register (dedup key). */
    private const val KEY_LAST_FCM_TOKEN = "last_fcm_token"

    /** Restricted prefixes under v1/ (contract) + the exact library.json file. */
    private val RESTRICTED_PREFIXES = listOf(
        "questions/",
        "questions_en/",
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

    /** Per-account activation storage (Nov 2026, sign-out bug fix):
     * the activation token lives in the signed-in account's own prefs
     * file, so it survives sign-out/sign-in with the same account and
     * is never inherited by a different account on this device. A null
     * uid (signed out) resolves to the legacy global file, whose
     * activation keys are only ever read for one-time migration. */
    private fun activationPrefs(context: Context, uid: String?) =
        context.getSharedPreferences(
            if (uid.isNullOrBlank()) PREFS_NAME else PREFS_NAME + "_" + uid,
            Context.MODE_PRIVATE
        )

    private fun currentUid(): String? =
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    /** Uid carried by a server token's payload (decoded, never trusted
     * for access — the server re-verifies the signature on every call).
     * Used only to decide whether a legacy global token belongs to the
     * currently signed-in account during one-time migration. */
    private fun tokenPayloadUid(token: String): String? {
        return try {
            val payload = token.substringBefore(".")
            val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE)
            org.json.JSONObject(String(decoded, Charsets.UTF_8)).optString("uid").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** One-time migration (Nov 2026): tokens stored before per-account
     * storage live in the legacy global file. Adopt one into the
     * current account's file ONLY when the token's own payload uid
     * matches the signed-in uid; a foreign token is left untouched for
     * its own account's later adoption. Runs only when the account's
     * file has no token yet, so it never overwrites a live activation. */
    private fun adoptLegacyTokenIfOwned(context: Context, uid: String) {
        val owned = activationPrefs(context, uid)
        if (!owned.getString(KEY_TOKEN, null).isNullOrBlank()) return
        val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = legacy.getString(KEY_TOKEN, null) ?: return
        if (token.isBlank()) return
        if (tokenPayloadUid(token) != uid) return
        owned.edit()
            .putBoolean(KEY_ACTIVATED, legacy.getBoolean(KEY_ACTIVATED, false))
            .putString(KEY_CODE, legacy.getString(KEY_CODE, null))
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, legacy.getLong(KEY_EXPIRES_AT, 0L))
            .apply()
        legacy.edit()
            .remove(KEY_ACTIVATED).remove(KEY_CODE).remove(KEY_TOKEN).remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun isActivated(context: Context): Boolean {
        val uid = currentUid() ?: return false
        adoptLegacyTokenIfOwned(context, uid)
        val prefs = activationPrefs(context, uid)
        if (!prefs.getBoolean(KEY_ACTIVATED, false)) return false
        val token = prefs.getString(KEY_TOKEN, null)
        if (token.isNullOrBlank()) return false
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        // expiresAt == 0 means the server gave no expiry: treat as valid.
        return expiresAt == 0L || expiresAt > System.currentTimeMillis()
    }

    /** True when the account was activated before but the token has expired. */
    fun hasExpiredToken(context: Context): Boolean {
        val uid = currentUid() ?: return false
        adoptLegacyTokenIfOwned(context, uid)
        val prefs = activationPrefs(context, uid)
        if (!prefs.getBoolean(KEY_ACTIVATED, false)) return false
        if (prefs.getString(KEY_TOKEN, null).isNullOrBlank()) return false
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return expiresAt != 0L && expiresAt <= System.currentTimeMillis()
    }

    fun activatedCode(context: Context): String? {
        val uid = currentUid() ?: return null
        adoptLegacyTokenIfOwned(context, uid)
        val prefs = activationPrefs(context, uid)
        return prefs.getString(KEY_CODE, null)
    }

    fun clearLocalActivation(context: Context) {
        activationPrefs(context, currentUid()).edit()
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
     * working offline). Silent: no UI event. Used by the deliberate
     * deactivation button and by gateway revocation. Sign-out
     * deliberately does NOT call it anymore (Nov 2026): activation is
     * stored per account, so a different account signing in finds no
     * token and must activate itself.
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

    private fun storeActivation(context: Context, uid: String?, code: String, token: String, expiresAt: Long) {
        activationPrefs(context, uid).edit()
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
                    storeActivation(context, user.uid, trimmed, token, parseExpiresAt(json))
                    // Best-effort FCM token upload: never fails activation.
                    try { uploadFcmToken(context) } catch (_: Exception) { }
                    Pair(true, "تم تفعيل الحساب والجهاز بنجاح!")
                }
            } catch (e: Exception) {
                Log.d("WorkerActivationApi", "activate network failure: ${e.localizedMessage}")
                Pair(false, "تعذر الاتصال بخادم التفعيل — تحقق من الشبكة وحاول مجددًا.")
            }
        }

    /**
     * Activation restore (Nov 2026, doctor's decision): ONE silent
     * attempt, made only at a successful Google sign-in moment when the
     * account has NO local activation. The gateway decides with the
     * same verifyAccess conditions as activation (uid taken from the
     * Firebase ID token only, 2-device limit, expiry/ban) and answers
     * {ok, token, expiresAt, code} on success.
     *
     * On success the returned activation is stored through the existing
     * [storeActivation] and the code prompt is simply never shown. On
     * ANY rejection or failure nothing changes: the manual code-entry
     * path stays exactly as it was. Daily entry never depends on this:
     * it runs only inside the sign-in LaunchedEffect, never on cold
     * start with an existing session, and never blocks the UI.
     *
     * Returns true only when an activation was restored and stored.
     */
    suspend fun tryRestoreActivation(context: Context, user: FirebaseUser): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (isActivated(context)) return@withContext false
                val idToken = try {
                    user.getIdToken(false).await().token
                } catch (_: Exception) {
                    null
                } ?: return@withContext false
                val bodyJson = JSONObject().apply {
                    put("idToken", idToken)
                    put("deviceId", deviceId(context))
                }.toString()
                val request = Request.Builder()
                    .url("$WORKER_BASE/v1/activate/restore")
                    .header("Authorization", "Bearer $idToken")
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: "{}"
                    // Pure decision (RestoreResponse — unit-tested):
                    // success only on HTTP success + ok:true + token +
                    // code; every other answer is a rejection and the
                    // manual code-entry path stays exactly as it was.
                    val restored = RestoreResponse.parse(resp.isSuccessful, text)
                        ?: return@withContext false
                    storeActivation(context, user.uid, restored.code, restored.token, restored.expiresAtMillis)
                    try { uploadFcmToken(context) } catch (_: Exception) { }
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Uploads the current FCM token to POST /v1/devices/register so the
     * admin panel can push to this device. Best-effort and idempotent:
     * never throws, uploads only when the token changed since the last
     * successful upload, and silently skips when nobody is signed in
     * (the next sign-in/activation upload covers it). Called at
     * activation, at sign-in, and from onNewToken.
     */
    suspend fun uploadFcmToken(context: Context) {
        try {
            withContext(Dispatchers.IO) {
                val token = try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                } catch (_: Exception) {
                    return@withContext
                }
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.getString(KEY_LAST_FCM_TOKEN, null) == token) return@withContext
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return@withContext
                val idToken = try {
                    user.getIdToken(false).await().token
                } catch (_: Exception) {
                    null
                } ?: return@withContext
                val bodyJson = JSONObject().apply {
                    put("idToken", idToken)
                    put("deviceId", deviceId(context))
                    put("fcmToken", token)
                }.toString()
                val request = Request.Builder()
                    .url("$WORKER_BASE/v1/devices/register")
                    .header("Authorization", "Bearer $idToken")
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: "{}"
                    if (resp.isSuccessful && JSONObject(text).optBoolean("ok", false)) {
                        prefs.edit().putString(KEY_LAST_FCM_TOKEN, token).apply()
                    }
                }
            }
        } catch (_: Exception) {
            // Best-effort: a later sign-in/activation upload retries.
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
     * Offline-activation fix (Nov 2026, doctor's order): the revocation
     * check runs ONLY when the device is online. An activated account
     * must keep working offline — so this function performs NO network
     * call at all without connectivity, and a failed/unreachable check
     * never cancels the activation nor hides any section (UNAVAILABLE
     * semantics, same as tryRefresh).
     *
     * When online it probes the Worker with the existing refresh path
     * (POST /v1/activate/refresh, which re-authorizes via verifyAccess:
     * device bound, not banned, code bound, code not expired). Only a
     * PROVEN server rejection (401/403) revokes: the local activation
     * is cleared, restricted downloads are wiped (M4, doctor's decision)
     * and the activation prompt event fires. A successful probe renews
     * the token (the refresh path's normal effect) — the code's own
     * duration, the 2-device limit and the gateway are untouched.
     *
     * Throttled to one attempt per 10 minutes (process lifetime) and
     * guarded against concurrent runs, so a flapping network or a
     * repeated entry cannot spam the Worker with refresh calls.
     */
    private val lastRevocationCheckMs = java.util.concurrent.atomic.AtomicLong(0L)
    private val revocationCheckInFlight = AtomicBoolean(false)

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun checkRevocationIfOnline(context: Context): RefreshOutcome {
        if (!isActivated(context)) return RefreshOutcome.UNAVAILABLE
        if (!isOnline(context)) return RefreshOutcome.UNAVAILABLE
        val now = System.currentTimeMillis()
        if (now - lastRevocationCheckMs.get() < 10 * 60 * 1000L) return RefreshOutcome.UNAVAILABLE
        if (!revocationCheckInFlight.compareAndSet(false, true)) return RefreshOutcome.UNAVAILABLE
        return try {
            lastRevocationCheckMs.set(now)
            when (val outcome = tryRefresh(context)) {
                RefreshOutcome.REVOKED -> {
                    // Proven by the server (401/403): clear + wipe + prompt.
                    notifyActivationLost(context)
                    outcome
                }
                else -> outcome
            }
        } finally {
            revocationCheckInFlight.set(false)
        }
    }

    /**
     * Returns (fresh token or null, serverRejected401_403).
     * STRICT ok check: a response WITHOUT `ok: true` is rejected even
     * when it carries a token — a missing `ok` must never be read as
     * success (the old `optBoolean("ok", true)` default did exactly that).
     */
    private fun refreshInternal(context: Context): Pair<String?, Boolean> {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return Pair(null, false)
        adoptLegacyTokenIfOwned(context, uid)
        val prefs = activationPrefs(context, uid)
        val oldToken = prefs.getString(KEY_TOKEN, null) ?: return Pair(null, false)
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
                storeActivation(context, uid, code, token, parseExpiresAt(json))
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
        val uid = currentUid() ?: return null
        adoptLegacyTokenIfOwned(context, uid)
        val prefs = activationPrefs(context, uid)
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
     * URL is malformed: encoded slashes/backslashes in any RAW segment
     * (detector b7 — decoding first would turn %2F into a real
     * separator and build a gateway URL for a DIFFERENT file), or when
     * the decoded path still carries '%' in any segment (M9
     * double-encoding guard). Callers must NOT turn this into an
     * activation prompt: it is a malformed URL, not a missing token.
     */
    fun contentRequestForUrl(context: Context, absoluteUrl: String): Request? {
        if (!absoluteUrl.startsWith(WataniApi.BASE_URL)) {
            return Request.Builder().url(absoluteUrl).build()
        }
        val remainder = absoluteUrl.removePrefix(WataniApi.BASE_URL)
        // Detector fix (b7): reject encoded separators BEFORE decoding.
        if (remainder.split("/").any {
                it.contains("%2F", ignoreCase = true) || it.contains("%5C", ignoreCase = true)
            }) {
            throw IllegalArgumentException("رابط المحتوى غير صالح")
        }
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

    /**
     * نتيجة نداء Worker برمز Google ID (للمسارات غير المقيدة بالتفعيل:
     * الصدارة والملف الشخصي وتثبيت النقاط).
     * code: كما رد الخادم (200/401/403/...)؛ -1 عند غياب الرمز (جلسة منتهية)؛
     * -2 عند فشل الشبكة قبل الرد. المستدعي يميّز الحالات ولا يطويها بخطأ عام.
     */
    data class WorkerIdResult(val code: Int, val body: String?)

    private suspend fun googleIdToken(user: FirebaseUser): String? = try {
        user.getIdToken(false).await().token
    } catch (_: Exception) {
        null
    }

    suspend fun getFromWorker(user: FirebaseUser, path: String): WorkerIdResult = withContext(Dispatchers.IO) {
        val token = googleIdToken(user) ?: return@withContext WorkerIdResult(-1, null)
        try {
            val request = Request.Builder()
                .url("$WORKER_BASE$path")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                WorkerIdResult(response.code, response.body?.string())
            }
        } catch (_: Exception) {
            WorkerIdResult(-2, null)
        }
    }

    suspend fun putToWorker(user: FirebaseUser, path: String, json: JSONObject): WorkerIdResult = withContext(Dispatchers.IO) {
        val token = googleIdToken(user) ?: return@withContext WorkerIdResult(-1, null)
        try {
            val request = Request.Builder()
                .url("$WORKER_BASE$path")
                .header("Authorization", "Bearer $token")
                .put(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                WorkerIdResult(response.code, response.body?.string())
            }
        } catch (_: Exception) {
            WorkerIdResult(-2, null)
        }
    }

    /**
     * Public exam date (migration phase 3): GET /v1/config/exam-date
     * needs no token (the Firestore doc it replaces was read with no
     * activation context either). Fire-and-forget via enqueue, exactly
     * like the old Firestore listener: the callback receives the
     * "yyyy-MM-dd" string, or null on any failure/absence — offline or
     * a missing date is silently ignored by the caller.
     */
    fun fetchExamDateAsync(onResult: (String?) -> Unit) {
        try {
            val request = Request.Builder()
                .url("$WORKER_BASE/v1/config/exam-date")
                .get()
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    onResult(null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val date = try {
                        response.use { resp ->
                            if (!resp.isSuccessful) null
                            else {
                                val bodyText = resp.body?.string() ?: ""
                                val raw = JSONObject(bodyText).optString("examDate", "")
                                raw.takeIf { it.isNotBlank() }
                            }
                        }
                    } catch (_: Exception) {
                        null
                    }
                    onResult(date)
                }
            })
        } catch (_: Exception) {
            onResult(null)
        }
    }

    /**
     * Public contact links (home-package contract): GET
     * /v1/config/contact-links needs no token, exactly like the exam
     * date. Fire-and-forget via enqueue: the callback receives a map
     * with the four contract keys (supportBot / wataniChannel /
     * medproChannel / instagram — full URLs or empty strings), or null
     * on any failure / non-ok response, so callers keep their last
     * good cached values offline.
     */
    fun fetchContactLinksAsync(onResult: (Map<String, String>?) -> Unit) {
        try {
            val request = Request.Builder()
                .url("$WORKER_BASE/v1/config/contact-links")
                .get()
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    onResult(null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val links = try {
                        response.use { resp ->
                            if (!resp.isSuccessful) null
                            else {
                                val bodyText = resp.body?.string() ?: ""
                                val json = JSONObject(bodyText)
                                if (!json.optBoolean("ok", false)) null
                                else mapOf(
                                    "supportBot" to json.optString("supportBot", ""),
                                    "wataniChannel" to json.optString("wataniChannel", ""),
                                    "medproChannel" to json.optString("medproChannel", ""),
                                    "instagram" to json.optString("instagram", "")
                                )
                            }
                        }
                    } catch (_: Exception) {
                        null
                    }
                    onResult(links)
                }
            })
        } catch (_: Exception) {
            onResult(null)
        }
    }

    /**
     * min_version floor (build 1.2.3, protection phase 2): GET
     * /v1/config is the gateway's public config channel — the same one
     * the exam date and contact links already ride on. Its payload
     * carries `minVersion` (the DO's app_config.min_version). The
     * callback receives the floor, or null on ANY failure/absence —
     * offline, a missing field, or a malformed body all mean "no
     * floor", and the caller must treat null as no restriction at all.
     * Nothing here changes any server value: the app only READS the
     * floor; raising it stays an admin-side act for a later phase.
     */
    fun fetchMinVersionAsync(onResult: (Int?) -> Unit) {
        try {
            val request = Request.Builder()
                .url("$WORKER_BASE/v1/config")
                .get()
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    onResult(null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val floor = try {
                        response.use { resp ->
                            if (!resp.isSuccessful) null
                            else {
                                val json = JSONObject(resp.body?.string() ?: "")
                                val raw = if (json.has("minVersion")) {
                                    json.optInt("minVersion", 0)
                                } else {
                                    json.optInt("min_version", 0)
                                }
                                raw.takeIf { it > 0 }
                            }
                        }
                    } catch (_: Exception) {
                        null
                    }
                    onResult(floor)
                }
            })
        } catch (_: Exception) {
            onResult(null)
        }
    }

    suspend fun postToWorker(user: FirebaseUser, path: String, json: JSONObject): WorkerIdResult = withContext(Dispatchers.IO) {
        val token = googleIdToken(user) ?: return@withContext WorkerIdResult(-1, null)
        try {
            val request = Request.Builder()
                .url("$WORKER_BASE$path")
                .header("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                WorkerIdResult(response.code, response.body?.string())
            }
        } catch (_: Exception) {
            WorkerIdResult(-2, null)
        }
    }
}
