package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.FirstAidBanksDto
import sy.medpro.watani.data.FirstAidIndexDto
import sy.medpro.watani.data.FirstAidIntegrationDto
import sy.medpro.watani.data.WataniRepository

/**
 * «First Aid - Med Pro» section home (build 1.2.36, merge phase 4):
 * the STRUCTURAL screen — it loads the real published packs through
 * the repository getters (cache-first + silent refresh) and renders
 * the section's true shape: the two book paths, the integration
 * layer, the printed banks and the booklets, with per-unit lock state
 * from [FirstAidProgress] and the flashcards due counter from
 * [FirstAidSrs]. The full screens and the unit player are phase 5,
 * which also applies the approved visual design verbatim — nothing
 * here is final styling.
 */
@Composable
fun FirstAidSectionScreen() {
    val context = LocalContext.current
    val repo = remember { WataniRepository(context) }

    var index1 by remember { mutableStateOf<FirstAidIndexDto?>(null) }
    var index2 by remember { mutableStateOf<FirstAidIndexDto?>(null) }
    var integration by remember { mutableStateOf<FirstAidIntegrationDto?>(null) }
    var banks by remember { mutableStateOf<FirstAidBanksDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    val srsChanges by FirstAidSrs.changes.collectAsState()
    val dueToday = remember(srsChanges) { FirstAidSrs.dueCountAll(context) }

    LaunchedEffect(Unit) {
        index1 = repo.getFirstAidIndex(1)
        index2 = repo.getFirstAidIndex(2)
        integration = repo.getFirstAidIntegration()
        banks = repo.getFirstAidBanks()
        failed = index1 == null && index2 == null
        loading = false
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (failed) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "تعذّر تحميل قسم First Aid — تحقق من الاتصال وحاول مجددًا.",
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "First Aid - Med Pro",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                "مساران مستقلان بترتيب الكتابين + طبقة تكامل بينهما. القسم مجاني ويعمل دون اتصال بعد تنزيل وحداته.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            if (dueToday > 0) {
                Text(
                    "بطاقات مستحقة اليوم: $dueToday",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        index1?.let { idx ->
            item { StepPathCard(step = 1, index = idx) }
        }
        index2?.let { idx ->
            item { StepPathCard(step = 2, index = idx) }
        }

        integration?.let { integ ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("طبقة التكامل", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val totals = integ.totals
                        Text(
                            "روابط مواضيع موثقة بين الكتابين: ${integ.count ?: integ.links?.size ?: 0}" +
                                " (داخل الأنظمة: ${totals?.topicLinksInSystem ?: "—"}، عابرة: ${totals?.crossSystemLinks ?: "—"})",
                            fontSize = 13.sp
                        )
                        Text(
                            "جلسات تكامل بلا قفل: Step 1 ‏${integ.integrationSessions?.step1?.size ?: 0} جلسة، " +
                                "Step 2 ‏${integ.integrationSessions?.step2?.size ?: 0} جلسات",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        banks?.let { b ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("بنوك الكتاب المطبوعة — Step 2", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "${b.count ?: 0} سؤالًا كما هي مطبوعة (${b.keyed ?: 0} بمفتاح مطبوع، ${b.unkeyed ?: 0} بلا مفتاح تُعرض بلا تصحيح آلي)",
                            fontSize = 13.sp
                        )
                        b.tests.orEmpty().forEach { test ->
                            Text(
                                "• ${test.test ?: ""} — ${test.questions?.size ?: 0} سؤالًا (صفحات ${test.sourcePages ?: "—"})",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        val booklets = (index1?.booklets.orEmpty().map { 1 to it } +
            index2?.booklets.orEmpty().map { 2 to it })
        if (booklets.isNotEmpty()) {
            item {
                Text("الكتيّبات (PDF — متاحة دائمًا)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(booklets) { (step, booklet) ->
                val fileName = booklet.file?.substringAfterLast('/') ?: ""
                val sizeMb = (booklet.size ?: 0L) / 1_000_000.0
                Text(
                    "• Step $step — $fileName (${"%.1f".format(sizeMb)} MB)",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/** One book path: its systems in book order, each unit with its live
 * lock state. Path order and session split come from the index itself
 * (units carry "order" and "kind"). */
@Composable
private fun StepPathCard(step: Int, index: FirstAidIndexDto) {
    val context = LocalContext.current
    val units = remember(index) { index.units.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE } }
    val sessionIds = remember(units) {
        units.filter { it.kind == "integration" }.mapNotNull { it.id }.toSet()
    }
    val pathIds = remember(units) {
        units.filter { it.kind != "integration" }.mapNotNull { it.id }
    }
    val doneCount = remember(units) {
        units.count { FirstAidProgress.isUnitDone(context, step, it.id ?: "", it.id in sessionIds) }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Step $step — ${index.count ?: units.size} وحدة",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "أُنجزت $doneCount وحدة • أسئلة تدريب: ${index.totals?.quiz ?: "—"}" +
                    (if (step == 2) " • مدخل: ${index.totals?.precheck ?: "—"} • خروج: ${index.totals?.exitcheck ?: "—"}" else ""),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            index.systems.orEmpty().forEach { system ->
                Text(
                    system.sectionAr ?: system.sectionEn ?: system.code ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                system.units.orEmpty().forEach { unitId ->
                    val summary = units.firstOrNull { it.id == unitId }
                    val status = FirstAidProgress.status(
                        context, step, pathIds, unitId, sessionIds
                    )
                    val statusLabel = when (status) {
                        FirstAidProgressRules.UnitStatus.DONE -> "منجزة"
                        FirstAidProgressRules.UnitStatus.AVAILABLE -> "متاحة"
                        FirstAidProgressRules.UnitStatus.LOCKED -> "مقفلة"
                    }
                    Text(
                        "• ${summary?.titleAr ?: unitId} — $statusLabel" +
                            " (تدريب ${summary?.quizCount ?: "—"}، بطاقات ${summary?.flashcardCount ?: "—"})",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
