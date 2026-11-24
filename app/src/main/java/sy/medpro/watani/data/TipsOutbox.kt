package sy.medpro.watani.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * «زوادة الطالب» outbox (build 1.2.51): the student's own tips live
 * LOCAL-FIRST. Every add/edit/delete is written to the outbox on the
 * device immediately (the submit always "succeeds" locally — the
 * network never blocks the student), and a sync engine flushes the
 * outbox to the gateway when connectivity allows: at submit time, at
 * app start, and when connectivity returns.
 *
 * State machine (per entry):
 *
 *   (new) ──enqueue──▶ PENDING_SEND ──POST ok──▶ PENDING_REVIEW
 *                          │                        │
 *                          │ POST fails             ├─ mine: published ─▶ PUBLISHED
 *                          ▼ (stays, retried)       └─ mine: rejected ──▶ REJECTED (+reason)
 *   PUBLISHED ──edit──▶ PENDING_SEND (serverId kept; flush uses the
 *                          edit endpoint and the tip returns to
 *                          PENDING_REVIEW — the student is told an
 *                          edit re-enters moderation)
 *   any ──delete──▶ serverId == null: dropped at once (never existed
 *                   server-side); serverId != null: tombstone
 *                   (deleted=true, PENDING_SEND) until DELETE confirms.
 *
 * All transitions are pure functions over the entry list (the
 * [TipsOutboxLogic] object) so the machine is unit-testable
 * off-device; persistence is a JSON round-trip in the same file.
 */

enum class TipLocalStatus { PENDING_SEND, PENDING_REVIEW, PUBLISHED, REJECTED }

data class OutboxTip(
    val localId: String,
    val serverId: String? = null,
    val subject: String,
    val sectionId: String,
    val text: String,
    /** Null = anonymous («بدون اسم»). */
    val authorName: String? = null,
    val status: TipLocalStatus = TipLocalStatus.PENDING_SEND,
    val rejectReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("localId", localId)
        put("serverId", serverId ?: JSONObject.NULL)
        put("subject", subject)
        put("sectionId", sectionId)
        put("text", text)
        put("authorName", authorName ?: JSONObject.NULL)
        put("status", status.name)
        put("rejectReason", rejectReason ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("deleted", deleted)
    }

    companion object {
        fun fromJson(obj: JSONObject): OutboxTip? {
            val localId = obj.optString("localId", "")
            if (localId.isBlank()) return null
            val status = try {
                TipLocalStatus.valueOf(obj.optString("status", "PENDING_SEND"))
            } catch (_: Exception) {
                TipLocalStatus.PENDING_SEND
            }
            return OutboxTip(
                localId = localId,
                serverId = if (obj.isNull("serverId")) null else obj.optString("serverId", "").takeIf { it.isNotEmpty() },
                subject = obj.optString("subject", ""),
                sectionId = obj.optString("sectionId", ""),
                text = obj.optString("text", ""),
                authorName = if (obj.isNull("authorName")) null else obj.optString("authorName", "").takeIf { it.isNotEmpty() },
                status = status,
                rejectReason = if (obj.isNull("rejectReason")) null else obj.optString("rejectReason", "").takeIf { it.isNotEmpty() },
                createdAt = obj.optLong("createdAt", 0L),
                updatedAt = obj.optLong("updatedAt", 0L),
                deleted = obj.optBoolean("deleted", false)
            )
        }
    }
}

@Suppress("TooManyFunctions")
object TipsOutboxLogic {

    fun newLocalId(): String = "tip-" + UUID.randomUUID().toString()

    fun serialize(entries: List<OutboxTip>): String {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun deserialize(raw: String?): List<OutboxTip> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { OutboxTip.fromJson(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun enqueue(entries: List<OutboxTip>, tip: OutboxTip): List<OutboxTip> =
        listOf(tip) + entries

    /** POST succeeded: the tip now sits in the moderation queue. */
    fun markSent(entries: List<OutboxTip>, localId: String, serverId: String?, now: Long): List<OutboxTip> =
        entries.map {
            if (it.localId == localId && !it.deleted) it.copy(
                serverId = serverId ?: it.serverId,
                status = TipLocalStatus.PENDING_REVIEW,
                rejectReason = null,
                updatedAt = now
            ) else it
        }

    /** Edit-endpoint succeeded: a published tip that was edited is
     * back in the moderation queue. */
    fun markEdited(entries: List<OutboxTip>, localId: String, now: Long): List<OutboxTip> =
        entries.map {
            if (it.localId == localId && !it.deleted) it.copy(
                status = TipLocalStatus.PENDING_REVIEW,
                rejectReason = null,
                updatedAt = now
            ) else it
        }

    /** DELETE confirmed server-side: the tombstone can go. */
    fun markDeleteConfirmed(entries: List<OutboxTip>, localId: String): List<OutboxTip> =
        entries.filterNot { it.localId == localId }

    /**
     * Edit: the text changes locally at once and the entry returns
     * to PENDING_SEND so the flush pushes it (create when it never
     * reached the server, edit endpoint otherwise). Editing a
     * PUBLISHED tip therefore visibly re-enters moderation after the
     * flush — the UI states this before the student confirms.
     */
    fun edit(entries: List<OutboxTip>, localId: String, newText: String, now: Long): List<OutboxTip> =
        entries.map {
            if (it.localId == localId && !it.deleted) it.copy(
                text = newText,
                status = TipLocalStatus.PENDING_SEND,
                rejectReason = null,
                updatedAt = now
            ) else it
        }

    /** Delete: never-sent entries vanish; sent ones tombstone until
     * the DELETE round-trip confirms. */
    fun delete(entries: List<OutboxTip>, localId: String, now: Long): List<OutboxTip> =
        entries.flatMap {
            when {
                it.localId != localId -> listOf(it)
                it.serverId == null -> emptyList()
                else -> listOf(it.copy(deleted = true, status = TipLocalStatus.PENDING_SEND, updatedAt = now))
            }
        }

    /** Entries the «إضافاتي» screen shows: tombstones hidden, newest first. */
    fun visibleMine(entries: List<OutboxTip>): List<OutboxTip> =
        entries.filter { !it.deleted }.sortedByDescending { it.createdAt }

    /** Entries the flush still owes the server. */
    fun pendingFlush(entries: List<OutboxTip>): List<OutboxTip> =
        entries.filter { it.status == TipLocalStatus.PENDING_SEND }
            .sortedBy { it.updatedAt }

    /**
     * Merge the server's «mine» list into the local outbox:
     * - Matched by serverId, or by the clientId the POST echoed back.
     * - The server's moderation state wins for entries with nothing
     *   unsent (a local PENDING_SEND entry keeps its local text and
     *   state — the flush has not pushed it yet).
     * - Server entries unknown locally are adopted (another device,
     *   or a reinstall with the server copy surviving).
     * - Local entries the server no longer returns AND that carry a
     *   serverId AND have nothing unsent are dropped (deleted
     *   server-side / moderation removed them).
     */
    fun applyServerMine(entries: List<OutboxTip>, server: List<ServerTip>, now: Long): List<OutboxTip> {
        val byServerId = server.associateBy { it.id }
        val byClientId = server.mapNotNull { s -> s.clientId?.let { it to s } }.toMap()
        val matchedServerIds = mutableSetOf<String>()
        val merged = entries.mapNotNull { local ->
            val match = (local.serverId?.let { byServerId[it] })
                ?: byClientId[local.localId]
            when {
                match != null -> {
                    matchedServerIds.add(match.id)
                    if (local.deleted || local.status == TipLocalStatus.PENDING_SEND) {
                        // Unsent local intent (tombstone or edit) wins
                        // until the flush resolves it; only the
                        // serverId binding is learned.
                        local.copy(serverId = match.id)
                    } else {
                        local.copy(
                            serverId = match.id,
                            status = statusFromServer(match.status),
                            rejectReason = match.rejectReason,
                            updatedAt = maxOf(local.updatedAt, now)
                        )
                    }
                }
                local.serverId != null && local.status != TipLocalStatus.PENDING_SEND && !local.deleted -> null
                else -> local
            }
        }
        val adopted = server.filter { it.id !in matchedServerIds }.map { s ->
            OutboxTip(
                localId = "srv-" + s.id,
                serverId = s.id,
                subject = s.subject,
                sectionId = s.sectionId,
                text = s.text,
                authorName = s.author,
                status = statusFromServer(s.status),
                rejectReason = s.rejectReason,
                createdAt = if (s.createdAt > 0) s.createdAt else now,
                updatedAt = now
            )
        }
        return merged + adopted
    }

    fun statusFromServer(raw: String): TipLocalStatus = when (raw.trim().lowercase()) {
        "published", "approved" -> TipLocalStatus.PUBLISHED
        "rejected" -> TipLocalStatus.REJECTED
        else -> TipLocalStatus.PENDING_REVIEW
    }

    /** Arabic status label for «إضافاتي». */
    fun statusLabel(status: TipLocalStatus): String = when (status) {
        TipLocalStatus.PENDING_SEND -> "بانتظار الإرسال"
        TipLocalStatus.PENDING_REVIEW -> "قيد المراجعة"
        TipLocalStatus.PUBLISHED -> "منشورة"
        TipLocalStatus.REJECTED -> "مرفوضة"
    }
}
