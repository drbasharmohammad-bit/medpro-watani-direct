package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.24 pure-logic pins: the student's own day goals
 * (free-text items, per-day rollover, storage encoding) and the
 * widget countdown split that feeds the self-ticking Chronometer.
 * The home editor, the three widgets and the storage layer all
 * read these exact functions, so they must never drift silently.
 */
class DayGoalsLogicTest {

    // ---------------- text sanitizing ----------------

    @Test
    fun sanitizeTrimsAndCollapsesWhitespace() {
        assertEquals("قلبية مراجعة", DayGoalsMath.sanitizeText("  قلبية   مراجعة  "))
        assertEquals("أطفال 1", DayGoalsMath.sanitizeText("أطفال\n1"))
        assertEquals("هدف", DayGoalsMath.sanitizeText("\t هدف \t"))
    }

    @Test
    fun sanitizeRejectsEmptyAndCapsLength() {
        assertNull(DayGoalsMath.sanitizeText(null))
        assertNull(DayGoalsMath.sanitizeText(""))
        assertNull(DayGoalsMath.sanitizeText("   \n  "))
        val long = "س".repeat(200)
        assertEquals(DayGoalsMath.MAX_TEXT_LENGTH, DayGoalsMath.sanitizeText(long)!!.length)
    }

    @Test
    fun sanitizeDropsControlCharacters() {
        // Storage separators and other control chars can never
        // survive into a stored text.
        val dirty = "هدف\u001Fخاص\u001Eهنا"
        val clean = DayGoalsMath.sanitizeText(dirty)!!
        assertTrue(!clean.contains('\u001F'))
        assertTrue(!clean.contains('\u001E'))
    }

    // ---------------- item operations ----------------

    @Test
    fun addItemAppendsSanitizedPendingGoal() {
        val start = listOf(DayGoalItem(1, "قلبية", done = true))
        val next = DayGoalsMath.addItem(start, "  هضمية  ", 2)
        assertEquals(2, next.size)
        assertEquals(DayGoalItem(2, "هضمية", false), next[1])
        assertEquals(start[0], next[0])
    }

    @Test
    fun addItemRejectsBlankAndOverflow() {
        val start = listOf(DayGoalItem(1, "قلبية"))
        assertEquals(start, DayGoalsMath.addItem(start, "   ", 2))
        assertEquals(start, DayGoalsMath.addItem(start, null, 2))
        val full = (1..DayGoalsMath.MAX_ITEMS).map { DayGoalItem(it, "هدف $it") }
        assertEquals(full, DayGoalsMath.addItem(full, "زائد", 99))
    }

    @Test
    fun toggleAndRemoveHitOnlyTheirItem() {
        val items = listOf(
            DayGoalItem(1, "قلبية"),
            DayGoalItem(2, "عصبية"),
            DayGoalItem(3, "أطفال")
        )
        val toggled = DayGoalsMath.toggleItem(items, 2)
        assertEquals(listOf(false, true, false), toggled.map { it.done })
        // Toggling again flips back.
        assertEquals(items, DayGoalsMath.toggleItem(toggled, 2))
        // Unknown id changes nothing.
        assertEquals(items, DayGoalsMath.toggleItem(items, 42))
        assertEquals(listOf(1, 3), DayGoalsMath.removeItem(items, 2).map { it.id })
        assertEquals(items, DayGoalsMath.removeItem(items, 42))
    }

    @Test
    fun doneCountCountsCheckedOnly() {
        val items = listOf(
            DayGoalItem(1, "أ", done = true),
            DayGoalItem(2, "ب"),
            DayGoalItem(3, "ج", done = true)
        )
        assertEquals(2, DayGoalsMath.doneCount(items))
        assertEquals(0, DayGoalsMath.doneCount(emptyList()))
    }

    // ---------------- storage encoding ----------------

    @Test
    fun encodeDecodeRoundTrips() {
        val items = listOf(
            DayGoalItem(1, "مراجعة القلبية", done = true),
            DayGoalItem(7, "أطفال 2 — جهاز هضمي"),
            DayGoalItem(9, "هدف بلا إنجاز", done = false)
        )
        assertEquals(items, DayGoalsMath.decode(DayGoalsMath.encode(items)))
        assertEquals(emptyList<DayGoalItem>(), DayGoalsMath.decode(DayGoalsMath.encode(emptyList())))
        assertEquals(emptyList<DayGoalItem>(), DayGoalsMath.decode(null))
        assertEquals(emptyList<DayGoalItem>(), DayGoalsMath.decode(""))
    }

    @Test
    fun decodeSkipsMalformedRecords() {
        val good = DayGoalsMath.encode(listOf(DayGoalItem(3, "سليم", done = true)))
        val dirty = good + "\u001E" + "garbage" + "\u001E" + "1\u001Fxx\u001Fنص"
        val decoded = DayGoalsMath.decode(dirty)
        assertEquals(listOf(DayGoalItem(3, "سليم", true)), decoded)
    }

    // ---------------- per-day rollover ----------------

    @Test
    fun rolloverKeepsOnlySameDayItems() {
        val items = listOf(DayGoalItem(1, "قلبية"))
        assertEquals(items, DayGoalsMath.rollover("2026-11-16", "2026-11-16", items))
        assertEquals(emptyList<DayGoalItem>(), DayGoalsMath.rollover("2026-11-15", "2026-11-16", items))
        assertEquals(emptyList<DayGoalItem>(), DayGoalsMath.rollover(null, "2026-11-16", items))
    }

    // ---------------- countdown split ----------------

    @Test
    fun splitSeparatesDaysFromWithinDayRemainder() {
        val day = CountdownMath.DAY_MILLIS
        assertEquals(CountdownMath.Parts(0, 0), CountdownMath.split(0))
        assertEquals(CountdownMath.Parts(0, 0), CountdownMath.split(-5_000))
        assertEquals(CountdownMath.Parts(36, 0), CountdownMath.split(36 * day))
        assertEquals(
            CountdownMath.Parts(36, 7 * 3_600_000L + 42 * 60_000L + 18_000L),
            CountdownMath.split(36 * day + 7 * 3_600_000L + 42 * 60_000L + 18_000L)
        )
        assertEquals(CountdownMath.Parts(0, 59_000), CountdownMath.split(59_000))
    }

    @Test
    fun chronometerBaseIsNowPlusWithinDay() {
        assertEquals(1_059_000L, CountdownMath.chronometerBase(1_000_000L, 59_000L))
        assertEquals(500L, CountdownMath.chronometerBase(500L, 0L))
    }

    @Test
    fun progressPercentClampsAndHandlesNoTarget() {
        assertEquals(0, CountdownMath.progressPercent(5, 0))
        assertEquals(0, CountdownMath.progressPercent(0, 20))
        assertEquals(50, CountdownMath.progressPercent(10, 20))
        assertEquals(100, CountdownMath.progressPercent(20, 20))
        assertEquals(100, CountdownMath.progressPercent(35, 20))
    }
}
