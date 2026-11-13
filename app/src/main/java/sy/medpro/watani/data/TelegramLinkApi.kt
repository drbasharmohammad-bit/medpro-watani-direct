package sy.medpro.watani.data

import com.google.firebase.auth.FirebaseUser
import org.json.JSONObject

/**
 * Telegram link client (doctor's order, 14 Nov 2026): the optional
 * drawer-only link between the student's app account and the Watani
 * Telegram bot, so periodic study reports (mistakes, weak topics,
 * progress and activity) plus study tips can reach his Telegram.
 *
 * Gateway contract (live, verified — do not invent another):
 *  - POST   /v1/telegram/link-code → {ok, code, expiresAt (ms),
 *    deepLink: "https://t.me/medpro_watani_bot?start=<CODE>"}
 *    The code lives 15 minutes and is single-use; asking for a new
 *    code invalidates the previous one.
 *  - GET    /v1/telegram/link → {ok, linked:false} or
 *    {ok, linked:true, username, linkedAt}.
 *  - DELETE /v1/telegram/link → {ok, linked:false}.
 *
 * Transport is [WorkerActivationApi] (same WORKER_BASE, same Firebase
 * ID-token fetch as every other /v1/user call). Every failure reads as
 * null / false — the screen shows its gentle network-error state with
 * a retry; nothing here ever throws into the UI.
 */
object TelegramLinkApi {

    data class LinkStatus(
        val linked: Boolean,
        val username: String?,
        val linkedAt: Long
    )

    data class LinkCode(
        val code: String,
        val deepLink: String,
        val expiresAt: Long
    )

    /** Current link state, or null on any failure (network, non-200,
     * bad body, missing session token). */
    suspend fun fetchLinkStatus(user: FirebaseUser): LinkStatus? {
        val res = WorkerActivationApi.getFromWorker(user, "/v1/telegram/link")
        if (res.code != 200) return null
        val root = try {
            JSONObject(res.body ?: "")
        } catch (_: Exception) {
            return null
        }
        if (!root.optBoolean("ok", false)) return null
        if (!root.optBoolean("linked", false)) return LinkStatus(false, null, 0L)
        val username = root.optString("username", "").takeIf { it.isNotBlank() }
        return LinkStatus(true, username, root.optLong("linkedAt", 0L))
    }

    /** Creates a fresh one-time link code (invalidates any previous
     * unused code server-side). Null on any failure or when the answer
     * carries no usable deep link. */
    suspend fun createLinkCode(user: FirebaseUser): LinkCode? {
        val res = WorkerActivationApi.postToWorker(user, "/v1/telegram/link-code", JSONObject())
        if (res.code != 200) return null
        val root = try {
            JSONObject(res.body ?: "")
        } catch (_: Exception) {
            return null
        }
        if (!root.optBoolean("ok", false)) return null
        val deepLink = root.optString("deepLink", "")
        if (deepLink.isBlank()) return null
        return LinkCode(
            code = root.optString("code", ""),
            deepLink = deepLink,
            expiresAt = root.optLong("expiresAt", 0L)
        )
    }

    /** Removes the link. True only on 200 + ok:true. */
    suspend fun unlink(user: FirebaseUser): Boolean {
        val res = WorkerActivationApi.deleteFromWorker(user, "/v1/telegram/link")
        if (res.code != 200) return false
        return try {
            JSONObject(res.body ?: "").optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }
}
