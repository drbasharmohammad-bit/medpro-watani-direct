package sy.medpro.watani.ui.library

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import sy.medpro.watani.data.BookTextIndex
import sy.medpro.watani.data.HighlightSelection
import sy.medpro.watani.data.PdfPagePoints
import sy.medpro.watani.data.PdfTextEngine
import sy.medpro.watani.data.PdfiumTextEngine
import sy.medpro.watani.data.SearchHit
import sy.medpro.watani.data.WordBox
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.GoldAccent
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * LEGACY (build 1.2.38): one freehand highlight stroke, stored
 * normalized (0..1 across the page). The freehand marking mode was
 * DELETED by the doctor's order in 1.2.38 — the class, its Moshi
 * adapter and its prefs payload stay so old data parses-and-is-
 * ignored safely (never rendered, never mutated, never crashes),
 * and so the release dex check for the reader DTO names keeps
 * passing. The reader's only marking system is now the yellow TEXT
 * highlight ([TextHighlightDto]).
 */
@JsonClass(generateAdapter = true)
data class HighlightStrokeDto(
    val page: Int,
    val points: List<Float>,
    val color: Int,
    val width: Float
)

/**
 * LEGACY (build 1.2.38): a shape/text annotation (TEXT / RECT /
 * ELLIPSE / ARROW), normalized (0..1 across the page). Deleted as a
 * feature together with the freehand mode — see [HighlightStrokeDto]:
 * the class and adapter stay for parse-and-ignore of old payloads.
 */
@JsonClass(generateAdapter = true)
data class PdfAnnotationDto(
    val page: Int,
    val type: String,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val text: String,
    val color: Int
)

/**
 * A TEXT highlight (build 1.2.37): whole extracted words the student
 * dragged over, stored as their WordBox rects (page fractions, y from
 * the top — the same normalized space as strokes) plus the covered
 * text for the highlights list. Persisted per book in prefs under
 * "texthighlights_<nameWithoutExtension>", separate from the freehand
 * strokes because the two markings are created and deleted apart.
 */
@JsonClass(generateAdapter = true)
data class TextHighlightDto(
    val page: Int,
    val rects: List<WordBox>,
    val text: String
)

/**
 * One entry of the reader's recent-books list (build 1.2.37):
 * most-recent-first, capped at 12, one entry per absolute path.
 * Persisted reader-wide in prefs under "reader_recents".
 */
@JsonClass(generateAdapter = true)
data class ReaderRecentDto(
    val path: String,
    val title: String,
    val page: Int,
    val totalPages: Int,
    val timestamp: Long
)

/**
 * One successfully opened book (build 1.2.52): the descriptor
 * the PDFium document reads through (kept open for the
 * document's whole lifetime, closed after it on dispose), the
 * document itself, its page count and its page sizes in
 * points. Produced by the open effect's armored PDFium open —
 * see the call site for why the armor catches Throwable.
 */
private data class OpenedBook(
    val fd: ParcelFileDescriptor,
    val document: PdfDocument,
    val pageCount: Int,
    val sizes: List<PdfPagePoints>
)

/** Hard cap on simultaneously held page bitmaps (build 1.2.37):
 * the cache keeps the visible pages ±1 plus at most one
 * zoom-resolution replacement per visible page — never more
 * than this many bitmaps alive; eviction always recycles.
 * (1.2.52: the cap rose from 4 to 6 because the vertical stack
 * legitimately shows more pages at once than the old pager —
 * a tall page plus parts of both neighbours, with one more
 * composed beyond each edge. The per-bitmap pixel budgets are
 * unchanged, so the worst case grows by the same pages the
 * student can actually see.) */
private const val READER_MAX_CACHED_BITMAPS = 6

/** Fallback page size (A4 points) for a page whose size the
 * engine could not report — layout only; the render itself
 * always uses the page's real size (build 1.2.52). */
private const val DEFAULT_PAGE_WIDTH_PT = 595
private const val DEFAULT_PAGE_HEIGHT_PT = 842

/** The seam between stacked pages in the vertical reader
 * (build 1.2.52): enough to read as a page boundary while
 * scrolling, never a layout band. */
private const val READER_PAGE_GAP_DP = 6

/** Settle delay before a zoom level earns its hi-res re-render
 * (1.2.46): pinch movements stream zoom values continuously, and
 * only the level the student actually stops at is rendered. */
private const val READER_HIRES_DEBOUNCE_MS = 250L

/** How long the "drag over the words themselves" hint stays in
 * the highlight panel after a selection matched nothing
 * (1.2.50). */
private const val SELECTION_MISS_HINT_MS = 2500L

/**
 * Whether the book's text layer — the extracted words that
 * search and yellow text highlighting run on — is still
 * extracting, ready, or absent (1.2.50). Before this the two
 * non-ready states were the same null: the highlight pen
 * simply did not exist while extraction ran (or after it
 * failed), with no explanation to the student.
 */
enum class TextLayerState { LOADING, READY, UNAVAILABLE }

private fun getFirebaseUid(): String {
    return try {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    } catch (e: Exception) {
        ""
    }
}

/**
 * Book reader, fixed (Oct 2026): the old version rendered one page at a time
 * with page-turn buttons hidden behind a bar that auto-hid after 3 seconds,
 * no swipe and no zoom at all. This version flips pages with a swipe
 * (HorizontalPager), zooms with double-tap and +/- buttons, pans while
 * zoomed, and jumps with a page slider. Rendered bitmaps are cached for
 * the current page ±1 and recycled on eviction (the old version never
 * recycled bitmaps on page change and never closed the file descriptor).
 *
 * Build 1.2.52 (the doctor's order of 27 Nov 2026, after the
 * 1.2.50/1.2.51 reader fixes did not settle his device's
 * «جارٍ تجهيز الصفحة» complaint): the engine is now PDFium
 * (PdfiumAndroidKt, Apache 2.0) — rendering AND the FPDFText
 * text layer — replacing PdfRenderer outright, and browsing
 * is a continuous VERTICAL stack (page under page, one
 * downward scroll) instead of the horizontal pager. MuPDF was
 * rejected for the engine role: AGPL. PDFBox remains only as
 * the text-layer fallback (see PdfiumTextEngine).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(pdfFile: File, title: String, onBack: () -> Unit, onDeleteCorrupt: (() -> Unit)? = null, onFullscreenChange: ((Boolean) -> Unit)? = null, onProgress: ((page: Int, totalPages: Int) -> Unit)? = null, onOpenRecent: ((java.io.File, String) -> Unit)? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var totalPages by remember { mutableStateOf(0) }
    var savedPage by remember { mutableStateOf(0) }
    // The open PDFium document (1.2.52) and its page sizes in
    // points, in page order: the sizes lay out the vertical
    // stack (each page's height follows its aspect ratio) and
    // feed the text engine's fraction conversion.
    var pdfiumDoc by remember { mutableStateOf<PdfDocument?>(null) }
    var pageSizesPt by remember { mutableStateOf<List<PdfPagePoints>>(emptyList()) }
    var parcelFd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var brightness by remember { mutableStateOf(1f) }
    var showBars by remember { mutableStateOf(true) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var bookmarks by remember { mutableStateOf(setOf<Int>()) }
    var hideBarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Fullscreen (tablets + landscape reading): hides the system bars and
    // the app bars; a floating exit button + Back are the way back, and
    // leaving the reader always restores the system bars.
    var isFullscreen by remember { mutableStateOf(false) }
    fun setSystemBarsVisible(visible: Boolean) {
        val activity = context as? android.app.Activity ?: return
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        setSystemBarsVisible(true)
        showBars = true
    }
    // Landscape reading (Nov 2026, doctor's order): in landscape the
    // maroon top bar eats a big share of the short height, while
    // fullscreen lays the page out correctly — so landscape reading
    // behaves like fullscreen for chrome: the reader bars start hidden
    // and the Scaffold bars hide too. A tap still brings bars back.
    // (The effect itself sits further down, next to the other chrome
    // effects, because it must not hide the bars mid text-highlight
    // session — 1.2.38.)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Reader chrome diet (1.2.38, item هـ): the reader is a full
    // takeover for its whole lifetime — the app Scaffold's top and
    // bottom bars stay hidden from open to dispose in BOTH hosts
    // (Library booklets and First Aid booklets), not only in
    // landscape/fullscreen as before. The dispose effect below
    // reports false on the way out.
    LaunchedEffect(Unit) {
        // True fullscreen for the reader's whole lifetime (1.2.46):
        // the SYSTEM bars hide too (transient reveal by edge
        // swipe), so the page inherits the physical screen end to
        // end — the manual fullscreen toggle used to be the only
        // way to win those strips back, and the dispose effect
        // below restores the bars on the way out.
        setSystemBarsVisible(false)
        onFullscreenChange?.invoke(true)
    }
    val prefs = remember { context.getSharedPreferences("watani_library_${getFirebaseUid()}", Context.MODE_PRIVATE) }

    // Dedicated single-thread render dispatcher (1.2.50): every
    // engine call of this reader (open + page renders) runs
    // on its own thread, so renders serialize naturally instead
    // of convoying behind one monitor on the shared
    // Dispatchers.Default pool — one of the mechanisms behind
    // the eternal «جارٍ تجهيز الصفحة» (see ReaderPagePipeline).
    // Closed with the reader in the dispose effect below.
    // (1.2.52: the engine is PDFium now; the discipline is the
    // same — one thread owns the document's renders.)
    val renderDispatcher = remember {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    // Highlighter persistence (outer scope so the open effect can restore it).
    // CRASH ARMOR (Crashlytics c3b30976, build 1.2.37): adapter creation
    // used to run bare inside remember{} during composition — when the
    // Moshi adapter for a marking DTO could not be built, composition
    // itself died and the book never opened. Adapter creation is now
    // runCatching-wrapped: on ANY failure the adapter is null, marking
    // persistence is disabled for this session (loads yield empty,
    // saves no-op) and the book still opens normally.
    val highlightAdapter = remember {
        runCatching {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter<List<HighlightStrokeDto>>(
                    Types.newParameterizedType(List::class.java, HighlightStrokeDto::class.java)
                )
        }.getOrNull()
    }
    // 1.2.38: no stroke state and no stroke saving remain — the
    // adapter above is kept ONLY so the open effect can parse-and-
    // ignore legacy payloads (see the load block below).

    // Shape/text annotations (outer scope so the open effect restores them).
    val annotationAdapter = remember {
        runCatching {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter<List<PdfAnnotationDto>>(
                    Types.newParameterizedType(List::class.java, PdfAnnotationDto::class.java)
                )
        }.getOrNull()
    }
    // 1.2.38: same parse-and-ignore treatment as the strokes — no
    // annotation state or saving remains in the reader.

    // Text highlights (1.2.37): whole-word highlights from the PDF
    // text layer, persisted per book like the strokes/annotations.
    val textHighlightAdapter = remember {
        runCatching {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter<List<TextHighlightDto>>(
                    Types.newParameterizedType(List::class.java, TextHighlightDto::class.java)
                )
        }.getOrNull()
    }
    var textHighlights by remember { mutableStateOf<List<TextHighlightDto>>(emptyList()) }
    fun saveTextHighlights() {
        val adapter = textHighlightAdapter ?: return
        runCatching {
            prefs.edit().putString(
                // Length-aware key (1.2.50, ReaderMarkingKeys): a
                // re-downloaded booklet no longer inherits the old
                // file's highlights on shifted text.
                ReaderMarkingKeys.textHighlightsKey(
                    pdfFile.nameWithoutExtension, pdfFile.length()
                ),
                adapter.toJson(textHighlights)
            ).apply()
        }
    }

    // Text-highlight mode state lives in the OUTER scope (1.2.38) so
    // the chrome auto-hide can consult it. Root cause of the «إنهاء
    // doesn't stop the mode» defect: the mode flag and the chrome
    // visibility used to be independent states — the panel carrying
    // the only visible exit sits inside the auto-hiding chrome, and
    // fullscreen entry (showBars = false), landscape rotation
    // (showBars = false) or the 3-second timer could hide that exit
    // while the mode's drag layer stayed live and its tap handler
    // was deliberately inert, so no gesture could bring the exit
    // back. Now: while the mode is on, auto-hide is fully suspended
    // (see scheduleHideBar and the mode effect below), and every
    // exit path funnels through one exit function.
    var textHighlightMode by remember { mutableStateOf(false) }

    // Recent books (1.2.37): reader-wide, most-recent-first, cap 12,
    // one entry per absolute path. Same crash armor as the marking
    // adapters: creation is runCatching-wrapped and nullable, every
    // read/write is runCatching-guarded — a corrupt recents payload
    // degrades to an empty list and never blocks opening a book.
    val recentAdapter = remember {
        runCatching {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter<List<ReaderRecentDto>>(
                    Types.newParameterizedType(List::class.java, ReaderRecentDto::class.java)
                )
        }.getOrNull()
    }
    var recents by remember { mutableStateOf<List<ReaderRecentDto>>(emptyList()) }
    fun loadRecents() {
        recents = runCatching {
            val json = prefs.getString("reader_recents", null) ?: return@runCatching emptyList()
            recentAdapter?.fromJson(json) ?: emptyList()
        }.getOrDefault(emptyList())
    }
    fun saveRecents(list: List<ReaderRecentDto>) {
        recents = list
        val adapter = recentAdapter ?: return
        runCatching {
            prefs.edit().putString("reader_recents", adapter.toJson(list)).apply()
        }
    }
    /** Records this book as the most recent (on successful open). */
    fun recordRecent(page: Int, total: Int) {
        if (total <= 0) return
        val entry = ReaderRecentDto(
            path = pdfFile.absolutePath,
            title = title,
            page = page,
            totalPages = total,
            timestamp = System.currentTimeMillis()
        )
        saveRecents((listOf(entry) + recents.filter { it.path != entry.path }).take(12))
    }
    /** Refreshes this book's saved page (on every page turn, next to
     * the existing last_page_ write) and keeps it most-recent. */
    fun updateRecentPage(page: Int) {
        val idx = recents.indexOfFirst { it.path == pdfFile.absolutePath }
        if (idx < 0) return
        val updated = recents[idx].copy(page = page, timestamp = System.currentTimeMillis())
        saveRecents(listOf(updated) + recents.filterIndexed { i, _ -> i != idx })
    }

    // Display mode (1.2.37): a READER-WIDE tri-state preference —
    // NORMAL / WARM (sepia paper) / NIGHT (inversion) — stored under
    // "reader_display_mode". Migration: the old boolean key
    // "reader_night_mode" seeds the first value (true -> NIGHT) and
    // is kept in sync on every change for downgrade safety. Filters
    // apply ONLY to the page bitmap; the chrome and every marking
    // layer stay exactly as they are in all three modes.
    var displayMode by remember {
        mutableStateOf(
            runCatching { prefs.getString("reader_display_mode", null) }.getOrNull()
                ?: if (prefs.getBoolean("reader_night_mode", false)) "NIGHT" else "NORMAL"
        )
    }
    val nightMode = displayMode == "NIGHT"
    val warmMode = displayMode == "WARM"
    fun cycleDisplayMode() {
        displayMode = when (displayMode) {
            "NORMAL" -> "WARM"
            "WARM" -> "NIGHT"
            else -> "NORMAL"
        }
        prefs.edit()
            .putString("reader_display_mode", displayMode)
            .putBoolean("reader_night_mode", displayMode == "NIGHT")
            .apply()
    }
    val nightColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    // Warm paper: the classic sepia matrix (no inversion) — warm
    // tint on the page image only, over a warm page background.
    val warmColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    val pageColorFilter = when {
        nightMode -> nightColorFilter
        warmMode -> warmColorFilter
        else -> null
    }

    // The book's text index (words + outline), extracted/cached by
    // PdfTextEngine in the background. Null = no text layer features
    // for this book (still loading, or extraction failed): search,
    // text highlighting and the outline button stay silently absent
    // and the reader behaves exactly as before.
    var textIndex by remember { mutableStateOf<BookTextIndex?>(null) }
    // Text-layer readiness (1.2.50): extraction starts only
    // after the first page has painted — at open it used to run
    // concurrently with the first renders, and PDFBox chewing a
    // whole book competed with them for CPU and memory on
    // exactly the devices that could least afford it. The
    // readiness is an explicit state (see TextLayerState), so
    // the highlight pen can always be present and can say WHY
    // highlighting is not usable yet.
    var textLayerState by remember { mutableStateOf(TextLayerState.LOADING) }
    var firstPagePainted by remember { mutableStateOf(false) }
    LaunchedEffect(pdfFile, firstPagePainted) {
        if (firstPagePainted && textLayerState == TextLayerState.LOADING) {
            // PDFium first (1.2.52): the text layer comes from
            // the engine that renders, so word geometry and
            // pixels share one coordinate truth. The PDFBox
            // engine is the fallback — it runs only when the
            // PDFium pass produced nothing usable (policy:
            // PdfiumTextEngine.shouldFallbackToPdfBox), and its
            // result wins whenever it has words; a wordless
            // PDFium index (outline only) still survives a
            // failed fallback.
            val doc = pdfiumDoc
            val fromPdfium = if (doc != null) {
                runCatching {
                    PdfiumTextEngine.load(context, pdfFile, doc, pageSizesPt)
                }.getOrNull()
            } else {
                null
            }
            val index = if (PdfiumTextEngine.shouldFallbackToPdfBox(fromPdfium)) {
                PdfTextEngine.load(context, pdfFile) ?: fromPdfium
            } else {
                fromPdfium
            }
            textIndex = index
            textLayerState =
                if (index != null) TextLayerState.READY else TextLayerState.UNAVAILABLE
        }
    }

    // Bookmarks: a TYPED List<Int> adapter built once (build 1.2.37).
    // The old code built a raw adapter(List::class.java) on every
    // save/load — untyped reflective plumbing of exactly the family
    // that crashed in Crashlytics c3b30976. The stored format is the
    // same JSON array of page numbers, so old bookmarks load as-is.
    val bookmarkAdapter = remember {
        runCatching {
            Moshi.Builder().build()
                .adapter<List<Int>>(
                    Types.newParameterizedType(List::class.java, Integer::class.java)
                )
        }.getOrNull()
    }

    fun saveBookmarks() {
        val adapter = bookmarkAdapter ?: return
        val key = "bookmarks_${pdfFile.nameWithoutExtension}"
        runCatching {
            prefs.edit().putString(key, adapter.toJson(bookmarks.toList())).apply()
        }
    }

    fun loadBookmarks() {
        val key = "bookmarks_${pdfFile.nameWithoutExtension}"
        val json = prefs.getString(key, null) ?: return
        bookmarks = runCatching {
            bookmarkAdapter?.fromJson(json) ?: emptyList()
        }.getOrDefault(emptyList()).toSet()
    }

    fun toggleBookmark(page: Int) {
        bookmarks = if (page in bookmarks) bookmarks - page else bookmarks + page
        saveBookmarks()
    }

    fun scheduleHideBar() {
        hideBarJob?.cancel()
        // Suspended for the whole text-highlight session (1.2.38):
        // the bars carry the mode's exit and must never auto-hide
        // from under it.
        if (textHighlightMode) return
        hideBarJob = coroutineScope.launch {
            kotlinx.coroutines.delay(3000)
            showBars = false
        }
    }

    fun resetHideBarTimer() {
        hideBarJob?.cancel()
        if (showBars) scheduleHideBar()
    }

    LaunchedEffect(pdfFile) {
        withContext(renderDispatcher) {
            try {
                loadBookmarks()
                // Legacy freehand strokes + shape/text annotations
                // (1.2.38): PARSED and deliberately DISCARDED — the
                // modes are deleted, the payloads are never rendered
                // or mutated, and a corrupt payload still never
                // blocks opening the book. Text highlights below are
                // untouched by this: they load and save as before.
                runCatching {
                    prefs.getString("highlights_${pdfFile.nameWithoutExtension}", null)?.let {
                        highlightAdapter?.fromJson(it)
                    }
                }
                runCatching {
                    prefs.getString("annotations_${pdfFile.nameWithoutExtension}", null)?.let {
                        annotationAdapter?.fromJson(it)
                    }
                }
                try {
                    // Length-aware key first (1.2.50); the legacy
                    // bare-name key is read once and migrated
                    // forward by the save below, so existing
                    // highlights survive the key change.
                    val bookName = pdfFile.nameWithoutExtension
                    val newKey = ReaderMarkingKeys.textHighlightsKey(
                        bookName, pdfFile.length()
                    )
                    val legacyKey = ReaderMarkingKeys.legacyTextHighlightsKey(bookName)
                    val stored = prefs.getString(newKey, null)
                    val legacy = if (stored == null) {
                        prefs.getString(legacyKey, null)
                    } else {
                        null
                    }
                    (stored ?: legacy)?.let {
                        textHighlights = textHighlightAdapter?.fromJson(it) ?: emptyList()
                    }
                    if (legacy != null && textHighlights.isNotEmpty()) {
                        saveTextHighlights()
                    }
                } catch (_: Exception) {
                    // Corrupt text-highlight JSON must never block opening the book.
                }
                // Recents load (armored inside loadRecents itself).
                loadRecents()
                // The PDFium open (1.2.52), armored with
                // runCatching — which catches Throwable, not just
                // Exception: a native library that cannot load
                // (a missing ABI build, a broken update) surfaces
                // as an UnsatisfiedLinkError, an Error, and the
                // reader must still land on its error state
                // instead of dying mid-composition. The
                // descriptor is closed again on every failure
                // path; on success it stays open for the
                // document's lifetime (PDFium reads through it)
                // and closes after the document on dispose.
                val opened = runCatching {
                    val fd = ParcelFileDescriptor.open(
                        pdfFile, ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    try {
                        val core = PdfiumCore(context)
                        val doc = core.newDocument(fd)
                        val count = doc.getPageCount()
                        val sizes = if (count > 0) {
                            // Page sizes in points (build
                            // 1.2.52): pdfiumandroid is pinned
                            // at 2.0.1 — the newest release
                            // whose AAR metadata accepts this
                            // project's compileSdk 36 (2.0.2+
                            // demand 37). 2.0.1 has no bulk
                            // getPageSizes call (that arrived
                            // in 2.0.2+), so sizes are read
                            // per page through the core's point
                            // getters, which return PDF points
                            // directly — the same numbers a
                            // bulk call at 72 dpi would have
                            // produced. A page whose size
                            // cannot be read stays null here
                            // and takes the fallback chain
                            // below (a sibling page's size,
                            // then the A4 default).
                            val raw = (0 until count).map { i ->
                                runCatching {
                                    val w = core.getPageWidthPoint(doc, i)
                                    val h = core.getPageHeightPoint(doc, i)
                                    if (w > 0 && h > 0) {
                                        PdfPagePoints(w, h)
                                    } else {
                                        null
                                    }
                                }.getOrNull()
                            }
                            (0 until count).map { i ->
                                raw.getOrNull(i)
                                    ?: raw.firstOrNull { it != null }
                                    ?: PdfPagePoints(
                                        DEFAULT_PAGE_WIDTH_PT, DEFAULT_PAGE_HEIGHT_PT
                                    )
                            }
                        } else {
                            emptyList()
                        }
                        OpenedBook(fd, doc, count, sizes)
                    } catch (t: Throwable) {
                        runCatching { fd.close() }
                        throw t
                    }
                }
                opened.fold(
                    onSuccess = { book ->
                        parcelFd = book.fd
                        pdfiumDoc = book.document
                        pageSizesPt = book.sizes
                        totalPages = book.pageCount
                        // 1.2.47: a file that "opens" with zero pages used to
                        // fall past every state branch and show the bare black
                        // canvas forever — it is an open failure, full stop.
                        // (1.2.50: the old body called error("...") here,
                        // which resolved to kotlin.error and threw its way
                        // into the catch below; the state is now assigned
                        // directly, as intended all along.)
                        if (totalPages <= 0) {
                            // Same «تعذر فتح الملف» prefix as the catch
                            // below: one open-failure wording for every
                            // way a book can fail to open.
                            error = "تعذر فتح الملف: بلا صفحات قابلة للعرض"
                            isLoading = false
                        } else {
                            savedPage = prefs.getInt("last_page_${pdfFile.nameWithoutExtension}", 0)
                                .coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                            // Successful open: this book becomes the most recent.
                            recordRecent(savedPage, totalPages)
                            isLoading = false
                        }
                    },
                    onFailure = { failure ->
                        error = "تعذر فتح الملف: ${failure.message ?: failure.javaClass.simpleName}"
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                error = "تعذر فتح الملف: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    LaunchedEffect(showBars) {
        if (showBars && !textHighlightMode) scheduleHideBar()
    }

    // Text-highlight session chrome rule (1.2.38): entering the mode
    // pins the bars visible for the WHOLE session; leaving it (via
    // the single exit function) re-arms the normal 3s auto-hide.
    LaunchedEffect(textHighlightMode) {
        if (textHighlightMode) {
            hideBarJob?.cancel()
            showBars = true
        } else if (showBars) {
            scheduleHideBar()
        }
    }

    // Landscape starts with the reader bars hidden (Nov 2026) —
    // except mid text-highlight session, where hiding the bars would
    // hide the mode's exit with them (1.2.38).
    LaunchedEffect(isLandscape) {
        if (isLandscape && !textHighlightMode) showBars = false
    }

    // Keep the screen awake for the whole reading session
    // (1.2.37): the flag is set on the activity window while the
    // reader is composed and always cleared on dispose.
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hideBarJob?.cancel()
            setSystemBarsVisible(true)
            onFullscreenChange?.invoke(false)
            // The document closes before its descriptor (1.2.52):
            // PDFium reads through the fd, so the fd must outlive
            // the document, never the other way round.
            try { pdfiumDoc?.close() } catch (_: Exception) {}
            try { parcelFd?.close() } catch (_: Exception) {}
            // The render thread dies with the reader (1.2.50).
            (renderDispatcher as? ExecutorCoroutineDispatcher)?.close()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isLoading) {
            // 1.2.47: labelled, so the black canvas never reads as a
            // dead screen while the book opens.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaroonPrimary)
                    Spacer(Modifier.height(14.dp))
                    Text("جارٍ فتح الكتاب…", color = Color.White, fontSize = 13.5.sp)
                }
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(error!!, color = Color.White, fontSize = 14.sp)
                    if (onDeleteCorrupt != null) {
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onDeleteCorrupt,
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("حذف النسخة التالفة وإعادة التحميل")
                        }
                    }
                }
            }
        } else if (totalPages > 0 && !isLoading) {
            // The VERTICAL page stack (1.2.52): pages compose in
            // one LazyColumn, top to bottom, starting at the
            // saved page. The stack's current page — the one the
            // indicator, the slider, the saved position and the
            // progress callback all follow — is the page
            // occupying the largest visible slice of the
            // viewport (ReaderScrollMath, derived from the
            // list's own layout info).
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = savedPage
            )
            val currentPage by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    ReaderScrollMath.currentPageFromVisible(
                        info.visibleItemsInfo.map { item ->
                            val visiblePx = (
                                minOf(
                                    item.offset + item.size,
                                    info.viewportEndOffset
                                ) - maxOf(item.offset, info.viewportStartOffset)
                                ).coerceAtLeast(0)
                            ReaderScrollMath.VisibleSlice(item.index, visiblePx)
                        }
                    )
                }
            }
            var zoom by remember { mutableStateOf(1f) }
            var pan by remember { mutableStateOf(Offset.Zero) }
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            // The column's measured width: page heights and the
            // touch normalization derive from it (1.2.52).
            var columnWidthPx by remember { mutableStateOf(0) }
            // A second finger is down (1.2.52): the column stops
            // scrolling so the pinch owns the gesture — written
            // by the viewport's non-consuming pointer observer,
            // read by the column's userScrollEnabled.
            var multiTouch by remember { mutableStateOf(false) }
            // A pinch/pan gesture in progress (1.2.50): written
            // by the viewport's pointer observer (1.2.52), read
            // by the hi-res gate below. Before this, the hi-res
            // debounce ran from the last zoom VALUE change, so
            // a slow pinch pausing mid-gesture fired a full
            // hi-res render whose bitmap swap (and immediate
            // recycle of the old bitmap) flickered mid-gesture
            // — part of the reported zoom shake.
            var pinchActive by remember { mutableStateOf(false) }
            // Per-page hi-res marks (1.2.52): page -> the target
            // width its cache slot was last zoom-rendered at.
            // The vertical stack zoom-renders EVERY visible page
            // (the pager rendered only the current one), so the
            // marks are per page; eviction clears a page's mark
            // with its bitmap.
            val hiResApplied = remember { mutableStateMapOf<Int, Int>() }
            var sliderPos by remember { mutableStateOf<Float?>(null) }
            val bitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }
            // Page render failures (1.2.47): a page whose render
            // returns null is marked here and its slot shows an
            // explicit error + retry (bumping the attempt re-runs
            // the render effect). Before this, a failed render left
            // a lone spinner on the black canvas forever — the
            // silent black screen of the doctor's device report.
            val failedPages = remember { mutableStateMapOf<Int, Boolean>() }
            val pageRenderAttempts = remember { mutableStateMapOf<Int, Int>() }
            // Pages whose FULL render already landed (or was
            // attempted) over the fast paint (1.2.50 staged
            // pipeline — see ReaderPagePipeline): the full stage
            // runs at most once per page per cache lifetime, and
            // eviction clears the mark with the bitmap.
            val fullResPages = remember { mutableStateMapOf<Int, Boolean>() }
            // PDFium calls are serialized twice over (1.2.52):
            // the library guards its native side with its own
            // global lock, and this reader additionally runs
            // every openPage/render/close sequence (base renders
            // + zoom re-renders) on its single render thread
            // under this lock, with every opened Page handle
            // closed in a finally block (1.2.37 hardening, kept).
            val renderLock = remember { Any() }

            /** Renders one page on the render dispatcher; null on
             * any failure. The caller owns the returned bitmap
             * (cache it or recycle it). With [targetWidthPx] > 0 the
             * render scale is derived from it via [ReaderRenderMath]
             * (display-matched resolution, 1.2.46) under the
             * [maxPixels] budget; otherwise the page renders at
             * [scaleFactor] x its point size (the legacy path).
             * (1.2.52: the render itself is PDFium's — it fills
             * the page background opaque white itself, so the old
             * explicit white erase the PdfRenderer path needed
             * for transparent-background pages is built in.) */
            suspend fun renderPageBitmap(
                page: Int,
                scaleFactor: Float,
                targetWidthPx: Int = 0,
                maxPixels: Int = 0
            ): Bitmap? {
                val doc = pdfiumDoc ?: return null
                return withContext(renderDispatcher) {
                    synchronized(renderLock) {
                        try {
                            val pg = doc.openPage(page)
                            if (pg == null) {
                                null
                            } else {
                                try {
                                    val pixelBudget =
                                        if (maxPixels > 0) maxPixels else ReaderRenderMath.BASE_MAX_PIXELS
                                    val pageWidthPt = pg.getPageWidthPoint()
                                    val pageHeightPt = pg.getPageHeightPoint()
                                    val renderScale = if (targetWidthPx > 0) {
                                        ReaderRenderMath.scaleForTargetWidth(
                                            pageWidthPt, pageHeightPt, targetWidthPx, pixelBudget
                                        )
                                    } else {
                                        scaleFactor
                                    }
                                    val bmp = Bitmap.createBitmap(
                                        (pageWidthPt * renderScale).toInt().coerceAtLeast(1),
                                        (pageHeightPt * renderScale).toInt().coerceAtLeast(1),
                                        Bitmap.Config.ARGB_8888
                                    )
                                    pg.renderPageBitmap(bmp, 0, 0, bmp.width, bmp.height)
                                    bmp
                                } finally {
                                    try { pg.close() } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }

            /** One bounded render attempt of [stage] for [page]
             * (1.2.50 — see ReaderPagePipeline): the attempt dies
             * at the pipeline timeout instead of hanging the page
             * state, and the caller walks the stage chain
             * (FAST → FULL → failure state). */
            suspend fun renderStage(
                page: Int,
                stage: ReaderPagePipeline.Stage
            ): Bitmap? {
                val target = when (stage) {
                    ReaderPagePipeline.Stage.FAST ->
                        ReaderPagePipeline.fastTargetWidthPx(boxSize.width)
                    ReaderPagePipeline.Stage.FULL ->
                        ReaderRenderMath.baseTargetWidthPx(boxSize.width)
                }
                val budget = when (stage) {
                    ReaderPagePipeline.Stage.FAST -> ReaderPagePipeline.FAST_MAX_PIXELS
                    ReaderPagePipeline.Stage.FULL -> ReaderRenderMath.BASE_MAX_PIXELS
                }
                return withTimeoutOrNull(ReaderPagePipeline.RENDER_TIMEOUT_MS) {
                    renderPageBitmap(
                        page,
                        ReaderRenderMath.FALLBACK_SCALE,
                        targetWidthPx = target,
                        maxPixels = budget
                    )
                }
            }

            /** Recycles a replaced page bitmap only after its
             * replacement has had a frame on screen (1.2.50):
             * recycling the old bitmap in the same instant as
             * the swap could blank the page for a frame — one
             * of the visible zoom flicker sources. If the slot
             * has moved on by then, the bitmap is left to the
             * cache's own eviction/dispose recycling. */
            fun recycleAfterFrame(replaced: Bitmap?, page: Int, current: Bitmap) {
                if (replaced == null || replaced === current) return
                coroutineScope.launch {
                    withFrameNanos { }
                    if (bitmapCache[page] === current) {
                        try { replaced.recycle() } catch (_: Exception) {}
                    }
                }
            }

            /** Text-highlight undo (1.2.38): pops the newest text
             * highlight on the CURRENT page and saves immediately.
             * (The 1.2.37 unified undo also covered freehand strokes
             * and shape/text annotations — both deleted with their
             * mode; text highlights are the only markings left.) */
            fun undoLastTextHighlight() {
                val idx = textHighlights.indexOfLast { it.page == currentPage }
                if (idx < 0) return
                textHighlights = textHighlights.toMutableList().also { it.removeAt(idx) }
                saveTextHighlights()
            }

            var showRecents by remember { mutableStateOf(false) }
            var showGoToPage by remember { mutableStateOf(false) }
            var goToPageText by remember { mutableStateOf("") }

            // Text-layer UI state (1.2.37). Search hits are recomputed
            // (debounced) from the text index; currentHitIndex is the
            // hit prev/next navigation walks and the one drawn strong.
            var searchHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
            var currentHitIndex by remember { mutableStateOf(-1) }
            // Text-highlight selection state (the mode flag itself is
            // hoisted to the outer scope — see its declaration). The
            // live selection rect is kept normalized like the saved
            // highlights.
            var selectionStart by remember { mutableStateOf<Offset?>(null) }
            var selectionEnd by remember { mutableStateOf<Offset?>(null) }
            // The page the live selection belongs to (1.2.52):
            // in the vertical stack several pages are composed
            // at once, so the selection is owned by ONE page —
            // the one the drag started on — and only that page
            // draws the live rect. -1 = no live selection.
            var selectionPage by remember { mutableStateOf(-1) }
            // A selection drag that matched no word (1.2.50):
            // the panel shows the corrective hint for a few
            // seconds instead of the drag silently doing
            // nothing (the old "the highlighter doesn't work"
            // experience).
            var selectionMissed by remember { mutableStateOf(false) }
            var showTextHighlights by remember { mutableStateOf(false) }
            var showOutline by remember { mutableStateOf(false) }
            // On-demand chrome popups (1.2.38 diet): zoom and
            // brightness live behind these, never as stacked sliders.
            var showZoomPopup by remember { mutableStateOf(false) }
            var showBrightnessPopup by remember { mutableStateOf(false) }
            // Premium chrome: the secondary tools (bookmark, display
            // mode, recents, outline, fullscreen) live ONLY behind
            // the top bar's single overflow button — this panel is
            // composed on demand and never as permanent chrome.
            var showToolsPanel by remember { mutableStateOf(false) }

            /** THE exit from text-highlight mode (1.2.38): every exit
             * path — the panel's «إنهاء ✓», the top-bar toggle,
             * system Back, opening another tool (search / bookmarks /
             * recents / outline / go-to-page / fullscreen) — calls
             * this one function, so no path can leave a stuck session
             * behind: the mode flag drops (the selection drag layer
             * is keyed on it and disposes), the live selection clears,
             * the saved highlights are flushed through the armored
             * save, and the outer mode effect re-arms the normal
             * chrome auto-hide. */
            fun exitTextHighlightMode() {
                if (!textHighlightMode) return
                textHighlightMode = false
                selectionStart = null
                selectionEnd = null
                selectionPage = -1
                saveTextHighlights()
            }

            // Touch point -> page-normalized point (0..1 across the fitted
            // page rect), so strokes survive zoom and screen sizes.
            // (1.2.52: the fitted rect is the page's own slot in the
            // vertical stack — the caller passes that slot's size, and
            // the bitmap fitted into it; the math is unchanged.)
            fun toNormalized(px: Offset, bmp: Bitmap, container: IntSize): Offset {
                val fit = minOf(container.width.toFloat() / bmp.width, container.height.toFloat() / bmp.height)
                val fittedW = bmp.width * fit
                val fittedH = bmp.height * fit
                val offX = (container.width - fittedW) / 2f
                val offY = (container.height - fittedH) / 2f
                return Offset(
                    ((px.x - offX) / fittedW).coerceIn(0f, 1f),
                    ((px.y - offY) / fittedH).coerceIn(0f, 1f)
                )
            }

            // The pan clamp for the zoom layer (1.2.52): the
            // layer wraps the whole stack viewport, so the
            // "fitted page" of ReaderZoomMath's clamp is the
            // viewport itself — the zoomed viewport may drift
            // by at most half its overflow per axis, exactly
            // the discipline the per-page clamp enforced when
            // the fitted page filled the box.
            fun clampViewPan(p: Offset, z: Float): Offset {
                if (boxSize.width <= 0 || boxSize.height <= 0) return p
                val clamped = ReaderZoomMath.clampPan(
                    ReaderZoomMath.Pan(p.x, p.y),
                    ReaderZoomMath.SizeF(
                        boxSize.width.toFloat(), boxSize.height.toFloat()
                    ),
                    ReaderZoomMath.BoxSize(boxSize.width, boxSize.height),
                    z
                )
                return Offset(clamped.x, clamped.y)
            }

            // Save the reading position and reset zoom on every page turn.
            LaunchedEffect(currentPage) {
                zoom = 1f
                pan = Offset.Zero
                // A gesture interrupted by the page turn must not
                // leave the hi-res gate shut (1.2.50).
                pinchActive = false
                prefs.edit().putInt("last_page_${pdfFile.nameWithoutExtension}", currentPage).apply()
                // The recents entry tracks the live page too (1.2.37).
                updateRecentPage(currentPage)
            }

            // Reading progress for the caller (library booklet cards):
            // snapshotFlow emits the saved start page on collection —
            // that is the on-open report — then every page turn.
            LaunchedEffect(listState, totalPages) {
                snapshotFlow { currentPage }.collect { page ->
                    onProgress?.invoke(page, totalPages)
                }
            }

            // Real search (1.2.37): debounce the query ~300ms, run the
            // pure search off the main thread, land on the first hit.
            LaunchedEffect(searchQuery, textIndex) {
                val index = textIndex
                if (index == null || searchQuery.isBlank()) {
                    searchHits = emptyList()
                    currentHitIndex = -1
                } else {
                    kotlinx.coroutines.delay(300)
                    val hits = withContext(Dispatchers.Default) {
                        PdfTextEngine.search(index, searchQuery)
                    }
                    searchHits = hits
                    currentHitIndex = if (hits.isNotEmpty()) 0 else -1
                }
            }

            // The selection-miss hint clears itself (1.2.50).
            LaunchedEffect(selectionMissed) {
                if (selectionMissed) {
                    kotlinx.coroutines.delay(SELECTION_MISS_HINT_MS)
                    selectionMissed = false
                }
            }

            // Prev/next (and the initial landing) scroll to the
            // current hit's page.
            LaunchedEffect(currentHitIndex, searchHits) {
                val hit = searchHits.getOrNull(currentHitIndex)
                if (hit != null && hit.page != currentPage) {
                    listState.animateScrollToItem(hit.page)
                }
            }

            // System Back leaves the text-highlight mode first (via
            // the single exit function), then fullscreen, then the
            // book — one predictable chain. (The fullscreen
            // BackHandler in the outer scope is composed earlier, so
            // this later one wins while the mode is on.)
            BackHandler(enabled = textHighlightMode) {
                exitTextHighlightMode()
            }

            // Eviction runs ONLY once the stack has settled
            // (1.2.46 discipline, kept): evicting mid-scroll
            // recycled bitmaps still on their way out of the
            // viewport — the outgoing page lost its image
            // mid-gesture and flashed a spinner, and a fast
            // fling back re-rendered from scratch. The keep
            // window is the visible range ±1, and the explicit
            // memory ceiling (READER_MAX_CACHED_BITMAPS) still
            // applies: evict the pages farthest from the
            // current one, recycling each.
            LaunchedEffect(listState, totalPages) {
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    if (!scrolling) {
                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        val firstVisible = visibleItems.firstOrNull()?.index ?: currentPage
                        val lastVisible = visibleItems.lastOrNull()?.index ?: currentPage
                        val keep = (firstVisible - 1..lastVisible + 1).toSet()
                        bitmapCache.keys.filter { it !in keep }.forEach { key ->
                            bitmapCache.remove(key)?.recycle()
                            // The full-render and hi-res marks
                            // die with their bitmap (1.2.50): a
                            // page that comes back re-earns both
                            // stages.
                            fullResPages.remove(key)
                            hiResApplied.remove(key)
                        }
                        while (bitmapCache.size > READER_MAX_CACHED_BITMAPS) {
                            val farthest = bitmapCache.keys.maxByOrNull { abs(it - currentPage) } ?: break
                            bitmapCache.remove(farthest)?.recycle()
                            fullResPages.remove(farthest)
                            hiResApplied.remove(farthest)
                        }
                    }
                }
            }

            // Zoom-resolution rendering (1.2.46): the settled zoom
            // (debounced) drives a re-render of the visible pages
            // whose target width follows the zoom itself (box width
            // x zoom x oversample, pixel-capped via ReaderRenderMath)
            // — the 1.2.37 cap of base x min(zoom, 2.0) left 3-4x
            // zooms permanently below display resolution. The
            // sharper bitmap simply replaces the page's cache slot
            // and STAYS there: settling back to 1x no longer
            // re-renders the base (that round trip was pure churn —
            // a full render on every zoom settle), and the eviction
            // above recycles the slot when the page leaves the
            // keep window as before. (1.2.52: the pager rendered
            // only its current page; the stack renders every page
            // in the viewport, each with its own applied-target
            // mark in hiResApplied.)
            var hiResTargetWidth by remember { mutableStateOf(0) }
            LaunchedEffect(zoom, boxSize, pinchActive) {
                if (!pinchActive) {
                    kotlinx.coroutines.delay(READER_HIRES_DEBOUNCE_MS)
                    hiResTargetWidth = ReaderRenderMath.hiResTargetWidthPx(boxSize.width, zoom)
                }
            }
            LaunchedEffect(hiResTargetWidth) {
                val target = hiResTargetWidth
                // The live zoom is re-checked at execution time:
                // a page turn resets it to 1x, and a stale target
                // must not fire zoom renders at 1x.
                if (target > 0 && zoom > ReaderRenderMath.HIRES_MIN_ZOOM) {
                    val visiblePages = listState.layoutInfo.visibleItemsInfo
                        .map { it.index }
                    for (page in (visiblePages + currentPage).distinct()) {
                        if (bitmapCache[page] != null && hiResApplied[page] != target) {
                            val bmp = renderPageBitmap(
                                page,
                                ReaderRenderMath.FALLBACK_SCALE,
                                targetWidthPx = target,
                                maxPixels = ReaderRenderMath.HIRES_MAX_PIXELS
                            )
                            if (bmp != null) {
                                val old = bitmapCache[page]
                                bitmapCache[page] = bmp
                                // The replaced bitmap is recycled only
                                // after the sharper one has had a frame
                                // on screen (1.2.50) — no swap flicker.
                                recycleAfterFrame(old, page, bmp)
                                hiResApplied[page] = target
                            }
                        }
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    bitmapCache.values.forEach { try { it.recycle() } catch (_: Exception) {} }
                    bitmapCache.clear()
                }
            }

            fun toggleZoom() {
                if (zoom > 1.01f) {
                    zoom = 1f
                } else {
                    zoom = 2f
                }
                pan = Offset.Zero
            }

            // The reading viewport (1.2.52): measures itself into
            // boxSize (the zoom math's frame), carries the zoom
            // layer, and hosts the vertical stack. The zoom/pan
            // graphicsLayer wraps the WHOLE stack now — read in
            // the draw phase, so a pinch/pan state change only
            // re-records this layer (the 1.2.46 discipline) — and
            // the pinch/pan handler lives on the same box: its
            // pointer coordinates are layer-local (pre-transform),
            // exactly as they were inside the old per-page layer,
            // so the anchored-zoom math is unchanged.
            Box(Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .pointerInput(Unit) {
                            // Pinch/pan that never steals
                            // single-finger scrolls at 1x: only
                            // multi-touch (pinch) and drags while
                            // zoomed are consumed; a single finger
                            // at 1x passes straight through to the
                            // column. In text-highlight mode the
                            // handler mutates nothing — drags
                            // belong to the pages' selection layer
                            // (the 1.2.37/1.2.38 discipline, when
                            // this handler simply was not installed
                            // in the mode). Gesture ACTIVITY is
                            // tracked by the column's own observer
                            // below (pinchActive / multiTouch).
                            var prev = mapOf<Long, Offset>()
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val down = event.changes.filter { it.pressed }
                                    if (!textHighlightMode && down.size >= 2) {
                                        val cur = down.associate { it.id.value to it.position }
                                        val ids = cur.keys.intersect(prev.keys).take(2)
                                        if (ids.size == 2) {
                                            val a = ids[0]; val b = ids[1]
                                            val oldDist = (prev[a]!! - prev[b]!!).getDistance()
                                            val newDist = (cur[a]!! - cur[b]!!).getDistance()
                                            val oldC = (prev[a]!! + prev[b]!!) / 2f
                                            val newC = (cur[a]!! + cur[b]!!) / 2f
                                            if (oldDist > 0f && newDist > 0f) {
                                                val scale = newDist / oldDist
                                                if (scale != 1f || newC != oldC) {
                                                    val oldZoom = zoom
                                                    val newZoom =
                                                        (zoom * scale).coerceIn(1f, 4f)
                                                    zoom = newZoom
                                                    pan = if (newZoom <= 1.01f) {
                                                        Offset.Zero
                                                    } else {
                                                        // Anchored zoom
                                                        // (1.2.50 — see
                                                        // ReaderZoomMath):
                                                        // the pan
                                                        // compensates the
                                                        // scale around
                                                        // the box centre
                                                        // in the SAME
                                                        // event, so the
                                                        // content under
                                                        // the fingers no
                                                        // longer swims a
                                                        // frame behind
                                                        // them (الرجفان).
                                                        val anchored =
                                                            ReaderZoomMath.anchoredPan(
                                                                ReaderZoomMath.Pan(oldC.x, oldC.y),
                                                                ReaderZoomMath.Pan(newC.x, newC.y),
                                                                ReaderZoomMath.BoxSize(
                                                                    boxSize.width,
                                                                    boxSize.height
                                                                ),
                                                                ReaderZoomMath.ZoomChange(
                                                                    oldZoom, newZoom
                                                                ),
                                                                ReaderZoomMath.Pan(pan.x, pan.y)
                                                            )
                                                        clampViewPan(
                                                            Offset(anchored.x, anchored.y),
                                                            newZoom
                                                        )
                                                    }
                                                    down.forEach { it.consume() }
                                                }
                                            }
                                        }
                                        prev = cur
                                    } else if (!textHighlightMode && down.size == 1 && zoom > 1.01f) {
                                        val c = down[0]
                                        val p = prev[c.id.value]
                                        if (p != null && c.position != p) {
                                            pan = clampViewPan(pan + (c.position - p), zoom)
                                            c.consume()
                                        }
                                        prev = mapOf(c.id.value to c.position)
                                    } else {
                                        // Single finger at 1x (or the
                                        // highlight mode): consume
                                        // nothing — the column scrolls
                                        // / the pages select.
                                        prev = down.associate { it.id.value to it.position }
                                    }
                                    if (down.isEmpty()) prev = emptyMap()
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        // Neighbours are composed (and so start
                        // rendering) BEFORE they scroll in
                        // (1.2.46 discipline, kept from the
                        // pager). The pinned Compose Foundation
                        // (BOM 2024.09.00 / foundation 1.7.2) has
                        // NO beyondBoundsItemCount parameter on
                        // LazyColumn at all (verified against the
                        // AAR's own LazyDslKt signature): 1.7
                        // folds neighbour prefetch into the list
                        // state's built-in prefetch strategy,
                        // which composes the next page ahead of
                        // the scroll on its own — the discipline
                        // survives, the knob does not exist.
                        // While zoomed, drags pan the stack
                        // instead of scrolling it; in
                        // text-highlight mode they select instead
                        // of scrolling; while a second finger is
                        // down the pinch owns the gesture; at 1x
                        // a single finger always scrolls.
                        userScrollEnabled = zoom <= 1.01f && !textHighlightMode && !multiTouch,
                        verticalArrangement = Arrangement.spacedBy(READER_PAGE_GAP_DP.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { columnWidthPx = it.width }
                            // The pointer observer (1.2.52):
                            // watches every pointer in the
                            // INITIAL pass and consumes NOTHING —
                            // it only records gesture activity
                            // (pinchActive gates the hi-res
                            // re-render, 1.2.50) and the second
                            // finger (multiTouch stops the
                            // column's own scrolling so the pinch
                            // can own the gesture).
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val observed = awaitPointerEvent(
                                            androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                        )
                                        val downCount = observed.changes.count { it.pressed }
                                        pinchActive = downCount > 0
                                        multiTouch = downCount >= 2
                                    }
                                }
                            }
                    ) {
                        items(count = totalPages, key = { it }) { page ->
                val bitmap = bitmapCache[page]
                val renderAttempt = pageRenderAttempts[page] ?: 0
                LaunchedEffect(page, pdfiumDoc, renderAttempt) {
                    if (pdfiumDoc == null) return@LaunchedEffect
                    // Staged, bounded page resolution (1.2.50 —
                    // see ReaderPagePipeline): the FAST paint
                    // lands first so a downloaded book shows its
                    // page at once, then the FULL render swaps in
                    // over it. Every attempt is timeout-bounded
                    // and the chain terminates — the page always
                    // resolves to content or to the explicit
                    // failure state below, never to an eternal
                    // spinner (the pre-1.2.50 single fancy render
                    // could stall and fire neither exit).
                    if (bitmapCache[page] == null) {
                        val fast = renderStage(page, ReaderPagePipeline.Stage.FAST)
                        if (fast != null) {
                            bitmapCache[page] = fast
                            failedPages.remove(page)
                            if (!firstPagePainted) firstPagePainted = true
                        } else {
                            val full = renderStage(page, ReaderPagePipeline.Stage.FULL)
                            if (full != null) {
                                bitmapCache[page] = full
                                failedPages.remove(page)
                                fullResPages[page] = true
                                if (!firstPagePainted) firstPagePainted = true
                            } else {
                                failedPages[page] = true
                            }
                        }
                    }
                    if (bitmapCache[page] != null && fullResPages[page] != true &&
                        failedPages[page] != true
                    ) {
                        val full = renderStage(page, ReaderPagePipeline.Stage.FULL)
                        if (full != null) {
                            val replaced = bitmapCache[page]
                            bitmapCache[page] = full
                            recycleAfterFrame(replaced, page, full)
                        }
                        // Landed or not, the full stage is not
                        // re-attempted for this cache lifetime:
                        // the fast paint stays rather than the
                        // page churning renders.
                        fullResPages[page] = true
                    }
                }
                // The labelled preparing state is gated behind
                // the pipeline grace (1.2.50): a downloaded
                // book's fast paint lands far inside it, so the
                // label is effectively never seen for local
                // files; before the grace the slot is just the
                // paper colour.
                var showPreparing by remember(page) { mutableStateOf(false) }
                LaunchedEffect(page, bitmap == null, failedPages[page]) {
                    showPreparing = false
                    if (bitmap == null && failedPages[page] != true) {
                        kotlinx.coroutines.delay(
                            ReaderPagePipeline.PREPARING_LABEL_DELAY_MS
                        )
                        showPreparing = true
                    }
                }
                // The page's slot in the stack (1.2.52): the
                // full column width, with the height following
                // the page's own aspect ratio — this slot IS the
                // fitted page rect that the touch normalization
                // and the markings canvas work in (the pager's
                // full-screen letterbox is gone; pages are the
                // layout now). itemSize is the same rect in
                // pixels, from the column's measured width.
                val sizePt = pageSizesPt.getOrNull(page)
                    ?: pageSizesPt.firstOrNull()
                    ?: PdfPagePoints(DEFAULT_PAGE_WIDTH_PT, DEFAULT_PAGE_HEIGHT_PT)
                val itemSize = IntSize(
                    columnWidthPx,
                    ReaderScrollMath.pageHeightPx(sizePt, columnWidthPx)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            if (sizePt.heightPt > 0) {
                                sizePt.widthPt.toFloat() / sizePt.heightPt.toFloat()
                            } else {
                                1f
                            }
                        )
                ) {
                    if (bitmap != null) {
                        // The ImageBitmap wrapper is remembered per
                        // bitmap (1.2.46): the old code rebuilt it on
                        // every recomposition of the page.
                        val pageImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        Box(
                            Modifier
                                .fillMaxSize()
                                // The page area carries the display
                                // mode's paper colour — black in
                                // night mode, warm paper in warm
                                // mode, white otherwise (1.2.52:
                                // PDFium fills the rendered bitmap's
                                // background white itself; this is
                                // the slot behind it, visible only
                                // if a fit ever letterboxes).
                                .background(
                                    when {
                                        nightMode -> Color.Black
                                        warmMode -> Color(0xFFF3E5C8)
                                        else -> Color.White
                                    }
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { tapOff ->
                                            if (textHighlightMode) {
                                                // Tap-to-delete (1.2.38): a tap
                                                // ON an existing highlight of
                                                // this page deletes just that
                                                // one and saves at once. A tap
                                                // anywhere else never kills
                                                // the mode — but if the bars
                                                // were somehow hidden it
                                                // brings them (and the exit)
                                                // back.
                                                val n = toNormalized(tapOff, bitmap, itemSize)
                                                val hitIdx = textHighlights.indexOfLast { th ->
                                                    th.page == page && th.rects.any { r ->
                                                        n.x >= r.x0 && n.x <= r.x1 &&
                                                            n.y >= r.y0 && n.y <= r.y1
                                                    }
                                                }
                                                if (hitIdx >= 0) {
                                                    textHighlights = textHighlights.toMutableList()
                                                        .also { it.removeAt(hitIdx) }
                                                    saveTextHighlights()
                                                } else if (!showBars) {
                                                    showBars = true
                                                }
                                            } else {
                                                if (!showBars) {
                                                    showBars = true
                                                    resetHideBarTimer()
                                                } else {
                                                    showBars = false
                                                }
                                            }
                                        },
                                        onDoubleTap = { toggleZoom() }
                                    )
                                }
                                .then(
                                    if (textHighlightMode) {
                                        // Keyed on the mode (1.2.38): the
                                        // layer installs on entry and
                                        // DISPOSES on exit, so no stuck
                                        // interception can outlive the
                                        // session.
                                        Modifier.pointerInput(textHighlightMode) {
                                            // Text selection drag. 1.2.38:
                                            // active at EVERY zoom level —
                                            // the 1.2.37 code gated it to
                                            // zoom <= 1.01f, which left a
                                            // dead touch zone while zoomed
                                            // (neither selection nor pan
                                            // was installed). Pointer
                                            // coordinates inside the
                                            // graphicsLayer are layer-local
                                            // (pre-transform), so the
                                            // normalized-rect math stays
                                            // exact at any zoom.
                                            detectDragGestures(
                                                onDragStart = { off ->
                                                    val n = toNormalized(off, bitmap, itemSize)
                                                    selectionPage = page
                                                    selectionStart = n
                                                    selectionEnd = n
                                                },
                                                onDrag = { change, _ ->
                                                    selectionEnd = toNormalized(change.position, bitmap, itemSize)
                                                },
                                                onDragEnd = {
                                                    val s = selectionStart
                                                    val e = selectionEnd
                                                    selectionStart = null
                                                    selectionEnd = null
                                                    selectionPage = -1
                                                    if (s != null && e != null) {
                                                        val left = minOf(s.x, e.x)
                                                        val right = maxOf(s.x, e.x)
                                                        val top = minOf(s.y, e.y)
                                                        val bottom = maxOf(s.y, e.y)
                                                        val pageWords = textIndex
                                                            ?.pages?.firstOrNull { it.page == page }
                                                            ?.words.orEmpty()
                                                        // Tolerant matching
                                                        // (1.2.50 —
                                                        // HighlightSelection):
                                                        // a graze at a word's
                                                        // edge counts, where
                                                        // the old strict
                                                        // overlap silently
                                                        // selected nothing.
                                                        val matched = HighlightSelection.selectWords(
                                                            pageWords,
                                                            HighlightSelection.SelectionRect(
                                                                left, top, right, bottom
                                                            )
                                                        )
                                                        if (matched.isNotEmpty()) {
                                                            selectionMissed = false
                                                            textHighlights = textHighlights + TextHighlightDto(
                                                                page = page,
                                                                rects = matched,
                                                                text = matched.joinToString(" ") { it.text }
                                                            )
                                                            saveTextHighlights()
                                                        } else if (textLayerState == TextLayerState.READY) {
                                                            selectionMissed = true
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    selectionStart = null
                                                    selectionEnd = null
                                                    selectionPage = -1
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                       ) {
                            Image(
                                bitmap = pageImageBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                // High-quality resampling (1.2.46):
                                // the default FilterQuality.Low hazed
                                // every fit of the page bitmap (down
                                // to phone widths, up on tablets).
                                filterQuality = FilterQuality.High,
                                // Display-mode filters apply ONLY to
                                // the page image (inversion at night,
                                // sepia when warm); markings drawn over
                                // it (the Canvas below) are never filtered.
                                colorFilter = pageColorFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Highlighter layer: strokes drawn over the page,
                            // inside the same zoom/pan transform as the image.
                            Canvas(Modifier.fillMaxSize()) {
                                val fit = minOf(size.width / bitmap.width, size.height / bitmap.height)
                                val fittedW = bitmap.width * fit
                                val fittedH = bitmap.height * fit
                                val offX = (size.width - fittedW) / 2f
                                val offY = (size.height - fittedH) / 2f
                                // Text-layer markings (1.2.37), drawn in
                                // this same fitted-page transform (offX/
                                // offY/fittedW/fittedH): saved text
                                // highlights first, then this page's
                                // search hits (the current hit stronger
                                // + outlined), then the live selection
                                // rect. Since 1.2.38 these are the ONLY
                                // markings drawn — the freehand layer
                                // that used to sit above them is deleted.
                                fun drawWordRect(w: WordBox, color: Color, alpha: Float, outline: Boolean = false) {
                                    val left = offX + w.x0 * fittedW
                                    val top = offY + w.y0 * fittedH
                                    val rectSize = Size((w.x1 - w.x0) * fittedW, (w.y1 - w.y0) * fittedH)
                                    drawRect(color = color, topLeft = Offset(left, top), size = rectSize, alpha = alpha)
                                    if (outline) {
                                        drawRect(
                                            color = color, topLeft = Offset(left, top), size = rectSize,
                                            style = Stroke(width = 2f)
                                        )
                                    }
                                }
                                textHighlights.filter { it.page == page }.forEach { th ->
                                    th.rects.forEach { r -> drawWordRect(r, Color(0xFFFFEB3B), 0.45f) }
                                }
                                if (searchHits.isNotEmpty()) {
                                    val pageWords = textIndex?.pages?.firstOrNull { it.page == page }?.words.orEmpty()
                                    searchHits.withIndex()
                                        .filter { it.value.page == page }
                                        .forEach { (hitIdx, hit) ->
                                            val isCurrentHit = hitIdx == currentHitIndex
                                            for (wi in hit.wordStart..hit.wordEnd) {
                                                pageWords.getOrNull(wi)?.let { w ->
                                                    if (isCurrentHit) {
                                                        drawWordRect(w, Color(0xFFFF9800), 0.55f, outline = true)
                                                    } else {
                                                        drawWordRect(w, Color(0xFFFFEB3B), 0.35f)
                                                    }
                                                }
                                            }
                                        }
                                }
                                if (page == selectionPage) {
                                    val s = selectionStart
                                    val e = selectionEnd
                                    if (s != null && e != null) {
                                        val left = offX + minOf(s.x, e.x) * fittedW
                                        val top = offY + minOf(s.y, e.y) * fittedH
                                        val rectSize = Size(abs(e.x - s.x) * fittedW, abs(e.y - s.y) * fittedH)
                                        drawRect(
                                            color = Color(0xFFFFEB3B), topLeft = Offset(left, top),
                                            size = rectSize, alpha = 0.30f
                                        )
                                        drawRect(
                                            color = Color(0xFFFF9800), topLeft = Offset(left, top),
                                            size = rectSize, style = Stroke(width = 2f)
                                        )
                                    }
                                }
                                                            }
                        }
                    } else {
                        // 1.2.47: an unrendered page looks like a PAGE
                        // (the display mode's paper color), never a
                        // black void; while rendering it carries a
                        // labelled spinner, and a failed render is an
                        // explicit error with a retry — no silent
                        // black screen and no endless spinner (the
                        // professional-cache rule: every loading state
                        // resolves to content or an error).
                        Box(
                            Modifier.fillMaxSize().background(
                                when {
                                    nightMode -> Color.Black
                                    warmMode -> Color(0xFFF3E5C8)
                                    else -> Color.White
                                }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (failedPages[page] == true) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Error, null, Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "تعذّرت قراءة هذه الصفحة",
                                        color = if (nightMode) Color.White else Color.Black,
                                        fontSize = 13.5.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            failedPages.remove(page)
                                            pageRenderAttempts[page] =
                                                (pageRenderAttempts[page] ?: 0) + 1
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaroonPrimary
                                        )
                                    ) {
                                        Text("إعادة المحاولة")
                                    }
                                }
                            } else if (showPreparing) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = MaroonPrimary,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "جارٍ تجهيز الصفحة…",
                                        color = if (nightMode) Color.White else Color.Black,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
                        }
                    }
                }
            }

            // Brightness: a real overlay above the page (the old version
            // painted it BEHIND the opaque bitmap, so it did nothing).
            if (brightness < 1f) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = (1f - brightness) * 0.7f))
                )
            }

            if (showBars) {
                // Slim reader chrome (1.2.38, item هـ — the doctor's
                // reader diet): ONE slim top bar (48dp: back, title,
                // tools) and ONE slim bottom bar (52dp: page
                // indicator + slider, with zoom and brightness as
                // on-demand popup overlays) replace the old 64dp
                // TopAppBar + stacked marking panels + 3-row bottom
                // stack. The only marking tool is yellow TEXT
                // highlighting, entered from the pen icon; its panel
                // is a slim overlay row that never pushes the page.
                // With the app Scaffold bars hidden for the reader's
                // whole lifetime, total visible chrome is 100dp —
                // the page keeps ~88% of an 800dp screen with bars
                // shown and 100% in the default auto-hidden reading
                // state (1.2.37: ~52% with bars shown, ~33% with a
                // marking panel open — the doctor's screenshot).
                // Premium chrome (this build): the top bar is now a
                // FLOATING semi-transparent strip over the page (the
                // pager always laid out full-size behind it) holding
                // only back, title, the highlight pen, search and ONE
                // overflow button; every secondary tool (bookmark
                // toggle + list, display-mode cycle, recents,
                // outline, fullscreen) moved into the on-demand
                // tools panel behind that button — no other
                // permanent chrome was added, and no reader logic
                // (render, text highlight, save) changed.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(MaroonPrimary.copy(alpha = 0.88f))
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        title, maxLines = 1, color = Color.White, fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f),
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // Bookmark toggle + bookmarks list moved into
                    // the tools panel below (Premium chrome) — the
                    // toggleBookmark/showBookmarks logic is unchanged.
                    // The pen: the reader's ONLY marking system —
                    // yellow text highlighting (the freehand mode is
                    // deleted, 1.2.38). Always present (1.2.50): it
                    // used to exist only once the text index had
                    // loaded, so on large books — whose extraction
                    // takes a while — the highlighter silently did
                    // not exist; the panel now explains the text
                    // layer's state instead (see TextLayerState).
                    IconButton(
                        onClick = {
                            showToolsPanel = false
                            if (textHighlightMode) exitTextHighlightMode()
                            else textHighlightMode = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.BorderColor, null, tint = if (textHighlightMode) GoldAccent else Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = {
                            showToolsPanel = false
                            if (!showSearch) exitTextHighlightMode()
                            showSearch = !showSearch
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Search, null, tint = if (showSearch) GoldAccent else Color.White, modifier = Modifier.size(20.dp))
                    }
                    // ONE overflow button for every secondary tool
                    // (Premium chrome): bookmark, display mode,
                    // recents, outline and fullscreen live in the
                    // tools panel it toggles — never as permanent
                    // top-bar icons.
                    IconButton(
                        onClick = { showToolsPanel = !showToolsPanel },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert, null,
                            tint = if (showToolsPanel) GoldAccent else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Secondary tools panel (Premium chrome): composed
                // ONLY while the overflow button has it open — a
                // floating card under the top bar that never pushes
                // the page. Every row calls the exact handler its
                // old top-bar icon called; state-feedback rows
                // (bookmark toggle, display cycle) keep the panel
                // open, navigation rows close it first.
                if (showToolsPanel) {
                    Column(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 52.dp, end = 8.dp)
                            .width(248.dp)
                            .background(
                                MaroonPrimary.copy(alpha = 0.97f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { toggleBookmark(currentPage) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bookmark, null,
                                tint = if (currentPage in bookmarks) GoldAccent else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (currentPage in bookmarks) "إزالة إشارة هذه الصفحة"
                                else "إشارة لهذه الصفحة",
                                color = Color.White, fontSize = 13.sp
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showToolsPanel = false
                                    exitTextHighlightMode()
                                    showBookmarks = true
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MenuBook, null, tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("قائمة الفهارس", color = Color.White, fontSize = 13.sp)
                        }
                        // The three display modes stay untouched —
                        // this row is the old top-bar cycle button,
                        // moved behind the overflow button.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { cycleDisplayMode() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (displayMode) {
                                    "WARM" -> Icons.Default.WbSunny
                                    "NIGHT" -> Icons.Default.DarkMode
                                    else -> Icons.Default.LightMode
                                },
                                null,
                                tint = if (displayMode == "NORMAL") Color.White else GoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "وضع العرض: " + when (displayMode) {
                                    "WARM" -> "دافئ"
                                    "NIGHT" -> "ليلي"
                                    else -> "عادي"
                                },
                                color = Color.White, fontSize = 13.sp
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showToolsPanel = false
                                    exitTextHighlightMode()
                                    showRecents = true
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.History, null, tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("آخر الكتيّبات المفتوحة", color = Color.White, fontSize = 13.sp)
                        }
                        if (!textIndex?.outline.isNullOrEmpty()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showToolsPanel = false
                                        exitTextHighlightMode()
                                        showOutline = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.List, null, tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("فهرس الكتاب", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showToolsPanel = false
                                    exitTextHighlightMode()
                                    isFullscreen = true
                                    setSystemBarsVisible(false)
                                    showBars = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Fullscreen, null, tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("ملء الشاشة", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                // Text-highlight panel (1.2.38): a slim overlay row
                // under the top bar — the hint, the saved-highlights
                // list entry, undo, and the mode's ONE exit («إنهاء ✓»
                // → exitTextHighlightMode()).
                if (textHighlightMode) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp)
                            .background(MaroonPrimary.copy(alpha = 0.92f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            // The panel narrates the text layer's
                            // state (1.2.50): still extracting, no
                            // text layer in this book, a selection
                            // that just missed, or the normal hint.
                            when (textLayerState) {
                                TextLayerState.LOADING ->
                                    "جارٍ تجهيز نص الكتاب… لحظات ويصير التظليل جاهزًا"
                                TextLayerState.UNAVAILABLE ->
                                    "هذا الكتاب بلا طبقة نصية — التظليل غير متاح له"
                                TextLayerState.READY ->
                                    if (selectionMissed) {
                                        "اسحب فوق كلمات النص مباشرة حتى يثبت التظليل"
                                    } else {
                                        "اسحب فوق النص لتظليله — ولمس مظللة يحذفها"
                                    }
                            },
                            color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showTextHighlights = true }) {
                            Text("المظللات (${textHighlights.size})", color = Color.White, fontSize = 12.sp)
                        }
                        TextButton(onClick = { undoLastTextHighlight() }) {
                            Text(
                                "تراجع",
                                color = Color.White.copy(
                                    alpha = if (textHighlights.any { it.page == currentPage }) 1f else 0.4f
                                ),
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { exitTextHighlightMode() }) {
                            Text("إنهاء ✓", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Slim bottom bar (1.2.38): ONE row — prev/next, the
                // page indicator (tap = go-to-page dialog, which
                // keeps the full remaining-pages line), the page
                // slider, the zoom % (tap = zoom popup), and the
                // brightness icon (tap = brightness popup). No
                // stacked permanent sliders anymore.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaroonPrimary.copy(alpha = 0.95f))
                        .height(52.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (currentPage > 0) coroutineScope.launch {
                                listState.animateScrollToItem(currentPage - 1)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = {
                        exitTextHighlightMode()
                        goToPageText = (currentPage + 1).toString()
                        showGoToPage = true
                    }) {
                        Text(
                            "صفحة ${currentPage + 1} من $totalPages (${((currentPage + 1).toFloat() / totalPages * 100).toInt()}%)",
                            color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp
                        )
                    }
                    if (totalPages > 1) {
                        Slider(
                            value = sliderPos ?: currentPage.toFloat(),
                            onValueChange = { sliderPos = it },
                            onValueChangeFinished = {
                                sliderPos?.let { target ->
                                    coroutineScope.launch {
                                        listState.scrollToItem(target.roundToInt().coerceIn(0, totalPages - 1))
                                    }
                                }
                                sliderPos = null
                            },
                            valueRange = 0f..(totalPages - 1).toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = GoldAccent,
                                activeTrackColor = GoldAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(
                        onClick = {
                            if (currentPage < totalPages - 1) coroutineScope.launch {
                                listState.animateScrollToItem(currentPage + 1)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = {
                        showBrightnessPopup = false
                        showZoomPopup = !showZoomPopup
                    }) {
                        Text(
                            "${(zoom * 100).toInt()}%",
                            color = if (showZoomPopup) GoldAccent else Color.White,
                            fontWeight = FontWeight.Medium, fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            showZoomPopup = false
                            showBrightnessPopup = !showBrightnessPopup
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.BrightnessMedium, null, tint = if (showBrightnessPopup) GoldAccent else Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Zoom popup (1.2.38): an on-demand overlay above
                // the bottom bar — it never pushes the page.
                if (showZoomPopup) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 56.dp, start = 12.dp, end = 12.dp)
                            .background(MaroonPrimary, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                zoom = (zoom - 0.5f).coerceAtLeast(1f)
                                if (zoom <= 1.01f) pan = Offset.Zero
                            },
                            enabled = zoom > 1.01f,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ZoomOut, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Slider(
                            value = zoom,
                            onValueChange = {
                                zoom = it
                                if (zoom <= 1.01f) pan = Offset.Zero
                            },
                            valueRange = 1f..4f,
                            modifier = Modifier.width(130.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GoldAccent,
                                activeTrackColor = GoldAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        IconButton(
                            onClick = { zoom = (zoom + 0.5f).coerceAtMost(4f) },
                            enabled = zoom < 4f,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        TextButton(onClick = {
                            zoom = 1f
                            pan = Offset.Zero
                        }) {
                            Text("100%", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Brightness popup (1.2.38): same on-demand shape.
                if (showBrightnessPopup) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 56.dp, start = 12.dp, end = 12.dp)
                            .background(MaroonPrimary, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.BrightnessLow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = 0.3f..1f,
                            modifier = Modifier.width(150.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GoldAccent,
                                activeTrackColor = GoldAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        Icon(Icons.Default.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                if (showBookmarks) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .align(Alignment.CenterEnd)
                            .background(MaroonPrimary, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("الفهارس", color = Color.White, fontWeight = FontWeight.Bold)
                            if (bookmarks.isEmpty()) {
                                Text("لا توجد فهارس", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(bookmarks.sorted()) { page ->
                                        Button(shape = MaterialTheme.shapes.medium,
                                            onClick = {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(page)
                                                }
                                                showBookmarks = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                        ) {
                                            Text("الصفحة ${page + 1}", color = Color.Black, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showSearch) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.TopCenter)
                            .padding(top = 52.dp)
                            .background(MaroonPrimary, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("البحث في الملف...") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    colors = TextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                                IconButton(onClick = { showSearch = false }) {
                                    Icon(Icons.Default.Close, null, tint = Color.White)
                                }
                            }
                            if (textIndex == null) {
                                Text(
                                    "البحث في النصوص يدعم ملفات PDF بطبقة نصية محفوظة.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            } else if (searchQuery.isNotBlank()) {
                                // Hit counter + prev/next walk (1.2.37):
                                // navigation itself is the effect on
                                // currentHitIndex above.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        if (searchHits.isEmpty()) "لا نتائج"
                                        else "${currentHitIndex + 1} من ${searchHits.size} نتيجة",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (searchHits.isNotEmpty()) {
                                                currentHitIndex =
                                                    (currentHitIndex - 1 + searchHits.size) % searchHits.size
                                            }
                                        },
                                        enabled = searchHits.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White)
                                    }
                                    IconButton(
                                        onClick = {
                                            if (searchHits.isNotEmpty()) {
                                                currentHitIndex = (currentHitIndex + 1) % searchHits.size
                                            }
                                        },
                                        enabled = searchHits.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isFullscreen) {
                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Button(
                        onClick = {
                            isFullscreen = false
                            setSystemBarsVisible(true)
                            showBars = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                    ) {
                        Text("خروج من ملء الشاشة", color = Color.White)
                    }
                }
            }

            // Book outline dialog (1.2.37): the PDF's own table of
            // contents, indented by level; a tap jumps and closes.
            if (showOutline) {
                val outline = textIndex?.outline.orEmpty()
                AlertDialog(
                    onDismissRequest = { showOutline = false },
                    title = { Text("فهرس الكتاب") },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(outline) { item ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                listState.scrollToItem(
                                                    item.page.coerceIn(0, totalPages - 1)
                                                )
                                            }
                                            showOutline = false
                                        }
                                        .padding(
                                            start = (item.level * 16).dp,
                                            top = 8.dp, bottom = 8.dp, end = 4.dp
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.title, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text("${item.page + 1}", fontSize = 12.sp, color = MaroonPrimary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showOutline = false }) { Text("إغلاق") }
                    }
                )
            }

            // Text-highlights list (1.2.37): page + snippet per saved
            // highlight, with individual delete (same dialog pattern
            // as the bookmarks panel).
            if (showTextHighlights) {
                AlertDialog(
                    onDismissRequest = { showTextHighlights = false },
                    title = { Text("المظللات النصية") },
                    text = {
                        if (textHighlights.isEmpty()) {
                            Text("لا توجد مظللات نصية بعد", fontSize = 13.sp)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                items(textHighlights) { th ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    listState.scrollToItem(
                                                        th.page.coerceIn(0, totalPages - 1)
                                                    )
                                                }
                                                showTextHighlights = false
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("الصفحة ${th.page + 1}", fontSize = 11.sp, color = MaroonPrimary)
                                            Text(
                                                th.text.take(80),
                                                fontSize = 13.sp,
                                                maxLines = 2
                                            )
                                        }
                                        IconButton(onClick = {
                                            textHighlights = textHighlights - th
                                            saveTextHighlights()
                                        }) {
                                            Icon(
                                                Icons.Default.Delete, null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTextHighlights = false }) { Text("إغلاق") }
                    }
                )
            }

            // Recent books dialog (1.2.37): title + saved page per
            // entry. Tapping the CURRENT book jumps to its saved
            // page; tapping another book delegates to onOpenRecent
            // when the caller wired it, and does nothing otherwise.
            if (showRecents) {
                AlertDialog(
                    onDismissRequest = { showRecents = false },
                    title = { Text("آخر الكتيّبات المفتوحة") },
                    text = {
                        if (recents.isEmpty()) {
                            Text("لا توجد كتيّبات مفتوحة بعد", fontSize = 13.sp)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                items(recents) { recent ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showRecents = false
                                                if (recent.path == pdfFile.absolutePath) {
                                                    coroutineScope.launch {
                                                        listState.scrollToItem(
                                                            recent.page.coerceIn(0, totalPages - 1)
                                                        )
                                                    }
                                                } else {
                                                    onOpenRecent?.invoke(File(recent.path), recent.title)
                                                }
                                            }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(recent.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            "صفحة ${recent.page + 1} من ${recent.totalPages}",
                                            fontSize = 11.sp, color = MaroonPrimary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showRecents = false }) { Text("إغلاق") }
                    }
                )
            }

            // Go-to-page dialog: outside showBars so the auto-hide timer
            // cannot dismiss it while the user types.
            if (showGoToPage) {
                AlertDialog(
                    onDismissRequest = { showGoToPage = false },
                    title = { Text("اذهب إلى صفحة") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = goToPageText,
                                onValueChange = { goToPageText = it.filter { ch -> ch.isDigit() } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = { Text("رقم الصفحة (1-$totalPages)") }
                            )
                            // The full remaining-pages line moved
                            // here from the bottom bar (1.2.38 diet):
                            // the slim bar shows page + percent only.
                            val remainingPages = totalPages - currentPage - 1
                            val remainingText = when {
                                remainingPages <= 0 -> "لا صفحات متبقية"
                                remainingPages == 1 -> "تبقّت صفحة واحدة"
                                remainingPages == 2 -> "تبقّت صفحتان"
                                remainingPages in 3..10 -> "تبقّى $remainingPages صفحات"
                                else -> "تبقّى $remainingPages صفحة"
                            }
                            Text(
                                "أنت في الصفحة ${currentPage + 1} — $remainingText",
                                fontSize = 11.5.sp, color = MaroonPrimary
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            goToPageText.toIntOrNull()?.let { n ->
                                coroutineScope.launch {
                                    listState.scrollToItem((n - 1).coerceIn(0, totalPages - 1))
                                }
                            }
                            showGoToPage = false
                        }) { Text("اذهب") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGoToPage = false }) { Text("إلغاء") }
                    }
                )
            }
        }
    }
}
