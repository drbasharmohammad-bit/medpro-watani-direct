package sy.medpro.watani.ui.concepts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.ConceptDiffTableDto
import sy.medpro.watani.data.ConceptSelfTestDto
import sy.medpro.watani.data.ConceptUnitDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.groupConceptsBySubject
import sy.medpro.watani.data.resolveSelfTestCorrectIndex
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.*

/**
 * «ادرس مع ميد برو» (build 1.2.13, doctor's order 14 Nov 2026) —
 * a fixed concept curriculum INSIDE the Library tab: subjects ->
 * topics (by exam weight) -> one concept unit page. Content is the
 * server pack concepts.json, loaded exactly like dosing_drills.json
 * (downloaded section first, manifest + downloadSection on first
 * use, silent refresh when the manifest moved on) — but with NO
 * bundled fallback: [rememberConceptUnits] stays empty until a real
 * downloaded pack exists, and every entry point renders nothing for
 * an empty pack, so the whole section is invisible before the
 * content is published.
 */

class ConceptUnitsState(
    val units: List<ConceptUnitDto> = emptyList(),
    val loaded: Boolean = false
)

@Composable
fun rememberConceptUnits(): ConceptUnitsState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ConceptUnitsState()) }
    LaunchedEffect(Unit) {
        try {
            val repo = WataniRepository(context)
            var envelope = repo.getConcepts()
            if (envelope?.units.isNullOrEmpty()) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val entry = manifestRes.getOrNull()?.files?.get("concepts.json")
                    if (entry != null) {
                        repo.downloadSection("concepts.json", entry)
                        envelope = repo.getConcepts()
                    }
                }
            }
            // Same silent refresh as the dosing drills (1.2.6): a
            // republished pack reaches the device on the next open.
            if (!envelope?.units.isNullOrEmpty() &&
                repo.refreshSectionIfStale("concepts.json")
            ) {
                envelope = repo.getConcepts() ?: envelope
            }
            state = ConceptUnitsState(
                units = envelope?.units.orEmpty().filter { !it.id.isNullOrBlank() },
                loaded = true
            )
        } catch (_: Exception) {
            state = ConceptUnitsState(units = state.units, loaded = true)
        }
    }
    return state
}

/**
 * The Library entry card for «ادرس مع ميد برو». The caller renders
 * it ONLY when the downloaded pack has units (units.isNotEmpty()) —
 * with no pack it is never composed at all, so the Library looks
 * exactly as it did in 1.2.12.
 */
@Composable
fun ConceptsEntryCard(
    units: List<ConceptUnitDto>,
    onClick: () -> Unit
) {
    val subjectsCount = remember(units) { groupConceptsBySubject(units).size }
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
                Icons.Default.School,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ادرس مع ميد برو",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "شرح المفاهيم الأهم في الدورات — ${units.size} موضوعًا في $subjectsCount مادة، مرتّبة بوزنها في الأسئلة",
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

@Composable
fun ConceptsSubjectsScreen(
    units: List<ConceptUnitDto>,
    onSubjectClick: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val groups = remember(units) { groupConceptsBySubject(units) }
    DrawerScreenScaffold(title = "ادرس مع ميد برو", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "المواد مرتّبة بمجموع أوزان مواضيعها في الدورات — الأعلى أولًا.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(groups, key = { it.subject }) { group ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSubjectClick(group.subject) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                group.subject,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaroonPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${group.units.size} موضوعًا • مجموع الوزن ${group.totalWeight}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaroonPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConceptsTopicsScreen(
    subject: String,
    units: List<ConceptUnitDto>,
    onUnitClick: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val topics = remember(units, subject) {
        units.filter { it.subject?.trim() == subject }
            .sortedByDescending { it.weight ?: 0 }
    }
    DrawerScreenScaffold(title = subject, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "المواضيع مرتّبة بالوزن في الدورات — الأعلى أولًا.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(topics, key = { it.id ?: it.topic ?: "" }) { unit ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { unit.id?.let(onUnitClick) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                unit.topic ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val counts = listOfNotNull(
                                unit.watani?.let { "وطني: $it" },
                                unit.damascus?.let { "دمشق: $it" }
                            )
                            if (counts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    counts.joinToString(" • ") + " سؤالًا",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Surface(
                            color = MaroonPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "الوزن ${unit.weight ?: 0}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaroonPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConceptUnitScreen(
    unitId: String,
    onBack: () -> Unit,
    onOpenQuestionById: (String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val loadState = rememberConceptUnits()
    val unit = remember(loadState.units, unitId) {
        loadState.units.firstOrNull { it.id == unitId }
    }

    // «افتح السؤال في البنك» is shown ONLY for self-test questions
    // actually found in a downloaded bank on this device.
    var bankQids by remember(unitId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(unit?.id) {
        val selfTests = unit?.selfTest.orEmpty()
        if (selfTests.isEmpty()) return@LaunchedEffect
        try {
            val repo = WataniRepository(context)
            val found = mutableSetOf<String>()
            for (test in selfTests) {
                val qid = test.qid?.trim().orEmpty()
                if (qid.isNotEmpty() && repo.findQuestionById(qid) != null) {
                    found.add(qid)
                }
            }
            bankQids = found
        } catch (_: Exception) {
            // Lookup failure hides the buttons — never a dead button.
        }
    }

    DrawerScreenScaffold(title = unit?.topic ?: "ادرس مع ميد برو", onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                unit != null -> ConceptUnitContent(
                    unit = unit,
                    bankQids = bankQids,
                    onOpenQuestionById = onOpenQuestionById
                )
                loadState.loaded -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "هذه الوحدة غير متاحة على الجهاز حاليًا.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaroonPrimary)
                }
            }
        }
    }
}

@Composable
private fun ConceptUnitContent(
    unit: ConceptUnitDto,
    bankQids: Set<String>,
    onOpenQuestionById: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header: subject + counts.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        unit.subject ?: "",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        unit.topic ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    val meta = listOfNotNull(
                        unit.weight?.let { "الوزن في الدورات: $it" },
                        unit.watani?.let { "أسئلة الوطني: $it" },
                        unit.damascus?.let { "أسئلة دمشق: $it" }
                    )
                    if (meta.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            meta.joinToString(" • "),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 1) شرح الفكرة — explanation sections in pack order.
        val sections = unit.explanation.orEmpty()
            .filter { !it.h.isNullOrBlank() || !it.body.isNullOrBlank() }
        if (sections.isNotEmpty()) {
            item { ConceptBlockTitle("شرح الفكرة", Icons.Default.MenuBook) }
            items(sections) { section ->
                Column {
                    if (!section.h.isNullOrBlank()) {
                        Text(
                            section.h,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaroonPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (!section.body.isNullOrBlank()) {
                        ConceptRichText(section.body)
                    }
                }
            }
        }

        // 2) أفخاخ الدورات.
        val traps = unit.traps.orEmpty().filter { it.isNotBlank() }
        if (traps.isNotEmpty()) {
            item { ConceptBlockTitle("أفخاخ الدورات", Icons.Default.Warning) }
            items(traps) { trap ->
                Surface(
                    color = ErrorRed.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ConceptRichText(trap, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 3) اختبر نفسك.
        val selfTests = unit.selfTest.orEmpty()
            .filter { !it.text.isNullOrBlank() && !it.options.isNullOrEmpty() }
        if (selfTests.isNotEmpty()) {
            item { ConceptBlockTitle("اختبر نفسك", Icons.Default.Quiz) }
            items(selfTests, key = { it.qid ?: it.text ?: "" }) { test ->
                ConceptSelfTestCard(
                    test = test,
                    bankAvailable = bankQids.contains(test.qid?.trim() ?: ""),
                    onOpenQuestionById = onOpenQuestionById
                )
            }
        }

        // 4) الجدول الفارق.
        val diffTable = unit.diffTable
        if (diffTable != null &&
            !diffTable.columns.isNullOrEmpty() && !diffTable.rows.isNullOrEmpty()
        ) {
            item { ConceptBlockTitle("الجدول الفارق", Icons.Default.TableChart) }
            item { ConceptDiffTable(table = diffTable) }
        }

        // 5) ربط بين المواد.
        val crossLinks = unit.crossLinks.orEmpty()
            .filter { !it.text.isNullOrBlank() || !it.subject.isNullOrBlank() }
        if (crossLinks.isNotEmpty()) {
            item { ConceptBlockTitle("ربط بين المواد", Icons.Default.Link) }
            items(crossLinks) { link ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (!link.subject.isNullOrBlank()) {
                            Text(
                                link.subject,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaroonPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        if (!link.text.isNullOrBlank()) {
                            ConceptRichText(link.text)
                        }
                    }
                }
            }
        }

        // 6) حيلة الذاكرة — the hook is set apart from the body.
        val mnemonic = unit.mnemonic
        if (mnemonic != null && (!mnemonic.body.isNullOrBlank() || !mnemonic.hook.isNullOrBlank())) {
            item { ConceptBlockTitle("حيلة الذاكرة", Icons.Default.Lightbulb) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!mnemonic.body.isNullOrBlank()) {
                            Text(
                                mnemonic.body,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                        if (!mnemonic.hook.isNullOrBlank()) {
                            if (!mnemonic.body.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Surface(
                                color = GoldAccent.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        mnemonic.hook,
                                        color = GoldAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp
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

@Composable
private fun ConceptBlockTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
    }
}

@Composable
private fun ConceptSelfTestCard(
    test: ConceptSelfTestDto,
    bankAvailable: Boolean,
    onOpenQuestionById: (String) -> Unit
) {
    val options = test.options.orEmpty()
    val correctIndex = remember(test.correctIndex, options.size) {
        resolveSelfTestCorrectIndex(test.correctIndex, options.size)
    }
    var revealed by remember(test.qid, test.text) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!test.bank.isNullOrBlank()) {
                Surface(
                    color = MaroonPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (test.bank == "watani") "من بنك الوطني" else "من بنك دمشق",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            ConceptRichText(test.text ?: "", fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(10.dp))
            options.forEachIndexed { idx, option ->
                val isCorrect = revealed && idx == correctIndex
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCorrect) {
                        SuccessGreen.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isCorrect) SuccessGreen
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option,
                            fontSize = 13.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCorrect) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (!revealed) {
                OutlinedButton(
                    onClick = { revealed = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaroonPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaroonPrimary)
                ) {
                    Text("إظهار الجواب", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                if (correctIndex != null) {
                    Text(
                        "الإجابة الصحيحة: ${options.getOrNull(correctIndex) ?: ""}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = SuccessGreen
                    )
                }
                if (!test.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ConceptRichText(test.note, fontSize = 13.sp)
                }
                val qid = test.qid?.trim().orEmpty()
                if (bankAvailable && qid.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onOpenQuestionById(qid) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("افتح السؤال في البنك", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptDiffTable(table: ConceptDiffTableDto) {
    val columns = table.columns.orEmpty()
    val rows = table.rows.orEmpty()
    val scrollState = rememberScrollState()
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Header row.
            Row(modifier = Modifier.background(MaroonPrimary)) {
                columns.forEach { column ->
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(
                            column,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.background(
                        if (rowIndex % 2 == 0) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                ) {
                    columns.indices.forEach { colIndex ->
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Text(
                                row.getOrNull(colIndex) ?: "",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (colIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }
        }
    }
}

/**
 * Concept body text in the app's explanation style (plain 14sp text,
 * 22sp line height on the surface colour) with the pack's simple
 * markup honoured: **bold** spans render bold, lines starting with
 * "- " render as bullets, and numbered lines keep their numbers.
 * Anything else renders verbatim.
 */
@Composable
fun ConceptRichText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val lines = remember(text) { text.split('\n') }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            } else if (line.startsWith("- ")) {
                Row {
                    Text(
                        "• ",
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary
                    )
                    Text(
                        text = conceptAnnotated(line.removePrefix("- ")),
                        fontSize = fontSize,
                        lineHeight = 22.sp,
                        fontWeight = fontWeight,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = conceptAnnotated(line),
                    fontSize = fontSize,
                    lineHeight = 22.sp,
                    fontWeight = fontWeight,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun conceptAnnotated(line: String): AnnotatedString {
    if (!line.contains("**")) return AnnotatedString(line)
    return buildAnnotatedString {
        var index = 0
        while (index < line.length) {
            val start = line.indexOf("**", index)
            if (start < 0) {
                append(line.substring(index))
                break
            }
            if (start > index) append(line.substring(index, start))
            val end = line.indexOf("**", start + 2)
            if (end < 0) {
                append(line.substring(start))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(line.substring(start + 2, end))
            }
            index = end + 2
        }
    }
}
