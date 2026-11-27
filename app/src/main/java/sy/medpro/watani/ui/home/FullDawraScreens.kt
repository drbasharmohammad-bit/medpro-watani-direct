package sy.medpro.watani.ui.home
import sy.medpro.watani.ui.library.libGoldTint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.DawraSubjectSessions
import sy.medpro.watani.data.DownloadedSectionEntity
import sy.medpro.watani.data.ManifestFileEntry
import sy.medpro.watani.data.dawraSessionPath
import sy.medpro.watani.data.listDawrat
import sy.medpro.watani.data.manifestDawraCounts
import sy.medpro.watani.data.mergeDawraInfos
import sy.medpro.watani.data.officialSubjectRank
import sy.medpro.watani.data.resolveDawraInputs
import sy.medpro.watani.ui.SlimSubBar
import sy.medpro.watani.ui.SlimBarTitle
import sy.medpro.watani.ui.library.LibCard
import sy.medpro.watani.ui.library.LibColors
import sy.medpro.watani.ui.library.libBackground
import sy.medpro.watani.ui.library.libHeading
import sy.medpro.watani.ui.library.libMuted
import sy.medpro.watani.ui.theme.NotoKufiFontFamily

/**
 * «دورة كاملة» (build 1.2.26, doctor's order 16 Nov 2026): the
 * third path at the top of بنك الوطني, beside the per-subject paths
 * (which keep their own «حسب المواضيع | حسب السنوات» toggle,
 * untouched). A full dawra assembles one named session («أيار
 * 2026») from every downloaded subject shard into ONE sitting on
 * the existing quiz engine — like the real exam.
 */

/** Featured entry card at the top of the subject list —
 * white LibCard of the approved section model (02): ivory letter
 * tile, title, description, short gold underline.
 *
 * 1.2.49: while «دورة كاملة» is hard-locked (FullDawraLock) the
 * card stays in its place but wears the lock on its face — a gold
 * lock badge («مقفلة حاليًا») under the title and a lock instead
 * of the chevron — and the tap only raises the lock message (the
 * view model refuses the navigation). */
@Composable
fun FullDawraEntryCard(onClick: () -> Unit) {
    LibCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(libGoldTint()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = LibColors.Maroon,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "دورة كاملة",
                    fontFamily = NotoKufiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = libHeading()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "ادرس دورة كاملة مجمّعة من كل المواد بجلسة واحدة — كالامتحان الحقيقي",
                    fontSize = 13.sp,
                    color = libMuted()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(LibColors.Gold)
                )
                if (sy.medpro.watani.data.FullDawraLock.isLocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FullDawraLockBadge()
                }
            }
            FullDawraEntryTrailingIcon()
        }
    }
}

/** The card's trailing glyph: a gold lock while the feature is
 * hard-locked (1.2.49), the usual chevron otherwise. Extracted
 * from the card so the card itself stays within the detekt
 * method-length gate. */
@Composable
private fun FullDawraEntryTrailingIcon() {
    if (sy.medpro.watani.data.FullDawraLock.isLocked) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = LibColors.Gold,
            modifier = Modifier.size(20.dp)
        )
    } else {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = LibColors.Chevron,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The gold lock pill on the entry card's face while the feature
 * is hard-locked (1.2.49) — extracted from the card so the card
 * itself stays within the detekt method-length gate. */
@Composable
private fun FullDawraLockBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(libGoldTint())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = LibColors.Gold,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            sy.medpro.watani.data.FullDawraLock.LOCK_BADGE,
            fontFamily = NotoKufiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            color = LibColors.Maroon
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDawraListScreen(
    inputs: List<DawraSubjectSessions>,
    loading: Boolean,
    onDawraClick: (String) -> Unit,
    onBack: () -> Unit,
    // 1.2.32: the standalone-file source — per-subject tables of the
    // downloaded dawrat_sessions files plus the manifest's counts, so
    // a dawra is listed (and complete) from its own file alone, and
    // even before anything is downloaded its file shows as available.
    fileInputs: List<DawraSubjectSessions> = emptyList(),
    files: Map<String, ManifestFileEntry> = emptyMap()
) {
    val dawrat = remember(inputs, fileInputs, files) {
        mergeDawraInfos(listDawrat(inputs), listDawrat(fileInputs), manifestDawraCounts(files))
    }
    Scaffold(
        topBar = {
            SlimSubBar(onBack = onBack, containerColor = MaterialTheme.colorScheme.primary) {
                SlimBarTitle("دورة كاملة")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(libBackground())
                .padding(16.dp)
        ) {
            when {
                loading && dawrat.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                dawrat.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (inputs.isEmpty()) {
                                "لا توجد مواد منزّلة بعد — حمّل مواد البنك أولًا لتظهر دوراتها الكاملة هنا."
                            } else {
                                "المواد المنزّلة عندك لا تحوي دورات مسمّاة بعد."
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            // Bank hero of the approved section model
                            // (02): white card, ivory tile, gold count.
                            LibCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(62.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(libGoldTint()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "و",
                                            fontFamily = NotoKufiFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 26.sp, lineHeight = 39.sp,
                                            color = LibColors.MaroonDeep
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "البنك",
                                            fontFamily = NotoKufiFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 21.sp, lineHeight = 32.sp,
                                            color = libHeading()
                                        )
                                        Text(
                                            "أسئلة الدورات كاملة… بشروحاتها",
                                            fontSize = 13.sp,
                                            color = libMuted()
                                        )
                                        Text(
                                            "${dawrat.sumOf { it.totalQuestions }} سؤالًا",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = LibColors.GoldText
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                "اختر الدورة لدراستها كاملة:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "كل دورة إما كاملة عندك بملفها المستقل، أو تُجمَّع من موادها المنزّلة عندك.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        items(dawrat) { dawra ->
                            LibCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onDawraClick(dawra.name) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(libGoldTint()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            dawra.name.firstOrNull()?.toString() ?: "د",
                                            fontFamily = NotoKufiFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = LibColors.MaroonDeep
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            dawra.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${dawra.totalQuestions} سؤالًا • " + when {
                                                dawra.standaloneFile -> "كاملة عندك بملفها المستقل"
                                                dawra.subjectsPresent > 0 -> "من ${dawra.subjectsPresent} مادة منزّلة"
                                                else -> "متاحة كملف دورة كامل"
                                            },
                                            fontSize = 12.sp,
                                            color = libMuted()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = LibColors.Chevron,
                                        modifier = Modifier.size(20.dp)
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
fun FullDawraDetailScreen(
    dawraName: String,
    inputs: List<DawraSubjectSessions>,
    files: Map<String, ManifestFileEntry>,
    downloadedSections: List<DownloadedSectionEntity>,
    downloadingPaths: Set<String>,
    downloadingProgress: Map<String, Float> = emptyMap(),
    onDownloadSection: (String, ManifestFileEntry) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    // 1.2.32: per-subject tables of the downloaded standalone dawra
    // files. When this dawra's own file is among them it is the
    // coverage source — the dawra is complete from that file alone
    // and the missing-subjects machinery below steps aside.
    fileInputs: List<DawraSubjectSessions> = emptyList()
) {
    // Coverage is computed from the manifest vs the local registry:
    // a subject is «missing» only when its shard is not on the
    // device at all. A downloaded subject with no session for this
    // dawra contributes zero at the source — it is NOT missing and
    // is never listed as such (no hidden shortfall, no false one).
    val allPaths = remember(files) {
        files.keys
            .filter { it.startsWith("questions/") && it.endsWith(".json") }
            .sortedWith(
                compareBy(
                    { officialSubjectRank(it.removePrefix("questions/").removeSuffix(".json")) },
                    { it }
                )
            )
    }
    val downloadedPathSet = remember(downloadedSections) {
        downloadedSections.map { it.sectionPath }.toSet()
    }
    val missingPaths = allPaths.filter { it !in downloadedPathSet }
    // 1.2.32: coverage source — the standalone file's tables when
    // the file is downloaded, the shard tables otherwise.
    val effectiveInputs = remember(inputs, fileInputs, dawraName) {
        resolveDawraInputs(inputs, fileInputs, dawraName)
    }
    val fromStandaloneFile = remember(effectiveInputs) {
        effectiveInputs.any { it.path.startsWith("dawrat_sessions/") }
    }
    val sessionPath = remember(dawraName) { dawraSessionPath(dawraName) }
    val sessionEntry = files[sessionPath]
    val sessionDownloaded = sessionPath in downloadedPathSet
    val totalForDawra = remember(effectiveInputs, dawraName) {
        effectiveInputs.sumOf { it.sessionCounts[dawraName] ?: 0 }
    }
    val perSubject = remember(effectiveInputs, dawraName) {
        effectiveInputs.mapNotNull { input ->
            val count = input.sessionCounts[dawraName] ?: 0
            if (count > 0) input.subjectName to count else null
        }
    }

    Scaffold(
        topBar = {
            SlimSubBar(onBack = onBack, containerColor = MaterialTheme.colorScheme.primary) {
                SlimBarTitle("دورة $dawraName")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(libBackground())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LibCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(libGoldTint()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dawraName.firstOrNull()?.toString() ?: "د",
                                fontFamily = NotoKufiFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = LibColors.MaroonDeep
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "دورة $dawraName — كاملة",
                                fontFamily = NotoKufiFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = libHeading()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (fromStandaloneFile) {
                                    "من ملف الدورة المستقل عندك"
                                } else {
                                    "مجمّعة من ${effectiveInputs.size} مادة منزّلة عندك"
                                },
                                fontSize = 13.sp,
                                color = libMuted()
                            )
                            Text(
                                "$totalForDawra سؤالًا",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = LibColors.GoldText
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onStart,
                    enabled = totalForDawra > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = LibColors.Maroon),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بدء الدورة كاملة ($totalForDawra سؤال)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 1.2.32: the standalone dawra file — one download makes
            // the whole dawra work offline from its own file alone.
            if (fromStandaloneFile) {
                item {
                    LibCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "هذه الدورة عندك بملفها المستقل — كاملة وتعمل أوفلاين من ملفها وحده، بلا حاجة لأي مادة أخرى.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LibColors.Maroon,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else if (sessionEntry != null && !sessionDownloaded) {
                item {
                    LibCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "الأسرع: ملف الدورة المستقل",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = LibColors.Maroon
                            )
                            Text(
                                "حمّل ملف هذه الدورة كاملًا بضغطة واحدة فتعمل عندك كاملة أوفلاين من ملفها وحده — بدل تحميل مواد البنك كلها.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
                item {
                    SectionCard(
                        path = sessionPath,
                        displayName = "ملف دورة $dawraName الكامل",
                        entry = sessionEntry,
                        isDownloaded = false,
                        isDownloading = downloadingPaths.contains(sessionPath),
                        downloadProgress = downloadingProgress[sessionPath],
                        onDownloadClick = { onDownloadSection(sessionPath, sessionEntry) },
                        onOpenClick = {}
                    )
                }
            }

            if (!fromStandaloneFile && missingPaths.isNotEmpty()) {
                item {
                    LibCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "الدورة مجمّعة عندك بـ$totalForDawra سؤالًا — حمّل المواد الناقصة لتكملها:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = LibColors.Maroon
                            )
                            Text(
                                "المواد التالية غير منزّلة على جهازك، وقد تحوي أسئلة إضافية من هذه الدورة.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
                items(missingPaths) { path ->
                    val entry = files[path]
                    if (entry != null) {
                        SectionCard(
                            path = path,
                            displayName = path.removePrefix("questions/").removeSuffix(".json"),
                            entry = entry,
                            isDownloaded = false,
                            isDownloading = downloadingPaths.contains(path),
                            downloadProgress = downloadingProgress[path],
                            onDownloadClick = { onDownloadSection(path, entry) },
                            onOpenClick = {}
                        )
                    }
                }
            } else if (!fromStandaloneFile) {
                item {
                    LibCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "كل مواد البنك منزّلة عندك — الدورة مكتملة بـ$totalForDawra سؤالًا.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LibColors.Maroon,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            if (perSubject.isNotEmpty()) {
                item {
                    Text(
                        "الأسئلة حسب المادة:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(perSubject) { (subjectName, count) ->
                    LibCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp, max = 76.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                subjectName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "($count سؤال)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else if (missingPaths.isEmpty()) {
                item {
                    Text(
                        "لا توجد أسئلة من هذه الدورة بالمواد المنزّلة عندك.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
