package sy.medpro.watani.ui.refassistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import sy.medpro.watani.data.RefAssistantApi
import sy.medpro.watani.data.RefAssistantPack
import sy.medpro.watani.data.RefAskResult
import sy.medpro.watani.data.RefBundle
import sy.medpro.watani.data.RefChunk
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * «الباحث المعتمد» (build 1.2.33) — the reference assistant section,
 * built to SPEC.md (release-prep/ref-assistant) and its two approved
 * models: ask in your own words, get an answer grounded ONLY in our
 * curated references, every claim carrying its source and — where the
 * source states it — its evidence level and recommendation grade.
 *
 * The «جواب دائمًا» ladder, as the student sees it:
 *  1. «بحث معتمد» (online): the gateway composes a grounded answer
 *     while the free smart quota lasts;
 *  2. when the quota is out (or the gateway degrades), the same
 *     question returns weighted text results from the server, with an
 *     explicit status banner — never an error, never silence;
 *  3. with no connection at all, the downloaded bundle answers on the
 *     device (offline text search over the very same chunks).
 * What is not in our references is said plainly («غير موجود في
 * مراجعنا») — the assistant never completes from model memory.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RefAssistantScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val packState by RefAssistantPack.state.collectAsState()

    var bundle by remember { mutableStateOf<RefBundle?>(null) }
    var question by remember { mutableStateOf("") }
    var smartMode by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RefAskResult?>(null) }
    var showRefs by remember { mutableStateOf(false) }
    var statusSmart by remember { mutableStateOf<Boolean?>(null) }

    fun refreshBundle() {
        bundle = RefAssistantPack.installedBundle(context)
    }

    LaunchedEffect(packState.completedRuns) { refreshBundle() }
    LaunchedEffect(Unit) {
        refreshBundle()
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            statusSmart = RefAssistantApi.fetchStatus(user)?.smartAvailable
        }
    }

    fun runSearch() {
        val q = question.trim()
        if (q.length < 3 || searching) return
        searching = true
        result = null
        scope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            var r: RefAskResult? = null
            if (smartMode && user != null) {
                r = RefAssistantApi.ask(user, q)
            }
            if (r == null) {
                // Offline rung (or text mode): the downloaded bundle
                // answers on-device; no bundle + no network is the one
                // honest failure state, and it says what to do.
                val local = RefAssistantPack.offlineSearch(context, q)
                r = when {
                    local.isNotEmpty() -> RefAskResult.TextResults(local, smartExhausted = false, offline = true)
                    bundle == null -> RefAskResult.Failed("نزّل حزمة المراجع أولًا لتتمكن من البحث بلا اتصال، أو تحقق من اتصالك وحاول مجددًا.")
                    else -> RefAskResult.NotInRefs(smartExhausted = false)
                }
            }
            if (r is RefAskResult.Failed && r.reason == "rate_limited") {
                val local = RefAssistantPack.offlineSearch(context, q)
                r = if (local.isNotEmpty()) {
                    RefAskResult.TextResults(local, smartExhausted = true, offline = true)
                } else {
                    RefAskResult.Failed("وصلت لحد الأسئلة اليومي — البحث النصي على جهازك يبقى متاحًا دائمًا.")
                }
            }
            result = r
            searching = false
        }
    }

    DrawerScreenScaffold(title = "الباحث المعتمد", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "اسأل مراجعنا الموثوقة — الجواب بالمصدر ومستوى الدليل",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Bundle state: the offline rung is downloaded once ──
            val b = bundle
            if (b == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("حزمة المراجع على جهازك", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "نزّل مراجع الباحث مرة واحدة (178 مرجعًا موثوقًا) ليعمل البحث النصي عندك بلا إنترنت دائمًا، حتى عند نفاد الحصة الذكية.",
                            fontSize = 13.sp
                        )
                        when {
                            packState.downloading -> {
                                LinearProgressIndicator(progress = { packState.progress }, modifier = Modifier.fillMaxWidth())
                                Text("جارٍ تنزيل الحزمة… ${(packState.progress * 100).toInt()}٪", fontSize = 12.sp)
                            }
                            packState.failed -> {
                                Text("تعذّر تنزيل الحزمة — تحقق من اتصالك ومن تفعيل حسابك ثم حاول مجددًا.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                Button(onClick = { RefAssistantPack.startDownload(context) }) { Text("إعادة المحاولة") }
                            }
                            else -> Button(onClick = { RefAssistantPack.startDownload(context) }) { Text("تنزيل حزمة المراجع") }
                        }
                    }
                }
            } else {
                Text(
                    "الحزمة جاهزة على جهازك: ${b.totalRefs} مرجعًا · ${"%,d".format(b.totalChunks)} مقطعًا — البحث النصي يعمل بلا إنترنت.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Mode switch (SPEC §3) ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = smartMode,
                    onClick = { smartMode = true },
                    label = { Text("بحث معتمد") }
                )
                FilterChip(
                    selected = !smartMode,
                    onClick = { smartMode = false },
                    label = { Text("بحث نصي") }
                )
            }
            if (smartMode && statusSmart == false) {
                StatusBanner("البحث الذكي استهلك حصته المجانية اليوم — البحث النصي شغال، والذكي يرجع مع تجدد الحصة.")
            }

            // ── Question box ──
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("شو بدك تعرف اليوم؟") },
                placeholder = { Text("اكتب سؤالك بالعربية أو الإنجليزية كما تتكلم…") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { runSearch() },
                enabled = question.trim().length >= 3 && !searching,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (searching) "جارٍ البحث في مراجعنا…" else "ابحث")
            }
            if (searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── Examples ──
            Text("جرّب مثلًا:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "ما علاج التهاب الشغاف العدوائي التجريبي قبل نتيجة الزرع؟",
                    "Metformin and lactic acidosis — when to stop?",
                    "كيف يُدبَّر الحماض الكيتوني السكري؟"
                ).forEach { ex ->
                    AssistChip(onClick = { question = ex }, label = { Text(ex, fontSize = 12.sp) })
                }
            }

            // ── Result ──
            when (val r = result) {
                is RefAskResult.Smart -> {
                    if (r.smartExhausted) {
                        StatusBanner("البحث الذكي استهلك حصته المجانية اليوم — البحث النصي شغال، والذكي يرجع مع تجدد الحصة.")
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("الجواب المعتمد", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Text(r.answer, fontSize = 14.sp)
                        }
                    }
                    Text("المصادر — اضغط أي مصدر لقراءة النص من المرجع مباشرة:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    r.sources.forEachIndexed { i, src -> SourceCard(index = i + 1, chunk = src) }
                }
                is RefAskResult.TextResults -> {
                    if (r.smartExhausted) {
                        StatusBanner("البحث الذكي استهلك حصته المجانية اليوم — هذه نتائج البحث النصي من نفس المراجع.")
                    }
                    if (r.offline) {
                        StatusBanner("أنت تبحث الآن على جهازك (بلا اتصال) — نتائج نصية من حزمة المراجع المنزّلة.")
                    }
                    Text("نتائج من مراجعنا:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    r.chunks.forEach { c -> SourceCard(index = null, chunk = c) }
                }
                is RefAskResult.NotInRefs -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("غير موجود في مراجعنا", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "هذا غير موجود في مراجعنا المتاحة حاليًا — الباحث لا يكمّل من عنده أبدًا. جرّب صياغة أخرى أو مصطلحًا طبيًا أدق، أو تصفّح قائمة المراجع المتاحة بالأسفل.",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                is RefAskResult.Failed -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(r.reason, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                    }
                }
                null -> Unit
            }

            // ── Available references (transparency from the first screen) ──
            val bb = bundle
            if (bb != null) {
                OutlinedButton(onClick = { showRefs = !showRefs }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showRefs) "إخفاء المراجع المتاحة بالبحث" else "المراجع المتاحة بالبحث (${bb.totalRefs})")
                }
                if (showRefs) {
                    bb.refs.forEach { ref ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(ref.reference ?: "", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                val meta = listOfNotNull(
                                    ref.publisher,
                                    ref.year?.toString(),
                                    ref.specialty,
                                    "${ref.chunks} مقطعًا"
                                ).joinToString(" · ")
                                Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "إجابة تعليمية من مراجع منشورة — لا تغني عن الحكم السريري",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusBanner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
    }
}

/** One cited chunk: source chips always visible; the full text from
 * the reference itself expands on tap («اقرأ بالمصدر»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(index: Int?, chunk: RefChunk) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = buildString {
                if (index != null) append("[$index] ")
                append(chunk.reference ?: "مرجع")
                chunk.year?.let { append(" ($it)") }
            }
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaroonPrimary)
            val meta = listOfNotNull(
                chunk.publisher,
                chunk.specialty,
                chunk.section,
                when {
                    chunk.pageStart != null && chunk.pageEnd != null && chunk.pageEnd != chunk.pageStart ->
                        "ص ${chunk.pageStart}–${chunk.pageEnd}"
                    chunk.pageStart != null -> "ص ${chunk.pageStart}"
                    else -> null
                }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                chunk.evidenceLevel?.let {
                    AssistChip(onClick = {}, label = { Text("مستوى الدليل: $it", fontSize = 11.sp) })
                }
                chunk.recommendationGrade?.let {
                    AssistChip(onClick = {}, label = { Text("درجة التوصية: $it", fontSize = 11.sp) })
                }
            }
            if (expanded) {
                Text(chunk.text, fontSize = 13.sp)
                Text("اضغط للطي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    chunk.text.take(220) + if (chunk.text.length > 220) "…" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("اقرأ بالمصدر — اضغط لعرض النص الكامل", fontSize = 11.sp, color = MaroonPrimary)
            }
        }
    }
}
