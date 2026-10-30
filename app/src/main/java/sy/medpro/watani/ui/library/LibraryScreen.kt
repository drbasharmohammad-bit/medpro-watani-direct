package sy.medpro.watani.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import sy.medpro.watani.data.LibraryItem
import sy.medpro.watani.data.LibrarySubject
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.GoldAccent

data class LibraryReaderState(
    val item: LibraryItem,
    val file: java.io.File?,
    val url: String?,
    val isBook: Boolean,
    val subtitleFile: java.io.File? = null,
    val subtitleUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = viewModel()) {
    // المكتبة مجانية حاليًا بقرار الدكتور (28 أكتوبر 2026): أُزيل حجب التفعيل
    // بالكامل من هذه الشاشة. التفعيل باقٍ لباقي أقسام التطبيق المدفوعة.
    val state by viewModel.state.collectAsState()
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { viewModel.loadLibrary() }
    }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && pullState.isRefreshing) pullState.endRefresh()
    }

    if (state.isLoading && state.library == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = MaroonPrimary)
                Text("جاري تحميل المكتبة...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }
        return
    }

    if (state.error != null && state.library == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Error, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Text(state.error ?: "خطأ", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.loadLibrary() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إعادة المحاولة")
                }
            }
        }
        return
    }

    val library = state.library
    if (library == null || library.subjects.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.LibraryBooks, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    Text(
                        "لا يوجد محتوى بعد — سيظهر هنا عند إضافة كتب وفيديوهات جديدة",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        "اسحب للأسفل للتحقق من جديد",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
        return
    }

    var selectedTab by remember { mutableStateOf(0) }
    var expandedSubjectId by remember { mutableStateOf<String?>(null) }
    var showDownloadedOnly by remember { mutableStateOf(false) }
    var currentReader by remember { mutableStateOf<LibraryReaderState?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var loadingOpen by remember { mutableStateOf(false) }

    if (currentReader != null) {
        val reader = currentReader ?: return
        val onReaderBack: () -> Unit = {
            viewModel.closeItem(reader.item)
            currentReader = null
        }
        when {
            reader.isBook -> {
                if (reader.file != null) {
                    PdfViewerScreen(reader.file, reader.item.title ?: "كتاب", onBack = onReaderBack)
                }
            }
            else -> {
                if (reader.file != null && reader.file.exists()) {
                    VideoPlayerScreen(
                        reader.file, reader.item.title ?: "فيديو", onBack = onReaderBack,
                        subtitleFile = reader.subtitleFile, subtitleUrl = reader.subtitleUrl
                    )
                } else if (!reader.url.isNullOrEmpty()) {
                    VideoPlayerScreen(
                        null, reader.item.title ?: "فيديو", onBack = onReaderBack,
                        videoFileUrl = reader.url, subtitleUrl = reader.subtitleUrl
                    )
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
        Column(Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaroonPrimary,
                contentColor = Color.White
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("الكتب", Modifier.padding(vertical = 14.dp), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("الفيديوهات", Modifier.padding(vertical = 14.dp), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("المحمّلة فقط", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = showDownloadedOnly,
                    onCheckedChange = { showDownloadedOnly = it },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaroonPrimary,
                        uncheckedTrackColor = GoldAccent.copy(alpha = 0.3f)
                    )
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(library.subjects!!) { subject ->
                    val items = if (selectedTab == 0) subject.books else subject.videos
                    if (items.isNullOrEmpty()) return@items

                    val filteredItems = if (showDownloadedOnly) {
                        items.filter { viewModel.isItemDownloaded(it) }
                    } else {
                        items
                    }
                    if (filteredItems.isEmpty()) return@items

                    val isExpanded = expandedSubjectId == subject.id

                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSubjectId = if (isExpanded) null else subject.id }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    subject.name ?: "موضوع",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaroonPrimary
                                )
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    tint = MaroonPrimary
                                )
                            }

                            if (isExpanded) {
                                HorizontalDivider()
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredItems.forEach { item ->
                                        LibraryItemRow(item, selectedTab == 0, viewModel, state.downloadingIds, onOpenReader = { file, url ->
                                            currentReader = LibraryReaderState(
                                                item, file, url, selectedTab == 0,
                                                // Local SRT side-car only for downloaded videos;
                                                // streamed videos use the item's subtitleUrl.
                                                subtitleFile = if (file != null) viewModel.getSubtitleFile(item) else null,
                                                subtitleUrl = item.subtitleUrl
                                            )
                                        }, onOpenError = { err ->
                                            openError = err
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun LibraryItemRow(item: LibraryItem, isBook: Boolean, viewModel: LibraryViewModel, downloadingIds: Set<String>, onOpenReader: (java.io.File?, String?) -> Unit = { _, _ -> }, onOpenError: (String) -> Unit = {}) {
    val id = item.id ?: item.fileUrl ?: ""
    val isDownloading = id in downloadingIds
    val isDownloaded by remember { derivedStateOf { viewModel.isItemDownloaded(item) } }
    val coroutineScope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title ?: "بدون عنوان", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    if (!item.description.isNullOrBlank()) {
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    val sizeMB = (item.size ?: 0L) / (1024.0 * 1024.0)
                    Text(
                        if (sizeMB > 0) "%.1f MB".format(sizeMB) else "حجم غير محدد",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                when {
                    isDownloading -> CircularProgressIndicator(Modifier.size(36.dp), color = MaroonPrimary, strokeWidth = 3.dp)
                    isDownloaded -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                viewModel.openItem(item).fold(
                                    onSuccess = { file -> onOpenReader(file, null) },
                                    onFailure = { onOpenError(it.message ?: "تعذر فتح الملف المحمّل — أعد تحميله") }
                                )
                            }
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isBook) Icons.Default.Description else Icons.Default.PlayArrow,
                                "فتح",
                                tint = GoldAccent
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> IconButton(onClick = { viewModel.downloadItem(item) }) {
                        Icon(Icons.Default.CloudDownload, "تحميل", tint = GoldAccent)
                    }
                }
            }

            if (!isDownloaded && !isBook && !item.fileUrl.isNullOrEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onOpenReader(null, item.fileUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("مشاهدة مباشرة", fontSize = 11.sp, color = Color.Black)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الملف المحمّل", fontWeight = FontWeight.Bold) },
            text = { Text("سيُحذف الملف من جهازك نهائيًا، ويمكنك تحميله مجددًا من المكتبة لاحقًا.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteItem(item)
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}