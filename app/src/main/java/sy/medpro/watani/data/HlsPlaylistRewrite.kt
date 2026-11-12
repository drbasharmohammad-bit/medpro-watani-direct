package sy.medpro.watani.data

/**
 * Phase-3 hardening (Nov 2026) — pure HLS playlist rewriting for the
 * encrypted-at-rest library trees (design §4.2). No Android APIs here:
 * every function is a plain String transform so it is unit-testable on
 * the JVM (see HlsPlaylistRewriteTest).
 *
 * Playback session shape:
 *  - The master playlist is decrypted in memory only, its variant
 *    references are rewritten to the BARE FILE NAMES of the decrypted
 *    variant temp files (master + variants all land in cacheDir, so a
 *    relative bare name resolves next to the master temp).
 *  - Each variant playlist is decrypted in memory only, its segment
 *    references are rewritten to the custom scheme
 *    `watani-enc:///<itemId>/<segmentFileName>`, which
 *    [EncryptedHlsDataSource] resolves against the item's encrypted
 *    tree on disk and decrypts in memory while streaming.
 *  - Tag lines, blank lines and ordering are preserved verbatim. A URI
 *    carried inside a tag attribute (EXT-X-KEY / EXT-X-MAP `URI="..."`)
 *    is rewritten by the same rules. Our library trees carry no key/map
 *    tags today; the handling exists so a future tree cannot silently
 *    leak a plaintext segment reference past the rewriter.
 */
object HlsPlaylistRewrite {

    /** Custom scheme served by [EncryptedHlsDataSource]. */
    const val ENC_SCHEME_PREFIX = "watani-enc:///"

    /** File-name portion of a playlist/segment reference or URL. */
    fun fileNameOf(ref: String): String =
        ref.trim().substringBefore('?').substringAfterLast('/')
            .takeIf { it.isNotBlank() } ?: "file"

    private fun rewriteTagUri(line: String, rewrite: (String) -> String): String {
        val marker = "URI=\""
        val start = line.indexOf(marker)
        if (start < 0) return line
        val valueStart = start + marker.length
        val valueEnd = line.indexOf('"', valueStart)
        if (valueEnd < 0) return line
        val uri = line.substring(valueStart, valueEnd)
        return line.substring(0, valueStart) + rewrite(uri) + line.substring(valueEnd)
    }

    private fun rewrite(
        playlistText: String,
        refRewrite: (String) -> String
    ): String {
        return playlistText.lines().joinToString("\n") { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> rawLine
                line.startsWith("#") -> rewriteTagUri(line, refRewrite)
                else -> refRewrite(line)
            }
        }
    }

    /** Master playlist: variant references become bare temp file names. */
    fun rewriteMaster(playlistText: String): String =
        rewrite(playlistText) { ref -> fileNameOf(ref) }

    /** Variant playlist: segment references become `watani-enc:///` URIs. */
    fun rewriteVariant(playlistText: String, itemId: String): String =
        rewrite(playlistText) { ref -> ENC_SCHEME_PREFIX + itemId + "/" + fileNameOf(ref) }

    /** Plain segment/variant references of a playlist, in order
     * (tag lines and blanks excluded). Used by the downloader to walk
     * the tree and by the session builder to find variant playlists. */
    fun mediaReferences(playlistText: String): List<String> =
        playlistText.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
}
