package sy.medpro.watani.ui.cases

import sy.medpro.watani.ui.PremiumListSkeleton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.data.ClinicalCaseDto
import sy.medpro.watani.data.PackDownloadManager
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.activation.ActivationPromptDialog
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.icons.SubjectIcon
import sy.medpro.watani.ui.icons.subjectIconRes
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.GoldAccentDark
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseOutline

/**
 * الحالات الألمانية — its OWN section for `v1/cases_de.json` (436
 * German-language virtual-patient cases), per the doctor's explicit
 * order. The file has exactly the cases.json envelope/DTO shape, so
 * the list mirrors CasesScreen and the player is the shared
 * CasePlayerScreen; only the loading path and the section identity
 * are separate. Case text is German, so titles/presentations render
 * LTR inside the RTL app chrome.
 */
@Composable
fun GermanCasesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cases by remember { mutableStateOf<List<ClinicalCaseDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCase by remember { mutableStateOf<ClinicalCaseDto?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // ---- Unified activation gate (same pattern as FirstAidSectionScreen) ----
    // The ONLY entitlement check is WorkerActivationApi.isActivated —
    // the app's single unified activation state (stored token +
    // expiry, no network), shared with CasesScreen and First Aid.
    // The German list is ONE family: the first 10 cases in display
    // order are free, from the 11th on activation is required
    // (CaseAccessRules in CasesScreen.kt).
    var activated by remember { mutableStateOf(WorkerActivationApi.isActivated(context)) }
    var showActivation by remember { mutableStateOf(false) }
    LaunchedEffect(selectedCase) {
        // Re-read on entry and on return from the player.
        if (selectedCase == null) {
            activated = WorkerActivationApi.isActivated(context)
        }
    }
    if (showActivation) {
        ActivationPromptDialog(
            onDismiss = { showActivation = false },
            onActivated = {
                activated = true
                showActivation = false
            }
        )
    }

    // Disk-only reload (F6): reads the installed pack, silently
    // refreshes it if stale, and settles the screen state. NEVER
    // starts a download — a reload that did would loop on the
    // manager's completion signal.
    suspend fun reloadGermanCases() {
        isLoading = true
        errorMessage = null
        try {
            val repo = WataniRepository(context)
            var env = repo.getCasesDeEnvelope()
            // 1.2.6: silently refresh a stale local pack (see
            // WataniRepository.refreshSectionIfStale).
            if (env != null && repo.refreshSectionIfStale("cases_de.json")) {
                env = repo.getCasesDeEnvelope() ?: env
            }
            if (env != null && !env.cases.isNullOrEmpty()) {
                cases = env.cases!!
            } else {
                errorMessage = "تعذر تحميل الحالات الألمانية. تحقق من الاتصال وحاول مجددًا."
            }
        } catch (e: Exception) {
            errorMessage = "خطأ في الاتصال: ${e.localizedMessage ?: "تأكد من اتصالك بالإنترنت"}"
        } finally {
            isLoading = false
        }
    }

    // Entry point (cache audit fix F6, 1.2.45): with no pack on
    // disk, the first download is handed to the process-level
    // PackDownloadManager and the screen stays in its loading
    // state — leaving the screen no longer kills the transfer,
    // and the observers below reload from disk when it lands.
    suspend fun loadGermanCases() {
        val repo = WataniRepository(context)
        if (repo.getCasesDeEnvelope() == null) {
            isLoading = true
            errorMessage = null
            PackDownloadManager.startDownload(context, "cases_de.json")
        } else {
            reloadGermanCases()
        }
    }

    LaunchedEffect(Unit) { loadGermanCases() }

    // F6: the manager announces the pack's fate — completion
    // reloads from disk; failure also reloads, which settles the
    // screen with its error text (the retry calls loadGermanCases).
    val packState by PackDownloadManager.state.collectAsState()
    LaunchedEffect(packState.completedRuns["cases_de.json"]) {
        if (cases.isEmpty() && (packState.completedRuns["cases_de.json"] ?: 0) > 0) {
            reloadGermanCases()
        }
    }
    LaunchedEffect(packState.failed, packState.downloading) {
        if ("cases_de.json" in packState.failed &&
            "cases_de.json" !in packState.downloading && cases.isEmpty()
        ) {
            reloadGermanCases()
        }
    }

    if (selectedCase != null) {
        CasePlayerScreen(caseItem = selectedCase!!, onBack = { selectedCase = null }, german = true)
        return
    }

    DrawerScreenScaffold(title = "الحالات الألمانية (Deutsche Fälle)", onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                isLoading -> PremiumListSkeleton(caption = "جاري تحميل الحالات الألمانية...")
                errorMessage != null || cases.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PendingActions, contentDescription = null, tint = GoldAccentDark, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("تعذر تحميل الحالات الألمانية", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "حدث خطأ أثناء التحميل.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { scope.launch { loadGermanCases() } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    // Ranks on the FULL list (single family: all flags
                    // false), so search filtering never changes a rank.
                    val familyRanks = CaseAccessRules.familyRanks(List(cases.size) { false })
                    val filtered = cases.withIndex().filter { (_, caseItem) ->
                        searchQuery.isBlank() ||
                            caseItem.title?.contains(searchQuery, ignoreCase = true) == true ||
                            caseItem.specialty?.contains(searchQuery, ignoreCase = true) == true ||
                            caseItem.presentation?.contains(searchQuery, ignoreCase = true) == true
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("بحث في الحالات الألمانية...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaroonPrimary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filtered, key = { it.value.id ?: it.value.title ?: "" }) { indexed ->
                                val caseItem = indexed.value
                                val rankInFamily = familyRanks.getOrElse(indexed.index) { Int.MAX_VALUE }
                                val locked = CaseAccessRules.isLockedRank(rankInFamily, activated)
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = BorderStroke(1.dp, RoseOutline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Fresh read at the click (see CasesScreen).
                                            val nowActivated = WorkerActivationApi.isActivated(context)
                                            activated = nowActivated
                                            if (CaseAccessRules.isFreeRank(rankInFamily, nowActivated)) {
                                                selectedCase = caseItem
                                            } else {
                                                showActivation = true
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = MaroonPrimary.copy(alpha = 0.1f),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (subjectIconRes(caseItem.specialty) != null) {
                                                        SubjectIcon(caseItem.specialty, Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = caseItem.specialty ?: "Allgemeinmedizin",
                                                        color = MaroonPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = GoldAccent.copy(alpha = 0.2f),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = "+${caseItem.xpReward ?: 50} XP",
                                                    color = GoldAccentDark,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = caseItem.title ?: "Klinischer Fall",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = MaroonPrimary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (locked) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = "Nur für aktivierte Konten",
                                                            tint = GoldAccent,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = caseItem.presentation ?: "",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                    maxLines = 2
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
    }
}
