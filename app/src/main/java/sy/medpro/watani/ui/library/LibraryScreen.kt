package sy.medpro.watani.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import sy.medpro.watani.ui.theme.MaroonDeep
import sy.medpro.watani.ui.theme.GoldAccent

data class LibraryReaderState(
    val item: LibraryItem,
    val file: java.io.File?,
    val url: String?,
    val subtitleFile: java.io.File? = null,
    val subtitleUrl: String? = null
)

/** Library tab navigation (1.2.14, doctor's order): the tab opens
 * on ENTRIES — parallel section entries like the old books entry:
 * «الفيديوهات» opens the video list, «ادرس مع ميد برو» opens the
 * concepts subjects/topics/unit chain. Each entry renders only
 * when its content exists on the device. */
private sealed interface LibraryRoute {
    object Entries : LibraryRoute
    object Videos : LibraryRoute
    object ConceptsSubjects : LibraryRoute
    data class ConceptsTopics(val subject: String) : LibraryRoute
    data class ConceptsUnit(val unitId: String) : LibraryRoute
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
    var libraryRoute by remember { mutableStateOf<LibraryRoute>(LibraryRoute.Entries) }
    // «ادرس مع ميد برو» pack check (1.2.14 fix): the check used to
    // run ONCE per process (LaunchedEffect(Unit) inside
    // rememberConceptUnits), so a pack published after the app had
    // already opened the Library stayed invisible until the app was
    // force-killed. The refresh key now bumps on every lifecycle
    // resume while this screen is composed and on every
    // pull-to-refresh, and rememberConceptUnits re-runs its
    // cache-first check for each bump.
    var conceptsRefreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // The observer receives a synthetic ON_RESUME the moment it
        // is added to an already-resumed lifecycle — swallow that
        // first one (the initial load is already running) so a real
        // download is never cancelled by its own re-trigger.
        var resumedOnce = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumedOnce) conceptsRefreshKey++ else resumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The pack loads exactly like dosing_drills.json; while no
    // downloaded pack with units exists the units stay empty and
    // the section's entry is not rendered at all (doctor's
    // conditional-display rule).
    val conceptState = rememberConceptUnits(conceptsRefreshKey)
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) isRefreshing = false
    }
    // Video-list state, declared up-front so it survives moving
    // between the entries screen and the video list.
    var expandedSubjectId by remember { mutableStateOf<String?>(null) }
    var showDownloadedOnly by remember { mutableStateOf(false) }
    var currentReader by remember { mutableStateOf<LibraryReaderState?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var loadingOpen by remember { mutableStateOf(false) }

    // System back from the video list returns to the section
    // entries (the concepts sub-screens register their own back
    // handlers while composed and take precedence then).
    BackHandler(enabled = libraryRoute == LibraryRoute.Videos) {
        libraryRoute = LibraryRoute.Entries
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

    // «ادرس مع ميد برو» sub-screens: rendered instead of the entries
    // and the video list while open (reachable only when a pack
    // with units exists, because the entry renders only then). Back
    // from the subjects list lands on the Library entries (1.2.14).
    when (val route = libraryRoute) {
        is LibraryRoute.ConceptsSubjects -> {
            ConceptsSubjectsScreen(
                units = conceptState.units,
                onSubjectClick = { libraryRoute = LibraryRoute.ConceptsTopics(it) },
                onBack = { libraryRoute = LibraryRoute.Entries }
            )
            return
        }
        is LibraryRoute.ConceptsTopics -> {
            ConceptsTopicsScreen(
                subject = route.subject,
                units = conceptState.units,
                onUnitClick = { libraryRoute = LibraryRoute.ConceptsUnit(it) },
                onBack = { libraryRoute = LibraryRoute.ConceptsSubjects }
            )
            return
        }
        is LibraryRoute.ConceptsUnit -> {
            ConceptUnitScreen(
                unitId = route.unitId,
                onBack = {
                    val subject = conceptState.units
                        .firstOrNull { it.id == route.unitId }?.subject?.trim()
                    libraryRoute = if (!subject.isNullOrBlank()) {
                        LibraryRoute.ConceptsTopics(subject)
                    } else {
                        LibraryRoute.ConceptsSubjects
                    }
                },
                onOpenQuestionById = onOpenQuestionById
            )
            return
        }
        LibraryRoute.Entries, LibraryRoute.Videos -> Unit
    }

    val library = state.library
    val subjectsWithVideos = library?.subjects.orEmpty()
        .filter { !it.videos.isNullOrEmpty() }
    val hasVideos = subjectsWithVideos.isNotEmpty()

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

    // Section entries (1.2.14): parallel entries, each rendered only
    // when its content exists — «الفيديوهات» when the library has
    // videos, «ادرس مع ميد برو» when a concepts pack with units is
    // downloaded. With neither, the tab shows the empty state.
    if (libraryRoute == LibraryRoute.Entries) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadLibrary()
                conceptsRefreshKey++
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (!hasVideos && conceptState.units.isEmpty()) {
                LibraryEmptyState(
                    onReload = { viewModel.loadLibrary(); conceptsRefreshKey++ }
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasVideos) {
                        item {
                            VideosEntryCard(
                                totalVideos = subjectsWithVideos.sumOf { it.videos?.size ?: 0 },
                                subjectsCount = subjectsWithVideos.size,
                                onClick = { libraryRoute = LibraryRoute.Videos }
                            )
                        }
                    }
                    if (conceptState.units.isNotEmpty()) {
                        item {
                            ConceptsEntryCard(
                                units = conceptState.units,
                                onClick = { libraryRoute = LibraryRoute.ConceptsSubjects }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // Video list route: reachable only from the «الفيديوهات» entry
    // (which renders only when videos exist); if a refresh emptied
    // the library underneath, fall back to the empty state.
    if (!hasVideos) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadLibrary()
                conceptsRefreshKey++
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LibraryEmptyState(
                onReload = { viewModel.loadLibrary(); conceptsRefreshKey++ }
            )
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.loadLibrary()
            conceptsRefreshKey++
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
            // Back to the section entries (1.2.14) + the section title.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { libraryRoute = LibraryRoute.Entries }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaroonPrimary
                    )
                }
                Text(
                    "الفيديوهات",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaroonPrimary
                )
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
                items(library?.subjects.orEmpty()) { subject ->
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

/** The «الفيديوهات» section entry (1.2.14) — visual twin of the
 * «ادرس مع ميد برو» entry card (ConceptsEntryCard): the Library
 * tab opens on these parallel entries, as the books entry used to
 * sit beside the videos. Rendered only when the library actually
 * has videos. */
@Composable
private fun VideosEntryCard(
    totalVideos: Int,
    subjectsCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaroonDeep),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "الفيديوهات",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "محاضرات الدورات — $totalVideos فيديو في $subjectsCount مادة",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 17.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** The Library empty state (unchanged from 1.2.13): shown when
 * neither section has content on the device yet. */
@Composable
private fun LibraryEmptyState(onReload: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
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
                onClick = onReload,
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