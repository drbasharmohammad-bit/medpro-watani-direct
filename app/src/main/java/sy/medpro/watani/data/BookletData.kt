package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Library booklet data layer (build 1.2.37).
 *
 * The library links 439 videos to 245 enriched chapter booklets
 * (public PDFs under `booklets/<slug>/` on the content origin).
 * This file is the whole data side of that feature:
 *
 *  - [BookletCatalog] — display metadata from the bundled asset
 *    `booklet_links.json` (chapter titles / page counts / sizes and
 *    the per-video chapter numbers). The LINK itself lives on
 *    [LibraryItem.booklet]; the catalog is display/order data only.
 *  - [BookletDownloadManager] — downloads the PUBLIC booklet files
 *    straight from the Pages origin (booklets are not gateway
 *    restricted, so they do NOT enter the encrypted library-item
 *    pipeline), with progress, cancellation and delete. State is
 *    process-scoped so a download survives leaving the screen.
 *  - [BookletProgress] — per-booklet reading progress. The reader
 *    reports pages ZERO-based ([sy.medpro.watani.ui.library
 *    .PdfViewerScreen] onProgress passes pagerState.currentPage);
 *    this store normalizes to ONE-based at record time, so every
 *    snapshot/display («صفحة X من Y») is 1-based.
 */

/** Display metadata of one booklet, from `booklet_links.json`. */
data class BookletMeta(
    val title: String?,
    val pages: Int?,
    val size: Long?,
    val chapter: Int?
)

/** Download state of one booklet, keyed by catalog path. */
sealed class BookletDlState {
    object NotDownloaded : BookletDlState()
    data class Downloading(val progress: Float) : BookletDlState()
    object Downloaded : BookletDlState()
    data class Failed(val message: String) : BookletDlState()
}

object BookletCatalog {

    private const val ASSET_NAME = "booklet_links.json"

    @Volatile
    private var cache: Parsed? = null

    private class Parsed(
        val booklets: Map<String, BookletMeta>,
        val videoChapters: Map<String, Int>
    )

    private fun parsed(context: Context): Parsed {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val result = try {
                val raw = context.applicationContext.assets.open(ASSET_NAME)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(raw)
                val booklets = mutableMapOf<String, BookletMeta>()
                val bookletsJson = root.optJSONObject("booklets")
                if (bookletsJson != null) {
                    val keys = bookletsJson.keys()
                    while (keys.hasNext()) {
                        val path = keys.next()
                        val entry = bookletsJson.optJSONObject(path) ?: continue
                        booklets[path] = BookletMeta(
                            title = entry.optString("title").takeIf { it.isNotBlank() },
                            pages = if (entry.has("pages")) entry.optInt("pages") else null,
                            size = if (entry.has("size")) entry.optLong("size") else null,
                            chapter = if (entry.has("chapter")) entry.optInt("chapter") else null
                        )
                    }
                }
                val videoChapters = mutableMapOf<String, Int>()
                val videosJson = root.optJSONObject("videos")
                if (videosJson != null) {
                    val keys = videosJson.keys()
                    while (keys.hasNext()) {
                        val videoId = keys.next()
                        val entry = videosJson.optJSONObject(videoId) ?: continue
                        if (entry.has("chapter")) {
                            videoChapters[videoId] = entry.optInt("chapter")
                        }
                    }
                }
                Parsed(booklets, videoChapters)
            } catch (_: Exception) {
                // A missing/corrupt asset degrades to "no metadata":
                // strips still render from the DTO link alone.
                Parsed(emptyMap(), emptyMap())
            }
            cache = result
            return result
        }
    }

    /** Metadata of the booklet at [path] (a [LibraryItem.booklet] value). */
    fun meta(context: Context, path: String): BookletMeta? =
        parsed(context).booklets[path]

    /** Chapter number of the booklet linked to [videoId], if any. */
    fun chapterForVideo(context: Context, videoId: String?): Int? {
        if (videoId.isNullOrBlank()) return null
        return parsed(context).videoChapters[videoId]
    }
}

object BookletProgress {

    private const val PREFS_NAME = "booklet_progress"

    private val _flow = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** path → reading fraction (0f..1f), for strips and the sheet. */
    val flow: StateFlow<Map<String, Float>> = _flow.asStateFlow()

    /** path → (page, totalPages); page is stored ONE-based. */
    private val snapshots = ConcurrentHashMap<String, Pair<Int, Int>>()

    @Volatile
    private var seeded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun fractionOf(page: Int, total: Int): Float =
        if (total > 0) (page.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    private fun publishFractions() {
        _flow.value = snapshots.mapValues { (_, v) -> fractionOf(v.first, v.second) }
    }

    /** Loads the persisted store into memory. Idempotent. */
    fun seed(context: Context) {
        if (seeded) return
        synchronized(this) {
            if (seeded) return
            try {
                val all = prefs(context).all
                for ((path, value) in all) {
                    val parts = (value as? String)?.split(":") ?: continue
                    if (parts.size != 2) continue
                    val page = parts[0].toIntOrNull() ?: continue
                    val total = parts[1].toIntOrNull() ?: continue
                    if (total > 0 && page >= 1) snapshots[path] = page to total
                }
                publishFractions()
            } catch (_: Exception) {
                // A corrupt store degrades to "no progress yet".
            }
            seeded = true
        }
    }

    private fun ensureSeeded(context: Context) {
        if (!seeded) seed(context)
    }

    /**
     * Records the reader's position. [page] arrives ZERO-based from
     * PdfViewerScreen.onProgress (pagerState.currentPage) and is
     * normalized to ONE-based here, at the store boundary, so the
     * snapshot below and every «صفحة X من Y» display are 1-based.
     */
    fun record(context: Context, path: String, page: Int, total: Int) {
        if (path.isBlank() || total <= 0) return
        ensureSeeded(context)
        val oneBased = (page + 1).coerceIn(1, total)
        val current = snapshots[path]
        if (current != null && current.first == oneBased && current.second == total) return
        snapshots[path] = oneBased to total
        publishFractions()
        try {
            prefs(context).edit().putString(path, "$oneBased:$total").apply()
        } catch (_: Exception) {
            // Persistence is best-effort; the in-memory flow is live.
        }
    }

    /** (page, totalPages) with page ONE-based, or null if never read. */
    fun snapshot(context: Context, path: String): Pair<Int, Int>? {
        ensureSeeded(context)
        return snapshots[path]
    }

    /** Reading fraction of one booklet (0f when never read). */
    fun fraction(context: Context, path: String): Float {
        val snap = snapshot(context, path) ?: return 0f
        return fractionOf(snap.first, snap.second)
    }

    /** Drops one booklet's stored progress (used on file delete). */
    fun forget(context: Context, path: String) {
        ensureSeeded(context)
        if (snapshots.remove(path) != null) publishFractions()
        try {
            prefs(context).edit().remove(path).apply()
        } catch (_: Exception) {
            // Best-effort, like record().
        }
    }
}

object BookletDownloadManager {

    /** Public origin path prefix of the booklet family. */
    private const val BOOKLETS_PREFIX = "booklets/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, BookletDlState>>(emptyMap())

    /** path → download state, observed by strips and the sheet. */
    val states: StateFlow<Map<String, BookletDlState>> = _states.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()
    private val tempFiles = ConcurrentHashMap<String, File>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun setState(path: String, state: BookletDlState) {
        _states.update { it + (path to state) }
    }

    /**
     * Local file of a booklet. Catalog paths look like
     * "booklets/<slug>/<file>.pdf"; they are stored under
     * filesDir/booklets/<slug>/<file>.pdf.
     */
    fun localFile(context: Context, path: String): File {
        val relative = path.removePrefix(BOOKLETS_PREFIX)
        val dir = File(context.applicationContext.filesDir, BOOKLETS_PREFIX)
        val target = File(dir, relative)
        // Catalog paths are ours, but never let one escape the dir.
        val canonicalDir = dir.canonicalPath
        return if (target.canonicalPath.startsWith(canonicalDir)) {
            target
        } else {
            File(dir, File(relative).name)
        }
    }

    private fun tempFileFor(target: File): File =
        File(target.parentFile, target.name + ".tmp")

    /** True when the booklet's PDF is fully on disk. */
    fun isDownloaded(context: Context, path: String): Boolean {
        val f = localFile(context, path)
        return f.exists() && f.length() > 0
    }

    /**
     * Recomputes the states of [paths] from disk (a path with an
     * active download keeps its Downloading state; a recorded
     * failure survives a refresh so the sheet can offer retry).
     */
    fun refresh(context: Context, paths: List<String>) {
        val appContext = context.applicationContext
        scope.launch {
            for (path in paths) {
                if (jobs[path]?.isActive == true) continue
                val current = _states.value[path]
                if (isDownloaded(appContext, path)) {
                    if (current !is BookletDlState.Downloaded) {
                        setState(path, BookletDlState.Downloaded)
                    }
                } else if (current !is BookletDlState.Failed) {
                    if (current !is BookletDlState.NotDownloaded) {
                        setState(path, BookletDlState.NotDownloaded)
                    }
                }
            }
        }
    }

    /**
     * Starts (or ignores, when already running/downloaded) the
     * download of one booklet from the public content origin.
     * Verification mirrors the First Aid booklet hardening: the
     * expected size (when the catalog provides one) AND the "%PDF-"
     * magic header must both hold before the file is installed
     * under its final name.
     */
    fun startDownload(context: Context, path: String) {
        if (jobs[path]?.isActive == true) return
        val appContext = context.applicationContext
        if (isDownloaded(appContext, path)) {
            setState(path, BookletDlState.Downloaded)
            return
        }
        val expectedSize = BookletCatalog.meta(appContext, path)?.size ?: 0L
        val target = localFile(appContext, path)
        val temp = tempFileFor(target)
        tempFiles[path] = temp
        val job = scope.launch {
            setState(path, BookletDlState.Downloading(0f))
            try {
                target.parentFile?.mkdirs()
                val request = Request.Builder()
                    .url(WataniApi.BASE_URL + path)
                    .build()
                val call = client.newCall(request)
                calls[path] = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body
                        ?: throw IOException("استجابة فارغة من الخادم")
                    val totalBytes = when {
                        body.contentLength() > 0 -> body.contentLength()
                        expectedSize > 0 -> expectedSize
                        else -> -1L
                    }
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var readSoFar = 0L
                            while (true) {
                                if (!isActive) throw kotlinx.coroutines.CancellationException("cancelled")
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                readSoFar += n
                                if (totalBytes > 0) {
                                    setState(
                                        path,
                                        BookletDlState.Downloading(
                                            (readSoFar.toFloat() / totalBytes.toFloat())
                                                .coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (expectedSize > 0 && temp.length() != expectedSize) {
                    val actual = temp.length()
                    temp.delete()
                    throw IOException(
                        "حجم الكتيّب غير مطابق: المتوقع $expectedSize بايت والواصل فعليًا $actual بايت — حُذفت النسخة الناقصة."
                    )
                }
                if (!hasPdfHeader(temp)) {
                    temp.delete()
                    throw IOException("الملف الواصل ليس PDF صالحًا — حُذف ولن يُفتح.")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                setState(path, BookletDlState.Downloaded)
            } catch (e: kotlinx.coroutines.CancellationException) {
                temp.delete()
                setState(path, BookletDlState.NotDownloaded)
                throw e
            } catch (e: Exception) {
                temp.delete()
                setState(
                    path,
                    BookletDlState.Failed(e.message ?: "فشل تنزيل الكتيّب — تحقق من الاتصال وحاول مجددًا.")
                )
            } finally {
                calls.remove(path)
                tempFiles.remove(path)
                jobs.remove(path)
            }
        }
        jobs[path] = job
    }

    /** Cancels a running download; its coroutine cleans up. */
    fun cancel(path: String) {
        calls[path]?.cancel()
        jobs[path]?.cancel()
    }

    /** Deletes the downloaded file (and any partial temp) of [path]. */
    fun delete(context: Context, path: String) {
        cancel(path)
        val appContext = context.applicationContext
        scope.launch {
            val target = localFile(appContext, path)
            val temp = tempFiles[path] ?: tempFileFor(target)
            if (temp.exists()) temp.delete()
            if (target.exists()) target.delete()
            setState(path, BookletDlState.NotDownloaded)
        }
        BookletProgress.forget(appContext, path)
    }

    /** A real booklet starts with the five bytes "%PDF-". */
    private fun hasPdfHeader(f: File): Boolean = try {
        if (!f.exists() || f.length() < 5) {
            false
        } else {
            f.inputStream().use { input ->
                val head = ByteArray(5)
                var off = 0
                while (off < head.size) {
                    val r = input.read(head, off, head.size - off)
                    if (r < 0) break
                    off += r
                }
                off == head.size && String(head, Charsets.US_ASCII) == "%PDF-"
            }
        }
    } catch (_: Exception) {
        false
    }
}
