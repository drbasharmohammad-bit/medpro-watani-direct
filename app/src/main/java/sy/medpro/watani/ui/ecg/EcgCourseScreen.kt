package sy.medpro.watani.ui.ecg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.EcgEnvelope
import sy.medpro.watani.data.EcgPatternDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.WidthBreakpoints
import sy.medpro.watani.ui.isMediumWidth
import sy.medpro.watani.ui.clinical.AuscultationTrainerScreen
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseSurfaceVariant

private data class UnitEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val done: Boolean,
    val dest: EcgDest
)

/** Internal destinations of the ECG course (single-activity, no nav graph). */
private enum class EcgDest {
    HOME, METHOD, METHOD_COURSE, PATTERNS, PATTERN_DETAIL, COMPARE, GUIDED_LIST, GUIDED,
    DRILL, LEAD_MAP, CALIPERS, AUSCULTATION, AUSC_QUIZ
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EcgSubScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaroonPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        // Tablet pass (1.2.5): every ECG sub-screen centers its content
        // under a governed max width instead of stretching blindly on
        // wide windows; below the cap (all phones) nothing changes.
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.fillMaxSize().widthIn(max = WidthBreakpoints.CONTENT_MAX)) {
                content()
            }
        }
    }
}

@Composable
private fun EcgUnitCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    done: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaroonPrimary)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (done) {
                Icon(Icons.Default.CheckCircle, contentDescription = "مكتمل", tint = MaroonPrimary)
            }
        }
    }
}

/**
 * Course home of the cardiology mini-course (build 1.2.4), the new
 * target of the «القلب والإصغاء» entry. Content loads exactly like
 * the auscultation pack: downloaded ecg.json section file first, then
 * a manifest-driven downloadSection, then the bundled sample pack as
 * a last resort so the course is never a dead screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcgCourseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { WataniRepository(context) }
    val store = remember { EcgProgressStore(context) }

    var envelope by remember { mutableStateOf<EcgEnvelope?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var usedSample by remember { mutableStateOf(false) }
    var dest by remember { mutableStateOf(EcgDest.HOME) }
    var selectedPatternId by remember { mutableStateOf<String?>(null) }
    var selectedStripId by remember { mutableStateOf<String?>(null) }
    // Recompose progress figures after returning from a unit.
    var progressTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        var env = repo.getEcgEnvelope()
        if (env == null) {
            val manifestRes = repo.fetchManifest()
            val entry = manifestRes.getOrNull()?.files?.get("ecg.json")
            if (entry != null) {
                repo.downloadSection("ecg.json", entry)
                env = repo.getEcgEnvelope()
            }
        }
        // 1.2.6: a local pack is no longer the end of the story — if
        // the server republished ecg.json, silently swap in the new
        // file before first paint (any failure keeps the local copy).
        if (env != null && repo.refreshSectionIfStale("ecg.json")) {
            env = repo.getEcgEnvelope() ?: env
        }
        if (env == null) {
            env = repo.getBundledEcgEnvelope()
            usedSample = env != null
        }
        envelope = env
        isLoading = false
    }

    val backToHome: () -> Unit = {
        dest = EcgDest.HOME
        progressTick++
    }

    when (dest) {
        EcgDest.HOME -> Unit
        EcgDest.METHOD -> {
            EcgMethodScreen(envelope, store, onBack = backToHome)
            return
        }
        EcgDest.METHOD_COURSE -> {
            MethodCourseScreen(
                envelope = envelope,
                onOpenStrip = { stripId ->
                    selectedStripId = stripId
                    dest = EcgDest.GUIDED
                },
                onBack = backToHome
            )
            return
        }
        EcgDest.PATTERNS -> {
            EcgPatternsScreen(
                envelope,
                onOpenPattern = { id ->
                    selectedPatternId = id
                    dest = EcgDest.PATTERN_DETAIL
                },
                onBack = backToHome
            )
            return
        }
        EcgDest.PATTERN_DETAIL -> {
            val pattern = envelope?.patterns?.firstOrNull { it.id == selectedPatternId }
            if (pattern != null) {
                EcgPatternDetailScreen(
                    envelope = envelope,
                    pattern = pattern,
                    store = store,
                    onCompare = { dest = EcgDest.COMPARE },
                    onGuidedStrip = { stripId ->
                        selectedStripId = stripId
                        dest = EcgDest.GUIDED
                    },
                    onBack = backToHome
                )
                return
            }
        }
        EcgDest.COMPARE -> {
            val env = envelope
            val pattern = env?.patterns?.firstOrNull { it.id == selectedPatternId }
            if (env != null && pattern != null) {
                CompareScreen(envelope = env, pattern = pattern, onBack = backToHome)
                return
            }
        }
        EcgDest.GUIDED_LIST -> {
            EcgGuidedListScreen(
                envelope = envelope,
                store = store,
                onOpenStrip = { stripId ->
                    selectedStripId = stripId
                    dest = EcgDest.GUIDED
                },
                onBack = backToHome
            )
            return
        }
        EcgDest.GUIDED -> {
            val env = envelope
            val strip = env?.strips?.firstOrNull { it.id == selectedStripId }
            if (env != null && strip != null) {
                GuidedReaderScreen(
                    envelope = env,
                    strip = strip,
                    store = store,
                    onBack = backToHome
                )
                return
            }
        }
        EcgDest.DRILL -> {
            val env = envelope
            if (env != null) {
                FlashDrillScreen(
                    envelope = env,
                    store = store,
                    onOpenPattern = { id ->
                        selectedPatternId = id
                        dest = EcgDest.PATTERN_DETAIL
                    },
                    onBack = backToHome
                )
                return
            }
        }
        EcgDest.LEAD_MAP -> {
            val env = envelope
            if (env != null) {
                LeadMapScreen(envelope = env, store = store, onBack = backToHome)
                return
            }
        }
        EcgDest.CALIPERS -> {
            val env = envelope
            if (env != null) {
                CaliperScreen(envelope = env, onBack = backToHome)
                return
            }
        }
        EcgDest.AUSCULTATION -> {
            AuscultationTrainerScreen(onBack = backToHome)
            return
        }
        EcgDest.AUSC_QUIZ -> {
            AuscultationQuizScreen(store = store, onBack = backToHome)
            return
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("كورس القلب المصغّر", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaroonPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaroonPrimary
                )
                envelope == null -> Text(
                    "تعذّر تحميل محتوى الكورس — تحقق من الاتصال وحاول مجددًا.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> {
                    val env = envelope!!
                    val patterns = env.patterns ?: emptyList()
                    val strips = env.strips ?: emptyList()
                    val methodCount = env.method?.size ?: 0
                    val weak = store.weakPatternIds(patterns.mapNotNull { it.id })
                    // progressTick read so returning from a unit refreshes figures.
                    @Suppress("UNUSED_EXPRESSION") progressTick
                    // Width read HERE, in composable context: the
                    // LazyColumn content lambda is a LazyListScope
                    // scope (not @Composable), so isMediumWidth()
                    // cannot be called from inside it.
                    val mediumWidth = isMediumWidth()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().widthIn(max = WidthBreakpoints.CONTENT_MAX).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (usedSample) {
                            item {
                                Text(
                                    "هذه نسخة عيّنة من المحتوى — الحزمة الكاملة تُنزَّل تلقائيًا عند توفّرها.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if ((env.methodLessons ?: emptyList()).isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { dest = EcgDest.METHOD_COURSE },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaroonPrimary),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "منهج القراءة — من الأساس إلى الاحتراف",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Text(
                                                "دروس مرقّمة بمستويات: من أول مبادئ التخطيط إلى الحالات الثقيلة، بمصادر موثّقة وشرائط تطبيقية",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = RoseSurfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("تقدّمك بالكورس", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "منهج القراءة: ${store.methodDoneCount(methodCount)}/$methodCount خطوات • " +
                                            "قراءات موجَّهة: ${store.guidedDoneCount(strips.mapNotNull { it.id })}/${strips.size} • " +
                                            "أفضل لمحات: ${store.drillBestScore()}/10",
                                        fontSize = 13.sp
                                    )
                                    if (weak.isNotEmpty()) {
                                        val names = patterns.filter { weak.contains(it.id) }
                                            .mapNotNull { it.ar }.joinToString("، ")
                                        if (names.isNotBlank()) {
                                            Text(
                                                "أنماط تحتاج تقوية: $names",
                                                fontSize = 12.sp,
                                                color = MaroonPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Unit cards: one column on phones, two governed
                        // columns at ≥600 dp window width (tablet pass).
                        val units = listOf(
                            UnitEntry("منهج القراءة", "الخطوات الخمس لقراءة أي تخطيط بثقة",
                                Icons.Default.MenuBook,
                                methodCount > 0 && store.methodDoneCount(methodCount) == methodCount,
                                EcgDest.METHOD),
                            UnitEntry("مكتبة الأنماط", "${patterns.size} نمطًا بعلامات اللمحة ومعايير موثّقة بالمصادر",
                                Icons.Default.Favorite, false, EcgDest.PATTERNS),
                            UnitEntry("القارئ الموجَّه", "اقرأ شرائط حقيقية خطوة بخطوة مع كشف تدريجي",
                                Icons.Default.PlayArrow,
                                strips.isNotEmpty() && store.guidedDoneCount(strips.mapNotNull { it.id }) == strips.size,
                                EcgDest.GUIDED_LIST),
                            UnitEntry("تدريب اللمحات", "10 شرائط × 10 ثوانٍ — ويتكيّف مع أخطائك",
                                Icons.Default.Speed, false, EcgDest.DRILL),
                            UnitEntry("الفرجار والقياس", "قِس الفواصل بنفسك واحسب المعدّل وQTc",
                                Icons.Default.Straighten, false, EcgDest.CALIPERS),
                            UnitEntry("خريطة الاتجاهات", "أي اتجاهات = أي جدار وأي شريان",
                                Icons.Default.Map, store.isLeadMapDone(), EcgDest.LEAD_MAP),
                            UnitEntry("الإصغاء القلبي", "مدرّب الإصغاء والأصوات القلبية (48 صوتًا)",
                                Icons.Default.Hearing, false, EcgDest.AUSCULTATION),
                            UnitEntry("اختبار الإصغاء", "اسمع وخمّن — أفضل نتيجة: ${store.auscQuizBestScore()}/10",
                                Icons.Default.Hearing, false, EcgDest.AUSC_QUIZ)
                        )
                        if (mediumWidth) {
                            items(units.chunked(2)) { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { unit ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            EcgUnitCard(
                                                title = unit.title,
                                                subtitle = unit.subtitle,
                                                icon = unit.icon,
                                                done = unit.done,
                                                onClick = { dest = unit.dest }
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(units) { unit ->
                                EcgUnitCard(
                                    title = unit.title,
                                    subtitle = unit.subtitle,
                                    icon = unit.icon,
                                    done = unit.done,
                                    onClick = { dest = unit.dest }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EcgMethodScreen(envelope: EcgEnvelope?, store: EcgProgressStore, onBack: () -> Unit) {
    val steps = (envelope?.method ?: emptyList()).sortedBy { it.step ?: Int.MAX_VALUE }
    var tick by remember { mutableStateOf(0) }
    EcgSubScaffold(title = "منهج القراءة", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(steps) { step ->
                val num = step.step ?: 0
                @Suppress("UNUSED_EXPRESSION") tick
                val done = num > 0 && store.isMethodStepDone(num)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "الخطوة $num: ${step.title ?: ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (done) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "مكتملة", tint = MaroonPrimary)
                            }
                        }
                        step.ar?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, fontSize = 14.sp)
                        }
                        step.source?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("المصدر: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (num > 0 && !done) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = {
                                store.markMethodStepDone(num)
                                tick++
                            }) { Text("أتممت هذه الخطوة") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EcgPatternsScreen(
    envelope: EcgEnvelope?,
    onOpenPattern: (String) -> Unit,
    onBack: () -> Unit
) {
    val patterns = envelope?.patterns ?: emptyList()
    val grouped = patterns.groupBy { it.category ?: "أنماط" }
    EcgSubScaffold(title = "مكتبة الأنماط", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            grouped.forEach { (category, list) ->
                item {
                    Text(
                        category,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaroonPrimary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(list) { pattern ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            pattern.id?.let { onOpenPattern(it) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pattern.ar ?: "", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    listOfNotNull(pattern.en, pattern.level?.let { "مستوى $it" }).joinToString(" • "),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaroonPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EcgPatternDetailScreen(
    envelope: EcgEnvelope?,
    pattern: EcgPatternDto,
    store: EcgProgressStore,
    onCompare: () -> Unit,
    onGuidedStrip: (String) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(pattern.id) { pattern.id?.let { store.markPatternSeen(it) } }
    val patternStrips = (envelope?.strips ?: emptyList()).filter { it.patternId == pattern.id }
    val normStripExists = (envelope?.strips ?: emptyList()).any { strip ->
        val p = envelope?.patterns?.firstOrNull { it.id == strip.patternId }
        p?.en?.equals("NORM", ignoreCase = true) == true || p?.id?.equals("norm", ignoreCase = true) == true
    }
    EcgSubScaffold(title = pattern.ar ?: "نمط", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    listOfNotNull(pattern.en, pattern.category).joinToString(" • "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val hooks = pattern.hooks ?: emptyList()
            if (hooks.isNotEmpty()) {
                item {
                    Text("علامات اللمحة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(hooks) { hook -> Text("• $hook", fontSize = 14.sp) }
            }
            val criteria = pattern.criteria ?: emptyList()
            if (criteria.isNotEmpty()) {
                item {
                    Text("المعايير", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(criteria) { criterion ->
                    Column {
                        Text("• ${criterion.ar ?: ""}", fontSize = 14.sp)
                        criterion.source?.let {
                            Text("  المصدر: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val pitfalls = pattern.pitfalls ?: emptyList()
            if (pitfalls.isNotEmpty()) {
                item {
                    Text("أخطاء شائعة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(pitfalls) { pitfall -> Text("• $pitfall", fontSize = 14.sp) }
            }
            pattern.sourceLine?.let { line ->
                item {
                    Text("مرجع النمط: $line", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (normStripExists && patternStrips.isNotEmpty()) {
                item {
                    Button(onClick = onCompare, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CompareArrows, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("  قارن مع الطبيعي")
                    }
                }
            }
            if (patternStrips.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { patternStrips.first().id?.let { onGuidedStrip(it) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("اقرأ شريطًا من هذا النمط بالقارئ الموجَّه") }
                }
            }
        }
    }
}

@Composable
private fun EcgGuidedListScreen(
    envelope: EcgEnvelope?,
    store: EcgProgressStore,
    onOpenStrip: (String) -> Unit,
    onBack: () -> Unit
) {
    val strips = envelope?.strips ?: emptyList()
    val patternsById = (envelope?.patterns ?: emptyList()).associateBy { it.id }
    EcgSubScaffold(title = "القارئ الموجَّه", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(strips) { strip ->
                val pattern = patternsById[strip.patternId]
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        strip.id?.let { onOpenStrip(it) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                pattern?.ar ?: "شريط تدريبي",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                listOfNotNull(
                                    strip.age?.let { "العمر $it" },
                                    strip.sex,
                                    strip.hr?.let { "النبض $it" }
                                ).joinToString(" • "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (strip.id != null && store.isGuidedDone(strip.id)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "مقروء", tint = MaroonPrimary)
                        }
                    }
                }
            }
        }
    }
}
