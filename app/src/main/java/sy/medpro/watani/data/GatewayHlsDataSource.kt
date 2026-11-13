package sy.medpro.watani.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Phase-3 hardening (Nov 2026) — gateway streaming source for the video
 * library (design §6 step ب/د).
 *
 * Every HTTP(S) request the player makes while STREAMING a library item
 * (master playlist, variant playlists, segments, subtitle side-car) is
 * rebuilt through the single switch point
 * [WorkerActivationApi.contentRequestForUrl]: a Pages URL under a
 * restricted prefix (`library/` included since phase 3) becomes a
 * gateway request carrying the activation token, exactly like a
 * download. Relative references inside the streamed master playlist
 * resolve against the original Pages URL, so each subsequent request
 * arrives here with its own absolute Pages URL and is switched
 * individually — no direct fetch from the public origin remains.
 *
 * This is the design's accepted first-version shape (§6.ب.5): full
 * token verification per request. If the gateway later ships signed
 * HLS sessions, only this class changes.
 *
 * Non-HTTP URIs are delegated to the fallback factory (file:// temps
 * and the like), mirroring [EncryptedHlsDataSource].
 */
@UnstableApi
class GatewayHlsDataSource(
    private val context: Context,
    private val fallbackFactory: DataSource.Factory? = null
) : BaseDataSource(/* isNetwork = */ true) {

    private var fallback: DataSource? = null
    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0L
    private var bytesRead: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.scheme != "http" && uri.scheme != "https") {
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
        val baseRequest = try {
            WorkerActivationApi.contentRequestForUrl(context, uri.toString())
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid content URL", e)
        } ?: throw IOException("المكتبة تحتاج كود تفعيل — فعّل حسابك أولاً.")
        val builder = baseRequest.newBuilder()
        if (dataSpec.position > 0) {
            builder.header("Range", "bytes=${dataSpec.position}-")
        }
        val resp = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw IOException("Gateway request failed", e)
        }
        if (resp.code == 401 || resp.code == 403) {
            // M3, same as the download paths: proven revocation clears
            // the activation, wipes restricted downloads and prompts.
            resp.close()
            WorkerActivationApi.notifyActivationLost(context)
            throw IOException("انتهى التفعيل على هذا الجهاز — أعد إدخال كود التفعيل.")
        }
        if (!resp.isSuccessful && resp.code != 206) {
            val code = resp.code
            resp.close()
            throw IOException("Gateway answered HTTP $code")
        }
        val body = resp.body ?: run {
            resp.close()
            throw IOException("Empty gateway response")
        }
        response = resp
        var stream: InputStream = body.byteStream()
        var contentLength = body.contentLength()
        if (dataSpec.position > 0 && resp.code == 200) {
            // Server ignored the Range header: skip to the position.
            var toSkip = dataSpec.position
            while (toSkip > 0) {
                val skipped = stream.skip(toSkip)
                if (skipped <= 0) break
                toSkip -= skipped
            }
            if (contentLength >= 0) contentLength -= dataSpec.position
        }
        inputStream = stream
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            contentLength >= 0 -> contentLength
            else -> C.LENGTH_UNSET.toLong()
        }
        bytesRead = 0L
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        fallback?.let { return it.read(buffer, offset, length) }
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = inputStream ?: throw IOException("DataSource not opened")
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length
            else minOf(length.toLong(), bytesRemaining).toInt()
        val read = try {
            stream.read(buffer, offset, toRead)
        } catch (e: Exception) {
            throw IOException("Gateway read failed", e)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        bytesRead += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = fallback?.getUri() ?: dataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> {
        fallback?.let { return it.getResponseHeaders() }
        val resp = response ?: return emptyMap()
        return resp.headers.toMultimap()
    }

    override fun close() {
        fallback?.let {
            it.close()
            fallback = null
            return
        }
        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        inputStream = null
        response?.close()
        response = null
        dataSpec = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    // Annotated directly (media3's own style — cf. DefaultDataSource
    // .Factory): with the marker only on the outer class, lint's
    // UnsafeOptInUsageError did not resolve the opt-in for usages of
    // this nested class (the 4 lintRelease errors on the merged
    // 1.2.8 tree); on the class itself it behaves like media3's.
    @UnstableApi
    class Factory(
        private val context: Context,
        private val fallbackFactory: DataSource.Factory? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            GatewayHlsDataSource(context, fallbackFactory)
    }

    companion object {
        // Same pinning + generous timeouts shape as the repository's
        // client: segment fetches on weak networks must not be cut by
        // a short read timeout.
        private val client: OkHttpClient = sy.medpro.watani.security.NetworkPinning.apply(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
        ).build()
    }
}
