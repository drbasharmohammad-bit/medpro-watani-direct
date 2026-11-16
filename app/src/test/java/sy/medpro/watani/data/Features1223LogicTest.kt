package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.notify.ReminderTimeMath
import java.time.LocalDate
import java.time.ZoneId

/**
 * Build 1.2.23 pure-logic pins: smart daily-goal math, reminder
 * scheduling math, and focus-stats week aggregation. All three are
 * the parts that must never drift silently — the UI, widget and
 * alarms all read these exact functions.
 */
class Features1223LogicTest {

    // ---------------- Daily goal ----------------

    @Test
    fun remainingNeverNegative() {
        assertEquals(40, DailyGoalMath.remainingQuestions(100, 60))
        assertEquals(0, DailyGoalMath.remainingQuestions(100, 100))
        assertEquals(0, DailyGoalMath.remainingQuestions(100, 140))
        assertEquals(0, DailyGoalMath.remainingQuestions(0, 0))
    }

    @Test
    fun autoGoalSpreadsOverDaysIncludingToday() {
        // 100 left, exam in 9 days -> 10 study days -> 10/day.
        assertEquals(10, DailyGoalMath.autoGoal(100, 9))
        // Ceil: 101 over 10 days -> 11.
        assertEquals(11, DailyGoalMath.autoGoal(101, 9))
        // Exam tomorrow (1 day left) -> 2 study days.
        assertEquals(50, DailyGoalMath.autoGoal(100, 1))
    }

    @Test
    fun autoGoalEdgeCases() {
        assertEquals(0, DailyGoalMath.autoGoal(0, 30))
        // Exam day: everything left is today's goal.
        assertEquals(77, DailyGoalMath.autoGoal(77, 0))
        assertEquals(77, DailyGoalMath.autoGoal(77, -3))
        // A single leftover question still yields a goal of 1.
        assertEquals(1, DailyGoalMath.autoGoal(1, 300))
    }

    @Test
    fun manualGoalWinsUntilCleared() {
        assertEquals(25, DailyGoalMath.effectiveGoal(10, 25))
        assertEquals(10, DailyGoalMath.effectiveGoal(10, null))
        assertEquals(1, DailyGoalMath.effectiveGoal(10, 0))
    }

    @Test
    fun progressFractionClamps() {
        assertEquals(0f, DailyGoalMath.progressFraction(0, 10), 0.001f)
        assertEquals(0.5f, DailyGoalMath.progressFraction(5, 10), 0.001f)
        assertEquals(1f, DailyGoalMath.progressFraction(15, 10), 0.001f)
        assertEquals(1f, DailyGoalMath.progressFraction(0, 0), 0.001f)
    }

    // ---------------- Reminder scheduling ----------------

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun millis(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun nextOccurrenceLaterToday() {
        val day = LocalDate.of(2026, 11, 16)
        val now = millis(day, 10, 0)
        assertEquals(millis(day, 19, 0), ReminderTimeMath.nextOccurrenceMillis(now, 19, 0, zone))
    }

    @Test
    fun nextOccurrenceRollsToTomorrow() {
        val day = LocalDate.of(2026, 11, 16)
        val now = millis(day, 20, 30)
        assertEquals(millis(day.plusDays(1), 19, 0), ReminderTimeMath.nextOccurrenceMillis(now, 19, 0, zone))
        // Exactly at the reminder time counts as passed (strictly after).
        val exact = millis(day, 19, 0)
        assertEquals(millis(day.plusDays(1), 19, 0), ReminderTimeMath.nextOccurrenceMillis(exact, 19, 0, zone))
    }

    @Test
    fun nextMidnightIsTomorrowStart() {
        val day = LocalDate.of(2026, 11, 16)
        val now = millis(day, 23, 59)
        assertEquals(millis(day.plusDays(1), 0, 0), ReminderTimeMath.nextMidnightMillis(now, zone))
    }

    // ---------------- Focus stats ----------------

    @Test
    fun weekTotalSumsLastSevenDaysOnly() {
        val today = LocalDate.of(2026, 11, 16)
        val map = mapOf(
            today.toString() to 25,
            today.minusDays(1).toString() to 50,
            today.minusDays(6).toString() to 10,
            today.minusDays(7).toString() to 999,
            today.plusDays(1).toString() to 999
        )
        assertEquals(85, FocusStatsMath.weekTotal(map, today))
    }

    @Test
    fun prunedKeepsOnlyWeekWindow() {
        val today = LocalDate.of(2026, 11, 16)
        val map = mapOf(
            today.toString() to 25,
            today.minusDays(6).toString() to 10,
            today.minusDays(7).toString() to 999
        )
        val pruned = FocusStatsMath.pruned(map, today)
        assertEquals(2, pruned.size)
        assertTrue(pruned.containsKey(today.toString()))
    }
}
