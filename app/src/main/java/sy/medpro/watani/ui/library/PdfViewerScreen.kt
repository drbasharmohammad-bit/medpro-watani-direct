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

/** Hard cap on simultaneously held page bitmaps (build 1.2.37):
 * the cache keeps the current page ±1 (3) plus at most one
 * zoom-resolution replacement of the current page — never more
 * than this many bitmaps alive; eviction always recycles. */
private const val READER_MAX_CACHED_BITMAPS = 4

/** Settle delay before a zoom level earns its hi-res re-render
 * (1.2.46): pinch movements stream zoom values continuously, and
 * only the level the student actually stops at is rendered. */
private const val READER_HIRES_DEBOUNCE_MS = 250L

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
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
                "texthighlights_${pdfFile.nameWithoutExtension}",
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
        withContext(Dispatchers.Default) {
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

            /** Renders one page on the Default dispatcher; null on
             * any failure. The caller owns the returned bitmap
             * (cache it or recycle it). With [targetWidthPx] > 0 the
             * render scale is derived from it via [ReaderRenderMath]
             * (display-matched resolution, 1.2.46) under the
             * [maxPixels] budget; otherwise the page renders at
             * [scaleFactor] x its point size (the legacy path). */
            suspend fun renderPageBitmap(
                page: Int,
                scaleFactor: Float,
                targetWidthPx: Int = 0,
                maxPixels: Int = 0
            ): Bitmap? {
                val renderer = pdfRenderer ?: return null
                return withContext(Dispatchers.Default) {
                    synchronized(renderLock) {
                        try {
                            val pg = renderer.openPage(page)
                            try {
                                val pixelBudget =
                                    if (maxPixels > 0) maxPixels else ReaderRenderMath.BASE_MAX_PIXELS
                                val renderScale = if (targetWidthPx > 0) {
                                    ReaderRenderMath.scaleForTargetWidth(
                                        pg.width, pg.height, targetWidthPx, pixelBudget
                                    )
                                } else {
                                    scaleFactor
                                }
                                val bmp = Bitmap.createBitmap(
                                    (pg.width * renderScale).toInt().coerceAtLeast(1),
                                    (pg.height * renderScale).toInt().coerceAtLeast(1),
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
                saveTextHighlights()
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

            // System Back leaves the text-highlight mode first (via
            // the single exit function), then fullscreen, then the
            // book — one predictable chain. (The fullscreen
            // BackHandler in the outer scope is composed earlier, so
            // this later one wins while the mode is on.)
            BackHandler(enabled = textHighlightMode) {
                exitTextHighlightMode()
            }

            // Eviction runs ONLY once the pager has settled
            // (1.2.46): the old per-page-turn eviction recycled
            // bitmaps the pager was still animating away from — the
            // outgoing page lost its image mid-swipe and flashed a
            // spinner, and a fast fling back re-rendered from
            // scratch. The keep window is the settled page ±1 plus
            // the pager's own target page, and the explicit memory
            // ceiling (READER_MAX_CACHED_BITMAPS) still applies:
            // evict the pages farthest from the settled one,
            // recycling each.
            LaunchedEffect(pagerState, totalPages) {
                snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
                    if (!scrolling) {
                        val settled = pagerState.currentPage
                        val keep = (settled - 1..settled + 1).toSet() + pagerState.targetPage
                        bitmapCache.keys.filter { it !in keep }.forEach { key ->
                            bitmapCache.remove(key)?.recycle()
                        }
                        while (bitmapCache.size > READER_MAX_CACHED_BITMAPS) {
                            val farthest = bitmapCache.keys.maxByOrNull { abs(it - settled) } ?: break
                            bitmapCache.remove(farthest)?.recycle()
                        }
                    }
                }
            }

            // Zoom-resolution rendering (1.2.46): the settled zoom
            // (debounced) drives ONE re-render of the CURRENT page
            // whose target width follows the zoom itself (box width
            // x zoom x oversample, pixel-capped via ReaderRenderMath)
            // — the 1.2.37 cap of base x min(zoom, 2.0) left 3-4x
            // zooms permanently below display resolution. The
            // sharper bitmap simply replaces the page's cache slot
            // and STAYS there: settling back to 1x no longer
            // re-renders the base (that round trip was pure churn —
            // a full render on every zoom settle), and the eviction
            // above recycles the slot on page change as before.
            var hiResPage by remember { mutableStateOf(-1) }
            var hiResAppliedTarget by remember { mutableStateOf(0) }
            var hiResTargetWidth by remember { mutableStateOf(0) }
            LaunchedEffect(zoom, boxSize) {
                kotlinx.coroutines.delay(READER_HIRES_DEBOUNCE_MS)
                hiResTargetWidth = ReaderRenderMath.hiResTargetWidthPx(boxSize.width, zoom)
            }
            LaunchedEffect(currentPage, hiResTargetWidth) {
                val target = hiResTargetWidth
                // The live zoom is re-checked at execution time: a
                // page turn resets it to 1x, and a stale target from
                // the previous page must not fire a zoom render for
                // the new one.
                val wanted = target > 0 && zoom > ReaderRenderMath.HIRES_MIN_ZOOM
                if (wanted && (hiResPage != currentPage || hiResAppliedTarget != target)) {
                    val bmp = renderPageBitmap(
                        currentPage,
                        ReaderRenderMath.FALLBACK_SCALE,
                        targetWidthPx = target,
                        maxPixels = ReaderRenderMath.HIRES_MAX_PIXELS
                    )
                    if (bmp != null) {
                        val old = bitmapCache[currentPage]
                        bitmapCache[currentPage] = bmp
                        if (old != null && old !== bmp) old.recycle()
                        hiResPage = currentPage
                        hiResAppliedTarget = target
                    }
                }
            }
            // A page turn invalidates the hi-res markers: the
            // eviction above already recycled that bitmap, and a
            // stale marker would wrongly skip a later hi-res render
            // of this page.
            LaunchedEffect(currentPage) {
                hiResPage = -1
                hiResAppliedTarget = 0
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
                // Neighbours are composed (and so start rendering)
                // BEFORE they slide in (1.2.46): with the default 0
                // the incoming page only began its render once the
                // swipe was already under way, and the render's
                // duration showed up as a spinner mid-flip.
                beyondViewportPageCount = 1,
                // While zoomed, horizontal drags pan the page instead of
                // flipping it; in text-highlight mode they select
                // instead of flipping; at 1x they always flip.
                userScrollEnabled = zoom <= 1.01f && !textHighlightMode,
                modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }
            ) { page ->
                val bitmap = bitmapCache[page]
                LaunchedEffect(page, pdfRenderer) {
                    if (bitmapCache[page] == null && pdfRenderer != null) {
                        // Display-matched base resolution (1.2.46):
                        // the target follows the pager's real pixel
                        // width (ReaderRenderMath), not a fixed x2 of
                        // the point size; zoom re-renders of the
                        // current page swap this entry at a
                        // zoom-matched target.
                        renderPageBitmap(
                            page,
                            ReaderRenderMath.FALLBACK_SCALE,
                            targetWidthPx = ReaderRenderMath.baseTargetWidthPx(boxSize.width),
                            maxPixels = ReaderRenderMath.BASE_MAX_PIXELS
                        )?.let { bitmapCache[page] = it }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        val isCurrent = page == currentPage
                        // The ImageBitmap wrapper is remembered per
                        // bitmap (1.2.46): the old code rebuilt it on
                        // every recomposition of the page.
                        val pageImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
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
                                .graphicsLayer {
                                    // Zoom/pan are read HERE, in the
                                    // draw phase (1.2.46): the old
                                    // parameter form read them during
                                    // composition, so every pinch/pan
                                    // pointer event recomposed the
                                    // whole page (image + markings
                                    // canvas + fit math) on the main
                                    // thread and movement stuttered.
                                    // Read in the layer block, a state
                                    // change only re-records this
                                    // layer — no recomposition — and
                                    // the markings Canvas inside
                                    // inherits the same transform.
                                    if (isCurrent) {
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = pan.x
                                        translationY = pan.y
                                    }
                                }
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
                                                val n = toNormalized(tapOff, bitmap)
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
                                    if (isCurrent && textHighlightMode) {
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
                                    if (isCurrent && !textHighlightMode) {
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
                    // deleted, 1.2.38). Shown only with a text index.
                    if (textIndex != null) {
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
                            "اسحب فوق النص لتظليله — ولمس مظللة يحذفها",
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
                                pagerState.animateScrollToPage(currentPage - 1)
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
                                        pagerState.scrollToPage(target.roundToInt().coerceIn(0, totalPages - 1))
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
                                pagerState.animateScrollToPage(currentPage + 1)
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
