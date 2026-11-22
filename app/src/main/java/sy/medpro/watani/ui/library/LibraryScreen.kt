package sy.medpro.watani.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import sy.medpro.watani.data.BookletCatalog
import sy.medpro.watani.data.BookletDlState
import sy.medpro.watani.data.BookletDownloadManager
import sy.medpro.watani.data.BookletMeta
import sy.medpro.watani.data.BookletProgress
import sy.medpro.watani.data.ConceptsCardKind
import sy.medpro.watani.data.ConceptsPackManager
import sy.medpro.watani.data.LibraryItem
import sy.medpro.watani.data.LibrarySubject
import sy.medpro.watani.data.conceptsCardKind
import sy.medpro.watani.ui.concepts.ConceptUnitScreen
import sy.medpro.watani.ui.concepts.ConceptsDownloadCard
import sy.medpro.watani.ui.concepts.ConceptsEntryCard
import sy.medpro.watani.ui.concepts.ConceptsSubjectsScreen
import sy.medpro.watani.ui.concepts.ConceptsTopicsScreen
import sy.medpro.watani.ui.concepts.ConceptsUnverifiedCard
import sy.medpro.watani.ui.concepts.rememberConceptUnits
import sy.medpro.watani.ui.icons.LibrarySubjectIcon
import sy.medpro.watani.ui.icons.librarySubjectIconRes
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.MaroonDeep
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

data class LibraryReaderState(
    val item: LibraryItem,
    val file: java.io.File?,
    val url: String?,
    val subtitleFile: java.io.File? = null,
    val subtitleUrl: String? = null
)

/** An open booklet reader (build 1.2.37): the local PDF file, the
 * chapter title shown in the reader bar, and the catalog path the
 * reading progress is recorded under. */
data class BookletReaderState(
    val file: java.io.File,
    val title: String,
    val path: String
)

/** Library tab navigation (1.2.14, doctor's order): the tab opens
 * on ENTRIES — parallel section entries like the old books entry:
 * «الفيديوهات» opens the video list, «ادرس مع ميد برو» opens the
 * concepts subjects/topics/unit chain. (Build 1.2.21: the videos
 * entry still renders only when videos exist, but the concepts
 * entry ALWAYS renders one of its three cards — see
 * conceptsCardKind.)
 * Build 1.2.37: the videos path is now a two-step drill-in —
 * Videos is the subjects GRID (design 01) and Subject is one
 * subject's page (design 02); system back walks Subject → Videos
 * → Entries. */
private sealed interface LibraryRoute {
    object Entries : LibraryRoute
    object Videos : LibraryRoute
    data class Subject(val subjectId: String) : LibraryRoute
    object ConceptsSubjects : LibraryRoute
    data class ConceptsTopics(val subject: String) : LibraryRoute
    data class ConceptsUnit(val unitId: String) : LibraryRoute
}

/** Back-navigation fix (1.2.22): the library route is saveable, so
 * leaving the tab's composition (a bank question opened from a
 * concept unit, a tab switch) no longer dumps the student back on
 * the Entries start — the route they left is restored as-is. The
 * payload separator is a control character that cannot appear in
 * subject names or unit ids. */
private val LibraryRouteSaver = androidx.compose.runtime.saveable.Saver<LibraryRoute, String>(
    save = { route ->
        when (route) {
            LibraryRoute.Entries -> "entries"
            LibraryRoute.Videos -> "videos"
            is LibraryRoute.Subject -> "subject\u0001" + route.subjectId
            LibraryRoute.ConceptsSubjects -> "subjects"
            is LibraryRoute.ConceptsTopics -> "topics\u0001" + route.subject
            is LibraryRoute.ConceptsUnit -> "unit\u0001" + route.unitId
        }
    },
    restore = { value ->
        when {
            value == "videos" -> LibraryRoute.Videos
            value.startsWith("subject\u0001") -> LibraryRoute.Subject(value.removePrefix("subject\u0001"))
            value == "subjects" -> LibraryRoute.ConceptsSubjects
            value.startsWith("topics\u0001") -> LibraryRoute.ConceptsTopics(value.removePrefix("topics\u0001"))
            value.startsWith("unit\u0001") -> LibraryRoute.ConceptsUnit(value.removePrefix("unit\u0001"))
            else -> LibraryRoute.Entries
        }
    }
)

/** Stable route key of a subject: its catalog id, else its name. */
private fun LibrarySubject.routeKey(): String =
    id?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: ""

/** Distinct non-blank booklet paths of a video list, in order. */
private fun List<LibraryItem>.bookletPaths(): List<String> =
    mapNotNull { it.booklet?.takeIf { p -> p.isNotBlank() } }.distinct()

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
    var libraryRoute by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = LibraryRouteSaver) {
        mutableStateOf<LibraryRoute>(LibraryRoute.Entries)
    }
    // «ادرس مع ميد برو» pack check (1.2.14 fix): the check used to
    // run ONCE per process (LaunchedEffect(Unit) inside
    // rememberConceptUnits), so a pack published after the app had
    // already opened the Library stayed invisible until the app was
    // force-killed. The refresh key now bumps on every lifecycle
    // resume while this screen is composed and on every
    // pull-to-refresh, and rememberConceptUnits re-runs its
    // cache-first check for each bump.
    // Build 1.2.21: a bump now re-reads the local cache and re-runs
    // ONLY the light manifest status check (badge/server status) —
    // it starts, restarts and cancels NOTHING. The pack download
    // itself lives in ConceptsPackManager (process scope), so an
    // ON_RESUME during a download can no longer kill it the way the
    // pre-1.2.21 effect cancellation did.
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
    // The pack state loads cache-first (rememberConceptUnits): an
    // installed pack is usable fully offline, and the entries list
    // ALWAYS renders one concepts card (entry / download /
    // unverified — conceptsCardKind), never silence (1.2.21 fix;
    // the pre-1.2.21 «entry renders only when a pack exists» rule
    // is what hid the section whenever the first download had not
    // completed).
    val conceptState = rememberConceptUnits(conceptsRefreshKey)
    // «ادرس مع ميد برو» explicit pack download/update: since
    // 1.2.21 the download runs in ConceptsPackManager (process
    // scope, shared StateFlow) — the same «outlives the screen»
    // pattern the bank section downloads use via MainViewModel.
    // Leaving the Library tab no longer cancels it; coming back
    // shows its progress/completion as it is. The entry card's
    // «حدّث الآن» button and the download card both just start /
    // observe this one download. A finished download bumps the
    // refresh key so the pack state reloads from disk.
    val context = LocalContext.current
    val conceptsDownloadState by ConceptsPackManager.state.collectAsState()
    val conceptsDownloading = conceptsDownloadState.downloading
    val conceptsProgress = conceptsDownloadState.progress
    val conceptsDownloadFailed = conceptsDownloadState.failed && !conceptsDownloadState.downloading
    val onConceptsDownloadClick: () -> Unit = {
        ConceptsPackManager.startDownload(context)
    }
    LaunchedEffect(conceptsDownloadState.completedRuns) {
        if (conceptsDownloadState.completedRuns > 0) conceptsRefreshKey++
    }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) isRefreshing = false
    }
    // Reader state, declared up-front so it survives moving between
    // the entries screen and the video routes.
    var currentReader by remember { mutableStateOf<LibraryReaderState?>(null) }
    // Booklet state (1.2.37): the open booklet reader and the open
    // booklet bottom sheet, hoisted beside the video reader so the
    // back chain can order them (booklet closes first).
    var currentBooklet by remember { mutableStateOf<BookletReaderState?>(null) }
    var bookletSheet by remember { mutableStateOf<BookletSheetRequest?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }

    // Booklet plumbing (1.2.37): seed the reading-progress store
    // and refresh the download states for every booklet the loaded
    // envelope links, so strips and sheets render their true state
    // on first paint.
    LaunchedEffect(state.library) {
        val envelope = state.library ?: return@LaunchedEffect
        BookletProgress.seed(context)
        val paths = envelope.subjects.orEmpty()
            .flatMap { it.videos.orEmpty() }
            .bookletPaths()
        if (paths.isNotEmpty()) BookletDownloadManager.refresh(context, paths)
    }

    // System back from the subjects grid returns to the section
    // entries (the concepts sub-screens register their own back
    // handlers while composed and take precedence then).
    BackHandler(enabled = libraryRoute == LibraryRoute.Videos) {
        libraryRoute = LibraryRoute.Entries
    }
    // System back from a subject page returns to the subjects grid.
    BackHandler(enabled = libraryRoute is LibraryRoute.Subject) {
        libraryRoute = LibraryRoute.Videos
    }

    // Back-navigation fix (1.2.22): system back while a video is
    // playing closes the READER first (one step — the same as the
    // player's own top-bar back). Before this, the press was eaten
    // silently by the Videos handler above (the route changed under
    // the still-open reader) and the next press jumped to the Home
    // tab with the player never closed. Composed after the Videos
    // handler so it takes precedence while a reader is open; the
    // player's own fullscreen handler is composed later still and
    // keeps winning in fullscreen.
    BackHandler(enabled = currentReader != null) {
        val reader = currentReader
        if (reader != null) {
            viewModel.closeItem(reader.item)
            currentReader = null
            onReaderFullscreenChange?.invoke(false)
        }
    }

    // Booklet reader back (1.2.37): composed after the video-reader
    // handler, so an open booklet closes before anything else —
    // the two readers never stack in practice, but if they did the
    // booklet (the topmost surface) is the one that must close.
    BackHandler(enabled = currentBooklet != null) {
        currentBooklet = null
        onReaderFullscreenChange?.invoke(false)
    }

    // «ادرس مع ميد برو» sub-screens FIRST (build 1.2.21): they depend
    // ONLY on the locally installed pack (conceptState), never on
    // the library envelope — so they are resolved before the
    // library loading/error early returns below. Before this fix a
    // failed library fetch held the whole tab hostage: the error
    // screen replaced everything, including a concepts section
    // whose pack was already on the device and fully offline.
    // Back from the subjects list lands on the Library entries
    // (1.2.14).
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
        LibraryRoute.Entries, LibraryRoute.Videos, is LibraryRoute.Subject -> Unit
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
        // Hostage fix (1.2.21): the library envelope failed to load,
        // but an installed concepts pack is local and free — its
        // entry card stays reachable in a minimal view, alongside
        // the library error and its retry (the rest of the tab's
        // content keeps the full error screen below, unchanged).
        if (conceptState.hasPack) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConceptsEntryCard(
                    unitCount = conceptState.unitCount,
                    subjectCount = conceptState.subjectCount,
                    onClick = { libraryRoute = LibraryRoute.ConceptsSubjects },
                    updateAvailable = conceptState.updateAvailable,
                    serverCount = conceptState.serverCount,
                    updating = conceptsDownloading,
                    updateProgress = conceptsProgress,
                    onUpdateClick = onConceptsDownloadClick
                )
                Text(
                    state.error ?: "تعذر تحميل المكتبة",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    shape = MaterialTheme.shapes.medium,
                    onClick = { viewModel.loadLibrary() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إعادة محاولة تحميل المكتبة")
                }
            }
            return
        }
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

    val library = state.library
    val subjectsWithVideos = library?.subjects.orEmpty()
        .filter { !it.videos.isNullOrEmpty() }
    val hasVideos = subjectsWithVideos.isNotEmpty()

    // Booklet reader FIRST (1.2.37): an open booklet outranks the
    // video reader below. Reading progress is recorded under the
    // booklet's catalog path on every page change the reader
    // reports.
    if (currentBooklet != null) {
        val booklet = currentBooklet ?: return
        PdfViewerScreen(
            pdfFile = booklet.file,
            title = booklet.title,
            onBack = {
                currentBooklet = null
                onReaderFullscreenChange?.invoke(false)
            },
            onDeleteCorrupt = {
                BookletDownloadManager.delete(context, booklet.path)
                currentBooklet = null
                onReaderFullscreenChange?.invoke(false)
            },
            onFullscreenChange = onReaderFullscreenChange,
            onProgress = { page, total ->
                BookletProgress.record(context, booklet.path, page, total)
            },
            onOpenRecent = { file, title ->
                // Recents are reader-wide, so an entry can name a
                // booklet outside the subject this host was opened
                // from. Open it in this same host when the file is
                // still on disk; its state key is the absolute path
                // (the catalog path is not recoverable from a recent
                // entry alone), which only affects the progress key
                // for this one switch path.
                if (file.exists()) {
                    currentBooklet = BookletReaderState(file, title, file.absolutePath)
                }
            }
        )
        return
    }

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

    // Section entries (1.2.14): parallel entries — «الفيديوهات» when
    // the library has videos, and «ادرس مع ميد برو» ALWAYS (build
    // 1.2.21, doctor's order): exactly one concepts card renders in
    // every state — entry (pack installed), download (no pack, the
    // server check succeeded), or «تعذّر التحقق» (no pack, the
    // check itself failed) — decided by conceptsCardKind. The
    // pre-1.2.21 rule (no card at all until a pack existed AND the
    // check had completed) is what made the section invisible on
    // slow connections.
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
                if (conceptState.loaded) {
                    item {
                        when (conceptsCardKind(
                            hasPack = conceptState.hasPack,
                            serverKnown = conceptState.serverKnown
                        )) {
                            ConceptsCardKind.ENTRY -> ConceptsEntryCard(
                                unitCount = conceptState.unitCount,
                                subjectCount = conceptState.subjectCount,
                                onClick = { libraryRoute = LibraryRoute.ConceptsSubjects },
                                updateAvailable = conceptState.updateAvailable,
                                serverCount = conceptState.serverCount,
                                updating = conceptsDownloading,
                                updateProgress = conceptsProgress,
                                onUpdateClick = onConceptsDownloadClick
                            )
                            // لا حزمة على الجهاز بعد لكن الخادم يعرضها:
                            // بطاقة تحميل صريحة — التحميل مرة واحدة ثم
                            // يعمل القسم بلا إنترنت.
                            ConceptsCardKind.DOWNLOAD -> ConceptsDownloadCard(
                                serverCount = conceptState.serverCount ?: 0,
                                downloading = conceptsDownloading,
                                progress = conceptsProgress,
                                failed = conceptsDownloadFailed,
                                onDownloadClick = onConceptsDownloadClick
                            )
                            // لا حزمة وتعذّر فحص الخادم نفسه: بطاقة
                            // صريحة بإعادة الفحص — لا اختفاء صامت.
                            ConceptsCardKind.UNVERIFIED -> ConceptsUnverifiedCard(
                                onRetryClick = { conceptsRefreshKey++ }
                            )
                        }
                    }
                } else if (!hasVideos) {
                    // The local cache read has not landed yet (a
                    // moment at most): a plain spinner, so the tab
                    // never flashes the wrong card.
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaroonPrimary)
                        }
                    }
                }
            }
        }
        return
    }

    // Video routes: reachable only from the «الفيديوهات» entry
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

    val onPullRefresh: () -> Unit = {
        isRefreshing = true
        viewModel.loadLibrary()
        conceptsRefreshKey++
    }
    val openVideoReader: (LibraryItem, java.io.File?, String?) -> Unit = { item, file, url ->
        currentReader = LibraryReaderState(
            item, file, url,
            // Local SRT side-car only for downloaded videos;
            // streamed videos use the item's subtitleUrl.
            subtitleFile = if (file != null) viewModel.getSubtitleFile(item) else null,
            subtitleUrl = item.subtitleUrl
        )
    }

    // Subjects grid (design 01) — also the fallback when a saved
    // Subject route names a subject a refresh has since removed.
    val activeSubject = (libraryRoute as? LibraryRoute.Subject)?.let { route ->
        subjectsWithVideos.firstOrNull { it.routeKey() == route.subjectId }
    }
    if (libraryRoute == LibraryRoute.Videos || (libraryRoute is LibraryRoute.Subject && activeSubject == null)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onPullRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            SubjectsGridScreen(
                subjects = subjectsWithVideos,
                viewModel = viewModel,
                onBackToEntries = { libraryRoute = LibraryRoute.Entries },
                onOpenSubject = { libraryRoute = LibraryRoute.Subject(it) }
            )
        }
        return
    }

    // Subject page (design 02).
    val subject = activeSubject ?: return
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onPullRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        SubjectVideosScreen(
            subject = subject,
            viewModel = viewModel,
            downloadingIds = state.downloadingIds,
            downloadingProgress = state.downloadingProgress,
            downloadErrors = state.downloadErrors,
            onBack = { libraryRoute = LibraryRoute.Videos },
            onOpenReader = openVideoReader,
            onOpenError = { openError = it },
            onBookletClick = { bookletSheet = it }
        )
    }

    // Booklet bottom sheet (design 03), hosted over the subject page.
    val sheetRequest = bookletSheet
    if (sheetRequest != null) {
        BookletSheet(
            request = sheetRequest,
            onDismiss = { bookletSheet = null },
            onOpenBooklet = { file, title, path ->
                bookletSheet = null
                currentBooklet = BookletReaderState(file, title, path)
            }
        )
    }

    if (openError != null) {
        AlertDialog(
            onDismissRequest = { openError = null },
            title = { Text("تعذّر فتح الفيديو", fontWeight = FontWeight.Bold) },
            text = { Text(openError ?: "") },
            confirmButton = {
                TextButton(onClick = { openError = null }) { Text("حسنًا") }
            }
        )
    }
}

// =====================================================================
// Subjects grid (design 01, build 1.2.37)
// =====================================================================

/** The library search field shared by the grid and the subject
 * page: a white rounded field; matching is a plain `contains` on
 * the trimmed query, Latin-case-insensitive (documented
 * simplification of the 1.2.37 spec). */
@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                placeholder, fontFamily = NotoSansArabicFontFamily,
                fontSize = 13.sp, color = libMuted()
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = LibColors.Maroon, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "مسح البحث", tint = libMuted(), modifier = Modifier.size(18.dp))
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = libSurface(),
            unfocusedContainerColor = libSurface(),
            focusedBorderColor = LibColors.Gold,
            unfocusedBorderColor = libLine(),
            focusedTextColor = libInk(),
            unfocusedTextColor = libInk()
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SubjectsGridScreen(
    subjects: List<LibrarySubject>,
    viewModel: LibraryViewModel,
    onBackToEntries: () -> Unit,
    onOpenSubject: (String) -> Unit
) {
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val trimmedQuery = query.trim()
    val searching = searchOpen && trimmedQuery.isNotEmpty()

    val totalVideos = subjects.sumOf { it.videos?.size ?: 0 }
    val totalBooklets = subjects
        .flatMap { it.videos.orEmpty() }
        .bookletPaths().size
    // Flat cross-subject results: (subject, video) pairs whose title
    // matches; a tap opens the video's subject page.
    val results = if (searching) {
        subjects.flatMap { subject ->
            subject.videos.orEmpty()
                .filter { it.title?.contains(trimmedQuery, ignoreCase = true) == true }
                .map { subject to it }
        }
    } else {
        emptyList()
    }

    LazyColumn(
        Modifier.fillMaxSize().background(libBackground()),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header (design .apphead): back-to-entries iconbtn (the one
        // documented deviation — the tab needs a way back to the
        // section entries), the app logo, titles, search iconbtn.
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                LibIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack, "رجوع إلى المداخل",
                    onClick = onBackToEntries
                )
                LibAppLogo()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "المكتبة",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 21.sp, color = libHeading(), lineHeight = 28.sp
                    )
                    Text(
                        "فيديوهات المواد وكتيّباتها في مكان واحد",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 12.5.sp, color = libMuted()
                    )
                }
                LibIconButton(
                    Icons.Default.Search, "بحث",
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }
                )
            }
        }
        if (searchOpen) {
            item {
                LibrarySearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "ابحث باسم الفيديو في كل المواد…"
                )
            }
        }

        if (searching) {
            if (results.isEmpty()) {
                item {
                    Text(
                        "لا فيديوهات مطابقة لبحثك",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 13.sp, color = libMuted(),
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(results) { (subject, video) ->
                    LibCard(radius = 20.dp, onClick = { onOpenSubject(subject.routeKey()) }) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(libPlayTint()),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow, contentDescription = null,
                                    tint = LibColors.Maroon, modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    video.title ?: "بدون عنوان",
                                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp, color = libInk(), lineHeight = 21.sp
                                )
                                Text(
                                    subject.name ?: "",
                                    fontFamily = NotoSansArabicFontFamily,
                                    fontSize = 12.sp, color = libMuted(),
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Summary card: maroon gradient, counts computed from the
        // envelope (subjects with videos / total videos / distinct
        // booklet paths).
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(LibColors.MaroonBrush)
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SummaryStat(subjects.size.toString(), "مادة", Modifier.weight(1f))
                        Box(
                            Modifier
                                .width(1.dp).height(38.dp)
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                        SummaryStat(totalVideos.toString(), "فيديو", Modifier.weight(1f))
                        Box(
                            Modifier
                                .width(1.dp).height(38.dp)
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                        SummaryStat(totalBooklets.toString(), "كتيّبًا", Modifier.weight(1f))
                    }
                    Text(
                        "لكل فيديو كتيّب قسمه من كتاب المادة — تجده تحت الفيديو نفسه",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.82f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "المواد",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp, color = libHeading()
                )
                Text(
                    "مرتّبة حسب المنهج",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.sp, color = libMuted()
                )
            }
        }

        items(subjects.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { subject ->
                    SubjectGridCard(
                        subject = subject,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenSubject(subject.routeKey()) }
                    )
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 25.sp, color = LibColors.GoldSoft, lineHeight = 30.sp
        )
        Text(
            label,
            fontFamily = NotoSansArabicFontFamily,
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun SubjectGridCard(
    subject: LibrarySubject,
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val videos = subject.videos.orEmpty()
    val bookletCount = videos.bookletPaths().size
    val fraction = viewModel.subjectWatchFraction(videos)
    LibCard(modifier = modifier, radius = 20.dp, onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The design's letter mark, replaced by the subject's
                // own icon (spec 1.2.37) inside the same 42dp gold
                // tile; a subject with no icon mapping keeps the
                // design's letter.
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(libGoldTint()),
                    contentAlignment = Alignment.Center
                ) {
                    if (librarySubjectIconRes(subject.id, subject.name) != null) {
                        LibrarySubjectIcon(subject.id, subject.name, Modifier.size(30.dp))
                    } else {
                        Text(
                            subject.name?.trim()?.firstOrNull()?.toString() ?: "؟",
                            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 17.sp, color = LibColors.Maroon
                        )
                    }
                }
                Text(
                    subject.name ?: "موضوع",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = libInk(), lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            val metaText = buildAnnotatedString {
                withStyle(SpanStyle(color = LibColors.Maroon, fontWeight = FontWeight.Bold)) {
                    append(videos.size.toString())
                }
                append(" فيديو")
                append("  ·  ")
                if (bookletCount > 0) {
                    withStyle(SpanStyle(color = LibColors.Maroon, fontWeight = FontWeight.Bold)) {
                        append(bookletCount.toString())
                    }
                    append(" ")
                    append(libBookletCountWord(bookletCount))
                } else {
                    append("بلا كتيّبات")
                }
            }
            Text(
                metaText,
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 12.sp, color = libMuted(),
                modifier = Modifier.padding(top = 8.dp)
            )
            LibGoldBar(fraction, modifier = Modifier.padding(top = 8.dp))
            Text(
                if (fraction <= 0f) "لم تبدأ بعد" else "تقدّم المشاهدة",
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 10.5.sp, color = libBarLabel(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// =====================================================================
// Subject page (design 02, build 1.2.37)
// =====================================================================

private enum class SubjectFilter { ALL, DOWNLOADED, WITH_BOOKLET }

@Composable
private fun LibFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .then(
                if (selected) Modifier
                else Modifier.shadow(4.dp, RoundedCornerShape(999.dp), clip = false)
            )
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) LibColors.Maroon else libSurface())
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = if (selected) Color.White else libMuted()
        )
    }
}

/** Group divider label of the subject page (.grouplbl). */
@Composable
private fun LibraryGroupLabel(text: String, countText: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text,
            fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp, color = LibColors.GoldText
        )
        Box(
            Modifier
                .weight(1f).height(1.dp)
                .background(libLine())
        )
        if (countText != null) {
            Text(
                countText,
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 11.5.sp, color = libMuted()
            )
        }
    }
}

@Composable
private fun SubjectVideosScreen(
    subject: LibrarySubject,
    viewModel: LibraryViewModel,
    downloadingIds: Set<String>,
    downloadingProgress: Map<String, Float>,
    downloadErrors: Map<String, String>,
    onBack: () -> Unit,
    onOpenReader: (LibraryItem, java.io.File?, String?) -> Unit,
    onOpenError: (String) -> Unit,
    onBookletClick: (BookletSheetRequest) -> Unit
) {
    val context = LocalContext.current
    val subjectKey = subject.routeKey()
    val videos = subject.videos.orEmpty()
    val bookletCount = videos.bookletPaths().size

    var filter by remember(subjectKey) { mutableStateOf(SubjectFilter.ALL) }
    var searchOpen by remember(subjectKey) { mutableStateOf(false) }
    var query by remember(subjectKey) { mutableStateOf("") }

    // Booklet catalog lookups for this subject, computed once per
    // envelope list: chapter number per video (the sort key) and
    // chapter metadata per booklet path (strip title / page chip).
    val chapters = remember(subjectKey, videos) {
        videos.associate { item ->
            val id = item.id
            (id ?: "") to (if (id.isNullOrBlank()) null else BookletCatalog.chapterForVideo(context, id))
        }
    }
    val bookletMetas = remember(subjectKey, videos) {
        videos.bookletPaths().associateWith { BookletCatalog.meta(context, it) }
    }
    LaunchedEffect(subjectKey) {
        val paths = videos.bookletPaths()
        if (paths.isNotEmpty()) BookletDownloadManager.refresh(context, paths)
    }
    val bookletStates by BookletDownloadManager.states.collectAsState()
    val readingFractions by BookletProgress.flow.collectAsState()

    val trimmedQuery = query.trim()
    val filteredIndexed = videos.withIndex().filter { (_, item) ->
        val byFilter = when (filter) {
            SubjectFilter.ALL -> true
            SubjectFilter.DOWNLOADED -> viewModel.isItemDownloaded(item)
            SubjectFilter.WITH_BOOKLET -> !item.booklet.isNullOrBlank()
        }
        val byQuery = trimmedQuery.isEmpty() ||
            item.title?.contains(trimmedQuery, ignoreCase = true) == true
        byFilter && byQuery
    }
    // Linked videos first, ordered by book chapter then by their
    // original library order; unlinked videos after, in their
    // original order (the 1.2.17 topic grouping is superseded by
    // this chapter grouping per the approved design).
    val linked = filteredIndexed
        .filter { !it.value.booklet.isNullOrBlank() }
        .sortedWith(
            compareBy<IndexedValue<LibraryItem>> {
                chapters[it.value.id ?: ""] ?: Int.MAX_VALUE
            }.thenBy { it.index }
        )
    val unlinked = filteredIndexed.filter { it.value.booklet.isNullOrBlank() }

    LazyColumn(
        Modifier.fillMaxSize().background(libBackground()),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                LibIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack, "رجوع إلى المواد",
                    onClick = onBack
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        subject.name ?: "موضوع",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 21.sp, color = libHeading(), lineHeight = 28.sp
                    )
                    Text(
                        if (bookletCount > 0) {
                            "${videos.size} فيديو · $bookletCount ${libBookletCountWord(bookletCount)} من كتاب ${subject.name ?: ""}"
                        } else {
                            "${videos.size} فيديو"
                        },
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 12.5.sp, color = libMuted()
                    )
                }
                LibIconButton(
                    Icons.Default.Search, "بحث في المادة",
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }
                )
            }
        }
        if (searchOpen) {
            item {
                LibrarySearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "ابحث باسم الفيديو في هذه المادة…"
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibFilterChip("الكل", filter == SubjectFilter.ALL) { filter = SubjectFilter.ALL }
                LibFilterChip("المحمّلة فقط", filter == SubjectFilter.DOWNLOADED) { filter = SubjectFilter.DOWNLOADED }
                if (bookletCount > 0) {
                    LibFilterChip("لها كتيّب", filter == SubjectFilter.WITH_BOOKLET) { filter = SubjectFilter.WITH_BOOKLET }
                }
            }
        }

        if (linked.isEmpty() && unlinked.isEmpty()) {
            item {
                Text(
                    "لا فيديوهات مطابقة",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 13.sp, color = libMuted(),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        if (linked.isNotEmpty()) {
            item { LibraryGroupLabel("بترتيب فصول الكتاب") }
            items(linked) { indexed ->
                val item = indexed.value
                val path = item.booklet?.takeIf { it.isNotBlank() }
                LibraryVideoRow(
                    item = item,
                    viewModel = viewModel,
                    downloadingIds = downloadingIds,
                    downloadProgress = downloadingProgress[item.id ?: item.fileUrl ?: ""],
                    downloadError = downloadErrors[item.id ?: item.fileUrl ?: ""],
                    bookletMeta = path?.let { bookletMetas[it] },
                    bookletDlState = path?.let { bookletStates[it] },
                    bookletDownloaded = path != null &&
                        (bookletStates[path] is BookletDlState.Downloaded ||
                            BookletDownloadManager.isDownloaded(context, path)),
                    bookletReadingFraction = path?.let { readingFractions[it] } ?: 0f,
                    onOpenReader = { file, url -> onOpenReader(item, file, url) },
                    onOpenError = onOpenError,
                    onBookletClick = {
                        if (path != null) {
                            onBookletClick(BookletSheetRequest(item, subject.name, path))
                        }
                    }
                )
            }
        }
        if (unlinked.isNotEmpty()) {
            item {
                LibraryGroupLabel(
                    "فيديوهات تأسيسية",
                    countText = "بلا كتيّب مقابل في الكتاب"
                )
            }
            items(unlinked) { indexed ->
                val item = indexed.value
                LibraryVideoRow(
                    item = item,
                    viewModel = viewModel,
                    downloadingIds = downloadingIds,
                    downloadProgress = downloadingProgress[item.id ?: item.fileUrl ?: ""],
                    downloadError = downloadErrors[item.id ?: item.fileUrl ?: ""],
                    bookletMeta = null,
                    bookletDlState = null,
                    bookletDownloaded = false,
                    bookletReadingFraction = 0f,
                    onOpenReader = { file, url -> onOpenReader(item, file, url) },
                    onOpenError = onOpenError,
                    onBookletClick = {}
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

/**
 * One video row of the subject page (design .vrow), carrying ALL
 * the behaviours the pre-1.2.37 row had: play a downloaded video,
 * stream via «مشاهدة مباشرة» (folded into the play circle — the
 * design's only play affordance), download with live progress,
 * download error text, and delete with confirmation. Under a
 * linked video, the booklet strip (design .booklet) opens the
 * booklet sheet.
 */
@Composable
private fun LibraryVideoRow(
    item: LibraryItem,
    viewModel: LibraryViewModel,
    downloadingIds: Set<String>,
    downloadProgress: Float? = null,
    downloadError: String? = null,
    bookletMeta: BookletMeta? = null,
    bookletDlState: BookletDlState? = null,
    bookletDownloaded: Boolean = false,
    bookletReadingFraction: Float = 0f,
    onOpenReader: (java.io.File?, String?) -> Unit = { _, _ -> },
    onOpenError: (String) -> Unit = {},
    onBookletClick: () -> Unit = {}
) {
    val id = item.id ?: item.fileUrl ?: ""
    val isDownloading = id in downloadingIds
    val isDownloaded = viewModel.isItemDownloaded(item)
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun openDownloaded() {
        coroutineScope.launch {
            viewModel.openItem(item).fold(
                onSuccess = { file -> onOpenReader(file, null) },
                onFailure = { onOpenError(it.message ?: "تعذر فتح الملف المحمّل — أعد تحميله") }
            )
        }
    }

    LibCard(radius = 20.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Play circle (.play): filled maroon once downloaded.
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isDownloaded) LibColors.Maroon else libPlayTint())
                        .clickable {
                            when {
                                isDownloaded -> openDownloaded()
                                !item.fileUrl.isNullOrEmpty() -> onOpenReader(null, item.fileUrl)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = "تشغيل",
                        tint = if (isDownloaded) Color.White else LibColors.Maroon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                ) {
                    Text(
                        item.title ?: "بدون عنوان",
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = libInk(), lineHeight = 21.sp
                    )
                    if (!item.description.isNullOrBlank()) {
                        Text(
                            item.description!!,
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 12.sp, color = libMuted(), maxLines = 2
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            libSizeText(item.size),
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 12.sp, color = libMuted()
                        )
                        Text("·", fontSize = 12.sp, color = libMuted())
                        if (isDownloaded) {
                            Text(
                                "محمّل على الجهاز",
                                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 12.sp, color = LibColors.Green
                            )
                        } else {
                            Text(
                                "مشاهدة مباشرة أو تنزيل",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 12.sp, color = libMuted()
                            )
                        }
                    }
                }
                when {
                    isDownloading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (downloadProgress != null && downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.width(56.dp),
                                color = LibColors.Maroon
                            )
                            Text(
                                "${(downloadProgress * 100).toInt()}٪",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 11.sp, color = libMuted()
                            )
                        } else {
                            CircularProgressIndicator(
                                Modifier.size(36.dp),
                                color = LibColors.Maroon, strokeWidth = 3.dp
                            )
                        }
                    }
                    isDownloaded -> IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete, "حذف",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(libGoldTint())
                            .clickable { viewModel.downloadItem(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download, "تحميل",
                            tint = LibColors.GoldText, modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (downloadError != null) {
                Text(
                    downloadError,
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Booklet strip (.booklet): only under a linked video.
            val bookletPath = item.booklet?.takeIf { it.isNotBlank() }
            if (bookletPath != null) {
                Box(
                    Modifier
                        .fillMaxWidth().padding(top = 10.dp)
                        .height(1.dp)
                        .background(libLine())
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onBookletClick() }
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(libGoldTint()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MenuBook, contentDescription = null,
                                tint = LibColors.GoldText, modifier = Modifier.size(19.dp)
                            )
                        }
                        if (bookletDownloaded) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-3).dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(LibColors.Gold),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(9.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "كتيّب القسم",
                            fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = LibColors.Maroon
                        )
                        Text(
                            bookletMeta?.title?.takeIf { it.isNotBlank() }
                                ?: item.title ?: "",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.5.sp, color = libMuted(), maxLines = 1
                        )
                    }
                    val pages = bookletMeta?.pages
                    if (pages != null && pages > 0) {
                        LibChip("$pages صفحة", pdfStyle = true)
                    }
                    Icon(
                        Icons.Default.ChevronLeft, contentDescription = null,
                        tint = LibColors.Chevron, modifier = Modifier.size(18.dp)
                    )
                }
                // Slim state line under the strip: a running booklet
                // download (% bar) or the reading progress (> 0).
                val dlProgress = (bookletDlState as? BookletDlState.Downloading)?.progress
                if (dlProgress != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibGoldBar(dlProgress, height = 4.dp, modifier = Modifier.weight(1f))
                        Text(
                            "${(dlProgress.coerceIn(0f, 1f) * 100).toInt()}٪",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.sp, color = libMuted()
                        )
                    }
                } else if (bookletReadingFraction > 0f) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibGoldBar(bookletReadingFraction, height = 4.dp, modifier = Modifier.weight(1f))
                        Text(
                            "قرأت ${(bookletReadingFraction.coerceIn(0f, 1f) * 100).toInt()}٪",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.sp, color = LibColors.GoldText
                        )
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
                        tint = if (sy.medpro.watani.ui.theme.wataniDarkTheme()) GoldAccent else MaroonPrimary,
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
