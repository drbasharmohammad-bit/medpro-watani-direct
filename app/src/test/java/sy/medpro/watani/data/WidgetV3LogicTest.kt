package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.viewmodel.AppTab
import sy.medpro.watani.ui.widget.WidgetDestination
import sy.medpro.watani.ui.widget.WidgetRouter
import java.time.LocalDate

/**
 * Build 1.2.25 pure-logic pins: landmark-of-the-day selection,
 * the dimming overlay stops, quick-action list editing and
 * storage encoding, the Levantine exam-date text, and — the
 * doctor's explicit condition — the widget-button routing map
 * (every quick action resolves to its OWN intended screen).
 */
class WidgetV3LogicTest {

    // ---------------- landmark of the day ----------------

    @Test
    fun rotationIsDeterministicAndInRange() {
        val count = 30
        for (day in 19_000L..19_400L) {
            val idx = WidgetBackgroundMath.indexForEpochDay(day, count)
            assertTrue(idx in 0 until count)
            assertEquals(idx, WidgetBackgroundMath.indexForEpochDay(day, count))
        }
    }

    @Test
    fun rotationAdvancesDailyAndWraps() {
        val count = 30
        val start = WidgetBackgroundMath.indexForEpochDay(20_000L, count)
        assertEquals((start + 1) % count, WidgetBackgroundMath.indexForEpochDay(20_001L, count))
        // A full cycle of `count` consecutive days visits every index.
        val seen = (0 until count).map {
            WidgetBackgroundMath.indexForEpochDay(20_000L + it, count)
        }.toSet()
        assertEquals(count, seen.size)
    }

    @Test
    fun pickIndexHonorsMode() {
        val day = 20_123L
        val rotating = WidgetSettings(bgMode = WidgetBgMode.ROTATE)
        assertEquals(
            WidgetBackgroundMath.indexForEpochDay(day, 30),
            WidgetBackgroundMath.pickIndex(rotating, day, 30)
        )
        val fixed = WidgetSettings(bgMode = WidgetBgMode.FIXED, fixedLandmarkIndex = 7)
        assertEquals(7, WidgetBackgroundMath.pickIndex(fixed, day, 30))
        // Out-of-range fixed picks clamp instead of crashing.
        val fixedOut = WidgetSettings(bgMode = WidgetBgMode.FIXED, fixedLandmarkIndex = 99)
        assertEquals(29, WidgetBackgroundMath.pickIndex(fixedOut, day, 30))
        val plain = WidgetSettings(bgMode = WidgetBgMode.PLAIN)
        assertNull(WidgetBackgroundMath.pickIndex(plain, day, 30))
        assertNull(WidgetBackgroundMath.pickIndex(rotating, day, 0))
    }

    // ---------------- dimming overlay ----------------

    @Test
    fun overlayStopsMatchApprovedDesignAtDefaultDim() {
        // DESIGN_NOTES: at the reference dimming (70%) the gradient
        // stops are exactly 65% / 55% / 92%.
        val (top, mid, bottom) = WidgetSettingsMath.overlayStops(70)
        assertEquals(0.65f, top, 0.001f)
        assertEquals(0.55f, mid, 0.001f)
        assertEquals(0.92f, bottom, 0.001f)
    }

    @Test
    fun overlayStopsScaleAndClamp() {
        val (t0, m0, b0) = WidgetSettingsMath.overlayStops(0)
        assertEquals(0f, t0, 0.001f)
        assertEquals(0f, m0, 0.001f)
        assertEquals(0f, b0, 0.001f)
        val (t1, m1, b1) = WidgetSettingsMath.overlayStops(100)
        assertTrue(t1 > 0.65f && m1 > 0.55f)
        assertEquals(1f, b1, 0.001f) // 0.92 * 100/70 clamps at full
        // Out-of-range inputs clamp to the 0..100 range first.
        assertEquals(
            WidgetSettingsMath.overlayStops(100),
            WidgetSettingsMath.overlayStops(140)
        )
        assertEquals(
            WidgetSettingsMath.overlayStops(0),
            WidgetSettingsMath.overlayStops(-5)
        )
    }

    // ---------------- quick actions ----------------

    @Test
    fun quickActionToggleAddsRemovesAndCaps() {
        var list = listOf(WidgetQuickAction.FOCUS)
        list = WidgetSettingsMath.toggleQuickAction(list, WidgetQuickAction.LIBRARY)
        assertEquals(listOf(WidgetQuickAction.FOCUS, WidgetQuickAction.LIBRARY), list)
        list = WidgetSettingsMath.toggleQuickAction(list, WidgetQuickAction.FOCUS)
        assertEquals(listOf(WidgetQuickAction.LIBRARY), list)
        val full = WidgetQuickAction.entries
        // Already at the 4-cap with all present: toggling one removes it.
        assertEquals(3, WidgetSettingsMath.toggleQuickAction(full, WidgetQuickAction.FOCUS).size)
        // Duplicates never survive a toggle.
        val dup = listOf(WidgetQuickAction.FOCUS, WidgetQuickAction.FOCUS)
        assertEquals(
            listOf(WidgetQuickAction.FOCUS, WidgetQuickAction.MISTAKES),
            WidgetSettingsMath.toggleQuickAction(dup, WidgetQuickAction.MISTAKES)
        )
    }

    @Test
    fun quickActionEncodingRoundTrips() {
        val actions = listOf(
            WidgetQuickAction.QUESTIONS, WidgetQuickAction.FOCUS, WidgetQuickAction.LIBRARY
        )
        val encoded = WidgetSettingsMath.encodeQuickActions(actions)
        assertEquals(actions, WidgetSettingsMath.decodeQuickActions(encoded))
        // Null/blank storage falls back to the full default set.
        assertEquals(WidgetQuickAction.entries, WidgetSettingsMath.decodeQuickActions(null))
        assertEquals(WidgetQuickAction.entries, WidgetSettingsMath.decodeQuickActions(""))
        // Unknown tokens are dropped, order is kept.
        assertEquals(
            listOf(WidgetQuickAction.FOCUS, WidgetQuickAction.LIBRARY),
            WidgetSettingsMath.decodeQuickActions("FOCUS,BOGUS,LIBRARY")
        )
    }

    @Test
    fun quickActionRoutesAreUnique() {
        val routes = WidgetQuickAction.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    // ---------------- exam date text ----------------

    @Test
    fun examDateFormatsLevantineArabic() {
        // 2026-12-22 is a Tuesday — the exact line on the mockups.
        assertEquals(
            "الثلاثاء 22 كانون الأول 2026",
            ExamDateText.format(LocalDate.of(2026, 12, 22))
        )
        assertEquals(
            "الاثنين 5 كانون الثاني 2026",
            ExamDateText.format(LocalDate.of(2026, 1, 5))
        )
        assertEquals(
            "الجمعة 14 آب 2026",
            ExamDateText.format(LocalDate.of(2026, 8, 14))
        )
    }

    // ---------------- button routing (doctor's condition) ----------------

    @Test
    fun everyQuickActionRoutesToItsOwnScreen() {
        assertEquals(
            WidgetDestination.Drawer("مؤقت التركيز"),
            WidgetRouter.destinationFor(WidgetQuickAction.FOCUS.route)
        )
        assertEquals(
            WidgetDestination.Drawer("بنك أخطائي"),
            WidgetRouter.destinationFor(WidgetQuickAction.MISTAKES.route)
        )
        assertEquals(
            WidgetDestination.Tab(AppTab.BANK),
            WidgetRouter.destinationFor(WidgetQuickAction.QUESTIONS.route)
        )
        assertEquals(
            WidgetDestination.Tab(AppTab.LIBRARY),
            WidgetRouter.destinationFor(WidgetQuickAction.LIBRARY.route)
        )
        assertEquals(
            WidgetDestination.Drawer("إعدادات الويدجت"),
            WidgetRouter.destinationFor(WidgetRouter.ROUTE_SETTINGS)
        )
    }

    @Test
    fun unknownOrEmptyRouteStaysHome() {
        assertEquals(WidgetDestination.Home, WidgetRouter.destinationFor(null))
        assertEquals(WidgetDestination.Home, WidgetRouter.destinationFor(""))
        assertEquals(WidgetDestination.Home, WidgetRouter.destinationFor("bogus"))
    }

    @Test
    fun routerRequestConsumeCycle() {
        WidgetRouter.consume()
        assertNull(WidgetRouter.pending.value)
        WidgetRouter.request("focus")
        assertEquals("focus", WidgetRouter.pending.value)
        // Blank requests never overwrite a real pending route.
        WidgetRouter.request("")
        assertEquals("focus", WidgetRouter.pending.value)
        WidgetRouter.consume()
        assertNull(WidgetRouter.pending.value)
    }
}
