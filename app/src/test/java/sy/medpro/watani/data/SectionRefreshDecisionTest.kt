package sy.medpro.watani.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.6 (doctor's device report): downloaded packs were never
 * refreshed inside their own screens — the screens downloaded only
 * when NO local file existed, so a republished pack (ecg.json
 * 44 -> 92 strips) never arrived. The fix routes every self-loading
 * pack screen through WataniRepository.refreshSectionIfStale, whose
 * entire decision is the pure [sectionNeedsRefresh] function tested
 * here: each test pins one branch of the contract the screens rely
 * on (silent keep-local on any doubt, exactly one refresh when the
 * manifest moved on, no refresh loop afterwards).
 */
class SectionRefreshDecisionTest {

    private fun entity(hash: String = "abc123", count: Int = 44) =
        DownloadedSectionEntity(
            sectionPath = "ecg.json",
            version = "1",
            hash = hash,
            count = count,
            downloadedAt = 0L
        )

    private fun entry(hash: String? = "abc123", count: Int? = 44) =
        ManifestFileEntry(hash = hash, size = 1000L, count = count, version = "1")

    @Test
    fun noManifestEntryMeansNoRefresh() {
        // Offline / stale cached manifest that predates the pack:
        // the local copy stays authoritative.
        assertFalse(sectionNeedsRefresh(entity(), null))
    }

    @Test
    fun noDownloadedEntityMeansNoRefresh() {
        // Never registered as downloaded: the screen's missing-file
        // flow owns the first download, refresh stays out of the way.
        assertFalse(sectionNeedsRefresh(null, entry()))
        assertFalse(sectionNeedsRefresh(null, null))
    }

    @Test
    fun identicalHashAndCountMeansNoRefresh() {
        assertFalse(sectionNeedsRefresh(entity(), entry()))
    }

    @Test
    fun republishedPackWithNewHashNeedsRefresh() {
        // The exact device failure: same screen, manifest hash moved.
        assertTrue(sectionNeedsRefresh(entity(hash = "oldhash"), entry(hash = "newhash")))
    }

    @Test
    fun grownPackWithHigherCountNeedsRefresh() {
        assertTrue(sectionNeedsRefresh(entity(count = 44), entry(count = 92)))
    }

    @Test
    fun blankStoredHashNeedsOneRefreshToRecordHash() {
        // Pre-hash-era download: one refresh records the computed
        // hash, after which the identical-hash branch stops the loop.
        assertTrue(sectionNeedsRefresh(entity(hash = ""), entry(hash = "newhash")))
        assertFalse(sectionNeedsRefresh(entity(hash = "newhash"), entry(hash = "newhash")))
    }

    @Test
    fun hashlessManifestEntryFallsBackToCountOnly() {
        assertFalse(sectionNeedsRefresh(entity(hash = "anything", count = 10), entry(hash = null, count = 10)))
        assertTrue(sectionNeedsRefresh(entity(hash = "anything", count = 9), entry(hash = null, count = 10)))
    }

    @Test
    fun hashComparisonIgnoresCaseAndSurroundingSpace() {
        assertFalse(sectionNeedsRefresh(entity(hash = " ABC123 "), entry(hash = "abc123")))
    }
}
