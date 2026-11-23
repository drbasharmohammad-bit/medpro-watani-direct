package sy.medpro.watani.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer 2 — the reader's render-resolution plan (build 1.2.46).
 *
 * The pre-1.2.46 reader rendered at a fixed x2 of the page's point
 * size and capped the zoom re-render at x4 points, which is what
 * made pages hazy on wide screens and permanently soft at 3-4x
 * zoom. These tests pin the replacement contract: base targets
 * follow the real box width, zoom targets follow the zoom itself,
 * per-bitmap pixel budgets always bind, and degenerate inputs fall
 * back to the legacy scale instead of producing a broken bitmap.
 * A4 (595x842 pt) is the page size of every shipped booklet.
 */
class ReaderRenderMathTest {

    private val a4WidthPt = 595
    private val a4HeightPt = 842

    @Test
    fun baseTargetFollowsBoxWidth() {
        assertEquals(1620, ReaderRenderMath.baseTargetWidthPx(1080))
        assertEquals(2160, ReaderRenderMath.baseTargetWidthPx(1440))
        assertEquals(0, ReaderRenderMath.baseTargetWidthPx(0))
        assertEquals(0, ReaderRenderMath.baseTargetWidthPx(-320))
    }

    @Test
    fun baseScaleMatchesTheDisplayedWidth() {
        // A 1080px-wide phone: the base render must come out at
        // the oversampled target width (1620px), NOT at the old
        // fixed x2 of the point size (1190px).
        val scale = ReaderRenderMath.scaleForTargetWidth(
            a4WidthPt, a4HeightPt,
            ReaderRenderMath.baseTargetWidthPx(1080),
            ReaderRenderMath.BASE_MAX_PIXELS
        )
        assertEquals(1620f, a4WidthPt * scale, 2f)
        assertTrue(scale > ReaderRenderMath.FALLBACK_SCALE)
    }

    @Test
    fun basePixelBudgetBindsOnVeryWideScreens() {
        // A 2560px-wide tablet asks for 3840px; the budget must
        // shrink the render to fit it instead of allocating it.
        val scale = ReaderRenderMath.scaleForTargetWidth(
            a4WidthPt, a4HeightPt,
            ReaderRenderMath.baseTargetWidthPx(2560),
            ReaderRenderMath.BASE_MAX_PIXELS
        )
        val pixels = (a4WidthPt * scale) * (a4HeightPt * scale)
        assertTrue(pixels <= ReaderRenderMath.BASE_MAX_PIXELS * 1.01f)
        // ... while still covering the real display width.
        assertTrue(a4WidthPt * scale >= 1440f)
    }

    @Test
    fun hiResTargetFollowsTheZoomItself() {
        assertEquals(2808f, ReaderRenderMath.hiResTargetWidthPx(1080, 2f).toFloat(), 1f)
        assertEquals(5616f, ReaderRenderMath.hiResTargetWidthPx(1080, 4f).toFloat(), 1f)
        // Near-1x zoom earns no re-render at all.
        assertEquals(0, ReaderRenderMath.hiResTargetWidthPx(1080, 1f))
        assertEquals(0, ReaderRenderMath.hiResTargetWidthPx(1080, ReaderRenderMath.HIRES_MIN_ZOOM))
        assertEquals(0, ReaderRenderMath.hiResTargetWidthPx(0, 3f))
    }

    @Test
    fun hiResRenderIsSharperThanTheOldFixedCap() {
        // The old zoom re-render stopped at x4 points = a 2380px
        // wide bitmap whatever the zoom. At a settled 2x zoom on a
        // 1080px screen the new plan must beat that width while
        // respecting its own budget.
        val scale = ReaderRenderMath.scaleForTargetWidth(
            a4WidthPt, a4HeightPt,
            ReaderRenderMath.hiResTargetWidthPx(1080, 2f),
            ReaderRenderMath.HIRES_MAX_PIXELS
        )
        assertTrue(a4WidthPt * scale > 2380f)
        val pixels = (a4WidthPt * scale) * (a4HeightPt * scale)
        assertTrue(pixels <= ReaderRenderMath.HIRES_MAX_PIXELS * 1.01f)
    }

    @Test
    fun degenerateInputsFallBackToTheLegacyScale() {
        assertEquals(
            ReaderRenderMath.FALLBACK_SCALE,
            ReaderRenderMath.scaleForTargetWidth(0, a4HeightPt, 1620, ReaderRenderMath.BASE_MAX_PIXELS),
            0.001f
        )
        assertEquals(
            ReaderRenderMath.FALLBACK_SCALE,
            ReaderRenderMath.scaleForTargetWidth(a4WidthPt, 0, 1620, ReaderRenderMath.BASE_MAX_PIXELS),
            0.001f
        )
        assertEquals(
            ReaderRenderMath.FALLBACK_SCALE,
            ReaderRenderMath.scaleForTargetWidth(a4WidthPt, a4HeightPt, 0, ReaderRenderMath.BASE_MAX_PIXELS),
            0.001f
        )
    }
}
