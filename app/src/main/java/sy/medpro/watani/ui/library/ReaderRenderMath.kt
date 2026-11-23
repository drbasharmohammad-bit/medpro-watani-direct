package sy.medpro.watani.ui.library

import kotlin.math.sqrt

/**
 * Render-resolution plan for the unified PDF reader (build 1.2.46).
 *
 * The pre-1.2.46 reader rendered every page at a FIXED x2 of its
 * point size (A4 -> 1190x1684 px) whatever the screen: on wide or
 * high-density screens the fitted page was upscaled past its own
 * resolution (the «غباش» the doctor reported), and the zoom
 * re-render capped out at x4 points, so 3-4x zooms stayed soft
 * forever. The plan now follows the DISPLAYED size instead:
 *
 *  - base render: target width = pager box width in px x
 *    [BASE_OVERSAMPLE], so the fitted page is always rendered a
 *    notch above the pixels it will occupy;
 *  - zoom render: target width = box width x settled zoom x
 *    [HIRES_OVERSAMPLE], so sharpness follows the zoom itself;
 *  - both are bounded by a per-bitmap PIXEL budget: a full-page
 *    render grows quadratically, so past the budget the scale is
 *    reduced to fit it (that is the honest ceiling of full-page
 *    rendering — true tiling would be needed beyond it).
 *
 * Pure math, no Android types: unit-tested directly.
 */
object ReaderRenderMath {

    /** Scale (page points -> px) used when the box size is not
     * known yet — the pre-1.2.46 behaviour, kept as the floor. */
    const val FALLBACK_SCALE = 2f

    /** Base renders oversample the on-screen width by this much,
     * so a fitted page downscales slightly (crisp) instead of
     * upscaling (soft). */
    const val BASE_OVERSAMPLE = 1.5f

    /** Zoom renders follow the settled zoom with this headroom. */
    const val HIRES_OVERSAMPLE = 1.3f

    /** Below this zoom the base render already covers the view;
     * no zoom re-render is requested. */
    const val HIRES_MIN_ZOOM = 1.15f

    /** Per-bitmap pixel budgets (ARGB_8888 = 4 bytes per pixel):
     * ~17 MB for a base page, ~36 MB for the one zoom render. */
    const val BASE_MAX_PIXELS = 4_200_000
    const val HIRES_MAX_PIXELS = 9_000_000

    /** Target pixel width for a BASE render of a page shown in a
     * box [boxWidthPx] wide; 0 when the box is not measured yet
     * (the caller then falls back to [FALLBACK_SCALE]). */
    fun baseTargetWidthPx(boxWidthPx: Int): Int =
        if (boxWidthPx > 0) (boxWidthPx * BASE_OVERSAMPLE).toInt() else 0

    /** Target pixel width for the ZOOM render at [zoom]; 0 when
     * no zoom render is wanted (unmeasured box or near-1x zoom). */
    fun hiResTargetWidthPx(boxWidthPx: Int, zoom: Float): Int =
        if (boxWidthPx > 0 && zoom > HIRES_MIN_ZOOM) {
            (boxWidthPx * zoom * HIRES_OVERSAMPLE).toInt()
        } else {
            0
        }

    /**
     * The render scale (page points -> px) that makes a
     * [pageWidthPt] x [pageHeightPt] page come out about
     * [targetWidthPx] wide, shrunk when needed so the bitmap
     * stays within [maxPixels]. Degenerate inputs (unopened page,
     * unmeasured box) return [FALLBACK_SCALE].
     */
    fun scaleForTargetWidth(
        pageWidthPt: Int,
        pageHeightPt: Int,
        targetWidthPx: Int,
        maxPixels: Int
    ): Float {
        if (pageWidthPt <= 0 || pageHeightPt <= 0 || targetWidthPx <= 0) {
            return FALLBACK_SCALE
        }
        var scale = targetWidthPx.toFloat() / pageWidthPt.toFloat()
        val pointArea = pageWidthPt.toDouble() * pageHeightPt.toDouble()
        val pixels = pointArea * scale.toDouble() * scale.toDouble()
        if (pixels > maxPixels) {
            scale = (scale * sqrt(maxPixels.toDouble() / pixels)).toFloat()
        }
        return scale
    }
}
