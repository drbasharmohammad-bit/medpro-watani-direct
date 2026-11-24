package sy.medpro.watani.ui.library

/**
 * The page-resolution pipeline of the unified PDF reader
 * (build 1.2.50), written from the doctor's device report of
 * 24 Nov 2026: a DOWNLOADED book's page hung forever on the
 * labelled «جارٍ تجهيز الصفحة...» state.
 *
 * Root cause (from the pre-1.2.50 code): a page had exactly
 * ONE resolution path — a single display-matched render (up
 * to [ReaderRenderMath.BASE_MAX_PIXELS]) that returned a
 * bitmap or null. Every render (current page, both pager
 * neighbours, the zoom re-render) serialized behind one
 * monitor on the shared Dispatchers.Default pool, and the
 * whole-book text extraction competed for the same machine
 * from the same moment. A render that stalled — or a
 * coroutine cancelled while blocked on the monitor — fired
 * NEITHER exit, so the page sat in "preparing" forever:
 * no timeout, no lighter fallback, no progressive paint,
 * and every open re-rendered from zero.
 *
 * The replacement contract pinned here:
 *  - FAST first paint: a small, width-capped render whose
 *    only job is to put the page on screen immediately —
 *    for a local file this lands in well under the label
 *    grace, so a downloaded book never shows a preparing
 *    state at all;
 *  - FULL render second: the display-matched bitmap swaps
 *    in over the fast paint (sharpness catches up a beat
 *    later, geometry never moves);
 *  - every attempt is bounded by [RENDER_TIMEOUT_MS] and
 *    the attempt chain terminates ([stageAfter] of FULL is
 *    null): a page ALWAYS resolves to content or to the
 *    explicit failure state with its retry — the
 *    professional-cache rule that no loading state may
 *    hang unresolved.
 *
 * Pure constants and plan math only — unit-tested directly.
 */
object ReaderPagePipeline {

    /** One render attempt (fast paint or full render) may
     * run at most this long before the pipeline moves on. */
    const val RENDER_TIMEOUT_MS = 5_000L

    /** The labelled preparing UI appears only when even the
     * fast paint outlives this grace — a downloaded book's
     * local render lands far inside it, so the state is
     * effectively invisible for local files and reserved
     * for genuinely slow or broken ones. */
    const val PREPARING_LABEL_DELAY_MS = 700L

    /** Pixel budget of the fast first paint (~2.8 MB): big
     * enough to read, small enough to land immediately. */
    const val FAST_MAX_PIXELS = 700_000

    /** The fast paint never renders wider than this, on any
     * screen — the full stage carries the real resolution. */
    const val FAST_TARGET_MAX_WIDTH_PX = 900

    /** The two render stages of a page, in order. */
    enum class Stage { FAST, FULL }

    /** Target pixel width for the FAST paint of a page shown
     * in a box [boxWidthPx] wide; 0 when unmeasured (the
     * caller then falls back to the legacy scale). */
    fun fastTargetWidthPx(boxWidthPx: Int): Int =
        if (boxWidthPx > 0) minOf(boxWidthPx, FAST_TARGET_MAX_WIDTH_PX) else 0

    /** The stage that follows a failed [stage], or null when
     * the chain is exhausted and the page must resolve to
     * its failure state. */
    fun stageAfter(stage: Stage): Stage? = when (stage) {
        Stage.FAST -> Stage.FULL
        Stage.FULL -> null
    }
}
