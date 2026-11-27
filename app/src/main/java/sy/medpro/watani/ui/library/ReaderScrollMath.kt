package sy.medpro.watani.ui.library

import sy.medpro.watani.data.PdfPagePoints

/**
 * Layout math of the reader's VERTICAL page stack (build
 * 1.2.52, the doctor's order of 27 Nov 2026: «التصفح عامودي
 * وليس افقي»).
 *
 * The reader used to flip pages horizontally (a pager); it now
 * stacks them in one continuous vertical column — page under
 * page — so reading is a single downward scroll, the way the
 * doctor asked and the way long booklets are actually read.
 * Two pieces of that stack are pure arithmetic and pinned
 * here:
 *
 *  - a page's on-screen height: pages render fitted to the
 *    column's full width, so the height follows from the
 *    page's own aspect ratio (points cancel out);
 *  - the "current page" of the stack: the page occupying the
 *    largest visible slice of the viewport — the page the
 *    student is actually reading — which drives the saved
 *    reading position, the page indicator and the slider.
 *
 * Pure math, no Android types: unit-tested directly.
 */
object ReaderScrollMath {

    /** One visible page's slice of the viewport: its [page]
     * index and how many of its pixels are on screen. */
    data class VisibleSlice(
        val page: Int,
        val visiblePx: Int
    )

    /**
     * The laid-out height (px) of a page of [page] points in a
     * column [columnWidthPx] wide: the width scaled by the
     * page's aspect ratio, at least 1px. Degenerate inputs
     * (unmeasured column, a page size the engine could not
     * report) yield 0 — the caller then falls back to a
     * default aspect instead of laying out a broken stack.
     */
    fun pageHeightPx(page: PdfPagePoints, columnWidthPx: Int): Int {
        // Stub (red-first commit of 1.2.52): implemented by the
        // build commit that follows.
        return 0
    }

    /**
     * The current page of the stack: the [VisibleSlice] with
     * the largest visible slice; a tie resolves to the earlier
     * page (the one being scrolled away from keeps the title
     * until the next page truly takes over). No visible page
     * (an unmeasured stack) yields 0 — the stack's start.
     */
    fun currentPageFromVisible(slices: List<VisibleSlice>): Int {
        // Stub (red-first commit of 1.2.52): implemented by the
        // build commit that follows.
        return 0
    }
}
