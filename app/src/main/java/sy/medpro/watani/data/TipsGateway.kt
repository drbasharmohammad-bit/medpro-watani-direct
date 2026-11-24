package sy.medpro.watani.data

import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

/**
 * «زوادة الطالب» gateway seam (build 1.2.51): every tips call to
 * the worker goes through this interface, on the Logbook pattern —
 * Firebase ID token auth via [WorkerActivationApi] (`postToWorker` /
 * `getFromWorker` / `deleteFromWorker`), the server extracting the
 * uid from the verified token; the client never sends a uid.
 *
 * The seam exists so the sync engine's full flow (offline add →
 * flush → moderation verdict → pack appearance) is testable
 * off-device with a fake gateway — the layer-3 pattern of this repo.
 *
 * Contract (shared with the gateway + admin panel, fixed):
 * - POST   /v1/tips            {subject, section, text, displayName, anonymous, clientId}
 * - GET    /v1/tips/mine?since=
 * - POST   /v1/tips/:id/edit   {text}
 * - DELETE /v1/tips/:id
 * - POST   /v1/tips/:id/report {reason}
 * - GET    /v1/config → tipsWriting ('all' | 'activated')
 */

/** HTTP success band + the worker client's local failure codes
 * (WorkerActivationApi convention: -1 no token, -2 exception). */
const val HTTP_SUCCESS_MIN: Int = 200
const val HTTP_SUCCESS_MAX: Int = 299
const val HTTP_UNAUTHORIZED: Int = 401
const val NO_TOKEN_CODE: Int = -1

data class TipsGatewayReply(
    /** HTTP code; negative = no request was made / transport failure
     * (WorkerActivationApi convention: -1 no token, -2 exception). */
    val code: Int,
    val serverId: String? = null
) {
    val isSuccess: Boolean get() = code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
    val isAuthFailure: Boolean get() = code == HTTP_UNAUTHORIZED || code == NO_TOKEN_CODE
}

data class TipsMineReply(
    val code: Int,
    val tips: List<ServerTip> = emptyList()
) {
    val isSuccess: Boolean get() = code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
}

interface TipsGateway {
    suspend fun submit(tip: OutboxTip): TipsGatewayReply
    suspend fun edit(serverId: String, text: String): TipsGatewayReply
    suspend fun delete(serverId: String): TipsGatewayReply
    suspend fun fetchMine(since: Long): TipsMineReply
    suspend fun report(serverId: String, reason: String): TipsGatewayReply

    /** 'all' | 'activated' | null when unknown (offline / not yet
     * published by the gateway) — null must behave as 'all'. */
    suspend fun fetchTipsWriting(): String?
}

/** Parses one entry of the GET /v1/tips/mine payload. Pure — shared
 * by the worker gateway and pinned by tests. */
fun parseServerTip(obj: JSONObject): ServerTip? {
    val id = obj.optString("id", "")
    if (id.isBlank()) return null
    val author = if (obj.isNull("author")) null
        else obj.optString("author", "").trim().takeIf { it.isNotEmpty() }
    return ServerTip(
        id = id,
        clientId = if (obj.isNull("clientId")) null else obj.optString("clientId", "").takeIf { it.isNotEmpty() },
        subject = obj.optString("subject", ""),
        sectionId = obj.optString("section", obj.optString("sectionId", "")),
        text = obj.optString("text", ""),
        author = author,
        status = obj.optString("status", "pending_review"),
        rejectReason = if (obj.isNull("rejectReason")) null
            else obj.optString("rejectReason", "").takeIf { it.isNotEmpty() },
        createdAt = obj.optLong("createdAt", 0L)
    )
}

/** The production gateway: the signed-in Firebase user + the shared
 * worker client. Every call fails soft (negative/HTTP code in the
 * reply) — the outbox, never the student, absorbs failures. */
class WorkerTipsGateway : TipsGateway {

    private fun currentUser() = FirebaseAuth.getInstance().currentUser

    override suspend fun submit(tip: OutboxTip): TipsGatewayReply {
        val user = currentUser() ?: return TipsGatewayReply(-1)
        val body = JSONObject().apply {
            put("subject", tip.subject)
            put("section", tip.sectionId)
            put("text", tip.text)
            put("displayName", tip.authorName ?: JSONObject.NULL)
            put("anonymous", tip.authorName == null)
            put("clientId", tip.localId)
        }
        val res = WorkerActivationApi.postToWorker(user, "/v1/tips", body)
        val serverId = try {
            res.body?.let { JSONObject(it) }?.optString("id", "")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        return TipsGatewayReply(res.code, serverId)
    }

    override suspend fun edit(serverId: String, text: String): TipsGatewayReply {
        val user = currentUser() ?: return TipsGatewayReply(-1)
        val body = JSONObject().apply { put("text", text) }
        val res = WorkerActivationApi.postToWorker(user, "/v1/tips/$serverId/edit", body)
        return TipsGatewayReply(res.code, serverId)
    }

    override suspend fun delete(serverId: String): TipsGatewayReply {
        val user = currentUser() ?: return TipsGatewayReply(-1)
        val res = WorkerActivationApi.deleteFromWorker(user, "/v1/tips/$serverId")
        return TipsGatewayReply(res.code, serverId)
    }

    override suspend fun fetchMine(since: Long): TipsMineReply {
        val user = currentUser()
        return if (user == null) {
            TipsMineReply(NO_TOKEN_CODE)
        } else {
            val res = WorkerActivationApi.getFromWorker(user, "/v1/tips/mine?since=$since")
            if (res.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                TipsMineReply(res.code)
            } else {
                TipsMineReply(res.code, parseMineTips(res.body))
            }
        }
    }

    /** Parse the «mine» payload defensively: any malformation is
     * an empty list, never a crash in a sync round. */
    private fun parseMineTips(body: String?): List<ServerTip> = try {
        val root = JSONObject(body ?: "")
        val arr = root.optJSONArray("tips") ?: root.optJSONArray("entries")
        if (arr == null) {
            emptyList()
        } else {
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseServerTip(it) } }
        }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun report(serverId: String, reason: String): TipsGatewayReply {
        val user = currentUser() ?: return TipsGatewayReply(-1)
        val body = JSONObject().apply { put("reason", reason) }
        val res = WorkerActivationApi.postToWorker(user, "/v1/tips/$serverId/report", body)
        return TipsGatewayReply(res.code, serverId)
    }

    override suspend fun fetchTipsWriting(): String? {
        val config = WorkerActivationApi.fetchAppConfig() ?: return null
        val raw = config.optString("tipsWriting", "").trim().lowercase()
        return raw.takeIf { it == "all" || it == "activated" }
    }
}
