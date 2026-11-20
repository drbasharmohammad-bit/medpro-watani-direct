package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.32 pure-logic pins — the doctor's Syria report on
 * 1.2.31: the forced-update download never completed on a Syrian
 * connection without a VPN (and did complete with one), while the
 * old downloader discarded every partial and restarted the full
 * 21 MB on each manual retry. The pins:
 *
 * 1. The trust list now spans three update hosts — the content
 *    host, github.com (whose release links redirect to GitHub's
 *    own asset host; the pin is on the INITIAL host), and the
 *    project's custom domain, trusted ahead of its DNS so it can
 *    be adopted later by config alone. Everything else — plain
 *    http, foreign hosts, look-alike hosts, redirect targets
 *    offered as initial URLs — is still refused.
 * 2. Candidate ordering: a trustworthy config URL leads, the
 *    built-ins follow, de-duplicated.
 * 3. Resume arithmetic: a kept partial resumes only strictly
 *    inside its expected total; only a 206 may be appended; the
 *    retry backoff doubles 2 s → 30 s cap; completion requires
 *    the exact total when the total is known.
 * 4. The partial's sidecar (URL + ETag + total) round-trips and
 *    rejects garbage.
 */
class Build1232LogicTest {

    // ---------------- Trusted hosts ----------------

    @Test
    fun allThreeUpdateHostsAreTrusted() {
        assertTrue(AppUpdate.isApkUrlAllowed(AppUpdate.FALLBACK_APK_URL))
        assertTrue(AppUpdate.isApkUrlAllowed(AppUpdate.GITHUB_APK_URL))
        assertTrue(
            AppUpdate.isApkUrlAllowed(
                "https://dl.hdr-market.com/apk/watani-latest.apk"
            )
        )
        assertEquals("dl.hdr-market.com", AppUpdate.CUSTOM_DOMAIN_HOST)
        assertEquals(
            "https://github.com/drbasharmohammad-bit/medpro-watani-direct/releases/download/v1.2.31/watani-latest.apk",
            AppUpdate.GITHUB_APK_URL
        )
    }

    @Test
    fun untrustedUrlsAreStillRefused() {
        // Plain http on a trusted host is refused.
        assertFalse(
            AppUpdate.isApkUrlAllowed(
                "http://github.com/drbasharmohammad-bit/medpro-watani-direct/releases/download/v1.2.31/watani-latest.apk"
            )
        )
        // Foreign and look-alike hosts are refused.
        assertFalse(AppUpdate.isApkUrlAllowed("https://evil.example/apk/watani-latest.apk"))
        assertFalse(AppUpdate.isApkUrlAllowed("https://github.com.evil.example/x.apk"))
        assertFalse(AppUpdate.isApkUrlAllowed("https://sub.github.com/x.apk"))
        assertFalse(AppUpdate.isApkUrlAllowed("https://watani-content.pages.dev.evil.example/x.apk"))
        // GitHub's redirect target is NOT a trusted initial URL —
        // it is only ever reached by following github.com's own
        // redirect, never by config pointing straight at it.
        assertFalse(
            AppUpdate.isApkUrlAllowed(
                "https://release-assets.githubusercontent.com/github-production-release-asset/1/abc"
            )
        )
        assertFalse(AppUpdate.isApkUrlAllowed(null))
        assertFalse(AppUpdate.isApkUrlAllowed(""))
        assertFalse(AppUpdate.isApkUrlAllowed("file:///sdcard/x.apk"))
    }

    // ---------------- Candidate ordering ----------------

    @Test
    fun candidatesLeadWithTrustworthyConfig() {
        assertEquals(
            listOf(AppUpdate.GITHUB_APK_URL, AppUpdate.FALLBACK_APK_URL),
            AppUpdate.candidateUrls(AppUpdate.GITHUB_APK_URL)
        )
        assertEquals(
            listOf(
                "https://dl.hdr-market.com/apk/watani-latest.apk",
                AppUpdate.FALLBACK_APK_URL,
                AppUpdate.GITHUB_APK_URL
            ),
            AppUpdate.candidateUrls("https://dl.hdr-market.com/apk/watani-latest.apk")
        )
    }

    @Test
    fun candidatesFallBackToBuiltInsInOrder() {
        val builtIns = listOf(AppUpdate.FALLBACK_APK_URL, AppUpdate.GITHUB_APK_URL)
        assertEquals(builtIns, AppUpdate.candidateUrls(null))
        assertEquals(builtIns, AppUpdate.candidateUrls(""))
        assertEquals(builtIns, AppUpdate.candidateUrls("https://evil.example/x.apk"))
        // A config equal to a built-in is de-duplicated, not doubled.
        assertEquals(builtIns, AppUpdate.candidateUrls(AppUpdate.FALLBACK_APK_URL))
    }

    // ---------------- Resume arithmetic ----------------

    @Test
    fun resumeOffsetOnlyStrictlyInsideTotal() {
        assertEquals(0L, AppUpdate.resumeOffset(0L, 21_219_369L))
        assertEquals(1L, AppUpdate.resumeOffset(1L, 21_219_369L))
        assertEquals(10_000_000L, AppUpdate.resumeOffset(10_000_000L, 21_219_369L))
        assertEquals(21_219_368L, AppUpdate.resumeOffset(21_219_368L, 21_219_369L))
        // A complete-looking or oversized partial is untrusted.
        assertEquals(0L, AppUpdate.resumeOffset(21_219_369L, 21_219_369L))
        assertEquals(0L, AppUpdate.resumeOffset(22_000_000L, 21_219_369L))
        // An unknown total makes any partial untrusted.
        assertEquals(0L, AppUpdate.resumeOffset(100L, 0L))
        assertEquals(0L, AppUpdate.resumeOffset(100L, -1L))
    }

    @Test
    fun only206MayBeAppended() {
        assertTrue(AppUpdate.shouldAppend(206))
        assertFalse(AppUpdate.shouldAppend(200))
        assertFalse(AppUpdate.shouldAppend(301))
        assertFalse(AppUpdate.shouldAppend(404))
        assertFalse(AppUpdate.shouldAppend(416))
    }

    @Test
    fun backoffDoublesToThirtySecondCap() {
        assertEquals(2_000L, AppUpdate.backoffMs(0))
        assertEquals(4_000L, AppUpdate.backoffMs(1))
        assertEquals(8_000L, AppUpdate.backoffMs(2))
        assertEquals(16_000L, AppUpdate.backoffMs(3))
        assertEquals(30_000L, AppUpdate.backoffMs(4))
        assertEquals(30_000L, AppUpdate.backoffMs(10))
    }

    @Test
    fun completionNeedsExactTotalWhenKnown() {
        assertTrue(AppUpdate.isComplete(21_219_369L, 21_219_369L))
        assertFalse(AppUpdate.isComplete(21_219_368L, 21_219_369L))
        assertFalse(AppUpdate.isComplete(21_219_370L, 21_219_369L))
        assertFalse(AppUpdate.isComplete(0L, 21_219_369L))
        // Unknown total: any non-empty stream end counts.
        assertTrue(AppUpdate.isComplete(5L, -1L))
        assertFalse(AppUpdate.isComplete(0L, -1L))
    }

    @Test
    fun contentRangeTotalParsing() {
        assertEquals(
            21_219_369L,
            AppUpdate.parseContentRangeTotal("bytes 1000000-21219368/21219369")
        )
        assertEquals(-1L, AppUpdate.parseContentRangeTotal(null))
        assertEquals(-1L, AppUpdate.parseContentRangeTotal(""))
        assertEquals(-1L, AppUpdate.parseContentRangeTotal("bytes */*"))
        assertEquals(-1L, AppUpdate.parseContentRangeTotal("garbage"))
    }

    // ---------------- Partial sidecar ----------------

    @Test
    fun metaRoundTrips() {
        val meta = AppUpdate.PartMeta(
            url = AppUpdate.GITHUB_APK_URL,
            etag = "\"96f0008caf90ccfe62019e9c538598d7\"",
            total = 21_219_369L
        )
        assertEquals(meta, AppUpdate.parseMeta(AppUpdate.encodeMeta(meta)))
        val noEtag = AppUpdate.PartMeta(AppUpdate.FALLBACK_APK_URL, "", -1L)
        assertEquals(noEtag, AppUpdate.parseMeta(AppUpdate.encodeMeta(noEtag)))
    }

    @Test
    fun metaRejectsGarbage() {
        assertNull(AppUpdate.parseMeta(null))
        assertNull(AppUpdate.parseMeta(""))
        assertNull(AppUpdate.parseMeta("hello world"))
        // Missing total or missing URL is incomplete → untrusted.
        assertNull(AppUpdate.parseMeta("url=https://github.com/x.apk\n"))
        assertNull(AppUpdate.parseMeta("total=123\n"))
        assertNull(AppUpdate.parseMeta("url=\ntotal=123\n"))
        assertNull(AppUpdate.parseMeta("url=https://github.com/x.apk\ntotal=abc\n"))
    }
}
