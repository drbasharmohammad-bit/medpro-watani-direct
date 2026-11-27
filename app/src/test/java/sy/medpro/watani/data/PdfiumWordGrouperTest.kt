package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.data.PdfiumWordGrouper.RangeRect

/**
 * Layer 2 — the PDFium text layer's pure bridge (build 1.2.52),
 * written RED first.
 *
 * Since 1.2.52 the reader's words come from FPDFText (the same
 * PDFium engine that renders), as one page string plus per-range
 * rects in PDF points (origin bottom-left, y up). The reader's
 * highlight space is page fractions with y from the TOP.
 * [PdfiumWordGrouper] is the conversion, and these tests pin:
 * word ranges are maximal non-whitespace runs; a word's box is
 * the union of its range rects converted to fractions; the
 * conversion lands identically under either y orientation the
 * binding might hand back (raw PDF top>bottom, or pre-flipped);
 * a word with no rect is dropped without harming its
 * neighbours; boxes are clamped to the page.
 */
class PdfiumWordGrouperTest {

    // ---- word ranges ----

    @Test
    fun wordRangesSplitOnWhitespaceRuns() {
        assertEquals(
            listOf(0..2, 4..6),
            PdfiumWordGrouper.wordRanges("abc def")
        )
        assertEquals(
            listOf(2..2, 5..5),
            PdfiumWordGrouper.wordRanges("  a  b ")
        )
        assertEquals(
            listOf(0..0, 2..2, 4..4),
            PdfiumWordGrouper.wordRanges("a\nb\tc")
        )
    }

    @Test
    fun wordRangesOfBlankTextAreEmpty() {
        assertTrue(PdfiumWordGrouper.wordRanges("").isEmpty())
        assertTrue(PdfiumWordGrouper.wordRanges("  \n \t ").isEmpty())
    }

    // ---- grouping + conversion (page 200 x 100 pt) ----

    @Test
    fun pdfOrientationRectConvertsToTopOriginFractions() {
        // "hello" at PDF points x 10..60, y 80..90 (PDF space:
        // top = 90 is the LARGER y). Fractions: x 0.05..0.30,
        // y from top: 1-90/100=0.10 .. 1-80/100=0.20.
        val words = PdfiumWordGrouper.groupWords(
            text = "hello",
            rects = listOf(RangeRect(0, 5, 10f, 90f, 60f, 80f)),
            pageWidthPt = 200f,
            pageHeightPt = 100f
        )
        assertEquals(1, words.size)
        val w = words.single()
        assertEquals("hello", w.text)
        assertEquals(0.05f, w.x0, 0.0001f)
        assertEquals(0.30f, w.x1, 0.0001f)
        assertEquals(0.10f, w.y0, 0.0001f)
        assertEquals(0.20f, w.y1, 0.0001f)
    }

    @Test
    fun flippedOrientationRectLandsIdentically() {
        // The same box stored Android-style (top = 80 < bottom
        // = 90) must produce the same fractions: the fold takes
        // min/max per axis and never trusts the orientation.
        val words = PdfiumWordGrouper.groupWords(
            text = "hello",
            rects = listOf(RangeRect(0, 5, 10f, 80f, 60f, 90f)),
            pageWidthPt = 200f,
            pageHeightPt = 100f
        )
        val w = words.single()
        assertEquals(0.05f, w.x0, 0.0001f)
        assertEquals(0.30f, w.x1, 0.0001f)
        assertEquals(0.10f, w.y0, 0.0001f)
        assertEquals(0.20f, w.y1, 0.0001f)
    }

    @Test
    fun wordWithoutRectIsDroppedNeighboursUnaffected() {
        // "one two": only "two" (start 4, length 3) has a rect.
        val words = PdfiumWordGrouper.groupWords(
            text = "one two",
            rects = listOf(RangeRect(4, 3, 100f, 90f, 160f, 80f)),
            pageWidthPt = 200f,
            pageHeightPt = 100f
        )
        assertEquals(1, words.size)
        assertEquals("two", words.single().text)
        assertEquals(0.50f, words.single().x0, 0.0001f)
    }

    @Test
    fun multipleRectsForOneRangeAreUnioned() {
        // A range reported as two rects (e.g. split glyph runs)
        // folds into their union.
        val words = PdfiumWordGrouper.groupWords(
            text = "hello",
            rects = listOf(
                RangeRect(0, 5, 10f, 90f, 35f, 80f),
                RangeRect(0, 5, 35f, 92f, 60f, 78f)
            ),
            pageWidthPt = 200f,
            pageHeightPt = 100f
        )
        val w = words.single()
        assertEquals(0.05f, w.x0, 0.0001f)
        assertEquals(0.30f, w.x1, 0.0001f)
        assertEquals(0.08f, w.y0, 0.0001f)
        assertEquals(0.22f, w.y1, 0.0001f)
    }

    @Test
    fun boxesAreClampedToThePage() {
        val words = PdfiumWordGrouper.groupWords(
            text = "edge",
            rects = listOf(RangeRect(0, 4, -20f, 130f, 260f, -10f)),
            pageWidthPt = 200f,
            pageHeightPt = 100f
        )
        val w = words.single()
        assertEquals(0f, w.x0, 0.0001f)
        assertEquals(1f, w.x1, 0.0001f)
        assertEquals(0f, w.y0, 0.0001f)
        assertEquals(1f, w.y1, 0.0001f)
    }

    @Test
    fun degeneratePageYieldsNoWords() {
        assertTrue(
            PdfiumWordGrouper.groupWords(
                text = "hello",
                rects = listOf(RangeRect(0, 5, 10f, 90f, 60f, 80f)),
                pageWidthPt = 0f,
                pageHeightPt = 100f
            ).isEmpty()
        )
    }
}
