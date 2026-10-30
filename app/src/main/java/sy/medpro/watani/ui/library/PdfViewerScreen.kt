package sy.medpro.watani.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.ui.graphics.toArgb
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
fun PdfViewerScreen(pdfFile: File, title: String, onBack: () -> Unit) {
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
                                        onTap = {
                                            // While highlighting, taps must never hide
                                            // the bars — the off switch lives in them.
                                            if (!showBars) {
                                                showBars = true
                                                resetHideBarTimer()
                                            } else if (!highlightMode) {
                                                showBars = false
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
                                                    paintingPoints = listOf(toNormalized(off, bitmap))
                                                },
                                                onDrag = { change, _ ->
                                                    paintingPoints = (paintingPoints ?: emptyList()) +
                                                        toNormalized(change.position, bitmap)
                                                },
                                                onDragEnd = {
                                                    val pts = paintingPoints
                                                    paintingPoints = null
                                                    if (pts != null && pts.size >= 2) {
                                                        highlightStrokes = highlightStrokes + HighlightStrokeDto(
                                                            page = page,
                                                            points = pts.flatMap { listOf(it.x, it.y) },
                                                            color = highlightColor.toArgb(),
                                                            width = 0.035f
                                                        )
                                                        saveHighlights()
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary)
                )

                // Highlighter panel: pen colors + finish + clear this page.
                // Two rows so nothing squeezes or overlaps on narrow phones.
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
                            Text("اسحب للتأشير", color = Color.White, fontSize = 12.sp)
                            TextButton(onClick = { highlightMode = false }) {
                                Text("إنهاء التأشير ✓", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            TextButton(onClick = {
                                highlightStrokes = highlightStrokes.filter { it.page != currentPage }
                                saveHighlights()
                            }) {
                                Text("مسح تأشير الصفحة", color = Color.White, fontSize = 12.sp)
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
