package sy.medpro.watani.ui.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.R
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.clinical.calcSpecsSet1
import sy.medpro.watani.ui.clinical.calcSpecsSet2
import sy.medpro.watani.ui.theme.*
import java.util.Locale

// 1. ABG & Electrolytes Lab (مختبر غازات الدم)
@Composable
fun AbgLabScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var ph by remember { mutableFloatStateOf(7.31f) }
    var pco2 by remember { mutableFloatStateOf(52f) }
    var hco3 by remember { mutableFloatStateOf(25f) }
    var na by remember { mutableFloatStateOf(140f) }
    var cl by remember { mutableFloatStateOf(102f) }

    val anionGap = na - (cl + hco3)
    val primaryDisorder = when {
        ph < 7.35f -> if (pco2 > 45f) "حماض تنفسي (Respiratory Acidosis)" else "حماض استقلابي (Metabolic Acidosis)"
        ph > 7.45f -> if (pco2 < 35f) "قلاء تنفسي (Respiratory Alkalosis)" else "قلاء استقلابي (Metabolic Alkalosis)"
        else -> "درجة حموضة طبيعية (Normal pH)"
    }
    val anionGapStatus = if (anionGap > 12f) "حماض استقلابي ذو فجوة صواعدية مرتفعة (High Anion Gap HAGMA)" else "حماض استقلابي طبيعي الفجوة (Normal Anion Gap / NAGMA)"

    DrawerScreenScaffold(title = "مختبر غازات الدم والشوارد (ABG Interpreter)", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaroonDeep), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("محلل غازات الدم الشرياني ومفسر الفجوة الصواعدية", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("ادخل قيم المختبر لتظهر لك المقاربة التشخيصية التدريجية فوراً.", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("درجة الحموضة (pH): ${String.format(Locale.US, "%.2f", ph)}", fontWeight = FontWeight.Bold)
                        Slider(value = ph, onValueChange = { ph = it }, valueRange = 6.8f..7.7f)

                        Text("الضغط الجزئي لـ CO2 (PaCO2): ${pco2.toInt()} mmHg", fontWeight = FontWeight.Bold)
                        Slider(value = pco2, onValueChange = { pco2 = it }, valueRange = 20f..80f)

                        Text("البيكاربونات (HCO3-): ${hco3.toInt()} mEq/L", fontWeight = FontWeight.Bold)
                        Slider(value = hco3, onValueChange = { hco3 = it }, valueRange = 10f..45f)

                        Text("الصوديوم (Na+): ${na.toInt()} | الكلور (Cl-): ${cl.toInt()}", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Slider(value = na, onValueChange = { na = it }, valueRange = 120f..160f, modifier = Modifier.weight(1f))
                            Slider(value = cl, onValueChange = { cl = it }, valueRange = 80f..130f, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaroonPrimary.copy(alpha = 0.1f)), border = BorderStroke(1.5.dp, MaroonPrimary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("التفسير السريري الفوري:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Text("• الاضطراب الأولي: $primaryDisorder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("• الفجوة الصواعدية الحسابية (Anion Gap): ${String.format(Locale.US, "%.1f", anionGap)} mEq/L (المجال الطبيعي 8–12)", fontSize = 13.sp)
                        if (ph < 7.35f) {
                            Text("• تصنيف الحماض: $anionGapStatus", fontSize = 13.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private data class TrainerTrace(
    val title: String,
    val description: String,
    val isNormal: Boolean,
    val imageRes: Int,
    val attribution: String
)

// 2. ECG Trainer (مدرب تخطيط القلب مع عزل LTR للمخططات)
@Composable
fun EcgTrainerScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var selectedTrace by remember { mutableIntStateOf(0) }
    val traces = listOf(
        TrainerTrace("النظم الجيبي الطبيعي (Normal Sinus Rhythm)", "معدل 75 ضربة/دقيقة، أمواج P نظامية تليها QRS ومسافة PR طبيعية (0.12–0.20s).", true, R.drawable.ecg_nsr, "المصدر: Ptrump16 — CC BY-SA 4.0 (مقصوصة الترويسة)"),
        TrainerTrace("احتشاء العضلة القلبية حاد (STEMI - ST Elevation)", "ارتفاع قطعة ST مقعر للأعلى مع أمواج T حادة شاهقة في المساري المخبرية الأمامية V1-V4.", false, R.drawable.ecg_stemi_ant, "المصدر: Cureus 2018 — CC BY"),
        TrainerTrace("الرجفان الأذيني (Atrial Fibrillation)", "غياب أمواج P الواضحة، عدم انتظام مسافات R-R تماماً، وظهور خط الأساس المتعرج (Baseline Fibrillatory waves).", false, R.drawable.ecg_afib, "المصدر: R.W. Koster (ECGPedia) — CC BY-SA 3.0"),
        TrainerTrace("تسرع القلب البطيني (Ventricular Tachycardia - VT)", "مركبات QRS عريضة ومنتظمة وسريعة (> 150 bpm) دون أمواج P سابقة.", false, R.drawable.ecg_vtach, "المصدر: Glenlarson — ملكية عامة")
    )

    DrawerScreenScaffold(title = "مدرب قراءة تخطيط القلب (ECG Trainer)", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaroonDeep), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("تدريب قراءة تخطيط القلب الحالات الحرجة", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("تخطيطات حقيقية لمرضى معروضة داخل بيئة LTR معزولة لضمان دقة المحور.", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }

            itemsIndexed(traces) { idx, trace ->
                val isSelected = selectedTrace == idx
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaroonPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaroonPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().clickable { selectedTrace = idx }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(trace.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(8.dp))

                        // LTR Isolated ECG image (real patient tracing)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Image(
                                painter = painterResource(id = trace.imageRes),
                                contentDescription = trace.title,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Text(trace.attribution, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(trace.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

// 4. Night-Shift Simulator (محاكاة المناوبة الليلية الإسعافية)
@Composable
fun NightShiftSimulatorScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var currentScenarioIndex by remember { mutableIntStateOf(0) }
    var shiftScore by remember { mutableIntStateOf(100) }
    var feedbackMessage by remember { mutableStateOf("") }
    var isShiftCompleted by remember { mutableStateOf(false) }

    val scenarios = listOf(
        Triple(
            "المريض 1 (الإسعاف): مريض مسن 68 سنة يعاني ألماَ غامضاً بالبطن مع هبوط ضغط (80/50 mmHg) وتبيّن وجود كتلة نابضة بالبطن.",
            listOf(
                Pair("إجراء طبقي محوري وعائي إسعافي فوراً لنفي أم دم أبهر بطينية منفتقة", 0),
                Pair("الإنعاش الفوري بالسوائل الوريدية المحدودة ونقل الدم والاستدعاء الجراحي الإسعافي", 20),
                Pair("إعطاء مسكنات أفيونية قوية ومراقبة المريض بالمصل العادي", -30)
            ),
            "أم الدم الأبهرية البطينية المنفتقة (Ruptured AAA) هي طارئة جراحية قصوى؛ الطبقي المحوري مسموح فقط إذا كان المريض مستقراً تماماً، وإلا فالتحويل الفوري للعمليات هو المنقذ للحياة."
        ),
        Triple(
            "المريض 2 (العناية): مريض مزروع قلب وضع على جهاز التنفس الاصطناعي أصيب بحمى مفاجئة وضيق تنفس وتراجع إشباع الأكسجين إلى 86%.",
            listOf(
                Pair("إجراء صورة شعاعية للصدر وتركيب قثطرة وريدية مركزية وبدء صادات واسعة الطيف", 20),
                Pair("تطبيق مناورات فتح الطرق الهوائية وشفط المفرزات وأخذ غازات دم شرياني ABG وتعديل إعدادات المنفسة", 15),
                Pair("الانتظار حتى صباح الغد لرؤية الطبيب الأخصائي", -40)
            ),
            "تدبير القصور التنفسي الحاد في العناية يتطلب تقييم ABCDE فورياً وشفط المفرزات و ABG وتعديل الأكسجة دون إبطاء."
        )
    )

    DrawerScreenScaffold(title = "محاكاة المناوبة الليلية الإسعافية (Night-Shift ER)", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaroonDeep), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("محاكي المناوبة الليلية (حالات طوارئ متسلسلة)", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("تقييم قدرتك على اتخاذ القرارات الإسعافية الحرجة تحت الضغط.", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("رصيد نقاط المناوبة: $shiftScore / 100", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            if (!isShiftCompleted) {
                val sc = scenarios[currentScenarioIndex]
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("الحالة الإسعافية رقم ${currentScenarioIndex + 1} من ${scenarios.size}:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(sc.first, fontSize = 15.sp, lineHeight = 23.sp)
                        }
                    }
                }

                items(sc.second) { option ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaroonPrimary.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                shiftScore += option.second
                                feedbackMessage = sc.third
                                if (currentScenarioIndex < scenarios.size - 1) {
                                    currentScenarioIndex++
                                } else {
                                    isShiftCompleted = true
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaroonPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(option.first, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (feedbackMessage.isNotEmpty()) {
                    item {
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("التغذية الراجعة السريرية:", fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(feedbackMessage, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("تهانينا! لقد أنهيت المناوبة الليلية بنجاح", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("النتيجة النهائية للمناوبة: $shiftScore / 100", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(shape = MaterialTheme.shapes.medium,
                                onClick = {
                                    currentScenarioIndex = 0
                                    shiftScore = 100
                                    isShiftCompleted = false
                                    feedbackMessage = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                            ) {
                                Text("إعادة المناوبة", color = MaroonDeep, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. Lab Values Lab (مختبر القيم المخبرية المتقدم)
@Composable
fun LabValuesLabScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var searchQuery by remember { mutableStateOf("") }
    val labs = listOf(
        Triple("الهيموغلوبين (Hb)", "13.5–17.5 g/dL (ذكر) | 12.0–15.5 (أنثى)", "نقص الحاد يشير للنزف؛ الارتفاع يشير لكثرة الحمر الحقيقية (Polycythemia vera)."),
        Triple("الصوديوم (Na+)", "135–145 mEq/L", "القيم الحرجة < 120 أو > 160 مح/لتر تهدد بالحياة وتسبب اختلاجات ووذمة أو نزف دماغي."),
        Triple("البوتاسيوم (K+)", "3.5–5.0 mEq/L", "فرط البوتاسيوم (> 6.0) يسبب تطاول PR وتموج T والانسمام بطيني."),
        Triple("الكرياتينين (Serum Cr)", "0.6–1.2 mg/dL", "المؤشر الأساسي لتقييم الوظيفة الكلوية وتصفية CrCl."),
        Triple("البيليروبين الكلي", "0.3–1.2 mg/dL", "الارتفاع المباشر يشير لركود صفراوي أو انسداد الطرق الصفراوية.")
    )
    val filteredLabs = labs.filter { it.first.contains(searchQuery) || it.third.contains(searchQuery) }

    DrawerScreenScaffold(title = "مختبر القيم المخبرية والتحاليل (Lab Values)", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("بحث عن تحليل أو اختبار مخبري...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            items(filteredLabs) { lab ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(lab.first, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("المجال الطبيعي: ${lab.second}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(lab.third, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

// 6. Clinical Calculators Expanded (الحاسبات السريرية الموسعة)
@Composable
fun ClinicalCalculatorsExpandedScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var selectedCalc by remember { mutableIntStateOf(0) }
    val calculators = listOf(
        Triple("معيار Wells للـ DVT / PE", "تقييم احتمال الإصابة بالصمة الرئوية أو خثار الأوردة العميقة dựa على العلامات السريرية.", "احتمال منخفض (0-1) | متوسط (2-6) | مرتفع (>= 7)"),
        Triple("مقياس غلاسكو للغيبوبة (GCS)", "تقييم الاستجابة الحركية (1-6)، اللفظية (1-5)، والعينية (1-4).", "طبيعي 15 | غيبوبة عميقة <= 8"),
        Triple("مقياس CHA2DS2-VASc", "تقدير خطر السكتة الدماغية لدى مرضى الرجفان الأذيني لتحديد استطباب مضادات التخثر.", "الذكور >= 2 والإناناث >= 3 تستطب مضادات التخثر الفموية NOACs")
    )
    val allNewCalcSpecs = remember { calcSpecsSet1 + calcSpecsSet2 }

    DrawerScreenScaffold(title = "الحاسبات والمعايير السريرية الموسعة", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    calculators.indices.forEach { idx ->
                        FilterChip(
                            selected = selectedCalc == idx,
                            onClick = { selectedCalc = idx },
                            label = { Text(calculators[idx].first, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaroonPrimary, selectedLabelColor = Color.White)
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(calculators[selectedCalc].first, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(calculators[selectedCalc].second, fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaroonPrimary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("الاستطباب والتفسير الإكلينيكي:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(calculators[selectedCalc].third, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "حاسبات إضافية (56) — بالصيغة والمدخلات والتفسير والمصدر",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(allNewCalcSpecs) { spec ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(spec.titleAr, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(spec.formula, fontSize = 13.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("المدخلات:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
                        spec.inputs.forEach { input ->
                            Text(
                                "• ${input.labelAr} (${input.unit})" +
                                    (if (input.min != null && input.max != null) " — المجال: ${input.min} إلى ${input.max}" else "") +
                                    (if (input.default != null) " — الافتراضي: ${input.default}" else ""),
                                fontSize = 13.sp, lineHeight = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("التفسير:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
                        Text(spec.interpretation, fontSize = 13.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("مثال محسوب:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
                        Text(spec.workedExample, fontSize = 13.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("المصدر: ${spec.source}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
