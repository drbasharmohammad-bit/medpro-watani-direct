package sy.medpro.watani.ui.library

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.GoldAccent
import java.io.File

private fun getFirebaseUid(): String {
    return try {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(pdfFile: File, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentPage by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var currentBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
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
        coroutineScope.launch(Dispatchers.Default) {
            try {
                loadBookmarks()
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                pdfRenderer = renderer
                totalPages = renderer.pageCount
                val saved = prefs.getInt("last_page_${pdfFile.nameWithoutExtension}", 0)
                currentPage = saved.coerceIn(0, totalPages - 1)
                isLoading = false
            } catch (e: Exception) {
                error = "تعذر فتح الملف: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentPage, pdfRenderer) {
        if (pdfRenderer != null) {
            coroutineScope.launch(Dispatchers.Default) {
                try {
                    val page = pdfRenderer!!.openPage(currentPage)
                    val bitmap = android.graphics.Bitmap.createBitmap((page.width * 1.5f).toInt(), (page.height * 1.5f).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    currentBitmap = bitmap
                    page.close()
                    withContext(Dispatchers.Main) {
                        prefs.edit().putInt("last_page_${pdfFile.nameWithoutExtension}", currentPage).apply()
                    }
                } catch (e: Exception) {
                    error = "خطأ في تحميل الصفحة: ${e.localizedMessage}"
                }
            }
        }
    }

    LaunchedEffect(showBars) {
        if (showBars) scheduleHideBar()
    }

    DisposableEffect(Unit) {
        onDispose {
            hideBarJob?.cancel()
            pdfRenderer?.close()
            currentBitmap?.recycle()
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
        } else {
            currentBitmap?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                showBars = !showBars
                                resetHideBarTimer()
                            })
                        }
                        .background(brightness.coerceIn(0.1f, 1f).let { Color(it, it, it) })
                )
            }
        }

        if (showBars) {
            TopAppBar(
                title = { Text(title, maxLines = 1, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                actions = {
                    IconButton(onClick = { showBookmarks = !showBookmarks }) {
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (currentPage > 0) currentPage-- }) {
                            Icon(Icons.Default.ChevronLeft, null, tint = Color.White)
                        }
                        Text(
                            "${currentPage + 1} / $totalPages (${((currentPage + 1).toFloat() / totalPages * 100).toInt()}%)",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = { if (currentPage < totalPages - 1) currentPage++ }) {
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BrightnessLow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = 0.3f..1f,
                            modifier = Modifier.weight(1f)
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
                                            currentPage = page
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
