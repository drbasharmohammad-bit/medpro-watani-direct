package sy.medpro.watani.data

/**
 * Word selection for the reader's yellow text highlighter
 * (build 1.2.50).
 *
 * The pre-1.2.50 drag matched words by strict rectangle
 * overlap with zero tolerance: a selection that ended a
 * hair before a word's edge — the natural way a finger
 * drag ends — matched nothing, created no highlight and
 * gave no feedback, so the highlighter read as broken.
 * Selection now expands the drag rect by [DEFAULT_TOLERANCE]
 * of the page on every side (a graze counts as intent) and
 * also counts a word whose centre falls inside the raw
 * selection (a thin drag through a word's middle). Words
 * beyond the tolerance never match — a deliberate stop
 * before a word stays a stop.
 *
 * Coordinates are page fractions, y from the top — the
 * [WordBox] space the text engine extracts.
 */
object HighlightSelection {

    /** Page-fraction slack added to every side of the drag
     * rect before overlap is tested. */
    const val DEFAULT_TOLERANCE = 0.004f

    /**
     * The words of [words] selected by the drag rect
     * ([left], [top])–([right], [bottom]), in list order.
     */
    fun selectWords(
        words: List<WordBox>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        tolerance: Float = DEFAULT_TOLERANCE
    ): List<WordBox> {
        val expandedLeft = left - tolerance
        val expandedTop = top - tolerance
        val expandedRight = right + tolerance
        val expandedBottom = bottom + tolerance
        return words.filter { w ->
            val overlaps = w.x1 > expandedLeft && w.x0 < expandedRight &&
                w.y1 > expandedTop && w.y0 < expandedBottom
            val centreX = (w.x0 + w.x1) / 2f
            val centreY = (w.y0 + w.y1) / 2f
            val centreInside = centreX >= left && centreX <= right &&
                centreY >= top && centreY <= bottom
            overlaps || centreInside
        }
    }
}
