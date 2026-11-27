package sy.medpro.watani.data

/**
 * Word grouping for the PDFium text layer (build 1.2.52).
 *
 * PDFium's FPDFText hands the reader two things per page: the
 * page's full text as one string (character indices are UTF-16
 * code units, the same indexing the string itself uses) and,
 * per character range, bounding rects in PDF page points
 * (origin at the page's bottom-left, y growing UP). The reader's
 * highlight space is the [WordBox] space instead: page
 * fractions with y measured from the TOP.
 *
 * This object is the pure bridge between the two: it finds the
 * word ranges of a page text (maximal non-whitespace runs) and
 * folds the rects PDFium returns for those ranges into
 * [WordBox]es. The fold is deliberately orientation-agnostic:
 * it takes min/max of each rect axis, so it lands identically
 * whether the binding hands back raw PDF orientation (top >
 * bottom) or a pre-flipped Android-style rect. A word whose
 * range came back with no rect at all is dropped — a highlight
 * without geometry cannot be drawn — while its neighbours are
 * unaffected.
 *
 * Pure Kotlin, no Android types: unit-tested directly.
 */
object PdfiumWordGrouper {

    /**
     * One range rect as PDFium reports it: the character range
     * [start]..[start]+[length] and its bounding box in PDF
     * page points (whichever y orientation the binding uses).
     */
    data class RangeRect(
        val start: Int,
        val length: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    /**
     * The word ranges of [text]: maximal runs of
     * non-whitespace characters, as inclusive index ranges
     * into the string. Whitespace is [Char.isWhitespace] plus
     * the non-breaking space PDFium likes to emit.
     */
    fun wordRanges(text: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var start = -1
        for (i in text.indices) {
            if (isSeparator(text[i])) {
                if (start >= 0) {
                    ranges += start..(i - 1)
                    start = -1
                }
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) {
            ranges += start..(text.length - 1)
        }
        return ranges
    }

    private fun isSeparator(ch: Char): Boolean =
        ch.isWhitespace() || ch == '\u00A0'

    /**
     * The [WordBox]es of one page: for every word range of
     * [text], the union of the [rects] reported for that exact
     * range, converted to page fractions (y from the top) on a
     * page of [pageWidthPt] x [pageHeightPt] points and clamped
     * to the page. Words with no rect, and degenerate pages,
     * yield no box.
     */
    fun groupWords(
        text: String,
        rects: List<RangeRect>,
        pageWidthPt: Float,
        pageHeightPt: Float
    ): List<WordBox> {
        if (pageWidthPt <= 0f || pageHeightPt <= 0f || text.isEmpty()) {
            return emptyList()
        }
        val byRange = rects.groupBy { it.start to it.length }
        return wordRanges(text).mapNotNull { range ->
            val length = range.last - range.first + 1
            val boxes = byRange[range.first to length].orEmpty()
            if (boxes.isEmpty()) {
                null
            } else {
                unionBox(text.substring(range), boxes, pageWidthPt, pageHeightPt)
            }
        }
    }

    /**
     * One word's box: the union of its range rects (min/max per
     * axis, so either y orientation folds identically), with
     * the PDF y axis flipped into the reader's top-origin
     * fractions and every edge clamped to the page. A union
     * that ends up degenerate (zero area after clamping)
     * yields no box.
     */
    private fun unionBox(
        word: String,
        boxes: List<RangeRect>,
        pageWidthPt: Float,
        pageHeightPt: Float
    ): WordBox? {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var lowY = Float.MAX_VALUE
        var highY = -Float.MAX_VALUE
        for (rect in boxes) {
            left = minOf(left, rect.left, rect.right)
            right = maxOf(right, rect.left, rect.right)
            lowY = minOf(lowY, rect.top, rect.bottom)
            highY = maxOf(highY, rect.top, rect.bottom)
        }
        // PDF space: highY is the word's TOP edge (y grows up),
        // so its from-the-top fraction is 1 - highY/height.
        val x0 = (left / pageWidthPt).coerceIn(0f, 1f)
        val x1 = (right / pageWidthPt).coerceIn(0f, 1f)
        val y0 = (1f - highY / pageHeightPt).coerceIn(0f, 1f)
        val y1 = (1f - lowY / pageHeightPt).coerceIn(0f, 1f)
        return if (x1 > x0 && y1 > y0) {
            WordBox(text = word, x0 = x0, y0 = y0, x1 = x1, y1 = y1)
        } else {
            null
        }
    }
}
