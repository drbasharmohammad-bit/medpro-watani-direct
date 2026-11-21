package sy.medpro.watani.ui.library

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sy.medpro.watani.data.BookTextIndex
import sy.medpro.watani.data.PdfTextEngine
import sy.medpro.watani.data.SearchHit
import sy.medpro.watani.data.WordBox
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.GoldAccent
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One highlight stroke on a book page, stored normalized (0..1 across the
 * page) so it survives zoom and screen sizes. Persisted per book in prefs.
 * The reader also supports go-to-page (jump by number) from the indicator.
 */
@JsonClass(generateAdapter = true)
data class HighlightStrokeDto(
    val page: Int,
    val points: List<Float>,
    val color: Int,
    val width: Float
)

/**
 * A shape/text annotation on a book page, normalized (0..1 across the
 * page) like strokes so it survives zoom and screen sizes. Persisted
 * per book in prefs alongside highlights.
 * type: TEXT (tap-placed text box; x1/y1 = origin, text = content),
 * RECT, ELLIPSE, ARROW (drag start x1/y1 -> end x2/y2).
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

/** Hard cap on simultaneously held page bitmaps (build 1.2.37):
 * the cache keeps the current page ±1 (3) plus at most one
 * zoom-resolution replacement of the current page — never more
 * than this many bitmaps alive; eviction always recycles. */
private const val READER_MAX_CACHED_BITMAPS = 4

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(pdfFile: File, title: String, onBack: () -> Unit, onDeleteCorrupt: (() -> Unit)? = null, onFullscreenChange: ((Boolean) -> Unit)? = null, onProgress: ((page: Int, totalPages: Int) -> Unit)? = null, onOpenRecent: ((java.io.File, String) -> Unit)? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var totalPages by remember { mutableStateOf(0) }
    var savedPage by remember { mutableStateOf(0) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(isLandscape) {
        if (isLandscape) showBars = false
    }
    // True fullscreen needs the main Scaffold bars hidden too, so the
    // reader reports its state up (MainActivity hides top/bottom bars).
    // Landscape counts as chrome-hidden for the same reason.
    LaunchedEffect(isFullscreen, isLandscape) {
        onFullscreenChange?.invoke(isFullscreen || isLandscape)
    }
    val prefs = remember { context.getSharedPreferences("watani_library_${getFirebaseUid()}", Context.MODE_PRIVATE) }

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
    var highlightStrokes by remember { mutableStateOf<List<HighlightStrokeDto>>(emptyList()) }
    fun saveHighlights() {
        val adapter = highlightAdapter ?: return
        runCatching {
            prefs.edit().putString(
                "highlights_${pdfFile.nameWithoutExtension}",
                adapter.toJson(highlightStrokes)
            ).apply()
        }
    }

    // Shape/text annotations (outer scope so the open effect restores them).
    val annotationAdapter = remember {
        runCatching {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter<List<PdfAnnotationDto>>(
                    Types.newParameterizedType(List::class.java, PdfAnnotationDto::class.java)
                )
        }.getOrNull()
    }
    var annotations by remember { mutableStateOf<List<PdfAnnotationDto>>(emptyList()) }
    fun saveAnnotations() {
        val adapter = annotationAdapter ?: return
        runCatching {
            prefs.edit().putString(
                "annotations_${pdfFile.nameWithoutExtension}",
                adapter.toJson(annotations)
            ).apply()
        }
    }

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
                "texthighlights_${pdfFile.nameWithoutExtension}",
                adapter.toJson(textHighlights)
            ).apply()
        }
    }

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
    LaunchedEffect(pdfFile) {
        textIndex = PdfTextEngine.load(context, pdfFile)
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
        withContext(Dispatchers.Default) {
            try {
                loadBookmarks()
                try {
                    prefs.getString("highlights_${pdfFile.nameWithoutExtension}", null)?.let {
                        highlightStrokes = highlightAdapter?.fromJson(it) ?: emptyList()
                    }
                } catch (_: Exception) {
                    // Corrupt highlights JSON must never block opening the book.
                }
                try {
                    prefs.getString("annotations_${pdfFile.nameWithoutExtension}", null)?.let {
                        annotations = annotationAdapter?.fromJson(it) ?: emptyList()
                    }
                } catch (_: Exception) {
                    // Corrupt annotations JSON must never block opening the book.
                }
                try {
                    prefs.getString("texthighlights_${pdfFile.nameWithoutExtension}", null)?.let {
                        textHighlights = textHighlightAdapter?.fromJson(it) ?: emptyList()
                    }
                } catch (_: Exception) {
                    // Corrupt text-highlight JSON must never block opening the book.
                }
                // Recents load (armored inside loadRecents itself).
                loadRecents()
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                parcelFd = fd
                pdfRenderer = renderer
                totalPages = renderer.pageCount
                savedPage = prefs.getInt("last_page_${pdfFile.nameWithoutExtension}", 0)
                    .coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                // Successful open: this book becomes the most recent.
                recordRecent(savedPage, totalPages)
                isLoading = false
            } catch (e: Exception) {
                error = "تعذر فتح الملف: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    LaunchedEffect(showBars) {
        if (showBars) scheduleHideBar()
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
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { parcelFd?.close() } catch (_: Exception) {}
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaroonPrimary)
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
            val pagerState = rememberPagerState(
                initialPage = savedPage,
                pageCount = { totalPages }
            )
            val currentPage = pagerState.currentPage
            var zoom by remember { mutableStateOf(1f) }
            var pan by remember { mutableStateOf(Offset.Zero) }
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            var sliderPos by remember { mutableStateOf<Float?>(null) }
            val bitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }
            // PdfRenderer is NOT thread-safe: every openPage/render/
            // close sequence (base renders + zoom re-renders) is
            // serialized on this lock, and every opened Page handle
            // is closed in a finally block (1.2.37 hardening).
            val renderLock = remember { Any() }

            /** Renders one page at [scaleFactor] x its point size on
             * the Default dispatcher; null on any failure. The caller
             * owns the returned bitmap (cache it or recycle it). */
            suspend fun renderPageBitmap(page: Int, scaleFactor: Float): Bitmap? {
                val renderer = pdfRenderer ?: return null
                return withContext(Dispatchers.Default) {
                    synchronized(renderLock) {
                        try {
                            val pg = renderer.openPage(page)
                            try {
                                val bmp = Bitmap.createBitmap(
                                    (pg.width * scaleFactor).toInt().coerceAtLeast(1),
                                    (pg.height * scaleFactor).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                // Fill white first: some PDFs leave the page
                                // background transparent, which showed as black.
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            } finally {
                                try { pg.close() } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }

            // One undo history for both marking kinds (strokes and
            // shape/text annotations): the student sees one kind of
            // marking, so there is one undo, not two. Entries are
            // (kind, page); session-only — reopening the book clears
            // undo history, the markings themselves stay saved.
            // Declared BEFORE undoLastMark() below: a local function
            // can only capture state declared earlier in the block.
            var markHistory by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

            /** Unified undo (1.2.37): removes the most recent marking
             * added on the CURRENT page across all three marking
             * groups (freehand stroke / shape-text annotation / text
             * highlight), driven by the shared markHistory stack, and
             * persists the affected group immediately. */
            fun undoLastMark() {
                val idx = markHistory.indexOfLast { (_, p) -> p == currentPage }
                if (idx < 0) return
                val (kind, _) = markHistory[idx]
                markHistory = markHistory.toMutableList().also { it.removeAt(idx) }
                when (kind) {
                    "stroke" -> {
                        val sIdx = highlightStrokes.indexOfLast { it.page == currentPage }
                        if (sIdx >= 0) {
                            highlightStrokes = highlightStrokes.toMutableList().also { it.removeAt(sIdx) }
                            saveHighlights()
                        }
                    }
                    "annotation" -> {
                        val aIdx = annotations.indexOfLast { it.page == currentPage }
                        if (aIdx >= 0) {
                            annotations = annotations.toMutableList().also { it.removeAt(aIdx) }
                            saveAnnotations()
                        }
                    }
                    "texthighlight" -> {
                        val tIdx = textHighlights.indexOfLast { it.page == currentPage }
                        if (tIdx >= 0) {
                            textHighlights = textHighlights.toMutableList().also { it.removeAt(tIdx) }
                            saveTextHighlights()
                        }
                    }
                }
            }

            // Highlighter UI state (strokes live in the outer scope).
            var highlightMode by remember { mutableStateOf(false) }
            var highlightColor by remember { mutableStateOf(Color.Yellow) }
            // Annotation tool inside highlight mode: FREEHAND (strokes),
            // TEXT (tap places a text box), RECT, ELLIPSE, ARROW (drag).
            var annotationTool by remember { mutableStateOf("FREEHAND") }
            var pendingTextAt by remember { mutableStateOf<Offset?>(null) }
            var showTextEditor by remember { mutableStateOf(false) }
            var paintingPoints by remember { mutableStateOf<List<Offset>?>(null) }
            var showRecents by remember { mutableStateOf(false) }
            var showGoToPage by remember { mutableStateOf(false) }
            var goToPageText by remember { mutableStateOf("") }

            // Text-layer UI state (1.2.37). Search hits are recomputed
            // (debounced) from the text index; currentHitIndex is the
            // hit prev/next navigation walks and the one drawn strong.
            var searchHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
            var currentHitIndex by remember { mutableStateOf(-1) }
            // Text-highlight selection: a drag at 1x only (see the drag
            // handler) — the live rect is kept normalized like strokes.
            var textHighlightMode by remember { mutableStateOf(false) }
            var selectionStart by remember { mutableStateOf<Offset?>(null) }
            var selectionEnd by remember { mutableStateOf<Offset?>(null) }
            var showTextHighlights by remember { mutableStateOf(false) }
            var showOutline by remember { mutableStateOf(false) }

            // While highlighting, the bars (which hold the off switch) must
            // never auto-hide — losing the off switch mid-stroke is what
            // made stopping highlight mode feel difficult.
            LaunchedEffect(highlightMode) {
                if (highlightMode) {
                    hideBarJob?.cancel()
                    showBars = true
                }
            }

            // System Back leaves highlight mode first, then fullscreen,
            // then the book — one predictable chain. (The fullscreen
            // BackHandler above is composed earlier, so this later one
            // wins while the mode is on.)
            BackHandler(enabled = highlightMode) {
                highlightMode = false
            }

            // Touch point -> page-normalized point (0..1 across the fitted
            // page rect), so strokes survive zoom and screen sizes.
            fun toNormalized(px: Offset, bmp: Bitmap): Offset {
                val fit = minOf(boxSize.width.toFloat() / bmp.width, boxSize.height.toFloat() / bmp.height)
                val fittedW = bmp.width * fit
                val fittedH = bmp.height * fit
                val offX = (boxSize.width - fittedW) / 2f
                val offY = (boxSize.height - fittedH) / 2f
                return Offset(
                    ((px.x - offX) / fittedW).coerceIn(0f, 1f),
                    ((px.y - offY) / fittedH).coerceIn(0f, 1f)
                )
            }

            // Save the reading position and reset zoom on every page turn.
            LaunchedEffect(currentPage) {
                zoom = 1f
                pan = Offset.Zero
                prefs.edit().putInt("last_page_${pdfFile.nameWithoutExtension}", currentPage).apply()
                // The recents entry tracks the live page too (1.2.37).
                updateRecentPage(currentPage)
            }

            // Reading progress for the caller (library booklet cards):
            // snapshotFlow emits the saved start page on collection —
            // that is the on-open report — then every page turn.
            LaunchedEffect(pagerState, totalPages) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
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

            // Prev/next (and the initial landing) scroll to the
            // current hit's page.
            LaunchedEffect(currentHitIndex, searchHits) {
                val hit = searchHits.getOrNull(currentHitIndex)
                if (hit != null && hit.page != pagerState.currentPage) {
                    pagerState.animateScrollToPage(hit.page)
                }
            }

            // Like highlight mode, text-highlight mode keeps its off
            // switch (the bars) on screen while it is active.
            LaunchedEffect(textHighlightMode) {
                if (textHighlightMode) {
                    hideBarJob?.cancel()
                    showBars = true
                }
            }
            BackHandler(enabled = textHighlightMode) {
                textHighlightMode = false
                selectionStart = null
                selectionEnd = null
            }

            // Keep only the current page ±1 rendered; recycle evicted bitmaps.
            LaunchedEffect(currentPage) {
                val keep = (currentPage - 1..currentPage + 1).toSet()
                bitmapCache.keys.filter { it !in keep }.forEach { key ->
                    bitmapCache.remove(key)?.recycle()
                }
                // Explicit memory ceiling (READER_MAX_CACHED_BITMAPS):
                // ±1 is 3 by construction, so this guard only fires
                // if a future change widens the window — evict the
                // pages farthest from the current one, recycling each.
                while (bitmapCache.size > READER_MAX_CACHED_BITMAPS) {
                    val farthest = bitmapCache.keys.maxByOrNull { abs(it - currentPage) } ?: break
                    bitmapCache.remove(farthest)?.recycle()
                }
            }

            // Zoom-resolution rendering (1.2.37): the settled zoom
            // (debounced ~250ms) drives ONE re-render of the CURRENT
            // page at base x min(zoom, 2.0); neighbours stay at base
            // resolution. Settling back to 1x restores the base render.
            var settledRenderScale by remember { mutableStateOf(2f) }
            var hiResPage by remember { mutableStateOf(-1) }
            var hiResScale by remember { mutableStateOf(2f) }
            LaunchedEffect(zoom) {
                kotlinx.coroutines.delay(250)
                settledRenderScale = 2f * min(zoom, 2f).coerceAtLeast(1f)
            }
            LaunchedEffect(currentPage, settledRenderScale) {
                val renderer = pdfRenderer ?: return@LaunchedEffect
                if (settledRenderScale > 2.01f) {
                    if (hiResPage != currentPage || hiResScale != settledRenderScale) {
                        val bmp = renderPageBitmap(currentPage, settledRenderScale)
                        if (bmp != null) {
                            val old = bitmapCache[currentPage]
                            bitmapCache[currentPage] = bmp
                            if (old != null && old !== bmp) old.recycle()
                            hiResPage = currentPage
                            hiResScale = settledRenderScale
                        }
                    }
                } else if (hiResPage == currentPage) {
                    // Back at 1x: swap the hi-res bitmap for the base one.
                    val bmp = renderPageBitmap(currentPage, 2f)
                    if (bmp != null) {
                        val old = bitmapCache[currentPage]
                        bitmapCache[currentPage] = bmp
                        if (old != null && old !== bmp) old.recycle()
                    }
                    hiResPage = -1
                    hiResScale = 2f
                }
            }
            // A page turn invalidates the hi-res marker: the eviction
            // above already recycled that bitmap, and a stale marker
            // would wrongly skip a later hi-res render of this page.
            LaunchedEffect(currentPage) {
                hiResPage = -1
                hiResScale = 2f
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

            HorizontalPager(
                state = pagerState,
                // While zoomed, horizontal drags pan the page instead of
                // flipping it; in highlight mode they paint instead of
                // flipping; at 1x they always flip.
                userScrollEnabled = zoom <= 1.01f && !highlightMode && !textHighlightMode,
                modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }
            ) { page ->
                val bitmap = bitmapCache[page]
                LaunchedEffect(page, pdfRenderer) {
                    if (bitmapCache[page] == null && pdfRenderer != null) {
                        // Base resolution (x2) through the shared
                        // serialized renderer; zoom re-renders of the
                        // current page swap this entry at higher scale.
                        renderPageBitmap(page, 2f)?.let { bitmapCache[page] = it }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        val isCurrent = page == currentPage
                        // Pan is clamped so the page cannot be dragged
                        // off-screen: max pan is half the overflow on
                        // each axis.
                        fun clampPan(p: Offset, z: Float): Offset {
                            val fit = minOf(
                                boxSize.width.toFloat() / bitmap.width,
                                boxSize.height.toFloat() / bitmap.height
                            )
                            val dispW = bitmap.width * fit * z
                            val dispH = bitmap.height * fit * z
                            val maxX = max(0f, (dispW - boxSize.width) / 2f)
                            val maxY = max(0f, (dispH - boxSize.height) / 2f)
                            return Offset(
                                p.x.coerceIn(-maxX, maxX),
                                p.y.coerceIn(-maxY, maxY)
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                // White page background: PdfRenderer does not clear
                                // the bitmap, so pages with a transparent background
                                // rendered as black text on the black canvas. The
                                // page area is always white now, whatever the PDF.
                                // Display mode: the letterbox follows
                                // the page — black in night mode, warm
                                // paper in warm mode, white otherwise;
                                // the outer canvas stays black as always.
                                .background(
                                    when {
                                        nightMode -> Color.Black
                                        warmMode -> Color(0xFFF3E5C8)
                                        else -> Color.White
                                    }
                                )
                                .graphicsLayer(
                                    scaleX = if (isCurrent) zoom else 1f,
                                    scaleY = if (isCurrent) zoom else 1f,
                                    translationX = if (isCurrent) pan.x else 0f,
                                    translationY = if (isCurrent) pan.y else 0f
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { tapOff ->
                                            // Text tool: tap places a text box.
                                            // Any other tap in highlight mode
                                            // does nothing on purpose: an
                                            // accidental tap must never kill
                                            // the mode mid-marking. Exit is
                                            // only the finish button, the
                                            // brush toggle, or Back.
                                            if (highlightMode && annotationTool == "TEXT") {
                                                pendingTextAt = toNormalized(tapOff, bitmap)
                                                showTextEditor = true
                                            } else if (textHighlightMode) {
                                                // Selection is drag-only: a
                                                // tap must not toggle the
                                                // bars out from under it.
                                            } else if (!highlightMode) {
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
                                    if (isCurrent && highlightMode) {
                                        Modifier.pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { off ->
                                                    // TEXT comes only from taps:
                                                    // a drag in TEXT mode paints
                                                    // nothing and stores nothing
                                                    // (a stroke preview that
                                                    // vanished unsaved was a trap).
                                                    if (annotationTool != "TEXT") {
                                                        paintingPoints = listOf(toNormalized(off, bitmap))
                                                    }
                                                },
                                                onDrag = { change, _ ->
                                                    if (annotationTool != "TEXT") {
                                                        paintingPoints = (paintingPoints ?: emptyList()) +
                                                            toNormalized(change.position, bitmap)
                                                    }
                                                },
                                                onDragEnd = {
                                                    val pts = paintingPoints
                                                    paintingPoints = null
                                                    if (pts != null && pts.size >= 2) {
                                                        if (annotationTool == "FREEHAND") {
                                                            highlightStrokes = highlightStrokes + HighlightStrokeDto(
                                                                page = page,
                                                                points = pts.flatMap { listOf(it.x, it.y) },
                                                                color = highlightColor.toArgb(),
                                                                width = 0.035f
                                                            )
                                                            saveHighlights()
                                                            markHistory = markHistory + ("stroke" to page)
                                                        } else if (annotationTool != "TEXT") {
                                                            // Shape tools: drag start -> end.
                                                            // TEXT comes only from taps (dialog),
                                                            // never from a drag, so a drag in
                                                            // TEXT mode must not store anything.
                                                            val a = pts.first()
                                                            val b = pts.last()
                                                            if (a != b) {
                                                                annotations = annotations + PdfAnnotationDto(
                                                                    page = page,
                                                                    type = annotationTool,
                                                                    x1 = a.x, y1 = a.y, x2 = b.x, y2 = b.y,
                                                                    text = "",
                                                                    color = highlightColor.toArgb()
                                                                )
                                                                saveAnnotations()
                                                                markHistory = markHistory + ("annotation" to page)
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragCancel = { paintingPoints = null }
                                            )
                                        }
                                    } else Modifier
                                )
                                .then(
                                    if (isCurrent && textHighlightMode && zoom <= 1.01f) {
                                        Modifier.pointerInput(Unit) {
                                            // Text selection drag (1.2.37).
                                            // LIMITATION, by design: active
                                            // ONLY at zoom 1f. While zoomed
                                            // the drag keeps its old job —
                                            // panning (the handler below) —
                                            // because a normalized selection
                                            // rect is only unambiguous
                                            // against the untransformed page.
                                            detectDragGestures(
                                                onDragStart = { off ->
                                                    val n = toNormalized(off, bitmap)
                                                    selectionStart = n
                                                    selectionEnd = n
                                                },
                                                onDrag = { change, _ ->
                                                    selectionEnd = toNormalized(change.position, bitmap)
                                                },
                                                onDragEnd = {
                                                    val s = selectionStart
                                                    val e = selectionEnd
                                                    selectionStart = null
                                                    selectionEnd = null
                                                    if (s != null && e != null) {
                                                        val left = minOf(s.x, e.x)
                                                        val right = maxOf(s.x, e.x)
                                                        val top = minOf(s.y, e.y)
                                                        val bottom = maxOf(s.y, e.y)
                                                        val pageWords = textIndex
                                                            ?.pages?.firstOrNull { it.page == page }
                                                            ?.words.orEmpty()
                                                        val matched = pageWords.filter { w ->
                                                            w.x1 > left && w.x0 < right &&
                                                                w.y1 > top && w.y0 < bottom
                                                        }
                                                        if (matched.isNotEmpty()) {
                                                            textHighlights = textHighlights + TextHighlightDto(
                                                                page = page,
                                                                rects = matched,
                                                                text = matched.joinToString(" ") { it.text }
                                                            )
                                                            saveTextHighlights()
                                                            // Unified undo stack (1.2.37): text
                                                            // highlights join strokes/annotations.
                                                            markHistory = markHistory + ("texthighlight" to page)
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    selectionStart = null
                                                    selectionEnd = null
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                                .then(
                                    if (isCurrent && !highlightMode && !textHighlightMode) {
                                        Modifier.pointerInput(Unit) {
                                            // Pinch/pan that never steals
                                            // single-finger swipes at 1x
                                            // (Nov 2026 fix): the old
                                            // detectTransformGestures
                                            // consumed every drag, so the
                                            // pager never received page
                                            // swipes and students were
                                            // forced onto the arrow
                                            // buttons. Here only
                                            // multi-touch (pinch) and
                                            // drags while zoomed are
                                            // consumed; a single finger
                                            // at 1x passes straight
                                            // through to the pager.
                                            var prev = mapOf<Long, Offset>()
                                            awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val down = event.changes.filter { it.pressed }
                                                if (down.size >= 2) {
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
                                                                zoom = (zoom * scale).coerceIn(1f, 4f)
                                                                pan = if (zoom <= 1.01f) Offset.Zero else clampPan(pan + (newC - oldC), zoom)
                                                                down.forEach { it.consume() }
                                                            }
                                                        }
                                                    }
                                                    prev = cur
                                                } else if (down.size == 1 && zoom > 1.01f) {
                                                    val c = down[0]
                                                    val p = prev[c.id.value]
                                                    if (p != null && c.position != p) {
                                                        pan = clampPan(pan + (c.position - p), zoom)
                                                        c.consume()
                                                    }
                                                    prev = mapOf(c.id.value to c.position)
                                                } else {
                                                    // Single finger at 1x:
                                                    // consume nothing —
                                                    // the pager swipes.
                                                    prev = down.associate { it.id.value to it.position }
                                                }
                                                if (down.isEmpty()) prev = emptyMap()
                                            }
                                            }
                                        }
                                    } else Modifier
                                )
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                // Display-mode filters apply ONLY to
                                // the page image (inversion at night,
                                // sepia when warm); markings drawn over
                                // it (the Canvas below) are never filtered.
                                colorFilter = pageColorFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Highlighter layer: strokes drawn over the page,
                            // inside the same zoom/pan transform as the image.
                            val textMeasurer = rememberTextMeasurer()
                            Canvas(Modifier.fillMaxSize()) {
                                val fit = minOf(size.width / bitmap.width, size.height / bitmap.height)
                                val fittedW = bitmap.width * fit
                                val fittedH = bitmap.height * fit
                                val offX = (size.width - fittedW) / 2f
                                val offY = (size.height - fittedH) / 2f
                                fun drawStroke(pts: List<Offset>, argb: Int, wFrac: Float) {
                                    if (pts.size < 2) return
                                    val path = Path().apply {
                                        moveTo(offX + pts[0].x * fittedW, offY + pts[0].y * fittedH)
                                        pts.drop(1).forEach {
                                            lineTo(offX + it.x * fittedW, offY + it.y * fittedH)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(argb),
                                        style = Stroke(width = wFrac * fittedW, cap = StrokeCap.Round),
                                        alpha = 0.45f
                                    )
                                }
                                // Text-layer markings (1.2.37), drawn in
                                // this same fitted-page transform (offX/
                                // offY/fittedW/fittedH) and UNDER the
                                // freehand layer: saved text highlights
                                // first, then this page's search hits
                                // (the current hit stronger + outlined),
                                // then the live selection rect.
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
                                if (isCurrent) {
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
                                highlightStrokes.filter { it.page == page }.forEach { s ->
                                    val pts = s.points.chunked(2).map { Offset(it[0], it[1]) }
                                    drawStroke(pts, s.color, s.width)
                                }
                                if (isCurrent) paintingPoints?.let {
                                    drawStroke(it, highlightColor.toArgb(), 0.035f)
                                }
                                // Shape/text annotations on this page + the
                                // in-progress shape preview while dragging.
                                fun drawShape(type: String, ax1: Float, ay1: Float, ax2: Float, ay2: Float, col: Color, strokeW: Float) {
                                    val left = minOf(ax1, ax2)
                                    val top = minOf(ay1, ay2)
                                    val w = abs(ax2 - ax1)
                                    val h = abs(ay2 - ay1)
                                    when (type) {
                                        "RECT" -> {
                                            drawRect(color = col.copy(alpha = 0.15f), topLeft = Offset(left, top), size = Size(w, h))
                                            drawRect(color = col, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = strokeW))
                                        }
                                        "ELLIPSE" -> {
                                            drawOval(color = col.copy(alpha = 0.15f), topLeft = Offset(left, top), size = Size(w, h))
                                            drawOval(color = col, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = strokeW))
                                        }
                                        "ARROW" -> {
                                            drawLine(color = col, start = Offset(ax1, ay1), end = Offset(ax2, ay2), strokeWidth = strokeW, cap = StrokeCap.Round)
                                            val ang = kotlin.math.atan2(ay2 - ay1, ax2 - ax1)
                                            val head = 22f
                                            drawLine(color = col, start = Offset(ax2, ay2), end = Offset(ax2 - head * kotlin.math.cos(ang - 0.45f), ay2 - head * kotlin.math.sin(ang - 0.45f)), strokeWidth = strokeW, cap = StrokeCap.Round)
                                            drawLine(color = col, start = Offset(ax2, ay2), end = Offset(ax2 - head * kotlin.math.cos(ang + 0.45f), ay2 - head * kotlin.math.sin(ang + 0.45f)), strokeWidth = strokeW, cap = StrokeCap.Round)
                                        }
                                    }
                                }
                                annotations.filter { it.page == page }.forEach { ann ->
                                    val ax1 = offX + ann.x1 * fittedW
                                    val ay1 = offY + ann.y1 * fittedH
                                    val ax2 = offX + ann.x2 * fittedW
                                    val ay2 = offY + ann.y2 * fittedH
                                    val col = Color(ann.color)
                                    if (ann.type == "TEXT") {
                                        val layout = textMeasurer.measure(
                                            text = AnnotatedString(ann.text),
                                            style = TextStyle(fontSize = 15.sp, color = Color.Black),
                                            constraints = Constraints(maxWidth = (fittedW * 0.7f).toInt().coerceAtLeast(100))
                                        )
                                        val pad = 8f
                                        val bw = layout.size.width + pad * 2
                                        val bh = layout.size.height + pad * 2
                                        drawRoundRect(color = Color.White.copy(alpha = 0.85f), topLeft = Offset(ax1, ay1), size = Size(bw, bh), cornerRadius = CornerRadius(10f, 10f))
                                        drawRoundRect(color = col, topLeft = Offset(ax1, ay1), size = Size(bw, bh), cornerRadius = CornerRadius(10f, 10f), style = Stroke(width = 2f))
                                        drawText(layout, topLeft = Offset(ax1 + pad, ay1 + pad))
                                    } else {
                                        drawShape(ann.type, ax1, ay1, ax2, ay2, col, 3f)
                                    }
                                }
                                if (isCurrent && annotationTool != "FREEHAND" && annotationTool != "TEXT") {
                                    paintingPoints?.let { pts ->
                                        if (pts.size >= 2) {
                                            val a = pts.first()
                                            val b = pts.last()
                                            drawShape(
                                                annotationTool,
                                                offX + a.x * fittedW, offY + a.y * fittedH,
                                                offX + b.x * fittedW, offY + b.y * fittedH,
                                                highlightColor, 3f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaroonPrimary,
                            strokeWidth = 3.dp
                        )
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
                TopAppBar(
                    title = { Text(title, maxLines = 1, color = Color.White) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                    actions = {
                        IconButton(onClick = { toggleBookmark(currentPage) }) {
                            Icon(Icons.Default.Bookmark, null, tint = if (currentPage in bookmarks) GoldAccent else Color.White)
                        }
                        IconButton(onClick = {
                            highlightMode = !highlightMode
                            if (highlightMode) textHighlightMode = false
                        }) {
                            Icon(Icons.Default.Brush, null, tint = if (highlightMode) GoldAccent else Color.White)
                        }
                        // Text highlighting exists only with a text
                        // index; without one the button stays absent.
                        if (textIndex != null) {
                            IconButton(onClick = {
                                textHighlightMode = !textHighlightMode
                                if (textHighlightMode) highlightMode = false
                                if (!textHighlightMode) {
                                    selectionStart = null
                                    selectionEnd = null
                                }
                            }) {
                                Icon(Icons.Default.BorderColor, null, tint = if (textHighlightMode) GoldAccent else Color.White)
                            }
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
                        }
                        // One button cycles the three display modes;
                        // the icon names the CURRENT mode (normal /
                        // warm paper / night).
                        IconButton(onClick = { cycleDisplayMode() }) {
                            Icon(
                                when (displayMode) {
                                    "WARM" -> Icons.Default.WbSunny
                                    "NIGHT" -> Icons.Default.DarkMode
                                    else -> Icons.Default.LightMode
                                },
                                null, tint = if (displayMode == "NORMAL") Color.White else GoldAccent
                            )
                        }
                        IconButton(onClick = { showRecents = true }) {
                            Icon(Icons.Default.History, null, tint = Color.White)
                        }
                        if (!textIndex?.outline.isNullOrEmpty()) {
                            IconButton(onClick = { showOutline = true }) {
                                Icon(Icons.Default.List, null, tint = Color.White)
                            }
                        }
                        TextButton(onClick = {
                            isFullscreen = true
                            setSystemBarsVisible(false)
                            showBars = false
                        }) {
                            Text("ملء الشاشة", color = Color.White, fontSize = 12.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary)
                )

                // Highlighter panel: one finish button, tools, colors,
                // one undo, one clear — a single markings model, not
                // two parallel ones. Three rows so the page keeps
                // as much height as possible.
                if (highlightMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                            .background(MaroonPrimary.copy(alpha = 0.9f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (annotationTool) {
                                    "TEXT" -> "اضغط على الصفحة لوضع مربع نص"
                                    "FREEHAND" -> "اسحب للتأشير"
                                    else -> "اسحب لرسم الشكل"
                                },
                                color = Color.White, fontSize = 12.sp
                            )
                            TextButton(onClick = { highlightMode = false }) {
                                Text("إنهاء التأشير ✓", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Annotation tools: freehand strokes plus text boxes
                        // and shapes, all saved per book like highlights.
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "FREEHAND" to "تأشير",
                                "TEXT" to "مربع نص",
                                "RECT" to "مستطيل",
                                "ELLIPSE" to "بيضوي",
                                "ARROW" to "سهم"
                            ).forEach { (tool, label) ->
                                TextButton(onClick = { annotationTool = tool }) {
                                    Text(
                                        label,
                                        color = if (annotationTool == tool) GoldAccent else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (annotationTool == tool) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Color.Yellow,
                                    Color(0xFFFF8FA3),
                                    Color(0xFF90EE90),
                                    Color(0xFF87CEEB)
                                ).forEach { c ->
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(c, CircleShape)
                                            .border(
                                                width = if (highlightColor == c) 2.dp else 0.dp,
                                                color = GoldAccent,
                                                shape = CircleShape
                                            )
                                            .clickable { highlightColor = c }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // One undo for all three marking kinds
                                // (1.2.37): the shared undoLastMark()
                                // pops the newest marking on this page —
                                // stroke, shape/text, or text highlight —
                                // and saves its group immediately.
                                val hasPageMark = markHistory.any { (_, p) -> p == currentPage }
                                TextButton(onClick = { undoLastMark() }) {
                                    Text(
                                        "تراجع",
                                        color = Color.White.copy(alpha = if (hasPageMark) 1f else 0.4f),
                                        fontSize = 12.sp
                                    )
                                }
                                TextButton(onClick = {
                                    highlightStrokes = highlightStrokes.filter { it.page != currentPage }
                                    saveHighlights()
                                    annotations = annotations.filter { it.page != currentPage }
                                    saveAnnotations()
                                    markHistory = markHistory.filter { (_, p) -> p != currentPage }
                                }) {
                                    Text("مسح العلامات", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Text-highlight panel (1.2.37): the mode's hint, the
                // saved-highlights list entry, and its off switch —
                // mirroring the freehand panel's shape.
                if (textHighlightMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                            .background(MaroonPrimary.copy(alpha = 0.9f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (zoom <= 1.01f) "اسحب فوق النص لتظليله" else "صغّر إلى 100% لتظليل النص",
                                color = Color.White, fontSize = 12.sp
                            )
                            Row {
                                TextButton(onClick = { showTextHighlights = true }) {
                                    Text("المظللات (${textHighlights.size})", color = Color.White, fontSize = 12.sp)
                                }
                                // Same unified undo as the freehand
                                // panel: pops this page's newest marking
                                // (here: the last text highlight).
                                TextButton(onClick = { undoLastMark() }) {
                                    Text(
                                        "تراجع",
                                        color = Color.White.copy(
                                            alpha = if (markHistory.any { (_, p) -> p == currentPage }) 1f else 0.4f
                                        ),
                                        fontSize = 12.sp
                                    )
                                }
                                TextButton(onClick = {
                                    textHighlightMode = false
                                    selectionStart = null
                                    selectionEnd = null
                                }) {
                                    Text("إنهاء ✓", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaroonPrimary.copy(alpha = 0.9f))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (currentPage > 0) coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentPage - 1)
                                }
                            }) {
                                Icon(Icons.Default.ChevronLeft, null, tint = Color.White)
                            }
                            TextButton(onClick = {
                                goToPageText = (currentPage + 1).toString()
                                showGoToPage = true
                            }) {
                                // Full progress label (1.2.37): page X
                                // of Y, the percentage, and how many
                                // pages remain (Arabic plural rules).
                                val remainingPages = totalPages - currentPage - 1
                                val remainingText = when {
                                    remainingPages <= 0 -> "لا صفحات متبقية"
                                    remainingPages == 1 -> "تبقّت صفحة واحدة"
                                    remainingPages == 2 -> "تبقّت صفحتان"
                                    remainingPages in 3..10 -> "تبقّى $remainingPages صفحات"
                                    else -> "تبقّى $remainingPages صفحة"
                                }
                                Text(
                                    "صفحة ${currentPage + 1} من $totalPages (${((currentPage + 1).toFloat() / totalPages * 100).toInt()}%) — $remainingText",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = {
                                if (currentPage < totalPages - 1) coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            }) {
                                Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                            }
                        }

                        // Page slider: drag to jump anywhere in the book.
                        if (totalPages > 1) {
                            Slider(
                                value = sliderPos ?: currentPage.toFloat(),
                                onValueChange = { sliderPos = it },
                                onValueChangeFinished = {
                                    sliderPos?.let { target ->
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(target.roundToInt().coerceIn(0, totalPages - 1))
                                        }
                                    }
                                    sliderPos = null
                                },
                                valueRange = 0f..(totalPages - 1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldAccent,
                                    activeTrackColor = GoldAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    zoom = (zoom - 0.5f).coerceAtLeast(1f)
                                    if (zoom <= 1.01f) pan = Offset.Zero
                                },
                                enabled = zoom > 1.01f
                            ) {
                                Icon(Icons.Default.ZoomOut, null, tint = Color.White)
                            }
                            TextButton(onClick = {
                                zoom = 1f
                                pan = Offset.Zero
                            }) {
                                Text(
                                    "${(zoom * 100).toInt()}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { zoom = (zoom + 0.5f).coerceAtMost(4f) },
                                enabled = zoom < 4f
                            ) {
                                Icon(Icons.Default.ZoomIn, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.BrightnessLow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                valueRange = 0.3f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldAccent,
                                    activeTrackColor = GoldAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Icon(Icons.Default.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
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
                                                    pagerState.animateScrollToPage(page)
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

            // Text-box editor: tap in TEXT mode opens it at the tap point.
            if (showTextEditor) {
                var textValue by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showTextEditor = false; pendingTextAt = null },
                    title = { Text("مربع نص") },
                    text = {
                        TextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            placeholder = { Text("اكتب النص...") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val at = pendingTextAt
                            if (at != null && textValue.isNotBlank()) {
                                annotations = annotations + PdfAnnotationDto(
                                    page = currentPage, type = "TEXT",
                                    x1 = at.x, y1 = at.y, x2 = at.x, y2 = at.y,
                                    text = textValue.trim(), color = highlightColor.toArgb()
                                )
                                saveAnnotations()
                                markHistory = markHistory + ("annotation" to currentPage)
                            }
                            showTextEditor = false
                            pendingTextAt = null
                        }) { Text("إضافة") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTextEditor = false; pendingTextAt = null }) { Text("إلغاء") }
                    }
                )
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
                                                pagerState.scrollToPage(
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
                                                    pagerState.scrollToPage(
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
                                                        pagerState.scrollToPage(
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
                        TextField(
                            value = goToPageText,
                            onValueChange = { goToPageText = it.filter { ch -> ch.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            placeholder = { Text("رقم الصفحة (1-$totalPages)") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            goToPageText.toIntOrNull()?.let { n ->
                                coroutineScope.launch {
                                    pagerState.scrollToPage((n - 1).coerceIn(0, totalPages - 1))
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
