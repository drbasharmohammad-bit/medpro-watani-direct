package sy.medpro.watani.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.DownloadedSectionEntity
import sy.medpro.watani.data.ManifestFileEntry
import sy.medpro.watani.data.SubjectEnvelope
import sy.medpro.watani.ui.cases.CasesScreen
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.featuredCardBackground
import sy.medpro.watani.ui.viewmodel.AppTab

val universitiesMap = mapOf(
    "damascus" to "جامعة دمشق",
    "aleppo" to "جامعة حلب",
    "lattakia" to "جامعة اللاذقية",
    "homs" to "جامعة حمص",
    "hama" to "جامعة حماة"
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
    onOpenUniversity: (String, String) -> Unit,
    onCaseClick: (sy.medpro.watani.data.ClinicalCaseDto) -> Unit
) {
    var selectedBankSection by remember { mutableStateOf(BankSection.WATANI) }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoadingManifest) {
        if (!isLoadingManifest) isRefreshing = false
    }
    LaunchedEffect(currentTab) {
        selectedBankSection = when (currentTab) {
            AppTab.UNIVERSITIES -> BankSection.UNIVERSITIES
            AppTab.UWORLD -> BankSection.UWORLD
            else -> BankSection.WATANI
        }
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
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 12.dp
            ) {
                BankSection.entries.forEach { section ->
                    Tab(
                        selected = selectedBankSection == section,
                        onClick = { selectedBankSection = section },
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
                                val casesDownloaded = downloadedMap["cases.json"] != null &&
                                    (downloadedMap["cases.json"]?.count ?: 0) >= (casesEntry?.count ?: 1)
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
                                        val isDownloaded = downloadedEntity != null && downloadedEntity.count >= (entry.count ?: 0)
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
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = featuredCardBackground()),
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عدد الأسئلة: ${entry.count ?: 0} | الحجم: ${((entry.size ?: 0) / 1024)} كيلوبايت",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isDownloading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (downloadProgress != null && downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.width(72.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${(downloadProgress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                } else if (isDownloaded) {
                    Button(
                        onClick = onOpenClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فتح القسم", fontSize = 14.sp)
                    }
                } else {
                    OutlinedButton(border = BorderStroke(1.dp, MaroonPrimary),
                        onClick = onDownloadClick,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
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
                text = "جامعات الوطن العربي / سورية",
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
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUniversity(key, name) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
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
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مواضيع: $subjectName", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
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
                    Button(shape = MaterialTheme.shapes.medium,
                        onClick = onDownloadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("تحميل القسم الآن")
                    }
                }
            } else {
                val topics = subjectEnvelope.topics ?: emptyList()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            "اختر الموضوع للبدء بالأسئلة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(topics) { topic ->
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTopicClick(topic.name ?: "") }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(topic.name ?: "موضوع", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    onBack: () -> Unit
) {
    val prefix = "dawrat_jami3at/$universityKey/"
    val univFiles = files.filterKeys { it.startsWith(prefix) && it.endsWith(".json") }
    val downloadedMap = downloadedSections.associateBy { it.sectionPath }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(universityName, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
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
                val isDownloaded = downloadedEntity != null && downloadedEntity.count >= (entry.count ?: 0)
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
