package sy.medpro.watani.data

import org.json.JSONObject

/** A successfully restored activation, as answered by POST /v1/activate/restore. */
data class RestoredActivation(val token: String, val code: String, val expiresAtMillis: Long)

/**
 * Pure parsing of the restore endpoint's answer (activation restore,
 * Nov 2026), extracted so the EXACT decision the app makes is
 * unit-testable off-device:
 * - success requires HTTP success AND `ok == true` AND a non-blank
 *   token AND a non-blank code — anything else is a rejection, and the
 *   caller keeps the manual code-entry path untouched.
 * - `expiresAt` accepts epoch millis, epoch seconds, or an ISO-8601
 *   string (the same shapes the activation/refresh answers use);
 *   absent/unparseable means 0 (no known expiry).
 */
object RestoreResponse {

    fun parse(httpSuccessful: Boolean, bodyText: String): RestoredActivation? {
        if (!httpSuccessful) return null
        val json = try {
            JSONObject(bodyText)
        } catch (_: Exception) {
            return null
        }
        if (!json.optBoolean("ok", false)) return null
        val token = json.optString("token", "")
        val code = json.optString("code", "")
        if (token.isBlank() || code.isBlank()) return null
        return RestoredActivation(token, code, parseExpiresAt(json))
    }

    fun parseExpiresAt(json: JSONObject): Long {
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
                    } ?: parseIso8601(s)
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}

/**
 * ISO-8601 parsing WITHOUT java.time (minSdk is 24 and core library
 * desugaring is not enabled, so java.time would be a NewApi hazard in
 * this new file): SimpleDateFormat patterns cover the shapes the
 * worker emits ("...Z", fractional seconds, numeric offsets).
 */
private fun parseIso8601(s: String): Long {
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val fmt = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            fmt.isLenient = false
            return fmt.parse(s)?.time ?: continue
        } catch (_: Exception) {
            // try the next pattern
        }
    }
    return 0L
}
