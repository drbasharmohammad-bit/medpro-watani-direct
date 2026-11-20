package sy.medpro.watani.ui.firstaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the First Aid flashcard scheduler (build 1.2.36).
 *
 * THE CONTRACT under test is the courses' own repetition.json, which
 * ships inside every unit package of both steps: intervals
 * [0, 1, 3, 7, 14, 30] days, long-term range 45–90 days,
 * on_lapse_reset_to_days = 1, quiz_error_cards = "same-day".
 */
class FirstAidSrsSchedulerTest {

    private val now = 1_700_000_000_000L
    private val day = FirstAidSrsScheduler.DAY_MS

    @Test
    fun ladderMatchesRepetitionJson() {
        assertEquals(listOf(0L, 1L, 3L, 7L, 14L, 30L), FirstAidSrsScheduler.LADDER_DAYS)
        for ((stage, days) in FirstAidSrsScheduler.LADDER_DAYS.withIndex()) {
            assertEquals(days, FirstAidSrsScheduler.intervalDaysForStage(stage))
        }
    }

    @Test
    fun newCardIsDueSameDay() {
        val card = FirstAidSrsScheduler.newCard(now)
        assertEquals(0, card.stage)
        assertEquals(now, card.dueAtMillis)
        assertEquals(0, card.lapses)
        assertTrue(FirstAidSrsScheduler.isDue(card, now))
    }

    @Test
    fun correctAnswersWalkTheWholeLadder() {
        var card = FirstAidSrsScheduler.newCard(now)
        val expectedDays = listOf(1L, 3L, 7L, 14L, 30L)
        for ((i, days) in expectedDays.withIndex()) {
            card = FirstAidSrsScheduler.onCorrect(card, now)
            assertEquals("stage after ${i + 1} correct", i + 1, card.stage)
            assertEquals(now + days * day, card.dueAtMillis)
            assertFalse(FirstAidSrsScheduler.isDue(card, now))
        }
    }

    @Test
    fun longTermRangeGrowsToNinetyAndCaps() {
        assertEquals(45L, FirstAidSrsScheduler.intervalDaysForStage(6))
        assertEquals(60L, FirstAidSrsScheduler.intervalDaysForStage(7))
        assertEquals(75L, FirstAidSrsScheduler.intervalDaysForStage(8))
        assertEquals(90L, FirstAidSrsScheduler.intervalDaysForStage(9))
        // Far past the ladder the interval never exceeds the range top.
        assertEquals(90L, FirstAidSrsScheduler.intervalDaysForStage(50))
        var card = FirstAidCardState(stage = 5, dueAtMillis = now, lapses = 0)
        card = FirstAidSrsScheduler.onCorrect(card, now)
        assertEquals(6, card.stage)
        assertEquals(now + 45L * day, card.dueAtMillis)
    }

    @Test
    fun lapseResetsToOneDayStageAndCounts() {
        val deep = FirstAidCardState(stage = 4, dueAtMillis = now, lapses = 2)
        val lapsed = FirstAidSrsScheduler.onLapse(deep, now)
        assertEquals(FirstAidSrsScheduler.LAPSE_STAGE, lapsed.stage)
        assertEquals(1L, FirstAidSrsScheduler.intervalDaysForStage(lapsed.stage))
        assertEquals(now + day, lapsed.dueAtMillis)
        assertEquals(3, lapsed.lapses)
    }

    @Test
    fun lapseFromNewCardStillLandsOnOneDay() {
        val lapsed = FirstAidSrsScheduler.onLapse(FirstAidSrsScheduler.newCard(now), now)
        assertEquals(now + day, lapsed.dueAtMillis)
        assertEquals(1, lapsed.lapses)
    }

    @Test
    fun quizErrorCardIsDueSameDayAndKeepsLapses() {
        val existing = FirstAidCardState(stage = 3, dueAtMillis = now + 7 * day, lapses = 2)
        val card = FirstAidSrsScheduler.quizErrorCard(now, existing)
        assertEquals(0, card.stage)
        assertEquals(now, card.dueAtMillis)
        assertEquals(2, card.lapses)
        assertTrue(FirstAidSrsScheduler.isDue(card, now))
    }

    @Test
    fun quizErrorCardWithoutHistoryStartsClean() {
        val card = FirstAidSrsScheduler.quizErrorCard(now)
        assertEquals(0, card.stage)
        assertEquals(0, card.lapses)
    }

    @Test
    fun negativeStageFallsBackToLadderStart() {
        assertEquals(0L, FirstAidSrsScheduler.intervalDaysForStage(-3))
    }

    @Test
    fun dueBoundaryIsInclusive() {
        val card = FirstAidCardState(stage = 1, dueAtMillis = now, lapses = 0)
        assertTrue(FirstAidSrsScheduler.isDue(card, now))
        assertFalse(FirstAidSrsScheduler.isDue(card, now - 1))
    }
}
