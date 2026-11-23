package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for the process-wide pack download bookkeeping
 * (build 1.2.45, cache audit fix F6).
 *
 * THE DEFECT under guard: the self-loading packs (DDx, «اقلبها»,
 * ECG, OSCE + procedures, dosing drills, auscultation, the study
 * guide, German cases) ran their FIRST download inside the screen's
 * own coroutine scope, so leaving the screen mid-download killed it
 * and the next entry restarted from zero. The downloads now live in
 * [PackDownloadManager] (process scope, the ConceptsPackManager
 * pattern); screens only start and observe. These tests pin the
 * bookkeeping the screens rely on:
 *  - a second start while a download runs is a no-op (a re-entry or
 *    an ON_RESUME re-check must never restart an in-flight job);
 *  - progress is published per path;
 *  - success bumps the path's completedRuns (the screens' reload
 *    trigger) and clears any earlier failure;
 *  - failure marks the path failed WITHOUT bumping completedRuns
 *    (no phantom reload), and a later retry may start again.
 */
class PackDownloadManagerTest {

    @Before
    fun reset() {
        PackDownloadManager.resetForTest()
    }

    @Test
    fun `second start while running is a no-op`() {
        assertTrue(PackDownloadManager.markStarted("ddx.json"))
        assertFalse(PackDownloadManager.markStarted("ddx.json"))
        assertTrue(PackDownloadManager.state.value.downloading.contains("ddx.json"))
    }

    @Test
    fun `different packs download independently`() {
        assertTrue(PackDownloadManager.markStarted("ddx.json"))
        assertTrue(PackDownloadManager.markStarted("ecg.json"))
        PackDownloadManager.markFinished("ddx.json", success = true)
        val state = PackDownloadManager.state.value
        assertFalse(state.downloading.contains("ddx.json"))
        assertTrue(state.downloading.contains("ecg.json"))
        assertEquals(1, state.completedRuns["ddx.json"])
        assertEquals(null, state.completedRuns["ecg.json"])
    }

    @Test
    fun `progress is published per path`() {
        PackDownloadManager.markStarted("reverse.json")
        PackDownloadManager.markProgress("reverse.json", 0.4f)
        assertEquals(0.4f, PackDownloadManager.state.value.progress["reverse.json"])
    }

    @Test
    fun `success bumps completed runs and clears failure`() {
        PackDownloadManager.markStarted("osce.json")
        PackDownloadManager.markFinished("osce.json", success = false)
        assertTrue(PackDownloadManager.state.value.failed.contains("osce.json"))
        PackDownloadManager.markStarted("osce.json")
        PackDownloadManager.markFinished("osce.json", success = true)
        val state = PackDownloadManager.state.value
        assertEquals(1, state.completedRuns["osce.json"])
        assertFalse(state.failed.contains("osce.json"))
        assertFalse(state.downloading.contains("osce.json"))
    }

    @Test
    fun `failure never bumps completed runs and allows a retry`() {
        PackDownloadManager.markStarted("guide.json")
        PackDownloadManager.markFinished("guide.json", success = false)
        val state = PackDownloadManager.state.value
        assertEquals(null, state.completedRuns["guide.json"])
        assertTrue(state.failed.contains("guide.json"))
        // A later entry may start the download again.
        assertTrue(PackDownloadManager.markStarted("guide.json"))
    }
}
