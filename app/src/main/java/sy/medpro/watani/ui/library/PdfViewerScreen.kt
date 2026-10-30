package sy.medpro.watani.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
fun PdfViewerScreen(pdfFile: File, title: String, onBack: () -> Unit, onDeleteCorrupt: (() -> Unit)? = null, onFullscreenChange: ((Boolean) -> Unit)? = null) {
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
    // True fullscreen needs the main Scaffold bars hidden too, so the
    // reader reports its state up (MainActivity hides top/bottom bars).
    LaunchedEffect(isFullscreen) {
        onFullscreenChange?.invoke(isFullscreen)
    }
    val prefs = remember { context.getSharedPreferences("watani_library_${getFirebaseUid()}", Context.MODE_PRIVATE) }

    // Highlighter persistence (outer scope so the open effect can restore it).
    val highlightAdapter = remember {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter<List<HighlightStrokeDto>>(
                Types.newParameterizedType(List::class.java, HighlightStrokeDto::class.java)
            )
    }
    var highlightStrokes by remember { mutableStateOf<List<HighlightStrokeDto>>(emptyList()) }
    fun saveHighlights() {
        prefs.edit().putString(
            "highlights_${pdfFile.nameWithoutExtension}",
            highlightAdapter.toJson(highlightStrokes)
        ).apply()
    }

    // Shape/text annotations (outer scope so the open effect restores them).
    val annotationAdapter = remember {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter<List<PdfAnnotationDto>>(
                Types.newParameterizedType(List::class.java, PdfAnnotationDto::class.java)
            )
    }
    var annotations by remember { mutableStateOf<List<PdfAnnotationDto>>(emptyList()) }
    fun saveAnnotations() {
        prefs.edit().putString(
            "annotations_${pdfFile.nameWithoutExtension}",
            annotationAdapter.toJson(annotations)
        ).apply()
    }

    fun saveBookmarks() {
        val key = "bookmarks_${pdfFile.nameWithoutExtension}"
        val json = com.squareup.moshi.Moshi.Builder().build().adapter(List::class.java).toJson(bookmarks.toList())
        prefs.edit().putString(key, json).apply()
    }

    fun loadBookmarks() {
        val key = "bookmarks_${pdfFile.nameWithoutExtension}"
        val json = prefs.getString(key, null)
        if (json != null) {
            try {
                val list = com.squareup.moshi.Moshi.Builder().build().adapter(List::class.java).fromJson(json) as? List<*>
                bookmarks = (list?.filterIsInstance<Number>()?.map { it.toInt() } ?: emptyList()).toSet()
            } catch (e: Exception) {
                bookmarks = emptySet()
            }
        }
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
                        highlightStrokes = highlightAdapter.fromJson(it) ?: emptyList()
                    }
                } catch (_: Exception) {
                    // Corrupt highlights JSON must never block opening the book.
                }
                try {
                    prefs.getString("annotations_${pdfFile.nameWithoutExtension}", null)?.let {
                        annotations = annotationAdapter.fromJson(it) ?: emptyList()
                    }
                } catch (_: Exception) {
                    // Corrupt annotations JSON must never block opening the book.
                }
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                parcelFd = fd
                pdfRenderer = renderer
                totalPages = renderer.pageCount
                savedPage = prefs.getInt("last_page_${pdfFile.nameWithoutExtension}", 0)
                    .coerceIn(0, (totalPages - 1).coerceAtLeast(0))
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

            // Highlighter UI state (strokes live in the outer scope).
            var highlightMode by remember { mutableStateOf(false) }
            var highlightColor by remember { mutableStateOf(Color.Yellow) }
            // Annotation tool inside highlight mode: FREEHAND (strokes),
            // TEXT (tap places a text box), RECT, ELLIPSE, ARROW (drag).
            var annotationTool by remember { mutableStateOf("FREEHAND") }
            // One undo history for both marking kinds (strokes and
            // shape/text annotations): the student sees one kind of
            // marking, so there is one undo, not two. Entries are
            // (kind, page); session-only — reopening the book clears
            // undo history, the markings themselves stay saved.
            var markHistory by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
            var pendingTextAt by remember { mutableStateOf<Offset?>(null) }
            var showTextEditor by remember { mutableStateOf(false) }
            var paintingPoints by remember { mutableStateOf<List<Offset>?>(null) }
            var showGoToPage by remember { mutableStateOf(false) }
            var goToPageText by remember { mutableStateOf("") }

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
            }

            // Keep only the current page ±1 rendered; recycle evicted bitmaps.
            LaunchedEffect(currentPage) {
                val keep = (currentPage - 1..currentPage + 1).toSet()
                bitmapCache.keys.filter { it !in keep }.forEach { key ->
                    bitmapCache.remove(key)?.recycle()
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

            HorizontalPager(
                state = pagerState,
                // While zoomed, horizontal drags pan the page instead of
                // flipping it; in highlight mode they paint instead of
                // flipping; at 1x they always flip.
                userScrollEnabled = zoom <= 1.01f && !highlightMode,
                modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }
            ) { page ->
                val bitmap = bitmapCache[page]
                LaunchedEffect(page, pdfRenderer) {
                    if (bitmapCache[page] == null && pdfRenderer != null) {
                        withContext(Dispatchers.Default) {
                            try {
                                val pg = pdfRenderer!!.openPage(page)
                                val bmp = Bitmap.createBitmap(
                                    (pg.width * 2f).toInt(),
                                    (pg.height * 2f).toInt(),
                                    Bitmap.Config.ARGB_8888
                                )
                                // Fill white first: some PDFs leave the page
                                // background transparent, which showed as black.
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pg.close()
                                bitmapCache[page] = bmp
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        val isCurrent = page == currentPage
                        Box(
                            Modifier
                                .fillMaxSize()
                                // White page background: PdfRenderer does not clear
                                // the bitmap, so pages with a transparent background
                                // rendered as black text on the black canvas. The
                                // page area is always white now, whatever the PDF.
                                .background(Color.White)
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
                                    if (isCurrent && !highlightMode) {
                                        Modifier.pointerInput(Unit) {
                                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                                // Pinch zoom (two fingers, like any phone
                                                // app) + single-finger pan while zoomed.
                                                // Pan is clamped so the page cannot be
                                                // dragged off-screen: max pan is half
                                                // the overflow on each axis.
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
                                                if (zoomChange != 1f) {
                                                    zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                                    pan = if (zoom <= 1.01f) Offset.Zero else clampPan(pan, zoom)
                                                } else if (zoom > 1.01f) {
                                                    pan = clampPan(pan + panChange, zoom)
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
                        IconButton(onClick = { highlightMode = !highlightMode }) {
                            Icon(Icons.Default.Brush, null, tint = if (highlightMode) GoldAccent else Color.White)
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
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
                                // One undo for both marking kinds: the
                                // student sees one kind of marking, so
                                // the last marking on this page (stroke
                                // or shape/text) is what comes off.
                                val hasPageMark = markHistory.any { (_, p) -> p == currentPage }
                                TextButton(onClick = {
                                    val idx = markHistory.indexOfLast { (_, p) -> p == currentPage }
                                    if (idx >= 0) {
                                        val (kind, _) = markHistory[idx]
                                        markHistory = markHistory.toMutableList().also { it.removeAt(idx) }
                                        if (kind == "stroke") {
                                            val sIdx = highlightStrokes.indexOfLast { it.page == currentPage }
                                            if (sIdx >= 0) {
                                                highlightStrokes = highlightStrokes.toMutableList().also { it.removeAt(sIdx) }
                                                saveHighlights()
                                            }
                                        } else {
                                            val aIdx = annotations.indexOfLast { it.page == currentPage }
                                            if (aIdx >= 0) {
                                                annotations = annotations.toMutableList().also { it.removeAt(aIdx) }
                                                saveAnnotations()
                                            }
                                        }
                                    }
                                }) {
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
                                Text(
                                    "${currentPage + 1} / $totalPages (${((currentPage + 1).toFloat() / totalPages * 100).toInt()}%)",
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
                        Text(
                            "البحث في النصوص يدعم ملفات PDF بطبقة نصية محفوظة.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 40.dp)
                        )
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
