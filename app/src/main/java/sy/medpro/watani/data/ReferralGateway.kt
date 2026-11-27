package sy.medpro.watani.data

import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

/**
 * نظام إحالة الطلاب (build 1.2.54): gateway seam on the tips /
 * logbook pattern — Firebase ID token auth via [WorkerActivationApi],
 * the server extracting the uid from the verified token; the client
 * never sends a uid.
 *
 * Contract (shared with the gateway, fixed):
 * - GET  /v1/referral/me    → {code, count, registeredCount,
 *   threshold, remainingForNextReward, rewards[], referredBy,
 *   canClaim, claimDeadline, activation{activated, expiresAt}}
 *   where `count` is ACTIVATED referrals only (the doctor's
 *   corrected spec: registrations alone never move the counter;
 *   only a referred account's paid-code activation counts, and
 *   referral-reward activations never count for anyone).
 * - POST /v1/referral/claim {code} → records the attribution once
 *   (errors: invalid_code / self_referral / already_claimed /
 *   account_too_old).
 */

data class ReferralReward(
    val milestone: Int,
    val code: String,
    /** Epoch ms; 0 = the reward never expires (grandfathered base). */
    val expiresAt: Long,
    val createdAt: Long
)

data class ReferralMe(
    /** Stored form without the display dash (e.g. MPAB3DEF). */
    val code: String,
    /** ACTIVATED referrals — the headline counter. */
    val count: Int,
    /** Attributions recorded (registered, maybe not yet activated). */
    val registeredCount: Int,
    val threshold: Int,
    val remainingForNextReward: Int,
    val rewards: List<ReferralReward>,
    val referredByCode: String?,
    val referredByName: String?,
    val canClaim: Boolean,
    val activationActivated: Boolean,
    val activationExpiresAt: Long
) {
    /** Display form with the dash: MP-AB3DEF. */
    val displayCode: String
        get() = if (code.length > 2) code.substring(0, 2) + "-" + code.substring(2) else code
}

data class ReferralMeReply(val code: Int, val me: ReferralMe? = null) {
    val isSuccess: Boolean get() = code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX && me != null
    val isAuthFailure: Boolean get() = code == HTTP_UNAUTHORIZED || code == NO_TOKEN_CODE
}

data class ReferralClaimReply(val code: Int, val error: String? = null) {
    val isSuccess: Boolean get() = code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
}

/** Fallback threshold when the gateway omits it (its own default). */
const val REFERRAL_DEFAULT_THRESHOLD: Int = 7

/** Parses the GET /v1/referral/me payload. Pure — pinned by tests. */
fun parseReferralMe(obj: JSONObject): ReferralMe? {
    val code = obj.optString("code", "").trim()
    if (!obj.optBoolean("ok", false) || code.isBlank()) return null
    val rewards = mutableListOf<ReferralReward>()
    val arr = obj.optJSONArray("rewards")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            rewards += ReferralReward(
                milestone = r.optInt("milestone", 0),
                code = r.optString("code", ""),
                expiresAt = r.optLong("expiresAt", 0L),
                createdAt = r.optLong("createdAt", 0L)
            )
        }
    }
    val referredBy = if (obj.isNull("referredBy")) null else obj.optJSONObject("referredBy")
    val activation = obj.optJSONObject("activation")
    return ReferralMe(
        code = code,
        count = obj.optInt("count", 0),
        registeredCount = obj.optInt("registeredCount", 0),
        threshold = obj.optInt("threshold", REFERRAL_DEFAULT_THRESHOLD).coerceAtLeast(1),
        remainingForNextReward = obj.optInt("remainingForNextReward", 0),
        rewards = rewards,
        referredByCode = referredBy?.optString("code", "")?.takeIf { it.isNotEmpty() },
        referredByName = referredBy?.optString("referrerName", "")?.takeIf { it.isNotEmpty() },
        canClaim = obj.optBoolean("canClaim", false),
        activationActivated = activation?.optBoolean("activated", false) ?: false,
        activationExpiresAt = activation?.optLong("expiresAt", 0L) ?: 0L
    )
}

/** Maps a gateway claim error string to the student-facing message. */
fun referralClaimErrorMessage(error: String?): String = when (error) {
    "invalid_code" -> "رمز الإحالة غير صحيح — تأكد منه وحاول مجددًا"
    "self_referral" -> "هذا رمزك أنت — شاركه مع أصدقائك ولا تدخله هنا"
    "already_claimed" -> "سبق أن سجّلت رمز إحالة لحسابك"
    "account_too_old" -> "إدخال رمز الإحالة متاح للحسابات الجديدة فقط"
    "auth" -> "سجّل الدخول أولًا ثم حاول مجددًا"
    else -> "تعذّر تسجيل الرمز — تحقق من الاتصال وحاول مجددًا"
}

interface ReferralGateway {
    suspend fun fetchMine(): ReferralMeReply
    suspend fun claim(code: String): ReferralClaimReply
}

/**
 * One-time «عندك رمز إحالة؟» prompt bookkeeping (build 1.2.54):
 * the prompt is offered ONCE per account (first sign-in moment on
 * a build that has the feature, and only when the gateway says the
 * account can still claim). Any dismissal — claimed, skipped, or
 * closed — marks it done forever; the permanent claim field lives
 * in «ادعُ أصدقاءك» for the whole claim window.
 */
object ReferralPromptStore {
    private const val PREFS = "watani_settings"
    private fun key(uid: String) = "referral_prompt_done_$uid"

    fun isDone(context: android.content.Context, uid: String?): Boolean {
        if (uid.isNullOrBlank()) return true
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(key(uid), false)
    }

    fun markDone(context: android.content.Context, uid: String?) {
        if (uid.isNullOrBlank()) return
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(key(uid), true).apply()
    }
}

/** The production gateway: the signed-in Firebase user + the shared
 * worker client. Every call fails soft (code in the reply). */
class WorkerReferralGateway : ReferralGateway {

    private fun currentUser() = FirebaseAuth.getInstance().currentUser

    override suspend fun fetchMine(): ReferralMeReply {
        val user = currentUser() ?: return ReferralMeReply(NO_TOKEN_CODE)
        val res = WorkerActivationApi.getFromWorker(user, "/v1/referral/me")
        val parsed = try {
            res.body?.let { JSONObject(it) }?.let { parseReferralMe(it) }
        } catch (_: Exception) {
            null
        }
        return ReferralMeReply(res.code, parsed)
    }

    override suspend fun claim(code: String): ReferralClaimReply {
        val user = currentUser() ?: return ReferralClaimReply(NO_TOKEN_CODE, "auth")
        val payload = JSONObject().put("code", code)
        val res = WorkerActivationApi.postToWorker(user, "/v1/referral/claim", payload)
        val error = try {
            res.body?.let { JSONObject(it) }?.optString("error", "")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        return ReferralClaimReply(res.code, if (res.code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) null else error)
    }
}
