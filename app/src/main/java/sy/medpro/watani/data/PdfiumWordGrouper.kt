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
        // Stub (red-first commit of 1.2.52): implemented by the
        // build commit that follows.
        return emptyList()
    }

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
        // Stub (red-first commit of 1.2.52): implemented by the
        // build commit that follows.
        return emptyList()
    }
}
