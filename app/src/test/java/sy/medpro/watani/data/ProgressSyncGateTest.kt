package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.15: the progress-sync batching gate, pinned off-device.
 * The contract that protects the free-plan ceiling AND the student's
 * progress: a network sync runs only when the payload content differs
 * from the last successful push and the minimum interval has passed;
 * a deferred push is never dropped (the fingerprint keeps
 * mismatching until an eligible trigger runs the sync).
 */
class ProgressSyncGateTest {

    private fun mistake(id: String, ts: Long) = SavedMistakeItem(
        id = id,
        subjectTitle = "الباطنة",
        questionText = "سؤال $id",
        selectedAnswer = "أ",
        correctAnswer = "ب",
        explanation = "شرح",
        timestamp = ts
    )

    private fun fp(
        totalAnswered: Int = 10,
        totalCorrect: Int = 7,
        mistakes: List<SavedMistakeItem> = listOf(mistake("m1", 100L)),
        favorites: List<String> = listOf("q1", "q2"),
        notesJson: String = "[]",
        subjectAnswered: Map<String, Int> = mapOf("الباطنة" to 10),
        subjectCorrect: Map<String, Int> = mapOf("الباطنة" to 7),
        dailyActivity: Map<String, Int> = mapOf("2026-11-14" to 10)
    ): String = ProgressSyncGate.fingerprint(
        totalAnswered, totalCorrect, mistakes, favorites, notesJson,
        subjectAnswered, subjectCorrect, dailyActivity
    )

    @Test
    fun fingerprintIsDeterministic() {
        assertEquals(fp(), fp())
    }

    @Test
    fun fingerprintIgnoresFavoriteAndMapOrder() {
        assertEquals(
            fp(favorites = listOf("q2", "q1")),
            fp(favorites = listOf("q1", "q2"))
        )
    }

    @Test
    fun fingerprintChangesWithAnyPayloadPart() {
        val base = fp()
        assertNotEquals(base, fp(totalAnswered = 11))
        assertNotEquals(base, fp(totalCorrect = 8))
        assertNotEquals(base, fp(mistakes = listOf(mistake("m1", 100L), mistake("m2", 200L))))
        assertNotEquals(base, fp(mistakes = listOf(mistake("m1", 101L))))
        assertNotEquals(base, fp(favorites = listOf("q1")))
        assertNotEquals(base, fp(notesJson = "[{\"x\":1}]"))
        assertNotEquals(base, fp(subjectAnswered = mapOf("الباطنة" to 11)))
        assertNotEquals(base, fp(subjectCorrect = mapOf("الباطنة" to 8)))
        assertNotEquals(base, fp(dailyActivity = mapOf("2026-11-14" to 11)))
    }

    @Test
    fun cleanStateNeverSyncs() {
        val f = fp()
        assertFalse(
            ProgressSyncGate.shouldSync(f, f, lastSyncAtMs = 0L, nowMs = Long.MAX_VALUE / 2)
        )
    }

    @Test
    fun neverSyncedAlwaysSyncs() {
        assertTrue(
            ProgressSyncGate.shouldSync(fp(), null, lastSyncAtMs = 0L, nowMs = 1_000L)
        )
    }

    @Test
    fun dirtyInsideIntervalDefers() {
        val now = 10_000_000L
        assertFalse(
            ProgressSyncGate.shouldSync(
                fp(totalAnswered = 11), fp(),
                lastSyncAtMs = now - ProgressSyncGate.MIN_SYNC_INTERVAL_MS + 1,
                nowMs = now
            )
        )
    }

    @Test
    fun dirtyAtIntervalBoundarySyncs() {
        val now = 10_000_000L
        assertTrue(
            ProgressSyncGate.shouldSync(
                fp(totalAnswered = 11), fp(),
                lastSyncAtMs = now - ProgressSyncGate.MIN_SYNC_INTERVAL_MS,
                nowMs = now
            )
        )
    }

    @Test
    fun dirtyLongAfterLastSyncSyncs() {
        assertTrue(
            ProgressSyncGate.shouldSync(
                fp(totalAnswered = 11), fp(),
                lastSyncAtMs = 0L, nowMs = 999_999_999L
            )
        )
    }
}
