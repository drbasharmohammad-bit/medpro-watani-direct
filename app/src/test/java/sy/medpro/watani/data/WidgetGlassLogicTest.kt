package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests — glass widget family v4 (build 1.2.51):
 *  - the glass tint floor: a stored slider value below the
 *    documented minimum (45) is raised on read, so no state can
 *    make the widget unreadable over bright landmark photos;
 *  - per-style effective tint (CLEAR is a fraction of the tint);
 *  - the daily tip pick: fixed flattening order (contract
 *    subjects × contract sections), deterministic per epoch day,
 *    null on empty packs, and the author-line rule (no line at
 *    all when author is null);
 *  - the streak math behind designs 2/4.
 */
class WidgetGlassLogicTest {

    @Test
    fun glassAlphaClampRaisesBelowMinimum() {
        assertEquals(WidgetGlassMath.GLASS_ALPHA_MIN, WidgetGlassMath.clampGlassAlpha(0))
        assertEquals(WidgetGlassMath.GLASS_ALPHA_MIN, WidgetGlassMath.clampGlassAlpha(30))
        assertEquals(WidgetGlassMath.GLASS_ALPHA_MIN, WidgetGlassMath.clampGlassAlpha(44))
        assertEquals(65, WidgetGlassMath.clampGlassAlpha(65))
        assertEquals(100, WidgetGlassMath.clampGlassAlpha(100))
        assertEquals(100, WidgetGlassMath.clampGlassAlpha(140))
    }

    @Test
    fun effectiveTintAlphaPerStyle() {
        val dark = WidgetGlassMath.effectiveTintAlpha(WidgetGlassStyle.DARK, 65)
        val light = WidgetGlassMath.effectiveTintAlpha(WidgetGlassStyle.LIGHT, 65)
        val clear = WidgetGlassMath.effectiveTintAlpha(WidgetGlassStyle.CLEAR, 65)
        assertEquals(dark, light)
        assertTrue("clear must be a fraction of the full tint", clear > 0f && clear < dark)
        // Even at the slider minimum the tint never vanishes.
        assertTrue(WidgetGlassMath.effectiveTintAlpha(WidgetGlassStyle.DARK, 45) > 0f)
    }

    private fun pack(subject: String, section: String, vararg tips: Pair<String, String?>): TipsPack =
        TipsPack(
            subject = subject,
            version = 1,
            updatedAt = "2026-11-24T00:00:00Z",
            sections = mapOf(
                section to tips.mapIndexed { i, (text, author) ->
                    TipItem(id = "$subject-$i", text = text, author = author, createdAt = "2026-11-24")
                }
            )
        )

    @Test
    fun dailyPickFollowsContractOrderAndIsDeterministic() {
        // القلبية precedes الكلية والشوارد in the contract order, and
        // most_common precedes key_points inside a subject.
        val packs = mapOf(
            "الكلية والشوارد" to pack("الكلية والشوارد", "most_common", "نصيحة كلية" to "سارة"),
            "القلبية" to pack(
                "القلبية", "key_points", "نقطة قلبية" to null
            ).let { p ->
                p.copy(
                    sections = p.sections + ("most_common" to listOf(
                        TipItem("c0", "أشيع قلبية", "أحمد", "2026-11-24")
                    ))
                )
            }
        )
        val flat = WidgetTipMath.flatten(packs)
        assertEquals(listOf("أشيع قلبية", "نقطة قلبية", "نصيحة كلية"), flat.map { it.text })
        assertEquals(flat[0], WidgetTipMath.pickDaily(packs, 0))
        assertEquals(flat[1], WidgetTipMath.pickDaily(packs, 1))
        assertEquals(flat[0], WidgetTipMath.pickDaily(packs, 3))
        // Same day, same packs → same tip, always.
        assertEquals(WidgetTipMath.pickDaily(packs, 42), WidgetTipMath.pickDaily(packs, 42))
    }

    @Test
    fun dailyPickIsNullWithoutAnyTip() {
        assertNull(WidgetTipMath.pickDaily(emptyMap(), 10))
        assertNull(
            WidgetTipMath.pickDaily(
                mapOf("القلبية" to TipsPack("القلبية", 1, "2026-11-24", emptyMap())), 10
            )
        )
    }

    @Test
    fun authorLineOnlyWhenAuthorPresent() {
        assertEquals(
            "من زوادة الطالب — أحمد",
            WidgetTipMath.authorLine(WidgetTip("نص", "أحمد", "القلبية", "most_common"))
        )
        assertNull(WidgetTipMath.authorLine(WidgetTip("نص", null, "القلبية", "most_common")))
        assertNull(WidgetTipMath.authorLine(WidgetTip("نص", "  ", "القلبية", "most_common")))
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val today = LocalDate.of(2026, 11, 24)
        val active = mapOf(
            "2026-11-24" to 5, "2026-11-23" to 3, "2026-11-22" to 1, "2026-11-20" to 9
        )
        assertEquals(3, WidgetStreakMath.streakDays(active, today))
        // Today not yet studied: the streak ending yesterday stands.
        assertEquals(2, WidgetStreakMath.streakDays(active - "2026-11-24", today))
        // A silent yesterday breaks it.
        assertEquals(0, WidgetStreakMath.streakDays(mapOf("2026-11-22" to 4), today))
        assertEquals(0, WidgetStreakMath.streakDays(emptyMap(), today))
    }

    @Test
    fun weekDotsAreOldestToNewest() {
        val today = LocalDate.of(2026, 11, 24)
        val activity = mapOf("2026-11-24" to 1, "2026-11-20" to 2, "2026-11-18" to 1)
        assertEquals(
            listOf(true, false, true, false, false, false, true),
            WidgetStreakMath.weekDots(activity, today)
        )
        assertEquals(List(7) { false }, WidgetStreakMath.weekDots(emptyMap(), today))
    }

    // ── Glass fix 1.2.52 ─────────────────────────────────────

    @Test
    fun clearTintHasAnAbsoluteFloor() {
        // At the slider minimum the CLEAR tint must not collapse
        // toward transparency (v4: ~0.16 over bright photos).
        val atMin = WidgetGlassMath.effectiveTintAlpha(WidgetGlassStyle.CLEAR, 45)
        assertTrue("clear tint at slider minimum must stay >= 0.30, was $atMin", atMin >= 0.30f)
        assertTrue(atMin < WidgetGlassMath.effectiveTintAlpha(WidgetGlassStyle.DARK, 45))
        // At full slider the fraction applies above the floor.
        assertEquals(
            0.55f, WidgetGlassMath.effectiveTintAlpha(WidgetGlassStyle.CLEAR, 100), 0.001f
        )
    }

    @Test
    fun deriveSizeUsesTheReportedRangePerOrientation() {
        val fallback = 250 to 140
        // Portrait: current width is MIN_WIDTH, current height
        // is MAX_HEIGHT (documented AppWidgetOptions semantics).
        assertEquals(
            250 to 300,
            WidgetSizeMath.deriveSizeDp(250, 140, 250, 300, isPortrait = true, fallback)
        )
        // Landscape: the axes swap.
        assertEquals(
            420 to 140,
            WidgetSizeMath.deriveSizeDp(250, 140, 420, 300, isPortrait = false, fallback)
        )
        // Hosts reporting no range (MIN == MAX) are unaffected.
        assertEquals(
            250 to 140,
            WidgetSizeMath.deriveSizeDp(250, 140, 250, 140, isPortrait = true, fallback)
        )
        // Nothing reported at all: the provider-info fallback.
        assertEquals(
            fallback,
            WidgetSizeMath.deriveSizeDp(0, 0, 0, 0, isPortrait = true, fallback)
        )
        // Partial range: a missing MAX falls back to MIN.
        assertEquals(
            250 to 140,
            WidgetSizeMath.deriveSizeDp(250, 140, 0, 0, isPortrait = true, fallback)
        )
    }

    @Test
    fun glassProvidersRebakeOnOptionsChanged() {
        // Structural: every glass provider must route through
        // the shared base that overrides onAppWidgetOptionsChanged
        // (v4 shipped without it — resized widgets kept a
        // wrong-size baked face until the next periodic update).
        val base = Class.forName("sy.medpro.watani.ui.widget.GlassWidgetProvider")
        val method = base.getDeclaredMethod(
            "onAppWidgetOptionsChanged",
            android.content.Context::class.java,
            android.appwidget.AppWidgetManager::class.java,
            Int::class.javaPrimitiveType,
            android.os.Bundle::class.java
        )
        assertTrue(method.declaringClass == base)
        listOf(
            "CountdownWideWidgetProvider", "HeroWidgetProvider", "GoalWidgetProvider",
            "StreakWidgetProvider", "ContinueWidgetProvider", "TipWidgetProvider",
            "QuickActionsWidgetProvider", "FocusWidgetProvider", "MiniCountdownWidgetProvider"
        ).forEach { name ->
            val clazz = Class.forName("sy.medpro.watani.ui.widget.$name")
            assertTrue("$name must extend GlassWidgetProvider", base.isAssignableFrom(clazz))
        }
    }
}
