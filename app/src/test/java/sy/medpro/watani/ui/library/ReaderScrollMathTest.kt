package sy.medpro.watani.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test
import sy.medpro.watani.data.PdfPagePoints
import sy.medpro.watani.ui.library.ReaderScrollMath.VisibleSlice

/**
 * Layer 2 — the vertical page stack's layout math (build
 * 1.2.52), written RED first from the doctor's order of
 * 27 Nov 2026: browsing is VERTICAL, not horizontal («التصفح
 * عامودي وليس افقي»).
 *
 * Pages stack in one column fitted to its full width, so a
 * page's laid-out height is the column width scaled by the
 * page's aspect ratio; and the stack's "current page" — the
 * one the indicator, the slider and the saved reading
 * position follow — is the page occupying the largest visible
 * slice of the viewport.
 */
class ReaderScrollMathTest {

    @Test
    fun pageHeightFollowsTheAspectRatio() {
        // A portrait page twice as tall as wide, in a 500px
        // column, lays out 1000px tall.
        assertEquals(
            1000,
            ReaderScrollMath.pageHeightPx(PdfPagePoints(100, 200), 500)
        )
        // A landscape page half as tall as wide lays out 250px.
        assertEquals(
            250,
            ReaderScrollMath.pageHeightPx(PdfPagePoints(200, 100), 500)
        )
    }

    @Test
    fun pageHeightIsAtLeastOnePixel() {
        // A sliver of a page (1000x1 pt) in a 100px column
        // still occupies a pixel, never zero.
        assertEquals(
            1,
            ReaderScrollMath.pageHeightPx(PdfPagePoints(1000, 1), 100)
        )
    }

    @Test
    fun pageHeightOfDegenerateInputsIsZero() {
        assertEquals(
            0,
            ReaderScrollMath.pageHeightPx(PdfPagePoints(0, 100), 500)
        )
        assertEquals(
            0,
            ReaderScrollMath.pageHeightPx(PdfPagePoints(100, 100), 0)
        )
    }

    @Test
    fun currentPageIsTheLargestVisibleSlice() {
        assertEquals(
            7,
            ReaderScrollMath.currentPageFromVisible(
                listOf(VisibleSlice(6, 120), VisibleSlice(7, 640))
            )
        )
        assertEquals(
            3,
            ReaderScrollMath.currentPageFromVisible(
                listOf(VisibleSlice(3, 800), VisibleSlice(4, 90))
            )
        )
    }

    @Test
    fun currentPageTieKeepsTheEarlierPage() {
        assertEquals(
            4,
            ReaderScrollMath.currentPageFromVisible(
                listOf(VisibleSlice(4, 300), VisibleSlice(5, 300))
            )
        )
    }

    @Test
    fun currentPageOfNothingVisibleIsTheStackStart() {
        assertEquals(0, ReaderScrollMath.currentPageFromVisible(emptyList()))
    }
}
