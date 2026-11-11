package sy.medpro.watani.ui.ecg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.EcgEnvelope
import sy.medpro.watani.ui.isExpandedWidth
import sy.medpro.watani.data.EcgStripDto
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseSurfaceVariant

/**
 * The guided reader (build 1.2.4): the student reads one real strip
 * through the five-step method. The first three steps are picks
 * (rate / rhythm / axis) checked against the strip's guided values;
 * the intervals step puts the calipers in his hands; the last step
 * reveals the documented findings and conclusion. Values are revealed
 * progressively — nothing about the diagnosis is visible up front.
 */
@Composable
fun GuidedReaderScreen(
    envelope: EcgEnvelope,
    strip: EcgStripDto,
    store: EcgProgressStore,
    onBack: () -> Unit
) {
    val guided = strip.guided
    var step by remember(strip.id) { mutableIntStateOf(0) }
    var picked by remember(strip.id) { mutableStateOf<String?>(null) }
    var revealed by remember(strip.id) { mutableStateOf(false) }
    var correctPicks by remember(strip.id) { mutableIntStateOf(0) }
    var finished by remember(strip.id) { mutableStateOf(false) }
    var caliperText by remember(strip.id) { mutableStateOf<String?>(null) }

    val stepTitles = listOf("المعدّل", "النظم", "المحور", "الموجات والفواصل", "ST/T والخلاصة")

    // Options for the pick steps, built once per strip.
    val rateOptions = remember(strip.id) {
        val hr = strip.hr ?: 75
        listOf(hr, hr + 15, (hr - 12).coerceAtLeast(35)).distinct().map { it.toString() }.shuffled()
    }
    val rhythmOptions = remember(strip.id) {
        val correct = guided?.rhythmAr ?: "نظم جيبي منتظم"
        (listOf(correct) + listOf("نظم جيبي منتظم", "رجفان أذيني", "تسرّع جيبي", "حصار أذيني بطيني كامل")
            .filter { it != correct }).distinct().take(3).shuffled()
    }
    val axisOptions = remember(strip.id) {
        val correct = guided?.axisAr ?: "محور طبيعي"
        (listOf(correct) + listOf("محور طبيعي", "انحراف محور أيسر", "انحراف محور أيمن", "محور متطرّف")
            .filter { it != correct }).distinct().take(3).shuffled()
    }

    fun correctForStep(s: Int): String? = when (s) {
        0 -> strip.hr?.toString()
        1 -> guided?.rhythmAr
        2 -> guided?.axisAr
        else -> null
    }

    fun optionsForStep(s: Int): List<String> = when (s) {
        0 -> rateOptions
        1 -> rhythmOptions
        2 -> axisOptions
        else -> emptyList()
    }

    EcgSubScaffold(title = "القارئ الموجَّه", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val stripsBlock: @Composable () -> Unit = {
            Text(
                listOfNotNull(
                    strip.age?.let { "العمر $it" },
                    strip.sex,
                    envelope.patterns?.firstOrNull { it.id == strip.patternId }?.category
                ).joinToString(" • "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EcgTwelveLeadView(strip = strip, modifier = Modifier.fillMaxWidth())
            Text(
                "عرض الاتجاهات الاثني عشر — كبّر شريط النظم بالأسفل وقِس بالفرجار بالخطوة الرابعة.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EcgRhythmView(
                strip = strip,
                lead = strip.rhythmLead(),
                modifier = Modifier.fillMaxWidth().height(110.dp)
            )

            }
            val panelBlock: @Composable () -> Unit = {
            if (finished) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RoseSurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("أنهيت قراءة هذا الشريط", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("إجابات صحيحة من أول محاولة: $correctPicks/3", fontSize = 13.sp)
                        guided?.conclusionAr?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("الخلاصة: $it", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        val findings = guided?.findingsAr ?: emptyList()
                        findings.forEach { finding ->
                            Text("• $finding", fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "الخطوة ${step + 1} من 5: ${stepTitles[step]}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaroonPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        when (step) {
                            0 -> Text("ما معدّل القلب التقريبي بهذا الشريط؟ (استخدم طريقة 300 أو الفرجار)", fontSize = 14.sp)
                            1 -> Text("ما النظم؟ ابحث عن موجات P وانتظام فواصل RR.", fontSize = 14.sp)
                            2 -> Text("ما اتجاه المحور؟ ابدأ بالاتجاهين I وaVF.", fontSize = 14.sp)
                            3 -> {
                                Text("قِس الفواصل بالفرجار: ضع العلامتين على قمّتي QRS متتاليتين ثم على بداية P وبداية QRS.", fontSize = 14.sp)
                                Spacer(Modifier.height(10.dp))
                                EcgCalipersView(
                                    strip = strip,
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    onMeasurement = { ms, hr ->
                                        caliperText = "المسافة بين العلامتين: ${ms.toInt()}ms" +
                                            if (hr > 0) " — لو كانتا نبضتين متتاليتين فالمعدّل ≈ ${hr.toInt()}/دقيقة" else ""
                                    }
                                )
                                caliperText?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            else -> Text("راجع ST وT اتجاهًا اتجاهًا، ثم اكشف الموجودات الموثّقة والخلاصة.", fontSize = 14.sp)
                        }

                        val options = optionsForStep(step)
                        if (options.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            options.forEach { option ->
                                val isPicked = picked == option
                                val isCorrect = option == correctForStep(step)
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .clickable(enabled = !revealed) {
                                            picked = option
                                            revealed = true
                                            if (isCorrect) correctPicks++
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            !revealed -> MaterialTheme.colorScheme.surface
                                            isCorrect -> RoseSurfaceVariant
                                            isPicked -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Text(
                                        option + if (revealed && isCorrect) " ✓" else if (revealed && isPicked) " ✗" else "",
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            if (revealed) {
                                val correct = correctForStep(step)
                                Text(
                                    when (step) {
                                        0 -> "المعدّل الموثّق: ${strip.hr ?: "—"}/دقيقة"
                                        1 -> "النظم الموثّق: ${guided?.rhythmAr ?: "—"}"
                                        else -> "المحور الموثّق: ${guided?.axisAr ?: "—"}${strip.axis?.let { " ($it°)" } ?: ""}"
                                    },
                                    fontSize = 13.sp,
                                    color = MaroonPrimary
                                )
                                @Suppress("UNUSED_EXPRESSION") correct
                            }
                        }

                        if (step == 3 && !revealed) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { revealed = true }) {
                                Text("قستُ — اكشف الموجودات")
                            }
                        }
                        if (step == 3 && revealed) {
                            val findings = guided?.findingsAr ?: emptyList()
                            if (findings.isEmpty()) {
                                Text("لا موجودات موثّقة بهذا الشريط بعد.", fontSize = 13.sp)
                            } else {
                                findings.forEach { Text("• $it", fontSize = 13.sp) }
                            }
                        }
                        if (step == 4 && !revealed) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { revealed = true }) {
                                Text("اكشف الخلاصة")
                            }
                        }
                        if (step == 4 && revealed) {
                            guided?.conclusionAr?.let {
                                Text("الخلاصة: $it", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            val findings = guided?.findingsAr ?: emptyList()
                            findings.forEach { Text("• $it", fontSize = 13.sp) }
                        }

                        if (revealed) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (step >= 4) {
                                        strip.id?.let { store.markGuidedDone(it) }
                                        finished = true
                                    } else {
                                        step++
                                        picked = null
                                        revealed = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (step >= 4) "إنهاء القراءة" else "الخطوة التالية")
                            }
                        }
                    }
                }
            }
            }
            // Tablet pass (1.2.5): at >=840 dp window width the
            // strips and the step panel sit side by side; narrower
            // windows keep the stacked phone order.
            if (isExpandedWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        modifier = Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { stripsBlock() }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { panelBlock() }
                }
            } else {
                stripsBlock()
                panelBlock()
            }

        }
    }
}
