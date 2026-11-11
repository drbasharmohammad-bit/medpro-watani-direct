package sy.medpro.watani.ui.osce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.data.ProcedureDto
import sy.medpro.watani.data.ProcedureStepDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.skills.SkillsProgress
import sy.medpro.watani.ui.theme.*

/**
 * OSCE procedures wing (build 1.2.6) — the «الإجراءات» entry inside
 * the OSCE section. Content = osce_procedures.json (15 procedures with
 * sourced checklists), loaded exactly like osce.json: downloaded
 * section first, manifest + downloadSection on first use, bundled
 * sample as the last fallback. Read view (indications /
 * contraindications / equipment / aftercare / complications / sources)
 * plus the ordering trainer: shuffled steps, tap-to-place sequencing,
 * exact-position scoring (ProcedureScoring), missed CRITICAL steps
 * flagged explicitly. Best score per procedure persists locally
 * (SkillsProgress).
 */
private enum class ProcedureMode { LIST, DETAIL, TRAIN }

@Composable
fun ProceduresWingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var procedures by remember { mutableStateOf<List<ProcedureDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ProcedureDto?>(null) }
    var mode by remember { mutableStateOf(ProcedureMode.LIST) }

    suspend fun loadProcedures() {
        isLoading = true
        errorMessage = null
        try {
            val repo = WataniRepository(context)
            var list = repo.getOsceProcedures()
            if (list.isNullOrEmpty()) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val entry = manifestRes.getOrNull()?.files?.get("osce_procedures.json")
                    if (entry != null) {
                        repo.downloadSection("osce_procedures.json", entry)
                        list = repo.getOsceProcedures()
                    }
                }
            }
            // 1.2.6: silently refresh a stale local pack (see
            // WataniRepository.refreshSectionIfStale).
            if (!list.isNullOrEmpty() && repo.refreshSectionIfStale("osce_procedures.json")) {
                list = repo.getOsceProcedures() ?: list
            }
            if (list.isNullOrEmpty()) {
                list = repo.getBundledOsceProcedures()
            }
            if (!list.isNullOrEmpty()) {
                procedures = list
            } else {
                errorMessage = "تعذر تحميل الإجراءات. يرجى التحقق من اتصال الإنترنت وإعادة المحاولة."
            }
        } catch (e: Exception) {
            errorMessage = "خطأ في الاتصال: ${e.localizedMessage ?: "تأكد من اتصالك بالإنترنت"}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadProcedures() }

    fun goBack() {
        when (mode) {
            ProcedureMode.TRAIN -> mode = ProcedureMode.DETAIL
            ProcedureMode.DETAIL -> { mode = ProcedureMode.LIST; selected = null }
            ProcedureMode.LIST -> onBack()
        }
    }
    BackHandler { goBack() }

    DrawerScreenScaffold(
        title = when (mode) {
            ProcedureMode.LIST -> "الإجراءات العملية — OSCE"
            ProcedureMode.DETAIL -> selected?.ar ?: "الإجراء"
            ProcedureMode.TRAIN -> "رتّب الخطوات"
        },
        onBack = { goBack() }
    ) { padding ->
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
                    Text("جاري تحميل الإجراءات...", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                }
                procedures.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PendingActions, contentDescription = null, tint = GoldAccentDark, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("تعذر تحميل الإجراءات", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "حدث خطأ أثناء تحميل الإجراءات. يرجى إعادة المحاولة.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { coroutineScope.launch { loadProcedures() } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                    }
                }
                mode == ProcedureMode.LIST -> ProceduresList(
                    procedures = procedures,
                    onOpen = { selected = it; mode = ProcedureMode.DETAIL }
                )
                mode == ProcedureMode.DETAIL && selected != null -> ProcedureDetail(
                    procedure = selected!!,
                    onStartTrain = { mode = ProcedureMode.TRAIN },
                    onBackToList = { mode = ProcedureMode.LIST; selected = null }
                )
                mode == ProcedureMode.TRAIN && selected != null -> ProcedureOrderingTrainer(
                    procedure = selected!!,
                    onDone = { mode = ProcedureMode.DETAIL }
                )
            }
        }
    }
}

@Composable
private fun ProceduresList(procedures: List<ProcedureDto>, onOpen: (ProcedureDto) -> Unit) {
    val context = LocalContext.current
    val changes by SkillsProgress.changes.collectAsState()
    val grouped = remember(procedures) { procedures.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "عام" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الإجراءات العملية بقوائم فحص موثّقة المصدر", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "اقرأ قائمة الفحص لكل إجراء ثم تدرّب على ترتيب خطواته بنفسك — الخطوات الحرجة لسلامة المريض مُعلَّمة، وإخطاؤها يظهر لك صراحة.",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp
                    )
                }
            }
        }
        grouped.forEach { (category, list) ->
            item(key = "cat_$category") {
                Text(category, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary, modifier = Modifier.padding(top = 4.dp))
            }
            items(list, key = { it.id ?: it.ar ?: "" }) { proc ->
                val best = remember(changes, proc.id) {
                    proc.id?.let { SkillsProgress.bestProcedurePercent(context, it) } ?: -1
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(proc) }
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaroonPrimary.copy(alpha = 0.1f), modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Checklist, contentDescription = null, tint = MaroonPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proc.ar ?: "", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${proc.steps?.size ?: 0} خطوة" + if (best >= 0) " • أفضل ترتيب: $best%" else "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaroonPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletSection(title: String, items: List<String>?) {
    if (items.isNullOrEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { line ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("•", color = MaroonPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(line, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProcedureDetail(
    procedure: ProcedureDto,
    onStartTrain: () -> Unit,
    onBackToList: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(procedure.ar ?: "", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaroonPrimary)
            if (!procedure.en.isNullOrBlank()) {
                Text(procedure.en, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        item { BulletSection("الاستطبابات", procedure.indicationsAr) }
        item { BulletSection("مضادات الاستطباب", procedure.contraindicationsAr) }
        item { BulletSection("التجهيزات", procedure.equipmentAr) }
        item {
            Text("قائمة الفحص بالترتيب الصحيح:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
        }
        items(procedure.orderedSteps(), key = { it.order ?: 0 }) { step ->
            Surface(
                color = MaroonPrimary.copy(alpha = 0.06f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = MaroonPrimary, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "${step.order ?: ""}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.ar ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (!step.whyAr.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(step.whyAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        if (step.critical == true) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("⚠ خطوة حرجة لسلامة المريض", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        }
                    }
                }
            }
        }
        item { BulletSection("العناية بعد الإجراء والتوثيق", procedure.aftercareAr) }
        item { BulletSection("المضاعفات المحتملة", procedure.complicationsAr) }
        if (!procedure.sources.isNullOrEmpty() || !procedure.sourceNote.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("المصادر", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        procedure.sources?.forEach { src ->
                            Text("• ${src.name ?: ""}", fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        if (!procedure.sourceNote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(procedure.sourceNote, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onStartTrain,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تدرّب: رتّب الخطوات بنفسك", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBackToList,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("العودة لقائمة الإجراءات", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProcedureOrderingTrainer(procedure: ProcedureDto, onDone: () -> Unit) {
    val context = LocalContext.current
    val ordered = remember(procedure.id) { procedure.orderedSteps() }
    val total = ordered.size

    var pool by remember(procedure.id) { mutableStateOf<List<ProcedureStepDto>>(emptyList()) }
    var placed by remember(procedure.id) { mutableStateOf<List<ProcedureStepDto>>(emptyList()) }
    var result by remember(procedure.id) { mutableStateOf<OrderingScore?>(null) }

    fun resetTrainer() {
        var shuffled = ordered.shuffled()
        if (total > 2) {
            var guard = 0
            while (shuffled == ordered && guard < 12) {
                shuffled = ordered.shuffled()
                guard++
            }
        }
        pool = shuffled
        placed = emptyList()
        result = null
    }
    LaunchedEffect(procedure.id) { resetTrainer() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(procedure.ar ?: "", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaroonPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (result == null) "اضغط الخطوات بالترتيب الصحيح لبناء قائمة الفحص. اضغط خطوة مرتّبة لإعادتها."
                else "النتيجة والترتيب الصحيح مع سبب كل خطوة:",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }

        val score = result
        if (score == null) {
            item {
                Text("ترتيبك (${placed.size}/$total):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
            }
            if (placed.isEmpty()) {
                item {
                    Text("لم ترتّب أي خطوة بعد.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            items(placed, key = { "p_${it.order}" }) { step ->
                val pos = placed.indexOf(step) + 1
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaroonPrimary.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().clickable {
                        placed = placed - step
                        pool = pool + step
                    }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaroonPrimary, shape = RoundedCornerShape(8.dp)) {
                            Text("$pos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(step.ar ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (step.critical == true) {
                            Icon(Icons.Default.Warning, contentDescription = "خطوة حرجة", tint = ErrorRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item {
                Text("الخطوات المتبقية:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
            }
            items(pool, key = { "s_${it.order}" }) { step ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        pool = pool - step
                        placed = placed + step
                    }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(step.ar ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (step.critical == true) {
                            Icon(Icons.Default.Warning, contentDescription = "خطوة حرجة", tint = ErrorRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        val correctOrders = ordered.map { it.order ?: 0 }
                        val studentOrders = placed.map { it.order ?: 0 }
                        val critical = ordered.filter { it.critical == true }.map { it.order ?: 0 }.toSet()
                        val sc = ProcedureScoring.score(correctOrders, studentOrders, critical)
                        result = sc
                        procedure.id?.let { SkillsProgress.recordProcedureAttempt(context, it, sc.percent) }
                    },
                    enabled = placed.size == total && total > 0,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحقق من الترتيب", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("نتيجة ترتيب الخطوات", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${score.percent}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 42.sp)
                        Text(
                            "مواضع صحيحة: ${score.exactPositions} من ${score.totalSteps}",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                        )
                        if (score.missedCriticalOrders.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "⚠ أخطأت ${score.missedCriticalOrders.size} خطوة حرجة لسلامة المريض — راجعها بالأسفل.",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
            item {
                Text("قائمة الفحص الصحيحة ومواضعك:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
            }
            items(ordered, key = { "r_${it.order}" }) { step ->
                val idx = ordered.indexOf(step)
                val atRightPlace = score.positionCorrect.getOrNull(idx) == true
                val missedCritical = (step.order ?: -1) in score.missedCriticalOrders
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            missedCritical -> ErrorRed.copy(alpha = 0.08f)
                            atRightPlace -> SuccessGreen.copy(alpha = 0.08f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when {
                            missedCritical -> ErrorRed
                            atRightPlace -> SuccessGreen
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (atRightPlace) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (atRightPlace) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${step.order}. ${step.ar ?: ""}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (!step.whyAr.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(step.whyAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            if (missedCritical) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("⚠ أخطأت خطوة حرجة — وضعتها في غير موضعها الصحيح.", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                            } else if (step.critical == true) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("خطوة حرجة لسلامة المريض", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { resetTrainer() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إعادة المحاولة بخلط جديد", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("العودة لصفحة الإجراء", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
