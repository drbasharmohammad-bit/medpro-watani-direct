package sy.medpro.watani.ui.home
import sy.medpro.watani.ui.library.libGoldTint

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.DownloadedSectionEntity
import sy.medpro.watani.data.ManifestFileEntry
import sy.medpro.watani.data.isSectionUpToDate
import sy.medpro.watani.data.SubjectEnvelope
import sy.medpro.watani.ui.SlimSubBar
import sy.medpro.watani.ui.cases.CasesScreen
import androidx.compose.ui.draw.clip
import sy.medpro.watani.ui.library.LibCard
import sy.medpro.watani.ui.library.LibColors
import sy.medpro.watani.ui.library.LibGoldBar
import sy.medpro.watani.ui.library.libInk
import sy.medpro.watani.ui.library.libMuted
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.viewmodel.AppTab

val universitiesMap = mapOf(
    "damascus" to "جامعة دمشق",
    "aleppo" to "جامعة حلب",
    "lattakia" to "جامعة اللاذقية",
    "homs" to "جامعة حمص",
    "hama" to "جامعة حماة",
    "idlib" to "جامعة إدلب",
    "tartus" to "جامعة طرطوس"
)

enum class BankSection(val title: String) {
    WATANI("بنك الوطني"),
    UNIVERSITIES("الجامعات"),
    UWORLD("UWorld"),
    CASES("الحالات السريرية")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentTab: AppTab,
    manifestVersion: String?,
    files: Map<String, ManifestFileEntry>,
    isLoadingManifest: Boolean,
    manifestError: String?,
    downloadedSections: List<DownloadedSectionEntity>,
    downloadingPaths: Set<String>,
    downloadingProgress: Map<String, Float> = emptyMap(),
    downloadError: String?,
    onRetryExplanations: (() -> Unit)? = null,
    casesList: List<sy.medpro.watani.data.ClinicalCaseDto>,
    onTabSelected: (AppTab) -> Unit,
    onRefreshManifest: () -> Unit,
    onDownloadSection: (String, ManifestFileEntry) -> Unit,
    onOpenSubject: (String, String, AppTab) -> Unit,
    // «دورة كاملة» (1.2.26): the third path at the top of بنك
    // الوطني, beside the per-subject paths — a whole named dawra
    // assembled across subjects into one sitting.
    onOpenFullDawra: () -> Unit = {},
    onOpenUniversity: (String, String) -> Unit,
    onCaseClick: (sy.medpro.watani.data.ClinicalCaseDto) -> Unit,
    onBankSectionsShown: () -> Unit = {},
    // Back-navigation fix (1.2.22): the bank section is HOISTED to
    // MainActivity (rememberSaveable). It used to be a local
    // `remember` reset by a LaunchedEffect(currentTab) on every
    // recomposition — so drilling بنك → الجامعات → جامعة → مادة →
    // كويز and stepping back through the (healthy) view-model stack
    // landed the student on the Watani list (the section start)
    // instead of the universities list they came from. Hoisted +
    // saveable, the section survives detail-screen detours exactly
    // as the student left it; requestTab still presets it on an
    // explicit UNIVERSITIES/UWORLD tab entry.
    bankSection: BankSection,
    onBankSectionChange: (BankSection) -> Unit
) {
    val selectedBankSection = bankSection
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoadingManifest) {
        if (!isLoadingManifest) isRefreshing = false
    }
    // Bank auto-update (build 1.2.21): showing a question-bank tab runs
    // the same silent stale-check the self-loading pack screens use —
    // installed banks refresh in the background (MainViewModel ->
    // WataniRepository.refreshInstalledBankSections) without blocking
    // the list. The cases tab is a self-loading pack flow, not a bank.
    LaunchedEffect(selectedBankSection) {
        if (selectedBankSection != BankSection.CASES) onBankSectionsShown()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Segmented section selector inside Question Bank (No duplicate TopAppBar or Bottom NavigationBar)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedBankSection.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = LibColors.Maroon,
                edgePadding = 12.dp
            ) {
                BankSection.entries.forEach { section ->
                    Tab(
                        selected = selectedBankSection == section,
                        onClick = { onBankSectionChange(section) },
                        text = {
                            Text(
                                text = section.title,
                                fontWeight = if (selectedBankSection == section) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            if (downloadError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = downloadError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        if (onRetryExplanations != null) {
                            Button(
                                onClick = onRetryExplanations,
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("إعادة محاولة الشروحات", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true; onRefreshManifest() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoadingManifest && files.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("جاري جلب الفهرس والأسئلة...", color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                    manifestError != null && files.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Text("خطأ في الاتصال: $manifestError", color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(shape = MaterialTheme.shapes.medium, onClick = onRefreshManifest) {
                                    Text("إعادة المحاولة")
                                }
                            }
                        }
                    }
                    else -> {
                        val downloadedMap = downloadedSections.associateBy { it.sectionPath }

                        when (selectedBankSection) {
                            BankSection.CASES -> {
                                val casesEntry = files["cases.json"]
                                val casesEntity = downloadedMap["cases.json"]
                                // Hash-aware verdict (see isSectionUpToDate). With no
                                // manifest entry to compare against, keep the previous
                                // count-only verdict (count >= 1).
                                val casesDownloaded = if (casesEntry != null) {
                                    isSectionUpToDate(casesEntity, casesEntry)
                                } else {
                                    casesEntity != null && casesEntity.count >= 1
                                }
                                if (casesList.isNotEmpty() && casesDownloaded) {
                                    CasesScreen(cases = casesList, onCaseClick = onCaseClick)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        if (casesEntry != null) {
                                            SectionCard(
                                                path = "cases.json",
                                                displayName = "الحالات السريرية التفاعلية",
                                                entry = casesEntry,
                                                isDownloaded = casesDownloaded,
                                                isDownloading = downloadingPaths.contains("cases.json"),
                                                onDownloadClick = { onDownloadSection("cases.json", casesEntry) },
                                                onOpenClick = { onTabSelected(AppTab.CASES) }
                                            )
                                        } else {
                                            CasesScreen(cases = casesList, onCaseClick = onCaseClick)
                                        }
                                    }
                                }
                            }
                            BankSection.UNIVERSITIES -> {
                                UniversityTabContent(onOpenUniversity = onOpenUniversity)
                            }
                            BankSection.WATANI, BankSection.UWORLD -> {
                                val prefix = if (selectedBankSection == BankSection.WATANI) "questions/" else "uworld/"
                                val targetTab = if (selectedBankSection == BankSection.WATANI) AppTab.BANK else AppTab.UWORLD
                                val tabFiles = files.filterKeys { it.startsWith(prefix) && it.endsWith(".json") }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // «دورة كاملة» (1.2.26): entry at the
                                    // section level, before any subject
                                    // is chosen — وطني only, the UWORLD
                                    // list is unchanged.
                                    if (selectedBankSection == BankSection.WATANI) {
                                        item {
                                            FullDawraEntryCard(onClick = onOpenFullDawra)
                                        }
                                    }
                                    item {
                                        Text(
                                            text = "${selectedBankSection.title} (${tabFiles.size} قسم)",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "قم بتحميل القسم لعرض المواضيع والبدء بالدراسة دون اتصال بالإنترنت",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    items(tabFiles.entries.toList()) { (path, entry) ->
                                        val downloadedEntity = downloadedMap[path]
                                        val isDownloaded = isSectionUpToDate(downloadedEntity, entry)
                                        val isDownloading = downloadingPaths.contains(path)
                                        val subjectDisplayName = path.substringAfterLast("/").substringBeforeLast(".json")

                                        SectionCard(
                                            path = path,
                                            displayName = subjectDisplayName,
                                            entry = entry,
                                            isDownloaded = isDownloaded,
                                            isDownloading = isDownloading,
                                            downloadProgress = downloadingProgress[path],
                                            onDownloadClick = { onDownloadSection(path, entry) },
                                            onOpenClick = { onOpenSubject(path, subjectDisplayName, targetTab) }
                                        )
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
fun SectionCard(
    path: String,
    displayName: String,
    entry: ManifestFileEntry,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float? = null,
    onDownloadClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    // Approved identity (models 01/02): clean white LibCard, no
    // coloured border; the meta line shows the question count and
    // the download state only — the raw size line is gone for good.
    LibCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction-2 subject icons: resolved through the ONE
                // canonical resolver (ui.icons.subjectIconRes — full
                // university-variant coverage with name normalization).
                // 1.2.43 fix: this card previously consulted the small
                // 29-key map in ui/AppIconMaps, so 66 of the 139
                // university files rendered name-only rows. A name the
                // resolver still doesn't know gets the unified fallback
                // glyph — a section row NEVER renders iconless.
                val subjectIconRes = sy.medpro.watani.ui.icons.subjectIconRes(displayName)
                if (subjectIconRes != null) {
                    Image(
                        painter = painterResource(subjectIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = LibColors.Maroon,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = libInk()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عدد الأسئلة: ${entry.count ?: 0}" + when {
                            isDownloading -> " • جارٍ التحميل"
                            isDownloaded -> " • محمّل على جهازك"
                            else -> " • غير محمّل بعد"
                        },
                        fontSize = 13.sp,
                        color = libMuted()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isDownloading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (downloadProgress != null && downloadProgress > 0f) {
                            LibGoldBar(
                                fraction = downloadProgress.coerceIn(0f, 1f),
                                modifier = Modifier.width(72.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${(downloadProgress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = libMuted()
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = LibColors.Maroon,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                } else if (isDownloaded) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onOpenClick,
                            colors = ButtonDefaults.buttonColors(containerColor = LibColors.Maroon),
                            shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فتح القسم", fontSize = 14.sp)
                        }
                    }
                } else {
                    OutlinedButton(border = BorderStroke(1.dp, LibColors.Maroon),
                        onClick = onDownloadClick,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LibColors.Maroon)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحميل", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UniversityTabContent(onOpenUniversity: (String, String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "جامعات",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "اختر الجامعة للاطلاع على مقررات وأسئلة كل جامعة",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(universitiesMap.entries.toList()) { (key, name) ->
            LibCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenUniversity(key, name) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(libGoldTint()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = LibColors.Maroon)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = libInk())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSelectionScreen(
    subjectName: String,
    subjectEnvelope: SubjectEnvelope?,
    downloaded: Boolean,
    onDownloadClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    onSessionClick: (String, String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    englishPath: String? = null,
    onOpenEnglish: (String) -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f
) {
    // «حسب السنوات» (مواصفة الدكتور 21:51): المواد التي تحمل sessions[]
    // بالشاردة المنزّلة تعرض مبدّلًا بين عرض المواضيع (الأصلي، لا يتغير)
    // وعرض الجلسات. مادة بلا جلسات لا ترى المبدّل إطلاقًا — صفر انحدار.
    val sessions = subjectEnvelope?.sessions ?: emptyList()
    // Back-navigation fix (1.2.22): rememberSaveable — a plain
    // `remember` reset the toggle on return from a session quiz, so
    // the student who came from «حسب السنوات» landed back on the
    // topics view (a lost step state).
    var byYears by androidx.compose.runtime.saveable.rememberSaveable(subjectName) { mutableStateOf(false) }
    val showYears = byYears && sessions.isNotEmpty()
    Scaffold(
        topBar = {
            // Slim chrome (1.2.7): 48dp sub-bar instead of the 64dp block.
            SlimSubBar(onBack = onBack, containerColor = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Same canonical resolver as SectionCard (1.2.43)
                    // + the same unified fallback — the subject title
                    // bar never renders iconless either.
                    val titleIconRes = sy.medpro.watani.ui.icons.subjectIconRes(subjectName)
                    if (titleIconRes != null) {
                        Image(
                            painter = painterResource(titleIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showYears) "جلسات: $subjectName" else "مواضيع: $subjectName", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            if (!downloaded || subjectEnvelope == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("القسم غير محمل أو لم يتم تنزيل ملفاته بعد.", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isDownloading) {
                        // Fix 1.2.1: the prompt state used to show the button
                        // unchanged during an in-screen download — the tap
                        // looked dead. Show real progress instead.
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(0.7f),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("جاري تحميل القسم...", fontSize = 13.sp)
                    } else {
                        Button(shape = MaterialTheme.shapes.medium,
                            onClick = onDownloadClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("تحميل القسم الآن")
                        }
                    }
                }
            } else {
                val topics = subjectEnvelope.topics ?: emptyList()
                // ترتيب السنوات: ترتيب البيانات نفسه، ودلو «دورات قديمة»
                // (بلا سنة) أخيرًا دائمًا — فرز مستقر يبقي بقية الجلسات
                // بترتيب الشاردة كما هي.
                val orderedSessions = sessions.sortedWith(
                    compareBy { session ->
                        if (session.year == null || session.name?.trim() == "دورات قديمة") 1 else 0
                    }
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // مدخل ثالث بمستوى مدخلي المادة (المواضيع/السنوات) — بأمر
                    // الدكتور: «باللغة الإنجليزية» يفتح شاردة المادة
                    // الإنجليزية (questions_en/) بنفس تصفح المواضيع، ولا
                    // يظهر إطلاقًا إن غابت الشاردة من الفهرس.
                    if (englishPath != null) {
                        item {
                            LibCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenEnglish(englishPath) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "باللغة الإنجليزية", fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp, color = LibColors.Maroon
                                        )
                                        Text(
                                            "نفس أسئلة المادة بالإنجليزية (الشرح يبقى بالعربية)",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    }
                                    Icon(Icons.Default.Language, contentDescription = null, tint = LibColors.Maroon)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    if (sessions.isNotEmpty()) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !showYears,
                                    onClick = { byYears = false },
                                    label = { Text("حسب المواضيع") }
                                )
                                FilterChip(
                                    selected = showYears,
                                    onClick = { byYears = true },
                                    label = { Text("حسب السنوات") }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    item {
                        Text(
                            if (showYears) "اختر الجلسة للبدء بالأسئلة:" else "اختر الموضوع للبدء بالأسئلة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // Topic/session rows (giant-card fix, Nov 2026):
                    // rows are COMPACT and height-bounded exactly like
                    // the app's other lists (Anki subject picker): the
                    // card height is capped (52–76dp), the name takes
                    // the flexible width with at most 2 lines, and the
                    // count is a fixed trailing label. Previously these
                    // cards carried NO height bound of their own and the
                    // LazyColumn sat unbounded inside the fillMaxSize
                    // Box of a Scaffold nested in AnimatedContent — on
                    // the doctor's device the cards rendered as giant
                    // empty squares filling the screen (two topics per
                    // screen). The explicit bound makes that layout
                    // impossible regardless of ancestor measurement.
                    if (showYears) {
                        items(orderedSessions) { session ->
                            val sessionName = session.name ?: "جلسة"
                            val yearText = when (val y = session.year) {
                                is Double -> if (y % 1.0 == 0.0) y.toInt().toString() else y.toString()
                                null -> ""
                                else -> y.toString()
                            }
                            LibCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp, max = 76.dp),
                                onClick = { onSessionClick(session.id ?: sessionName, sessionName) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sessionName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        if (yearText.isNotEmpty()) {
                                            Text(
                                                yearText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "(${session.questions?.size ?: session.questionCount ?: 0} سؤال)",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    } else {
                    items(topics) { topic ->
                        LibCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp, max = 76.dp),
                            onClick = { onTopicClick(topic.name ?: "") }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(topic.name ?: "موضوع", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "(${topic.questions?.size ?: 0} سؤال)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversitySubjectsScreen(
    universityName: String,
    universityKey: String,
    files: Map<String, ManifestFileEntry>,
    downloadedSections: List<DownloadedSectionEntity>,
    downloadingPaths: Set<String>,
    downloadingProgress: Map<String, Float> = emptyMap(),
    onDownloadSection: (String, ManifestFileEntry) -> Unit,
    onOpenSubject: (String, String) -> Unit,
    onBack: () -> Unit,
    onBankSectionsShown: () -> Unit = {}
) {
    // Bank auto-update (build 1.2.21): same background stale-check as
    // the bank tabs — this university's installed subjects refresh
    // silently while the list is browsed.
    LaunchedEffect(universityKey) { onBankSectionsShown() }
    val prefix = "dawrat_jami3at/$universityKey/"
    val univFiles = files.filterKeys { it.startsWith(prefix) && it.endsWith(".json") }
    val downloadedMap = downloadedSections.associateBy { it.sectionPath }

    Scaffold(
        topBar = {
            // Slim chrome (1.2.7): 48dp sub-bar instead of the 64dp block.
            SlimSubBar(onBack = onBack, containerColor = MaterialTheme.colorScheme.primary) {
                Text(universityName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "مقررات وأسئلة $universityName (${univFiles.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(univFiles.entries.toList()) { (path, entry) ->
                val downloadedEntity = downloadedMap[path]
                val isDownloaded = isSectionUpToDate(downloadedEntity, entry)
                val isDownloading = downloadingPaths.contains(path)
                val subjectName = path.substringAfterLast("/").substringBeforeLast(".json")

                SectionCard(
                    path = path,
                    displayName = subjectName,
                    entry = entry,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = downloadingProgress[path],
                    onDownloadClick = { onDownloadSection(path, entry) },
                    onOpenClick = { onOpenSubject(path, subjectName) }
                )
            }
        }
    }
}
