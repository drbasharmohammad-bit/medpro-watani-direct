package sy.medpro.watani.data

import java.security.MessageDigest

/**
 * Pure decision logic for the batched progress sync (build 1.2.15 —
 * free-plan ceiling). The sync's network run is gated on two facts:
 *
 *  1. CONTENT: a fingerprint of everything the push carries (totals,
 *     saved mistakes, favorites, notes, per-subject performance and
 *     the daily-activity map). If it equals the fingerprint recorded
 *     after the last successful push, there is nothing new to send —
 *     the trigger is a local-only no-op, even at a cold start.
 *  2. TIME: at least [MIN_SYNC_INTERVAL_MS] must have passed since
 *     the last successful push, so a day of many short sessions
 *     cannot turn into a stream of writes. Dirty progress inside the
 *     interval is NOT lost: the vault on disk is the source of truth,
 *     the fingerprint keeps mismatching, and the next eligible
 *     trigger (start, quiz event, backgrounding) pushes it.
 *
 * First sync of an account (no recorded fingerprint) always runs.
 * Kept free of Android types so the decision is unit-testable.
 */
object ProgressSyncGate {

    /** Minimum spacing between two actual network syncs of one account. */
    const val MIN_SYNC_INTERVAL_MS: Long = 60L * 60L * 1000L

    /**
     * Deterministic content fingerprint of one sync payload. The
     * caller must build it from exactly the values a push would send
     * (resp. did send), so equality really means "server already has
     * this state". Collections are canonicalised (maps and favorites
     * sorted; the mistakes list keeps its stored order, which is part
     * of the payload).
     */
    fun fingerprint(
        totalAnswered: Int,
        totalCorrect: Int,
        mistakes: List<SavedMistakeItem>,
        favorites: List<String>,
        notesJson: String,
        subjectAnswered: Map<String, Int>,
        subjectCorrect: Map<String, Int>,
        dailyActivity: Map<String, Int>
    ): String {
        val sb = StringBuilder()
        sb.append("a=").append(totalAnswered).append(";c=").append(totalCorrect).append('\n')
        sb.append("m=").append(mistakes.size).append('\n')
        for (m in mistakes) {
            sb.append(m.id).append('|').append(m.timestamp).append('|')
                .append(m.subjectTitle).append('|').append(m.questionText).append('|')
                .append(m.selectedAnswer).append('|').append(m.correctAnswer).append('|')
                .append(m.explanation).append('\n')
        }
        sb.append("f=")
        for (f in favorites.sorted()) sb.append(f).append('|')
        sb.append('\n')
        sb.append("n=").append(notesJson).append('\n')
        appendMap(sb, "sa", subjectAnswered)
        appendMap(sb, "sc", subjectCorrect)
        appendMap(sb, "da", dailyActivity)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun appendMap(sb: StringBuilder, tag: String, map: Map<String, Int>) {
        sb.append(tag).append('=')
        for ((k, v) in map.toSortedMap()) {
            sb.append(k).append(':').append(v).append('|')
        }
        sb.append('\n')
    }

    /**
     * True when a trigger must perform the network sync now.
     * [syncedFingerprint] == null means this account never completed a
     * push on this device (fresh install, cleared data, new account):
     * always sync. A dirty fingerprint inside the minimum interval
     * defers — it does not drop — the push.
     */
    fun shouldSync(
        localFingerprint: String,
        syncedFingerprint: String?,
        lastSyncAtMs: Long,
        nowMs: Long
    ): Boolean {
        if (localFingerprint == syncedFingerprint) return false
        if (syncedFingerprint == null) return true
        return nowMs - lastSyncAtMs >= MIN_SYNC_INTERVAL_MS
    }
}
