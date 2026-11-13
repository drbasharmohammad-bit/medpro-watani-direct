package sy.medpro.watani.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import sy.medpro.watani.data.LibraryItem
import sy.medpro.watani.data.LibrarySubject
import sy.medpro.watani.ui.concepts.ConceptUnitScreen
import sy.medpro.watani.ui.concepts.ConceptsEntryCard
import sy.medpro.watani.ui.concepts.ConceptsSubjectsScreen
import sy.medpro.watani.ui.concepts.ConceptsTopicsScreen
import sy.medpro.watani.ui.concepts.rememberConceptUnits
import sy.medpro.watani.ui.icons.LibrarySubjectIcon
import sy.medpro.watani.ui.icons.librarySubjectIconRes
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.GoldAccent

data class LibraryReaderState(
    val item: LibraryItem,
    val file: java.io.File?,
    val url: String?,
    val subtitleFile: java.io.File? = null,
    val subtitleUrl: String? = null
)

/** «ادرس مع ميد برو» sub-navigation INSIDE the Library tab (1.2.13):
 * Home is the video list itself; the concepts subjects/topics/unit
 * screens replace it while open and back returns through them. */
private sealed interface ConceptsRoute {
    object Home : ConceptsRoute
    object Subjects : ConceptsRoute
    data class Topics(val subject: String) : ConceptsRoute
    data class Unit(val unitId: String) : ConceptsRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = viewModel(),
    onReaderFullscreenChange: ((Boolean) -> Unit)? = null,
    onOpenQuestionById: (String) -> Unit = {}
) {
    // Team 1 (entry-activation): the Library is gated again per the
    // 30 Oct entry spec — Questions + Library require activation, all
    // other sections are free. Enforcement is the bottom-tab gate in
    // MainActivity (requestTab) plus the fetch-layer switch in
    // WorkerActivationApi (library.json + books/ via the Worker gateway);
    // this screen itself renders whatever the repository returns.
    // (The 28 Oct "library is free" note above is superseded.)
    val state by viewModel.state.collectAsState()
    // «ادرس مع ميد برو» (1.2.13): the concepts pack state + the
    // in-Library sub-navigation. The pack loads exactly like
    // dosing_drills.json; while no downloaded pack with units exists
    // the units stay empty and NOTHING of the section is rendered —
    // the entry is hidden completely (doctor's conditional-display
    // rule), and this screen behaves exactly as in 1.2.12.
    val conceptState = rememberConceptUnits()
    var conceptsRoute by remember { mutableStateOf<ConceptsRoute>(ConceptsRoute.Home) }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) isRefreshing = false
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
                Button(shape = MaterialTheme.shapes.medium, onClick = { viewModel.loadLibrary() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إعادة المحاولة")
                }
            }
        }
        return
    }

    // «ادرس مع ميد برو» sub-screens: rendered instead of the video
    // list while open (only reachable when a pack with units exists,
    // because the entry points below render only then).
    when (val route = conceptsRoute) {
        is ConceptsRoute.Subjects -> {
            ConceptsSubjectsScreen(
                units = conceptState.units,
                onSubjectClick = { conceptsRoute = ConceptsRoute.Topics(it) },
                onBack = { conceptsRoute = ConceptsRoute.Home }
            )
            return
        }
        is ConceptsRoute.Topics -> {
            ConceptsTopicsScreen(
                subject = route.subject,
                units = conceptState.units,
                onUnitClick = { conceptsRoute = ConceptsRoute.Unit(it) },
                onBack = { conceptsRoute = ConceptsRoute.Subjects }
            )
            return
        }
        is ConceptsRoute.Unit -> {
            ConceptUnitScreen(
                unitId = route.unitId,
                onBack = {
                    val subject = conceptState.units
                        .firstOrNull { it.id == route.unitId }?.subject?.trim()
                    conceptsRoute = if (!subject.isNullOrBlank()) {
                        ConceptsRoute.Topics(subject)
                    } else {
                        ConceptsRoute.Subjects
                    }
                },
                onOpenQuestionById = onOpenQuestionById
            )
            return
        }
        ConceptsRoute.Home -> Unit
    }

    val library = state.library
    if (library == null || library.subjects.isNullOrEmpty()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; viewModel.loadLibrary() },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (conceptState.units.isNotEmpty()) {
                        ConceptsEntryCard(
                            units = conceptState.units,
                            onClick = { conceptsRoute = ConceptsRoute.Subjects }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = GoldAccent.copy(alpha = 0.18f),
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LibraryBooks, null,
                                tint = if (isSystemInDarkTheme()) GoldAccent else MaroonPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Text(
                        "لا يوجد محتوى بعد",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        "سيظهر هنا عند إضافة فيديوهات جديدة",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.loadLibrary() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("إعادة التحميل")
                    }
                    Text(
                        "اسحب للأسفل للتحقق من جديد",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        }
        return
    }

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
            onReaderFullscreenChange?.invoke(false)
        }
        // Videos only: books were removed from the in-app Library by the
        // doctor's order (11 Nov 2026) — the repository strips them at the
        // data layer, so no book can ever reach this reader.
        if (reader.file != null && reader.file.exists()) {
            VideoPlayerScreen(
                reader.file, reader.item.title ?: "فيديو", onBack = onReaderBack,
                subtitleFile = reader.subtitleFile, subtitleUrl = reader.subtitleUrl,
                onFullscreenChange = onReaderFullscreenChange
            )
        } else if (!reader.url.isNullOrEmpty()) {
            VideoPlayerScreen(
                null, reader.item.title ?: "فيديو", onBack = onReaderBack,
                videoFileUrl = reader.url, subtitleUrl = reader.subtitleUrl,
                onFullscreenChange = onReaderFullscreenChange
            )
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true; viewModel.loadLibrary() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
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
                // «ادرس مع ميد برو» entry (1.2.13): one card at the top
                // of the Library list — rendered ONLY when a concepts
                // pack with units is downloaded on this device.
                if (conceptState.units.isNotEmpty()) {
                    item {
                        ConceptsEntryCard(
                            units = conceptState.units,
                            onClick = { conceptsRoute = ConceptsRoute.Subjects }
                        )
                    }
                }
                items(library.subjects!!) { subject ->
                    val items = subject.videos
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
                        shape = MaterialTheme.shapes.medium
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (librarySubjectIconRes(subject.id, subject.name) != null) {
                                        LibrarySubjectIcon(subject.id, subject.name, Modifier.size(30.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Text(
                                        subject.name ?: "موضوع",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaroonPrimary
                                    )
                                }
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
                                        val itemId = item.id ?: item.fileUrl ?: ""
                                        LibraryItemRow(item, viewModel, state.downloadingIds, downloadProgress = state.downloadingProgress[itemId], downloadError = state.downloadErrors[itemId], onOpenReader = { file, url ->
                                            currentReader = LibraryReaderState(
                                                item, file, url,
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
    }
}

@Composable
private fun LibraryItemRow(item: LibraryItem, viewModel: LibraryViewModel, downloadingIds: Set<String>, downloadProgress: Float? = null, downloadError: String? = null, onOpenReader: (java.io.File?, String?) -> Unit = { _, _ -> }, onOpenError: (String) -> Unit = {}) {
    val id = item.id ?: item.fileUrl ?: ""
    val isDownloading = id in downloadingIds
    val isDownloaded by remember { derivedStateOf { viewModel.isItemDownloaded(item) } }
    val coroutineScope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.small
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
                    isDownloading -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (downloadProgress != null && downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.width(72.dp),
                                color = MaroonPrimary
                            )
                            Text(
                                "${(downloadProgress * 100).toInt()}٪",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(36.dp), color = MaroonPrimary, strokeWidth = 3.dp)
                        }
                    }
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
                                Icons.Default.PlayArrow,
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

            if (downloadError != null) {
                Text(
                    downloadError,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (!isDownloaded && !item.fileUrl.isNullOrEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(shape = MaterialTheme.shapes.medium,
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