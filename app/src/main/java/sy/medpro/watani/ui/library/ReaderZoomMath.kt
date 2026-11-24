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

    /** A 2D point or offset in pixels (centroids, pans). */
    data class Pan(val x: Float, val y: Float)

    /** A size in whole pixels (the pager box). */
    data class BoxSize(val widthPx: Int, val heightPx: Int)

    /** A size in fractional pixels (the fitted page). */
    data class SizeF(val width: Float, val height: Float)

    /** One pinch event's zoom movement. */
    data class ZoomChange(val from: Float, val to: Float)

    /**
     * The pan after one pinch event: the content point under
     * the old centroid stays under the fingers (now at the
     * new centroid) when the zoom moves per [zoom].
     * Degenerate inputs (unmeasured box, no prior zoom) keep
     * the pan unchanged.
     */
    fun anchoredPan(
        oldCentroid: Pan,
        newCentroid: Pan,
        box: BoxSize,
        zoom: ZoomChange,
        pan: Pan
    ): Pan {
        if (box.widthPx <= 0 || box.heightPx <= 0 || zoom.from <= 0f) {
            return pan
        }
        val originX = box.widthPx / 2f
        val originY = box.heightPx / 2f
        val ratio = zoom.to / zoom.from
        return Pan(
            x = newCentroid.x - originX - (oldCentroid.x - originX - pan.x) * ratio,
            y = newCentroid.y - originY - (oldCentroid.y - originY - pan.y) * ratio
        )
    }

    /**
     * Clamps a pan so the fitted page can never be dragged
     * off-screen: the most it may move on an axis is half
     * the overflow of the zoomed fitted page over the box
     * (zero when the zoomed page still fits).
     */
    fun clampPan(pan: Pan, fitted: SizeF, box: BoxSize, zoom: Float): Pan {
        val maxX = max(0f, (fitted.width * zoom - box.widthPx) / 2f)
        val maxY = max(0f, (fitted.height * zoom - box.heightPx) / 2f)
        return Pan(
            x = pan.x.coerceIn(-maxX, maxX),
            y = pan.y.coerceIn(-maxY, maxY)
        )
    }
}
