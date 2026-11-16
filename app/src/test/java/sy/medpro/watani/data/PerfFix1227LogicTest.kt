package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.27 pure-logic pins for the two device reports of
 * 16 Nov 2026:
 *
 * 1. Entry lag («يبقى وقتًا حتى يبدأ الاختبار»): the envelope
 *    cache's hash-stamped invalidation and LRU bound, and the
 *    session extraction rules the fast entry path shares with the
 *    old inline code (identical counts/selection — performance
 *    only, content behavior unchanged).
 * 2. Widget corner («الصورة خارجة عن قوس الإطار»): the one corner
 *    path — radius constant equals the frame drawables' 24dp, and
 *    the bitmap geometry (center-crop, size caps) the in-code
 *    clipping is built from.
 */
class PerfFix1227LogicTest {

    // ---------------- EnvelopeCache ----------------

    @Test
    fun cacheServesOnlyTheExactRegistryHash() {
        val cache = EnvelopeCache<String>(maxWeight = 1000)
        cache.put("questions/القلبية.json", "hashA", 100, "envelopeA")
        assertEquals("envelopeA", cache.get("questions/القلبية.json", "hashA"))
        // A re-download swaps the hash: the stale parse must drop.
        assertNull(cache.get("questions/القلبية.json", "hashB"))
        assertEquals(0, cache.size())
        assertNull(cache.get("questions/القلبية.json", "hashA"))
    }

    @Test
    fun cacheEvictsLeastRecentlyUsedByWeight() {
        val cache = EnvelopeCache<String>(maxWeight = 300)
        cache.put("a", "h", 120, "A")
        cache.put("b", "h", 120, "B")
        // Touch A so B becomes the LRU entry.
        assertEquals("A", cache.get("a", "h"))
        cache.put("c", "h", 120, "C")
        assertNull(cache.get("b", "h"))
        assertEquals("A", cache.get("a", "h"))
        assertEquals("C", cache.get("c", "h"))
        assertEquals(2, cache.size())
    }

    @Test
    fun cacheNeverStoresAnEntryHeavierThanTheBudget() {
        val cache = EnvelopeCache<String>(maxWeight = 100)
        cache.put("small", "h", 50, "S")
        cache.put("huge", "h", 5000, "H")
        assertNull(cache.get("huge", "h"))
        // …and the oversize put must not evict the healthy entry.
        assertEquals("S", cache.get("small", "h"))
    }

    @Test
    fun cachePutSamePathReplacesWeightAndValue() {
        val cache = EnvelopeCache<String>(maxWeight = 300)
        cache.put("a", "h1", 200, "old")
        cache.put("a", "h2", 200, "new")
        assertEquals(1, cache.size())
        assertEquals("new", cache.get("a", "h2"))
        cache.evict("a")
        assertNull(cache.get("a", "h2"))
    }

    // ---------------- SectionIndex ----------------

    private fun q(id: String) = QuestionDto(
        id = id, text = "stem $id", options = listOf("أ", "ب"),
        optionLabels = null, correct = 0, correctIndices = null,
        explanation = null, years = null, source = null, session = null,
        topics = null, free = null
    )

    private fun envelope(vararg sessions: SessionDto) = SubjectEnvelope(
        version = null, subjectId = null, subjectName = "مادة",
        university = null, universityName = null, count = null,
        topics = null, sessions = sessions.toList()
    )

    private fun session(name: String?, count: Int?, questions: List<QuestionDto>?) =
        SessionDto(
            id = null, name = name, year = null, term = null,
            questionCount = count, questions = questions
        )

    @Test
    fun sessionCountsMergeDuplicateNamesAndPreferRealQuestions() {
        val env = envelope(
            session(" أيار 2026 ", null, listOf(q("1"), q("2"))),
            session("أيار 2026", null, listOf(q("3"))),
            // No question list: the declared count is the fallback.
            session("تشرين 2025", 7, null),
            // Blank names never enter the table.
            session("   ", null, listOf(q("9"))),
            session(null, null, listOf(q("10")))
        )
        val counts = SectionIndex.sessionCounts(env)
        assertEquals(3, counts["أيار 2026"])
        assertEquals(7, counts["تشرين 2025"])
        assertEquals(2, counts.size)
    }

    @Test
    fun sessionQuestionsConcatenateMatchingSessionsInShardOrder() {
        val env = envelope(
            session("أيار 2026", null, listOf(q("1"))),
            session("تشرين 2025", null, listOf(q("x"))),
            session(" أيار 2026", null, listOf(q("2"), q("3")))
        )
        val picked = SectionIndex.sessionQuestions(env, "أيار 2026")
        assertEquals(listOf("1", "2", "3"), picked.map { it.id })
        assertTrue(SectionIndex.sessionQuestions(env, "حزيران 2020").isEmpty())
    }

    // ---------------- WidgetShapeMath ----------------

    @Test
    fun widgetCornerRadiusMatchesTheFrameDrawables() {
        // widget_bg / widget_photo_frame both declare 24dp — the
        // in-code bitmap clip must use the same radius. (The old
        // widget_frame border drawable is gone: no frame line at
        // all since the doctor's 16 Nov 18:32 order.)
        assertEquals(24, WidgetShapeMath.CORNER_RADIUS_DP)
    }

    @Test
    fun widgetRadiusNeverExceedsHalfTheShortestSide() {
        assertEquals(72f, WidgetShapeMath.radiusPx(250, 140, 3f), 0.01f)
        // Short widget: capped at half the height.
        assertEquals(60f, WidgetShapeMath.radiusPx(250, 40, 3f), 0.01f)
    }

    @Test
    fun widgetBitmapSizeKeepsAspectUnderTheCap() {
        assertEquals(750 to 420, WidgetShapeMath.bitmapSizePx(250, 140, 3f))
        val (w, h) = WidgetShapeMath.bitmapSizePx(800, 800, 3f)
        assertTrue(w <= WidgetShapeMath.MAX_BITMAP_SIDE_PX)
        assertTrue(h <= WidgetShapeMath.MAX_BITMAP_SIDE_PX)
        assertEquals(w, h)
    }

    @Test
    fun widgetCenterCropRectIsCenteredAndKeepsTargetAspect() {
        // Square source into a wide target: full width, centered band.
        val wide = WidgetShapeMath.centerCropRect(1200, 1200, 2f)
        assertEquals(0f, wide[0], 0.01f)
        assertEquals(300f, wide[1], 0.01f)
        assertEquals(1200f, wide[2], 0.01f)
        assertEquals(600f, wide[3], 0.01f)
        // Wide source into a square target: centered column.
        val square = WidgetShapeMath.centerCropRect(1600, 800, 1f)
        assertEquals(400f, square[0], 0.01f)
        assertEquals(0f, square[1], 0.01f)
        assertEquals(800f, square[2], 0.01f)
        assertEquals(800f, square[3], 0.01f)
    }

    @Test
    fun widgetFallbackSizesMatchTheProviderInfos() {
        assertEquals(110 to 110, WidgetShapeMath.fallbackSizeDp(0))
        assertEquals(250 to 140, WidgetShapeMath.fallbackSizeDp(1))
        assertEquals(250 to 180, WidgetShapeMath.fallbackSizeDp(2))
    }
}
