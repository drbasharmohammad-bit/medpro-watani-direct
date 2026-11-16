package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.widget.WidgetDestination
import sy.medpro.watani.ui.widget.WidgetRouter

/**
 * Build 1.2.30 pure-logic pins — the five lock-screen features:
 *
 * 1. The permanent countdown card speaks the same days-left
 *    numbers as the widget (Arabic day phrasing by count) and
 *    mirrors the daily-goal states.
 * 2. The morning question pick is the daily challenge's own
 *    day-index formula — same question on the notification and
 *    on the screen, all day.
 * 3. The focus bar clock/phrasing is exact, and its toggle label
 *    follows the running state.
 * 4. The strong alarm uses the full screen only when the student
 *    enabled BOTH the study reminder and the strong alarm AND the
 *    system permission is actually available — otherwise it falls
 *    back to the plain reminder.
 * 5. Every new feature defaults OFF, and the new notification
 *    routes resolve to their own screens.
 */
class Build1230LogicTest {

    // ---------------- Countdown card text ----------------

    @Test
    fun countdownTitlePhrasesDaysInArabic() {
        assertEquals("بقي يوم واحد على الامتحان الوطني", CountdownCardMath.titleFor(1))
        assertEquals("بقي يومان على الامتحان الوطني", CountdownCardMath.titleFor(2))
        assertEquals("بقي 5 أيام على الامتحان الوطني", CountdownCardMath.titleFor(5))
        assertEquals("بقي 10 أيام على الامتحان الوطني", CountdownCardMath.titleFor(10))
        assertEquals("بقي 11 يومًا على الامتحان الوطني", CountdownCardMath.titleFor(11))
        assertEquals("بقي 36 يومًا على الامتحان الوطني", CountdownCardMath.titleFor(36))
    }

    @Test
    fun countdownTitleHandlesExamDayAndPast() {
        assertEquals("الامتحان الوطني اليوم — بالتوفيق", CountdownCardMath.titleFor(0))
        assertEquals("مرّ موعد الامتحان الوطني", CountdownCardMath.titleFor(-3))
    }

    @Test
    fun countdownBodyMirrorsGoalStates() {
        val date = "الثلاثاء 22 كانون الأول 2026"
        assertTrue(CountdownCardMath.bodyFor(0, 0, false, date).contains("حمّل بنوك الأسئلة"))
        assertTrue(CountdownCardMath.bodyFor(0, 0, true, date).contains("حدّد هدفك اليومي"))
        assertTrue(CountdownCardMath.bodyFor(40, 40, true, date).contains("أنجزت هدف اليوم كاملًا"))
        val partial = CountdownCardMath.bodyFor(40, 17, true, date)
        assertTrue(partial.contains("هدف اليوم: 40 سؤالًا"))
        assertTrue(partial.contains("أنجزت 17 حتى الآن"))
        // The exam date text always rides along.
        assertTrue(partial.contains(date))
    }

    // ---------------- Daily question pick ----------------

    @Test
    fun questionPickIsDeterministicWithinTheDay() {
        val dayStart = 20_000L * DailyQuestionPick.DAY_MS
        val size = 137
        val first = DailyQuestionPick.indexFor(dayStart + 3_600_000L, size)
        val later = DailyQuestionPick.indexFor(dayStart + 80_000_000L, size)
        assertEquals(first, later)
        assertTrue(first in 0 until size)
    }

    @Test
    fun questionPickMatchesTheLegacyScreenFormula() {
        // The pre-1.2.30 screen picked (millis / 86_400_000).toInt() % size.
        for (millis in listOf(0L, 86_400_000L, 1_700_000_000_000L, 1_763_000_000_000L)) {
            for (size in listOf(1, 7, 250, 4_096)) {
                val legacy = (millis / (1000 * 60 * 60 * 24)).toInt() % size
                assertEquals(legacy, DailyQuestionPick.indexFor(millis, size))
            }
        }
    }

    @Test
    fun questionPickRejectsEmptyPool() {
        assertEquals(-1, DailyQuestionPick.indexFor(1_700_000_000_000L, 0))
        assertEquals(-1, DailyQuestionPick.indexFor(1_700_000_000_000L, -5))
    }

    // ---------------- Focus bar ----------------

    @Test
    fun focusClockFormatsMinutesAndSeconds() {
        assertEquals("00:00", FocusBarMath.formatClock(0))
        assertEquals("00:07", FocusBarMath.formatClock(7))
        assertEquals("01:05", FocusBarMath.formatClock(65))
        assertEquals("25:00", FocusBarMath.formatClock(25 * 60))
        assertEquals("60:00", FocusBarMath.formatClock(60 * 60))
        assertEquals("00:00", FocusBarMath.formatClock(-30))
    }

    @Test
    fun focusBarTitlesAndLabelsFollowState() {
        assertEquals("جلسة تركيز — القلبية", FocusBarMath.titleFor(FocusPhase.FOCUS, "القلبية"))
        assertEquals("جلسة تركيز", FocusBarMath.titleFor(FocusPhase.FOCUS, null))
        assertEquals("جلسة تركيز", FocusBarMath.titleFor(FocusPhase.FOCUS, "  "))
        assertEquals("استراحة", FocusBarMath.titleFor(FocusPhase.BREAK, "القلبية"))
        assertEquals("إيقاف مؤقت", FocusBarMath.toggleLabel(isRunning = true))
        assertEquals("استئناف", FocusBarMath.toggleLabel(isRunning = false))
        assertEquals("متبقّي 12:34 — متوقف مؤقتًا", FocusBarMath.pausedLine(12 * 60 + 34))
    }

    // ---------------- Snooze + full-screen decision ----------------

    @Test
    fun snoozeIsExactlyTenMinutesLater() {
        assertEquals(10, SnoozeMath.SNOOZE_MINUTES)
        assertEquals(1_600_000L, SnoozeMath.triggerMillis(1_000_000L))
    }

    @Test
    fun fullScreenNeedsBothTogglesAndThePermission() {
        assertTrue(FullScreenAlarmMath.useFullScreen(true, true, true))
        assertFalse(FullScreenAlarmMath.useFullScreen(false, true, true))
        assertFalse(FullScreenAlarmMath.useFullScreen(true, false, true))
        // The decisive fallback: permission absent ⇒ plain reminder.
        assertFalse(FullScreenAlarmMath.useFullScreen(true, true, false))
        assertFalse(FullScreenAlarmMath.useFullScreen(false, false, false))
    }

    // ---------------- Defaults + routes ----------------

    @Test
    fun lockScreenFeaturesDefaultOff() {
        val config = ReminderConfig()
        assertFalse(config.countdownCardEnabled)
        assertFalse(config.questionEnabled)
        assertFalse(config.focusBarEnabled)
        assertFalse(config.strongAlarmEnabled)
        // The morning question defaults to 08:00.
        assertEquals(8, config.questionHour)
        assertEquals(0, config.questionMinute)
    }

    @Test
    fun newRoutesResolveToTheirOwnScreens() {
        assertEquals(
            WidgetDestination.Drawer(WidgetRouter.DRAWER_CHALLENGE),
            WidgetRouter.destinationFor(WidgetRouter.ROUTE_CHALLENGE)
        )
        assertEquals(
            WidgetDestination.MistakesReview,
            WidgetRouter.destinationFor(WidgetRouter.ROUTE_MISTAKES_REVIEW)
        )
        // Existing routes are untouched by the additions.
        assertEquals(
            WidgetDestination.Drawer(WidgetRouter.DRAWER_MISTAKES),
            WidgetRouter.destinationFor(WidgetQuickAction.MISTAKES.route)
        )
        assertEquals(WidgetDestination.Home, WidgetRouter.destinationFor("nonsense"))
        assertEquals(WidgetDestination.Home, WidgetRouter.destinationFor(null))
    }
}
