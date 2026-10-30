package sy.medpro.watani.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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
    // Device id — same rule as the old Firestore code.
    // ------------------------------------------------------------------

    fun deviceId(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        return if (!androidId.isNullOrBlank()) androidId else (Build.MODEL ?: "AndroidDevice")
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
     * Returns Pair(success, message) where message is the EXACT server
     * error text on failure (the `error` field, verbatim) so the UI can
     * show it without paraphrasing.
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
                        // Exact server error text; never paraphrased.
                        val serverError = json.optString("error", "").takeIf { it.isNotBlank() }
                            ?: "خطأ في التفعيل (HTTP ${resp.code})"
                        return@withContext Pair(false, serverError)
                    }
                    val token = json.optString("token", "")
                    if (token.isBlank()) {
                        return@withContext Pair(false, "استجابة التفعيل بلا رمز — أعد المحاولة.")
                    }
                    storeActivation(context, trimmed, token, parseExpiresAt(json))
                    Pair(true, "تم تفعيل الحساب والجهاز بنجاح!")
                }
            } catch (e: Exception) {
                Pair(false, "تعذر الاتصال بخادم التفعيل: ${e.localizedMessage ?: "خطأ شبكة"}")
            }
        }

    /**
     * Tries a token refresh when the stored token expired. Returns true
     * when a fresh token was stored. Callers on the UI thread must call
     * this from a coroutine (it performs network I/O).
     */
    suspend fun tryRefresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        refreshBlocking(context) != null
    }

    /** Blocking refresh used by the fetch layer (already on an IO thread). */
    private fun refreshBlocking(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldToken = prefs.getString(KEY_TOKEN, null) ?: return null
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return null
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
                if (!resp.isSuccessful || !json.optBoolean("ok", true)) return null
                val token = json.optString("token", "")
                if (token.isBlank()) return null
                val code = prefs.getString(KEY_CODE, null) ?: ""
                storeActivation(context, code, token, parseExpiresAt(json))
                token
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * A usable token for the fetch layer: the stored one when still valid,
     * otherwise one blocking refresh attempt. Null = no usable token
     * (caller must surface the activation prompt, not fetch restricted
     * content directly).
     */
    fun validTokenOrRefresh(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        if (token.isNullOrBlank()) return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt == 0L || expiresAt > System.currentTimeMillis() + 60_000L) return token
        return refreshBlocking(context)
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
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host(WORKER_BASE.removePrefix("https://"))
            .addPathSegments("v1/content")
            .addQueryParameter("path", rel)
            .build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
    }

    /**
     * Same switch point for absolute content URLs (e.g. LibraryItem.fileUrl
     * values such as https://watani-content.pages.dev/v1/books/...).
     * Pages /v1/ URLs are mapped to their relative path and routed above;
     * any other host is fetched directly, unchanged.
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
        return contentRequest(context, decoded)
    }
}
