package sy.medpro.watani.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import sy.medpro.watani.security.ContentEncryption
import java.io.File
import java.io.IOException

/**
 * Phase-3 hardening (Nov 2026) — streaming decryptor for downloaded HLS
 * library trees (design §4.2).
 *
 * Serves URIs of the form `watani-enc:///<itemId>/<segmentFileName>`
 * (produced by [HlsPlaylistRewrite] when a playback session is built in
 * WataniRepository.openLibraryItem) from the item's encrypted tree in
 * `filesDir/library_hls_enc/<itemId>/`. Each segment file on disk is in
 * [ContentEncryption] format (12-byte IV ‖ AES-GCM ciphertext ‖ tag).
 *
 * Per the design's implementer warning (§4.2), a segment is decrypted
 * WHOLE into memory inside [open] (segments are ≤ ~8.3MB) instead of
 * streaming through a raw CipherInputStream: the GCM tag is verified by
 * doFinal BEFORE a single plaintext byte is served, so a tampered
 * segment fails the open with an [IOException] and can never surface as
 * silently corrupted media. No plaintext is ever written to disk, and
 * seeking inside a segment is a plain array offset (design: seeks land
 * on segment boundaries in HLS; the in-segment cost is trivial).
 *
 * Any URI that is not `watani-enc` is delegated to the fallback
 * [DataSource] (the gateway HTTP source in the player chain), so this
 * class can sit at the head of the player's DataSource chain.
 */
@UnstableApi
class EncryptedHlsDataSource(
    private val context: Context,
    private val fallbackFactory: DataSource.Factory? = null
) : BaseDataSource(/* isNetwork = */ false) {

    private var fallback: DataSource? = null
    private var dataSpec: DataSpec? = null
    private var plainBytes: ByteArray? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.scheme != "watani-enc") {
            val fb = fallbackFactory?.createDataSource()
                ?: throw IOException("Unsupported URI scheme: ${uri.scheme}")
            fallback = fb
            transferInitializing(dataSpec)
            val len = fb.open(dataSpec)
            transferStarted(dataSpec)
            return len
        }
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        val segments = uri.pathSegments
        if (segments.size != 2) throw IOException("Malformed watani-enc URI")
        val itemDirName = segments[0].replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = segments[1]
        if (fileName.contains('/') || fileName.contains('\\') || fileName == "..") {
            throw IOException("Malformed watani-enc URI")
        }
        val encRoot = File(context.filesDir, "library_hls_enc")
        val encFile = File(File(encRoot, itemDirName), fileName)
        // Path containment: the resolved file must stay inside the tree.
        val rootCanonical = encRoot.canonicalPath
        if (!encFile.canonicalPath.startsWith(rootCanonical + File.separator)) {
            throw IOException("Malformed watani-enc URI")
        }
        if (!encFile.exists()) throw IOException("Encrypted segment missing")
        val plain = ContentEncryption.decryptFileToBytes(encFile)
            ?: throw IOException("Encrypted segment failed integrity check")
        if (dataSpec.position < 0 || dataSpec.position > plain.size) {
            throw IOException("Seek position outside segment")
        }
        plainBytes = plain
        readPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            (plain.size - dataSpec.position)
        } else {
            minOf(dataSpec.length, plain.size - dataSpec.position)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        fallback?.let { return it.read(buffer, offset, length) }
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val plain = plainBytes ?: throw IOException("DataSource not opened")
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        System.arraycopy(plain, readPosition.toInt(), buffer, offset, toRead)
        readPosition += toRead
        bytesRemaining -= toRead
        bytesTransferred(toRead)
        return toRead
    }

    override fun getUri(): Uri? = fallback?.getUri() ?: dataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        fallback?.getResponseHeaders() ?: emptyMap()

    override fun close() {
        fallback?.let {
            it.close()
            fallback = null
            return
        }
        plainBytes = null
        dataSpec = null
        readPosition = 0L
        bytesRemaining = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val context: Context,
        private val fallbackFactory: DataSource.Factory? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            EncryptedHlsDataSource(context, fallbackFactory)
    }
}
