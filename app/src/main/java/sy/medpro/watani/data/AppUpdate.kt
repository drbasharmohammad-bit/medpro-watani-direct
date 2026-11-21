package sy.medpro.watani.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import sy.medpro.watani.security.ApkSigningPin
import android.content.pm.PackageManager
import java.security.MessageDigest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * In-app update download + install (build 1.2.28, doctor's order
 * 16 Nov 2026): the update-required screen carries a «تحميل التحديث
 * وتثبيته» button that downloads the new APK itself and hands it to
 * the system installer, which installs it OVER the current build —
 * same signing certificate, so no data is lost and no Telegram trip
 * is needed.
 *
 * Build 1.2.32 (doctor's Syria report, 20 Nov 2026): on Syrian
 * connections without a VPN the 21 MB download from the content
 * host never completed — and the old downloader made a bad link
 * fatal (default OkHttp timeouts, a 10 s read timeout, no resume,
 * no retry, the partial discarded on every attempt). The download
 * layer is now hardened and multi-address:
 *
 * - Generous timeouts (30 s connect / 120 s read, no overall call
 *   cap — a throttled 21 MB transfer may legitimately take many
 *   minutes; the read timeout still detects a dead stall).
 * - The partial download is KEPT (a `.part` file plus a small meta
 *   sidecar: URL + ETag + expected total) and resumed with
 *   `Range`/`If-Range`. A server that answers a range request with
 *   200 (the content host does exactly that today — verified) is
 *   handled by restarting that stream cleanly, never by appending
 *   a full body onto a partial; a 416 discards the stale partial.
 * - Automatic retries with exponential backoff (2 s → 30 s cap),
 *   four attempts per address, then the NEXT candidate address is
 *   tried: the config URL first when trustworthy, then the fixed
 *   content-host URL, then the GitHub release mirror of the live
 *   build — whichever address a student's network can actually
 *   reach completes the update.
 *
 * Trust model (unchanged in spirit since 1.2.28): the app only
 * ever STARTS a download from an https URL whose host is on
 * [ALLOWED_HOSTS] — a poisoned config can never point the
 * installer at a foreign file. Redirects after that first hop are
 * followed by OkHttp as usual (github.com release links redirect
 * to GitHub's own asset host); the pin is on the initial host,
 * exactly like a browser trusting the link it was given.
 *
 * Audit hardening (21 Nov 2026, SEC-N2 + SEC-C1): the initial-host
 * check now uses OkHttp's own URL parser and refuses userinfo
 * URLs, and — decisively — a completed download is handed to the
 * installer ONLY when its signing certificate matches the pinned
 * release certificate ([ApkSigningPin]); a mismatch deletes the
 * file. The host allowlist decides where a download may START;
 * the certificate decides what may INSTALL.
 */
object AppUpdate {

    /** The fixed update address on the content host; replaced in
     * place per release. */
    const val FALLBACK_APK_URL = "https://watani-content.pages.dev/apk/watani-latest.apk"

    /** GitHub release mirror of the live build (release asset on
     * the app repository — a different network path than the
     * content host, for networks where *.pages.dev is throttled). */
    const val GITHUB_APK_URL =
        "https://github.com/drbasharmohammad-bit/medpro-watani-direct/releases/download/v1.2.42/watani-latest.apk"

    private val ALLOWED_HOSTS = setOf(
        "watani-content.pages.dev",
        "github.com"
    )

    private const val UPDATE_DIR = "updates"
    private const val UPDATE_FILE = "watani-update.apk"
    private const val PART_FILE = "watani-update.apk.part"
    private const val META_FILE = "watani-update.meta"

    /** Attempts per candidate address before failing over. */
    const val MAX_ATTEMPTS_PER_URL = 4

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // No overall call cap: on a throttled link the full 21 MB
        // may take many minutes; the 120 s read timeout is what
        // detects a truly dead stall.
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    /** Pure (unit-tested): only an https URL whose host is exactly
     * one of the trusted update hosts is installable.
     *
     * Audit fix (21 Nov 2026, SEC-N2): the host is now taken from a
     * real URL parse (OkHttp HttpUrl — the same parser the download
     * itself uses), not from string surgery. The old string split
     * read «github.com» out of `https://github.com:443@evil/…`
     * while OkHttp would have connected to the userinfo host
     * (evil). Any URL carrying userinfo (user@host) is refused
     * outright, and the compared host is the parser's host. */
    fun isApkUrlAllowed(url: String?): Boolean {
        val parsed = url?.trim().orEmpty().toHttpUrlOrNull()
        return parsed != null &&
            parsed.scheme == "https" &&
            parsed.username.isEmpty() && parsed.password.isEmpty() &&
            parsed.host in ALLOWED_HOSTS
    }

    /** Pure (unit-tested): the config value when it is trustworthy,
     * the fixed URL otherwise. */
    fun resolveApkUrl(configValue: String?): String =
        if (isApkUrlAllowed(configValue)) configValue!!.trim() else FALLBACK_APK_URL

    /** Pure (unit-tested): the ordered candidate addresses the
     * downloader tries until one completes — the trustworthy
     * config value first (the operator's chosen primary), then the
     * built-in addresses, de-duplicated. */
    fun candidateUrls(configValue: String?): List<String> {
        val out = mutableListOf<String>()
        if (isApkUrlAllowed(configValue)) out += configValue!!.trim()
        for (u in listOf(FALLBACK_APK_URL, GITHUB_APK_URL)) {
            if (u !in out) out += u
        }
        return out
    }

    /** Pure (unit-tested): where a kept partial may resume from.
     * A partial is only trusted when its length sits strictly
     * inside the expected total; a complete-looking, oversized or
     * total-less partial restarts from zero. */
    fun resumeOffset(partialLength: Long, expectedTotal: Long): Long =
        if (expectedTotal > 0 && partialLength in 1L until expectedTotal) {
            partialLength
        } else {
            0L
        }

    /** Pure (unit-tested): only a 206 answer may be appended to a
     * kept partial; a 200 means the server ignored the range and
     * is streaming the whole file again. */
    fun shouldAppend(responseCode: Int): Boolean = responseCode == 206

    /** Pure (unit-tested): retry backoff — 2 s doubling to a 30 s
     * cap. */
    fun backoffMs(attempt: Int): Long =
        minOf(2000L shl attempt.coerceIn(0, 10), 30_000L)

    /** Pure (unit-tested): completion check at end of stream. */
    fun isComplete(downloaded: Long, total: Long): Boolean =
        if (total > 0) downloaded == total else downloaded > 0

    /** Pure (unit-tested): the total out of a `Content-Range:
     * bytes a-b/TOTAL` header; -1 when absent or malformed. */
    fun parseContentRangeTotal(header: String?): Long {
        if (header.isNullOrBlank()) return -1
        val slash = header.lastIndexOf('/')
        if (slash < 0) return -1
        return header.substring(slash + 1).trim().toLongOrNull() ?: -1
    }

    /** The kept partial's sidecar: which URL it belongs to, the
     * ETag it was started under, and the expected total. */
    data class PartMeta(val url: String, val etag: String, val total: Long)

    /** Pure (unit-tested): sidecar serialization (key=value lines). */
    fun encodeMeta(meta: PartMeta): String =
        "url=${meta.url}\netag=${meta.etag}\ntotal=${meta.total}\n"

    /** Pure (unit-tested): sidecar parsing; null on anything
     * incomplete or malformed (the partial is then discarded). */
    fun parseMeta(text: String?): PartMeta? {
        if (text.isNullOrBlank()) return null
        var url: String? = null
        var etag = ""
        var total: Long? = null
        for (line in text.lineSequence()) {
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            when (line.substring(0, idx).trim()) {
                "url" -> url = line.substring(idx + 1).trim()
                "etag" -> etag = line.substring(idx + 1).trim()
                "total" -> total = line.substring(idx + 1).trim().toLongOrNull()
            }
        }
        val u = url?.takeIf { it.isNotEmpty() } ?: return null
        val t = total ?: return null
        return PartMeta(u, etag, t)
    }

    fun updateFile(context: Context): File =
        File(File(context.cacheDir, UPDATE_DIR), UPDATE_FILE)

    /** Downloads the APK from the single given address (kept for
     * API compatibility; the update screen uses the candidate
     * form). */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = downloadApkFromCandidates(context, listOf(url), onProgress)

    /** Tries each candidate address in order — up to
     * [MAX_ATTEMPTS_PER_URL] attempts each, resuming the kept
     * partial and backing off between attempts — until one
     * completes the file. Throws the last error when every
     * candidate is exhausted. */
    suspend fun downloadApkFromCandidates(
        context: Context,
        urls: List<String>,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val clean = urls.map { it.trim() }
            .filter { isApkUrlAllowed(it) }
            .distinct()
        require(clean.isNotEmpty()) { "no trusted update url" }
        var lastError: Exception? = null
        for (url in clean) {
            for (attempt in 0 until MAX_ATTEMPTS_PER_URL) {
                try {
                    if (downloadOnce(context, url, onProgress)) {
                        // Audit fix (21 Nov 2026, SEC-C1): a completed
                        // download is verified against the pinned
                        // release certificate BEFORE it can be handed
                        // to the installer. A mismatch (poisoned mirror,
                        // swapped file) deletes the file and moves on
                        // to the next candidate address; no candidate
                        // may install an APK signed by anyone else.
                        val file = updateFile(context)
                        if (isTrustedApk(context, file)) {
                            return@withContext file
                        }
                        file.delete()
                        lastError = SecurityException(
                            "update apk signature mismatch"
                        )
                        break
                    }
                } catch (e: Exception) {
                    lastError = e
                }
                if (attempt < MAX_ATTEMPTS_PER_URL - 1) {
                    delay(backoffMs(attempt))
                }
            }
        }
        throw lastError ?: IllegalStateException("update download failed")
    }

    /** SHA-256 (lowercase hex) of the signing certificate of an APK
     * file on disk, read through PackageManager's archive info;
     * null when the file is not a readable APK or carries no
     * signature. */
    fun archiveCertSha256(context: Context, apk: File): String? {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNATURES
                )
            }
            val certs = when {
                info == null -> null
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                    info.signingInfo?.apkContentsSigners
                else -> @Suppress("DEPRECATION") info.signatures
            }
            certs?.firstOrNull()?.let { first ->
                MessageDigest.getInstance("SHA-256")
                    .digest(first.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** True only when [apk] is signed with the pinned release
     * certificate ([ApkSigningPin]). An unreadable or unsigned file
     * is NOT trusted. */
    fun isTrustedApk(context: Context, apk: File): Boolean =
        archiveCertSha256(context, apk)
            ?.equals(ApkSigningPin.SHA256, ignoreCase = true) == true

    /** One download attempt against [url], resuming the kept
     * partial when it provably belongs to this URL. Returns true
     * when the file completed and was promoted to [updateFile];
     * false when the attempt was rejected in a way a fresh attempt
     * can fix (a stale partial answered 416). Throws on I/O and
     * HTTP failures — the caller retries with backoff. */
    private fun downloadOnce(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val part = File(dir, PART_FILE)
        val metaFile = File(dir, META_FILE)
        val meta = if (metaFile.exists()) {
            try {
                parseMeta(metaFile.readText())
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        var offset = 0L
        if (meta != null && meta.url == url && part.exists()) {
            offset = resumeOffset(part.length(), meta.total)
            if (offset == 0L && part.length() > 0L) {
                // Complete-looking or oversized partial: untrusted.
                part.delete()
            }
        } else {
            // A partial from another address (or with no sidecar)
            // is never resumed against this one.
            if (part.exists()) part.delete()
            if (metaFile.exists()) metaFile.delete()
        }

        val request = Request.Builder().url(url).get()
        if (offset > 0L) {
            request.header("Range", "bytes=$offset-")
            if (!meta?.etag.isNullOrBlank()) {
                request.header("If-Range", meta!!.etag)
            }
        }

        downloadClient.newCall(request.build()).execute().use { resp ->
            if (resp.code == 416) {
                // The kept partial no longer matches the server's
                // file; drop it so the next attempt starts clean.
                part.delete()
                metaFile.delete()
                return false
            }
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            val append = shouldAppend(resp.code)
            if (!append) offset = 0L
            val total = if (append) {
                parseContentRangeTotal(resp.header("Content-Range"))
                    .takeIf { it > 0 } ?: meta?.total ?: -1L
            } else {
                body.contentLength()
            }
            // Persist the sidecar before streaming so an interrupted
            // attempt leaves a resumable, attributable partial.
            val etag = resp.header("ETag") ?: meta?.etag.orEmpty()
            try {
                metaFile.writeText(encodeMeta(PartMeta(url, etag, total)))
            } catch (_: Exception) {
            }
            var downloaded = offset
            body.byteStream().use { input ->
                FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                    out.flush()
                }
            }
            if (!isComplete(downloaded, total)) {
                throw IOException("short read: $downloaded of $total")
            }
        }

        val target = updateFile(context)
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        metaFile.delete()
        return true
    }

    /** Android 8+: sideload installs need the per-app «install
     * unknown apps» grant; older versions install directly. */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Opens the system settings page where the user grants this
     * app the install permission (one-time). */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The system installer over the current build (same cert). */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
