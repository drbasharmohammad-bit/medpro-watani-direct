package sy.medpro.watani.ui.cases

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
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.icons.SubjectIcon
import sy.medpro.watani.ui.icons.subjectIconRes
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.GoldAccentDark
import sy.medpro.watani.ui.theme.MaroonPrimary

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

    suspend fun loadGermanCases() {
        isLoading = true
        errorMessage = null
        try {
            val repo = WataniRepository(context)
            var env = repo.getCasesDeEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val entry = manifestRes.getOrNull()?.files?.get("cases_de.json")
                    if (entry != null) {
                        repo.downloadSection("cases_de.json", entry)
                        env = repo.getCasesDeEnvelope()
                    }
                }
            }
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

    LaunchedEffect(Unit) { loadGermanCases() }

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
                isLoading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("جاري تحميل الحالات الألمانية...", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                }
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
                    val filtered = cases.filter {
                        searchQuery.isBlank() ||
                            it.title?.contains(searchQuery, ignoreCase = true) == true ||
                            it.specialty?.contains(searchQuery, ignoreCase = true) == true ||
                            it.presentation?.contains(searchQuery, ignoreCase = true) == true
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
                            items(filtered, key = { it.id ?: it.title ?: "" }) { caseItem ->
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCase = caseItem }
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
                                                Text(
                                                    text = caseItem.title ?: "Klinischer Fall",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaroonPrimary
                                                )
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
