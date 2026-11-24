package sy.medpro.watani.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistence seam for the outbox — SharedPreferences in
 * production, in-memory in tests. */
interface TipsOutboxStore {
    fun load(): List<OutboxTip>
    fun save(entries: List<OutboxTip>)
}

/**
 * The «زوادة الطالب» sync engine (build 1.2.51): owns the outbox
 * state and every gateway round-trip. Offline-first rules, mirroring
 * the Logbook repository's discipline:
 *
 * 1. Local writes are instant and unconditional — add/edit/delete
 *    hit the store before any network thought.
 * 2. The flush is opportunistic and silent: submit-time, app start
 *    and connectivity-return all call [flush]; a failure (offline,
 *    auth, server) leaves the entry PENDING_SEND for the next
 *    trigger and never surfaces as a student-facing error.
 * 3. [refreshMine] merges the server's moderation states (قيد
 *    المراجعة / منشورة / مرفوضة + سبب الرفض) over the local list via
 *    [TipsOutboxLogic.applyServerMine].
 */
/** HTTP 200 — the synthetic success for a never-sent tombstone. */
private const val HTTP_OK_CODE: Int = 200

class TipsSyncEngine(
    private val store: TipsOutboxStore,
    private val gateway: TipsGateway,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val _mine = MutableStateFlow(TipsOutboxLogic.visibleMine(store.load()))
    val mine: StateFlow<List<OutboxTip>> = _mine.asStateFlow()

    private val flushMutex = Mutex()

    @Synchronized
    private fun current(): List<OutboxTip> = store.load()

    @Synchronized
    private fun commit(entries: List<OutboxTip>) {
        store.save(entries)
        _mine.value = TipsOutboxLogic.visibleMine(entries)
    }

    /** Reload from the store (account switch rebinds the store's
     * owner in the repository layer, then calls this). */
    @Synchronized
    fun reload() {
        _mine.value = TipsOutboxLogic.visibleMine(store.load())
    }

    /** Add: validated by the caller (the form), stored at once,
     * then a best-effort flush is attempted by the repository. */
    @Synchronized
    fun add(subject: String, sectionId: String, text: String, authorName: String?): OutboxTip {
        val now = clock()
        val tip = OutboxTip(
            localId = TipsOutboxLogic.newLocalId(),
            subject = subject,
            sectionId = sectionId,
            text = text.trim(),
            authorName = authorName?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now
        )
        commit(TipsOutboxLogic.enqueue(current(), tip))
        return tip
    }

    @Synchronized
    fun edit(localId: String, newText: String) {
        commit(TipsOutboxLogic.edit(current(), localId, newText.trim(), clock()))
    }

    @Synchronized
    fun delete(localId: String) {
        commit(TipsOutboxLogic.delete(current(), localId, clock()))
    }

    /**
     * Push every PENDING_SEND entry. Returns true when the outbox
     * fully drained. An auth failure stops the round (retrying the
     * rest with a dead token only burns requests); other failures
     * skip the entry and continue with the next.
     */
    suspend fun flush(): Boolean = flushMutex.withLock {
        var drained = true
        for (entry in TipsOutboxLogic.pendingFlush(current())) {
            val reply = dispatch(entry)
            when {
                reply.isAuthFailure -> {
                    drained = false
                    break
                }
                reply.isSuccess -> commit(applySuccess(entry, reply))
                else -> drained = false
            }
        }
        drained && TipsOutboxLogic.pendingFlush(current()).isEmpty()
    }

    /** One outbox entry → its gateway call: delete for tombstones,
     * submit for new tips, edit for changed published ones. */
    private suspend fun dispatch(entry: OutboxTip): TipsGatewayReply = when {
        entry.deleted && entry.serverId != null -> gateway.delete(entry.serverId)
        // Never sent: delete() already dropped it; defensive success.
        entry.deleted -> TipsGatewayReply(HTTP_OK_CODE)
        entry.serverId == null -> gateway.submit(entry)
        else -> gateway.edit(entry.serverId, entry.text)
    }

    /** The outbox transition for one accepted entry. */
    private fun applySuccess(entry: OutboxTip, reply: TipsGatewayReply): List<OutboxTip> {
        val now = clock()
        return when {
            entry.deleted -> TipsOutboxLogic.markDeleteConfirmed(current(), entry.localId)
            entry.serverId == null -> TipsOutboxLogic.markSent(current(), entry.localId, reply.serverId, now)
            else -> TipsOutboxLogic.markEdited(current(), entry.localId, now)
        }
    }

    /** Pull the server's view of «mine» and merge moderation states. */
    suspend fun refreshMine(): Boolean {
        val reply = gateway.fetchMine(0L)
        if (!reply.isSuccess) return false
        commit(TipsOutboxLogic.applyServerMine(current(), reply.tips, clock()))
        return true
    }

    /** One opportunistic sync round: flush, then refresh. */
    suspend fun syncNow() {
        flush()
        refreshMine()
    }

    suspend fun report(serverId: String, reason: String = ""): Boolean =
        gateway.report(serverId, reason).isSuccess
}
