package sy.medpro.watani.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer 2 — the 1.2.50 reader contracts, written RED first from
 * the doctor's device report of 24 Nov 2026 (build 1.2.49):
 *
 * 1. A DOWNLOADED book's page hung forever on «جارٍ تجهيز
 *    الصفحة...». The pre-1.2.50 pipeline gave a page exactly one
 *    resolution path — a single display-matched render (up to
 *    4.2M px) serialized behind one monitor together with both
 *    neighbours and the zoom re-render — with no timeout, no
 *    lighter fallback and no progressive first paint, so a
 *    stalled render left the labelled preparing state up
 *    forever. [ReaderPagePipeline] pins the replacement: a fast
 *    first paint, then the full render, every attempt bounded
 *    by a timeout, and the preparing label itself gated behind
 *    a grace delay a local render never reaches.
 *
 * 2. Pinch zoom shook (رجفان): the transform scaled around the
 *    box centre while pan only followed the fingers' centroid
 *    translation, so the point under the fingers swam every
 *    frame. [ReaderZoomMath] pins the anchored pan: the content
 *    point under the old centroid lands under the new centroid
 *    at the new zoom, and pan clamps to the page overflow.
 *
 * 3. Text highlights persisted under the bare file name, so a
 *    re-downloaded booklet inherited stale highlights on
 *    shifted text. [ReaderMarkingKeys] pins the length-aware
 *    key and the legacy key it migrates from.
 */
class ReaderPagePipelineTest {

    // ---- Page pipeline: staged, bounded, terminating ----

    @Test
    fun fastStageIsLighterThanTheFullStage() {
        // The fast first paint of a 1080px phone is a plain
        // box-width render — never the oversampled full target.
        assertEquals(900, ReaderPagePipeline.fastTargetWidthPx(1080))
        // A wide tablet is capped at the same fast width — the
        // full stage that follows carries the real resolution.
        assertEquals(900, ReaderPagePipeline.fastTargetWidthPx(2560))
        assertTrue(
            ReaderPagePipeline.fastTargetWidthPx(1080) <
                ReaderRenderMath.baseTargetWidthPx(1080)
        )
        assertTrue(
            ReaderPagePipeline.FAST_MAX_PIXELS < ReaderRenderMath.BASE_MAX_PIXELS
        )
    }

    @Test
    fun fastStageIsUnmeasuredSafe() {
        assertEquals(0, ReaderPagePipeline.fastTargetWidthPx(0))
        assertEquals(0, ReaderPagePipeline.fastTargetWidthPx(-1080))
    }

    @Test
    fun attemptChainTerminates() {
        // FAST falls forward to FULL; after FULL the chain is
        // exhausted and the page must resolve to its failure
        // state — there is no third silent attempt and no loop.
        assertEquals(
            ReaderPagePipeline.Stage.FULL,
            ReaderPagePipeline.stageAfter(ReaderPagePipeline.Stage.FAST)
        )
        assertNull(ReaderPagePipeline.stageAfter(ReaderPagePipeline.Stage.FULL))
    }

    @Test
    fun everyPageStateIsBoundedInTime() {
        // The preparing label may only appear after a grace a
        // local fast render never reaches, and every render
        // attempt dies at the timeout — so no page state can
        // outlive (label delay + two bounded attempts).
        assertTrue(ReaderPagePipeline.PREPARING_LABEL_DELAY_MS > 0)
        assertTrue(
            ReaderPagePipeline.PREPARING_LABEL_DELAY_MS <
                ReaderPagePipeline.RENDER_TIMEOUT_MS
        )
        assertTrue(ReaderPagePipeline.RENDER_TIMEOUT_MS <= 10_000L)
    }

    // ---- Zoom: anchored at the fingers, clamped to the page ----

    @Test
    fun anchoredPanKeepsThePointUnderTheFingersFixed() {
        // Pinch at (800, 1200) of a 1080x2400 box, 1x -> 2x, no
        // finger translation: the pan must move by exactly the
        // scale-around-centroid compensation.
        val pan = ReaderZoomMath.anchoredPan(
            oldCentroidX = 800f, oldCentroidY = 1200f,
            newCentroidX = 800f, newCentroidY = 1200f,
            boxWidthPx = 1080, boxHeightPx = 2400,
            oldZoom = 1f, newZoom = 2f, panX = 0f, panY = 0f
        )
        assertEquals(-260f, pan.x, 0.5f)
        assertEquals(0f, pan.y, 0.5f)
        // Invariant, checked through the layer's own mapping
        // (origin = box centre): the content point that sat
        // under the centroid at 1x sits under it at 2x.
        val ox = 540f
        val oy = 1200f
        val pcx = ox + (800f - ox - 0f) / 1f
        val pcy = oy + (1200f - oy - 0f) / 1f
        assertEquals(800f, ox + (pcx - ox) * 2f + pan.x, 0.5f)
        assertEquals(1200f, oy + (pcy - oy) * 2f + pan.y, 0.5f)
    }

    @Test
    fun anchoredPanFollowsPureFingerTranslation() {
        // No zoom change: the pan grows by exactly the centroid
        // delta — the page tracks the fingers 1:1.
        val pan = ReaderZoomMath.anchoredPan(
            oldCentroidX = 400f, oldCentroidY = 600f,
            newCentroidX = 430f, newCentroidY = 640f,
            boxWidthPx = 1080, boxHeightPx = 2400,
            oldZoom = 2f, newZoom = 2f, panX = 10f, panY = 20f
        )
        assertEquals(40f, pan.x, 0.5f)
        assertEquals(60f, pan.y, 0.5f)
    }

    @Test
    fun clampPanBoundsToThePageOverflow() {
        // A4 fitted into 1080x2400 is 1080x1528 at 1x; at 2x the
        // overflow halves are (540, 328) — beyond them the page
        // would leave the screen.
        val clamped = ReaderZoomMath.clampPan(
            panX = 9999f, panY = 9999f,
            fittedWidthPx = 1080f, fittedHeightPx = 1528f,
            boxWidthPx = 1080, boxHeightPx = 2400, zoom = 2f
        )
        assertEquals(540f, clamped.x, 0.5f)
        assertEquals(328f, clamped.y, 0.5f)
        // At 1x there is no overflow at all: pan is pinned to 0.
        val atRest = ReaderZoomMath.clampPan(
            panX = 120f, panY = -80f,
            fittedWidthPx = 1080f, fittedHeightPx = 1528f,
            boxWidthPx = 1080, boxHeightPx = 2400, zoom = 1f
        )
        assertEquals(0f, atRest.x, 0.5f)
        assertEquals(0f, atRest.y, 0.5f)
    }

    // ---- Highlight persistence keys ----

    @Test
    fun highlightKeysDistinguishSameNameDifferentFiles() {
        val first = ReaderMarkingKeys.textHighlightsKey("booklet", 1_000_000L)
        val redownloaded = ReaderMarkingKeys.textHighlightsKey("booklet", 1_200_000L)
        assertTrue(first != redownloaded)
        assertTrue(first.contains("booklet"))
        // The legacy key is pinned so the migration reads exactly
        // what every build up to 1.2.49 wrote.
        assertEquals(
            "texthighlights_booklet",
            ReaderMarkingKeys.legacyTextHighlightsKey("booklet")
        )
        assertEquals(
            "texthighlights_booklet_1000000",
            first
        )
    }
}
