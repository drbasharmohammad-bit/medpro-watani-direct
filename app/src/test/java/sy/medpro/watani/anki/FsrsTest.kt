package sy.medpro.watani.anki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.anki.AnkiGrade
import sy.medpro.watani.ui.anki.Fsrs

/**
 * FSRS-5 reference-vector tests (build 1.2.54): every expected value
 * below was computed from the published FSRS-5 formulas with the
 * official default weights (open-spaced-repetition reference), so the
 * scheduler is pinned to the algorithm, not to itself.
 */
class FsrsTest {

    private fun close(expected: Double, actual: Double, tol: Double = 0.01) {
        assertEquals("expected $expected but was $actual", expected, actual, tol)
    }

    @Test
    fun initialStabilityMatchesReference() {
        close(0.40255, Fsrs.initialStability(AnkiGrade.AGAIN))
        close(1.18385, Fsrs.initialStability(AnkiGrade.HARD))
        close(3.173, Fsrs.initialStability(AnkiGrade.GOOD))
        close(15.69105, Fsrs.initialStability(AnkiGrade.EASY))
    }

    @Test
    fun initialDifficultyMatchesReference() {
        close(7.1949, Fsrs.initialDifficulty(AnkiGrade.AGAIN))
        close(6.4883, Fsrs.initialDifficulty(AnkiGrade.HARD))
        close(5.2824, Fsrs.initialDifficulty(AnkiGrade.GOOD))
        close(3.2245, Fsrs.initialDifficulty(AnkiGrade.EASY))
    }

    @Test
    fun retrievabilityAtStabilityIsNinetyPercent() {
        close(0.9, Fsrs.retrievability(3.173, 3.173), 0.0001)
        close(0.9, Fsrs.retrievability(10.0, 10.0), 0.0001)
        // Interval at the desired retention equals the stability.
        close(3.173, Fsrs.intervalDays(3.173), 0.0001)
        close(10.0, Fsrs.intervalDays(10.0), 0.0001)
    }

    @Test
    fun recallStabilityMatchesReference() {
        // State after a first Good (D=5.2824, S=3.173), reviewed exactly
        // at t=S (R=0.9) with each successful grade.
        val d = 5.2824
        val s = 3.173
        val r = 0.9
        close(11.1313, Fsrs.nextRecallStability(d, s, r, AnkiGrade.GOOD), 0.02)
        close(5.0153, Fsrs.nextRecallStability(d, s, r, AnkiGrade.HARD), 0.02)
        close(26.9666, Fsrs.nextRecallStability(d, s, r, AnkiGrade.EASY), 0.05)
        // Difficulty after the same reviews (mean reversion to D0(4)).
        close(5.273, Fsrs.nextDifficulty(d, AnkiGrade.GOOD), 0.01)
        close(6.035, Fsrs.nextDifficulty(d, AnkiGrade.HARD), 0.01)
        close(4.511, Fsrs.nextDifficulty(d, AnkiGrade.EASY), 0.01)
    }

    @Test
    fun forgetStabilityMatchesReference() {
        val d = 5.2824
        val s = 3.173
        close(1.0669, Fsrs.nextForgetStability(d, s, 0.9), 0.01)
        close(6.7969, Fsrs.nextDifficulty(d, AnkiGrade.AGAIN), 0.01)
        // Post-lapse stability never exceeds the pre-lapse stability.
        assertTrue(Fsrs.nextForgetStability(d, s, 0.9) < s)
    }

    @Test
    fun sameDayStabilityMatchesReference() {
        close(4.4669, Fsrs.sameDayStability(3.173, AnkiGrade.GOOD), 0.01)
    }

    @Test
    fun fullScheduleNewCardGoodThenGood() {
        val t0 = 1_700_000_000_000L
        val first = Fsrs.schedule(null, AnkiGrade.GOOD, t0)
        close(3.173, first.stability)
        close(5.2824, first.difficulty)
        assertEquals(t0 + 3.173 * Fsrs.DAY_MS, first.dueAtMillis.toDouble(), 1000.0)
        // Review exactly when due: R = 0.9, stability grows to ~11.13.
        val second = Fsrs.schedule(first, AnkiGrade.GOOD, first.dueAtMillis)
        close(11.1313, second.stability, 0.02)
        assertEquals(2, second.repetitions)
        assertEquals(0, second.lapses)
    }

    @Test
    fun fullScheduleLapseUsesTenMinuteFloorButKeepsFsrsStability() {
        val t0 = 1_700_000_000_000L
        val first = Fsrs.schedule(null, AnkiGrade.GOOD, t0)
        val lapsed = Fsrs.schedule(first, AnkiGrade.AGAIN, first.dueAtMillis)
        close(1.0669, lapsed.stability, 0.01)
        assertEquals(1, lapsed.lapses)
        assertEquals(0, lapsed.repetitions)
        // Interval from stability 1.0669 is > 1 day, so no floor here;
        // a FIRST Again (S=0.40255) does take the 10-minute floor.
        val firstAgain = Fsrs.schedule(null, AnkiGrade.AGAIN, t0)
        close(0.40255, firstAgain.stability)
        assertEquals(t0 + Fsrs.AGAIN_DELAY_MS, firstAgain.dueAtMillis)
    }

    @Test
    fun migrationFromSm2ConvertsNotWipes() {
        val due = 1_700_100_000_000L
        // A learned card: SM-2 interval 12.5 days at default ease 2.5.
        val learned = Fsrs.migrateFromSm2(Fsrs.Sm2Record(12.5, 2.5, repetitions = 4, lapses = 1, dueAtMillis = due), now = 1_700_000_000_000L)
        close(12.5, learned.stability)
        close(5.0, learned.difficulty)
        assertEquals(due, learned.dueAtMillis)
        assertEquals(4, learned.repetitions)
        assertEquals(1, learned.lapses)
        // Hard card (ease at the SM-2 floor) maps to high difficulty.
        val hard = Fsrs.migrateFromSm2(Fsrs.Sm2Record(3.0, 1.3, repetitions = 2, lapses = 3, dueAtMillis = due), now = 0L)
        close(9.8, hard.difficulty)
        // Never-passed card starts from the FSRS initial Again stability.
        val fresh = Fsrs.migrateFromSm2(Fsrs.Sm2Record(0.0, 2.5, repetitions = 0, lapses = 2, dueAtMillis = due), now = 0L)
        close(0.40255, fresh.stability)
    }
}
