package sy.medpro.watani.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.GoldAccent
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

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
                // flipping it; at 1x they always flip.
                userScrollEnabled = zoom <= 1.01f,
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
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = if (isCurrent) zoom else 1f,
                                    scaleY = if (isCurrent) zoom else 1f,
                                    translationX = if (isCurrent) pan.x else 0f,
                                    translationY = if (isCurrent) pan.y else 0f
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            showBars = !showBars
                                            resetHideBarTimer()
                                        },
                                        onDoubleTap = { toggleZoom() }
                                    )
                                }
                                .then(
                                    if (isCurrent && zoom > 1.01f) {
                                        Modifier.pointerInput(Unit) {
                                            detectTransformGestures { _, panChange, _, _ ->
                                                // Clamp the pan so the page cannot
                                                // be dragged off-screen: max pan is
                                                // half the overflow on each axis.
                                                val fit = minOf(
                                                    boxSize.width.toFloat() / bitmap.width,
                                                    boxSize.height.toFloat() / bitmap.height
                                                )
                                                val dispW = bitmap.width * fit * zoom
                                                val dispH = bitmap.height * fit * zoom
                                                val maxX = max(0f, (dispW - boxSize.width) / 2f)
                                                val maxY = max(0f, (dispH - boxSize.height) / 2f)
                                                pan = Offset(
                                                    (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                                    (pan.y + panChange.y).coerceIn(-maxY, maxY)
                                                )
                                            }
                                        }
                                    } else Modifier
                                )
                        )
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
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary)
                )

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
                            Text(
                                "${currentPage + 1} / $totalPages (${((currentPage + 1).toFloat() / totalPages * 100).toInt()}%)",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
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
                                        Button(
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
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
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
        }
    }
}
