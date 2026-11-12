package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-3 (§4.2): pins the HLS playlist rewriting used when a playback
 * session is built for an encrypted downloaded tree.
 *
 *  - Master: variant references become bare file names (the decrypted
 *    variant temps land next to the master temp in cacheDir).
 *  - Variant: segment references become watani-enc:/// URIs served by
 *    EncryptedHlsDataSource from the encrypted tree.
 *  - Tag lines, blanks and ordering survive verbatim; no plaintext
 *    segment reference may survive a rewrite.
 *
 * Pure String transforms — JVM-safe, no Android APIs.
 */
class HlsPlaylistRewriteTest {

    private val master = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        stemi_360p.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
        stemi_720p.m3u8
    """.trimIndent()

    private val variant = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:10
        #EXT-X-MEDIA-SEQUENCE:0
        #EXTINF:10.000,
        seg_000.ts
        #EXTINF:9.967,
        seg_001.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun master_variantsBecomeBareFileNames() {
        val out = HlsPlaylistRewrite.rewriteMaster(master)
        assertTrue(out.contains("\nstemi_360p.m3u8"))
        assertTrue(out.contains("\nstemi_720p.m3u8"))
        // Tag lines untouched.
        assertTrue(out.contains("#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360"))
        assertTrue(out.startsWith("#EXTM3U"))
    }

    @Test
    fun master_absoluteVariantUrlAlsoBecomesBareName() {
        val absMaster = master.replace(
            "stemi_360p.m3u8",
            "https://watani-content.pages.dev/v1/library/cardiology/stemi/stemi_360p.m3u8"
        )
        val out = HlsPlaylistRewrite.rewriteMaster(absMaster)
        assertFalse("no origin URL may survive the master rewrite", out.contains("pages.dev"))
        assertTrue(out.contains("stemi_360p.m3u8"))
    }

    @Test
    fun variant_segmentsBecomeWataniEncUris() {
        val out = HlsPlaylistRewrite.rewriteVariant(variant, "stemi")
        assertTrue(out.contains("watani-enc:///stemi/seg_000.ts"))
        assertTrue(out.contains("watani-enc:///stemi/seg_001.ts"))
        // No bare plaintext segment reference survives.
        assertFalse(out.lines().any { it.trim() == "seg_000.ts" })
        // Structure lines survive verbatim.
        assertTrue(out.contains("#EXTINF:10.000,"))
        assertTrue(out.contains("#EXT-X-ENDLIST"))
    }

    @Test
    fun mediaReferences_listsOnlyMediaLinesInOrder() {
        assertEquals(
            listOf("stemi_360p.m3u8", "stemi_720p.m3u8"),
            HlsPlaylistRewrite.mediaReferences(master)
        )
        assertEquals(
            listOf("seg_000.ts", "seg_001.ts"),
            HlsPlaylistRewrite.mediaReferences(variant)
        )
    }

    @Test
    fun fileNameOf_stripsPathAndQuery() {
        assertEquals(
            "seg_9.ts",
            HlsPlaylistRewrite.fileNameOf("https://example.com/a/b/seg_9.ts?x=1")
        )
        assertEquals("seg_9.ts", HlsPlaylistRewrite.fileNameOf("seg_9.ts"))
    }
}
