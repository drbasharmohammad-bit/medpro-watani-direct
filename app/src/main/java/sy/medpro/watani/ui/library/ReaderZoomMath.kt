package sy.medpro.watani.ui.library

import kotlin.math.max

/**
 * Zoom/pan math for the unified PDF reader (build 1.2.50),
 * written from the doctor's device report of 24 Nov 2026:
 * pinch zoom shook (رجفان بالشاشة).
 *
 * Root cause (from the pre-1.2.50 code): the page's
 * graphicsLayer scales around its default transform origin
 * — the CENTRE of the box — while the pinch handler's pan
 * only followed the fingers' centroid translation. The
 * content point under the fingers therefore moved on every
 * event by the scale-around-centre term, and the pan
 * correction arrived a frame later: the page visibly swam
 * and shook under the fingers, worst at the page edges
 * where the pan clamp fought the uncompensated scale.
 *
 * [anchoredPan] is the fix: given the gesture's old and new
 * centroids and zooms, it returns the pan that lands the
 * content point that sat under the OLD centroid exactly
 * under the NEW centroid at the NEW zoom — one formula
 * covering both the scale and the translation of the same
 * event, so nothing is left to lag a frame behind. The
 * layer's mapping is screen(p) = O + (p − O)·z + t with
 * O the box centre; solving C_new = O + (p_c − O)·z' + t'
 * for t' gives the body below.
 *
 * Pure float math, no Android types — unit-tested directly.
 */
object ReaderZoomMath {

    /** A pan offset in pixels. */
    data class Pan(val x: Float, val y: Float)

    /**
     * The pan after one pinch event: the content point under
     * the old centroid stays under the fingers (now at the
     * new centroid) when the zoom moves [oldZoom] →
     * [newZoom]. Degenerate inputs (unmeasured box, no prior
     * zoom) keep the pan unchanged.
     */
    fun anchoredPan(
        oldCentroidX: Float,
        oldCentroidY: Float,
        newCentroidX: Float,
        newCentroidY: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
        oldZoom: Float,
        newZoom: Float,
        panX: Float,
        panY: Float
    ): Pan {
        if (boxWidthPx <= 0 || boxHeightPx <= 0 || oldZoom <= 0f) {
            return Pan(panX, panY)
        }
        val originX = boxWidthPx / 2f
        val originY = boxHeightPx / 2f
        val ratio = newZoom / oldZoom
        return Pan(
            x = newCentroidX - originX - (oldCentroidX - originX - panX) * ratio,
            y = newCentroidY - originY - (oldCentroidY - originY - panY) * ratio
        )
    }

    /**
     * Clamps a pan so the fitted page can never be dragged
     * off-screen: the most it may move on an axis is half
     * the overflow of the zoomed fitted page over the box
     * (zero when the zoomed page still fits).
     */
    fun clampPan(
        panX: Float,
        panY: Float,
        fittedWidthPx: Float,
        fittedHeightPx: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
        zoom: Float
    ): Pan {
        val maxX = max(0f, (fittedWidthPx * zoom - boxWidthPx) / 2f)
        val maxY = max(0f, (fittedHeightPx * zoom - boxHeightPx) / 2f)
        return Pan(
            x = panX.coerceIn(-maxX, maxX),
            y = panY.coerceIn(-maxY, maxY)
        )
    }
}
