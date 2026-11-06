package sy.medpro.watani.ui.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import sy.medpro.watani.ui.clinical.CalcInputSpec
import sy.medpro.watani.ui.clinical.CalcOutcome
import sy.medpro.watani.ui.clinical.CalcSpec
import sy.medpro.watani.ui.clinical.evaluateCalcSpec
import sy.medpro.watani.ui.clinical.ScoreItem
import sy.medpro.watani.ui.clinical.clinicalScoreDefinitions
import sy.medpro.watani.ui.clinical.evaluateScoreSpec
import sy.medpro.watani.ui.clinical.scoreSpecIds
import sy.medpro.watani.ui.clinical.BedsideClinicalTab
import sy.medpro.watani.ui.clinical.BedsideDosingAndIcuSuiteScreen
import sy.medpro.watani.ui.clinical.CALC_CATEGORY_TITLES
import sy.medpro.watani.ui.clinical.calcCatalogCategoryOf
import sy.medpro.watani.ui.clinical.calcCatalogCountOf
import sy.medpro.watani.ui.clinical.calcCatalogEntries
import sy.medpro.watani.ui.offline.OfflineClinicalCalculatorsScreen
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    var selectedTab by remember { mutableIntStateOf(0) }

    val anionGap = na - (cl + hco3)
    val primaryDisorder = when {
        ph < 7.35f -> if (pco2 > 45f) "حماض تنفسي (Respiratory Acidosis)" else "حماض استقلابي (Metabolic Acidosis)"
        ph > 7.45f -> if (pco2 < 35f) "قلاء تنفسي (Respiratory Alkalosis)" else "قلاء استقلابي (Metabolic Alkalosis)"
        else -> "درجة حموضة طبيعية (Normal pH)"
    }
    val anionGapStatus = if (anionGap > 12f) "حماض استقلابي ذو فجوة صواعدية مرتفعة (High Anion Gap HAGMA)" else "حماض استقلابي طبيعي الفجوة (Normal Anion Gap / NAGMA)"

    DrawerScreenScaffold(title = "مختبر غازات الدم والشوارد (ABG Interpreter)", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("محلل الغازات", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("اختبار الغازات (Quiz)", fontWeight = FontWeight.Bold) })
            }
            if (selectedTab == 0) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
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
            } else {
                LabQuizTab(cases = abgQuizCases)
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val labs = listOf(
        Triple("الهيموغلوبين (Hb)", "ذكر: 13.5–17.5 g/dL • أنثى: 12.0–15.5 g/dL", "نقص الحاد يشير للنزف؛ الارتفاع يشير لكثرة الحمر الحقيقية (Polycythemia vera)."),
        Triple("الصوديوم (Na+)", "135–145 mEq/L", "القيم الحرجة < 120 أو > 160 مح/لتر تهدد بالحياة وتسبب اختلاجات ووذمة أو نزف دماغي."),
        Triple("البوتاسيوم (K+)", "3.5–5.0 mEq/L", "فرط البوتاسيوم (> 6.0) يسبب تطاول PR وتموج T والانسمام بطيني."),
        Triple("الكرياتينين (Serum Cr)", "0.6–1.2 mg/dL", "المؤشر الأساسي لتقييم الوظيفة الكلوية وتصفية CrCl."),
        Triple("البيليروبين الكلي", "0.3–1.2 mg/dL", "الارتفاع المباشر يشير لركود صفراوي أو انسداد الطرق الصفراوية."),
        Triple("كريات الدم البيضاء (WBC)", "4,500–11,000 /μL", "الارتفاع يشير غالباً لعدوى أو التهاب حاد؛ الانخفاض يرفع خطر العدوى الشديدة."),
        Triple("العدلات % (Neutrophils %)", "40–70%", "ارتفاع النسبة مع ارتفاع WBC يرجح عدوى جرثومية حادة."),
        Triple("الصفيحات الدموية (Platelets)", "150,000–450,000 /μL", "خطر النزف العفوي يرتفع عندما تنخفض الصفيحات دون 10,000–20,000 /μL."),
        Triple("الحجم الكريوي الوسطي (MCV)", "80–100 fL", "< 80 صغير الكريات (نقص حديد، تالاسيميا) • > 100 كبير الكريات (عوز B12/فولات)."),
        Triple("غلوكوز الصيام (Fasting Glucose)", "70–99 mg/dL", "100–125 صياماً = ما قبل السكري؛ ≥ 126 في مناسبتين = داء سكري."),
        Triple("الهيموغلوبين السكري (HbA1c)", "<5.7%", "5.7–6.4% = ما قبل السكري؛ ≥ 6.5% = داء سكري؛ يعكس متوسط الغلوكوز لشهرين إلى ثلاثة."),
        Triple("ناقلة أمين الألانين (ALT)", "7–56 U/L", "ارتفاعها الشديد (> 10 أضعاف) يرجح أذية كبدية حادة خلوياً (التهاب كبد حاد)."),
        Triple("ناقلة أمين الأسبارتات (AST)", "10–40 U/L", "ترتفع في الأذية الكبدية وأيضاً في أذية العضلات واحتشاء القلب."),
        Triple("الفوسفاتاز القلوية (ALP)", "44–147 U/L", "الارتفاع مع ارتفاع البيليروبين يرجح ركوداً صفراوياً؛ ارتفاعها المعزول قد يكون عظمي المنشأ."),
        Triple("الألبومين (Albumin)", "3.5–5.0 g/dL", "انخفاضه يشير لسوء تغذية أو مرض كبدي مزمن أو فقدان بروتيني (كلوي أو معوي)."),
        Triple("الكالسيوم الكلي (Total Calcium)", "8.5–10.2 mg/dL", "يُصحح بإضافة 0.8 mg/dL لكل انخفاض 1 g/dL في ألبومين المصل عن 4.0."),
        Triple("المغنيسيوم (Magnesium)", "1.7–2.2 mg/dL", "نقصه يسبب نقص بوتاسيوم وكالسيوم مقاوماً للتصحيح واضطرابات نظم."),
        Triple("الفوسفات (Phosphate)", "2.5–4.5 mg/dL", "انخفاضه الشديد يسبب ضعفاً عضلياً وانحلالاً عضلياً؛ ارتفاعه يرافق القصور الكلوي."),
        Triple("يوريا الدم (Urea / BUN)", "7–20 mg/dL", "ارتفاعها مع كرياتينين طبيعي نسبياً يرجح نقص حجم أو نزفاً هضمياً علوياً أو حمية غنية بالبروتين."),
        Triple("تروبونين عالي الحساسية (hs-Troponin)", "حسب الفحص: hs-cTnT أنثى <14 ng/L • ذكر <22 ng/L", "أي قيمة فوق الحد الأعلى المرجعي (المئين 99) تعني أذية عضلة قلبية؛ التشخيص النهائي يعتمد السياق السريري والتغير التسلسلي."),
        Triple("الهرمون المنبه للدرق (TSH)", "0.4–4.0 mIU/L", "ارتفاعه مع انخفاض T4 الحر = قصور درق أولي؛ انخفاضه مع ارتفاع T4 الحر = فرط درق."),
        Triple("زمن البروثرومبين (PT)", "11–13.5 sec", "يطول في عوز عوامل التخثر ونقص فيتامين K وأمراض الكبد؛ يختلف قليلاً حسب الكاشف المخبري."),
        Triple("النسبة المعيارية الدولية (INR)", "0.8–1.1", "الهدف العلاجي عند مميعات الوارفارين عادة 2.0–3.0 حسب الاستطباب."),
        Triple("زمن الثرومبوبلاستين الجزئي المنشط (aPTT)", "25–35 sec", "يطول عند استخدام الهيبارين غير المجزأ وعوز عوامل المسار الداخلي."),
        Triple("البروتين المتفاعل C (CRP)", "<10 mg/L", "يرتفع سريعاً في العدوى والالتهاب الحاد؛ قيم > 100 mg/L ترجح عدوى جرثومية شديدة."),
        Triple("سرعة ترسب الكريات (ESR)", "ذكر: 0–15 mm/hr • أنثى: 0–20 mm/hr", "علامة التهابية بطيئة وغير نوعية؛ ترتفع مع العمر وفقر الدم وأمراض المناعة الذاتية."),
        Triple("كيناز الكرياتين (CK)", "ذكر: 55–170 U/L • أنثى: 30–135 U/L", "ارتفاعه الشديد يرجح انحلالاً عضلياً أو أذية عضلية؛ يرتفع باعتدال بعد الجهد العضلي الشديد."),
        Triple("اللاكتات (Lactate)", "0.5–2.2 mmol/L", "≥ 4 mmol/L مع علامات نقص إرواء = صدمة وحماض لاكتيكي ويستدعي إنعاشاً فورياً.")
    )
    val filteredLabs = labs.filter { it.first.contains(searchQuery) || it.third.contains(searchQuery) }

    DrawerScreenScaffold(title = "مختبر القيم المخبرية والتحاليل (Lab Values)", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("القيم المخبرية", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("اختبار المختبر (Quiz)", fontWeight = FontWeight.Bold) })
            }
            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
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
            } else {
                LabQuizTab(cases = labQuizCases)
            }
        }
    }
}

// 6. Clinical Calculators — directory by specialty (دليل الحاسبات حسب الاختصاص)
// Reorganization (Nov 2026 — doctor's order): the calculators used to render
// as one chaotic screen — the 4 hardcoded calculators on top, then huge
// cards carrying formula + inputs + interpretation + example + source for
// every spec in the list. Now there is ONE directory screen: specialty
// chips with counts, and clean rows showing ONLY the calculator name plus
// one short description line (from calcCatalog.kt — the single
// classification source for all 66 tools). Tapping a row opens that
// calculator's own detail screen, which renders its full existing content
// unchanged: for specs the title, formula, inputs, the existing
// SpecComputeBlock, interpretation, worked example and source; for the 4
// hardcoded calculators their existing components as-is; CURB-65 / ABG open
// OfflineClinicalCalculatorsScreen on their tab; the 4 bedside panes open
// BedsideDosingAndIcuSuiteScreen on their tab. No medical content, formula
// or compute logic was changed — navigation and grouping only.
private val CALC_CATEGORIES: List<String> get() = CALC_CATEGORY_TITLES

private fun calcCategoryOf(specId: String): String = calcCatalogCategoryOf(specId)

@Composable
fun ClinicalCalculatorsExpandedScreen(
    onBack: () -> Unit,
    initialCategory: String = CALC_CATEGORY_TITLES.first()
) {
    val allCalcSpecs = remember { calcSpecsSet1 + calcSpecsSet2 }
    val specsById = remember(allCalcSpecs) { allCalcSpecs.associateBy { it.id } }
    var selectedCalcCategory by remember {
        mutableStateOf(
            if (initialCategory in CALC_CATEGORY_TITLES) initialCategory else CALC_CATEGORY_TITLES.first()
        )
    }
    var selectedToolKey by remember { mutableStateOf<String?>(null) }
    val backToDirectory: () -> Unit = { selectedToolKey = null }

    when (val key = selectedToolKey) {
        null -> CalculatorsDirectoryScreen(
            onBack = onBack,
            selectedCategory = selectedCalcCategory,
            onSelectCategory = { selectedCalcCategory = it },
            specsById = specsById,
            onOpenTool = { selectedToolKey = it }
        )
        "curb65" -> OfflineClinicalCalculatorsScreen(onBack = backToDirectory, initialTab = 0)
        "abg_interpreter" -> OfflineClinicalCalculatorsScreen(onBack = backToDirectory, initialTab = 1)
        "bedside_icu" -> BedsideDosingAndIcuSuiteScreen(
            initialTab = BedsideClinicalTab.ICU_INFUSIONS,
            onBack = backToDirectory
        )
        "bedside_fluids" -> BedsideDosingAndIcuSuiteScreen(
            initialTab = BedsideClinicalTab.FLUIDS_AND_BURNS,
            onBack = backToDirectory
        )
        "bedside_renal_abx" -> BedsideDosingAndIcuSuiteScreen(
            initialTab = BedsideClinicalTab.RENAL_ANTIBIOTICS,
            onBack = backToDirectory
        )
        "bedside_peds" -> BedsideDosingAndIcuSuiteScreen(
            initialTab = BedsideClinicalTab.PEDIATRIC_DOSING,
            onBack = backToDirectory
        )
        "wells_dvt", "wells_pe", "gcs", "cha2ds2vasc" -> HardcodedCalcDetailScreen(
            toolKey = key,
            onBack = backToDirectory
        )
        else -> {
            val spec = specsById[key]
            if (spec != null) {
                CalcSpecDetailScreen(spec = spec, onBack = backToDirectory)
            } else {
                CalculatorsDirectoryScreen(
                    onBack = onBack,
                    selectedCategory = selectedCalcCategory,
                    onSelectCategory = { selectedCalcCategory = it },
                    specsById = specsById,
                    onOpenTool = { selectedToolKey = it }
                )
            }
        }
    }
}

/**
 * شاشة الدليل: شرائح الاختصاصات العشرة مع عدّاد كل فئة، وصفوف نظيفة
 * بالاسم + سطر وصف قصير فقط — بلا صيغ ولا معايير ولا أرقام في القائمة.
 */
@Composable
private fun CalculatorsDirectoryScreen(
    onBack: () -> Unit,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    specsById: Map<String, CalcSpec>,
    onOpenTool: (String) -> Unit
) {
    BackHandler { onBack() }
    val visibleEntries = remember(selectedCategory) {
        calcCatalogEntries.filter { it.categoryTitleAr == selectedCategory }
    }
    DrawerScreenScaffold(title = "الحاسبات الطبية", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "كل الحاسبات مرتبة حسب الاختصاص الطبي — اختر اختصاصًا ثم اضغط أي حاسبة لفتحها كاملة بصيغتها ومدخلاتها وتفسيرها.",
                    fontSize = 13.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            item {
                // Specialty bar: horizontally scrollable chips with the
                // catalog count of each of the 10 specialties.
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CALC_CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            label = { Text("$category (${calcCatalogCountOf(category)})", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaroonPrimary, selectedLabelColor = Color.White)
                        )
                    }
                }
            }
            item {
                Text(
                    "حاسبات $selectedCategory (${visibleEntries.size})",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(visibleEntries, key = { it.key }) { entry ->
                val title = entry.titleAr ?: specsById[entry.key]?.titleAr ?: entry.key
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenTool(entry.key) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(entry.shortDescriptionAr, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

/**
 * شاشة تفصيل مواصفة واحدة: المحتوى الكامل القائم كما هو حرفيًا —
 * العنوان والصيغة والمدخلات وكتلة الحساب القائمة والتفسير والمثال
 * المحسوب والمصدر، من حقول الـ CalcSpec نفسها دون أي إعادة كتابة.
 */
@Composable
private fun CalcSpecDetailScreen(spec: CalcSpec, onBack: () -> Unit) {
    BackHandler { onBack() }
    DrawerScreenScaffold(title = "الحاسبات الطبية", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
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
                        if (spec.id in scoreSpecIds) {
                            // The 19 interactive scores render their items as
                            // choice chips inside SpecComputeBlock below; the
                            // numeric inputs list (with zero-ish defaults)
                            // would falsely imply an assessment already made.
                            Text("قيّم البنود التالية — تُحسب الدرجة فور اكتمالها:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
                        } else {
                            Text("المدخلات:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
                            spec.inputs.forEach { input ->
                                Text(
                                    "• ${input.labelAr} (${input.unit})" +
                                        (if (input.min != null && input.max != null) " — المجال: ${input.min} إلى ${input.max}" else "") +
                                        (if (input.default != null) " — الافتراضي: ${input.default}" else ""),
                                    fontSize = 13.sp, lineHeight = 20.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SpecComputeBlock(spec)
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

/**
 * شاشة تفصيل إحدى الحاسبات الأربع المبرمجة مباشرة: مكوّنها القائم
 * كما هو دون أي تغيير في محتواه الداخلي.
 */
@Composable
private fun HardcodedCalcDetailScreen(toolKey: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    DrawerScreenScaffold(title = "الحاسبات الطبية", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            when (toolKey) {
                "wells_dvt" -> item { WellsDvtCalculator() }
                "wells_pe" -> item { WellsPeCalculator() }
                "gcs" -> item { GcsCalculator() }
                else -> item { ChaCalculator() }
            }
        }
    }
}

// Interactive clinical scores (replacing the former static text cards).
// Sources: Wells et al. DVT 2003 / PE 2000, Teasdale & Jennett GCS 1974,
// Lip et al. / ESC AF guideline CHA2DS2-VASc. All cutoffs verified
// against the published criteria before wiring.

@Composable
private fun ScoreResultCard(scoreText: String, band: String, action: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaroonPrimary.copy(alpha = 0.1f)),
        border = BorderStroke(1.5.dp, MaroonPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("النتيجة: $scoreText", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaroonPrimary)
            Text("التصنيف: $band", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(action, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun CheckRow(label: String, points: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) }
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle(it) })
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(points, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
        }
    }
}

@Composable
private fun WellsDvtCalculator() {
    val labels = listOf(
        "سرطان نشط (علاج خلال 6 أشهر أو تلطيفي)" to 1,
        "شلل أو ضعف أو جبس حديث بالطرف السفلي" to 1,
        "ملازمة الفراش ≥ 3 أيام أو جراحة كبرى خلال 12 أسبوعًا" to 1,
        "إيلام موضعي على مسار الوريد العميق" to 1,
        "تورم الساق بالكامل" to 1,
        "تورم الربلة ≥ 3 سم عن الساق الأخرى (10 سم تحت حدبة الظنبوب)" to 1,
        "وذمة انطباعية محصورة بالساق المصابة" to 1,
        "أوردة سطحية جانبية (غير دوالية)" to 1,
        "خثار وريدي عميق موثق سابقًا" to 1,
        "تشخيص بديل مساوٍ أو أرجح من الخثار" to -2
    )
    val checked = remember { mutableStateListOf<Boolean>().apply { repeat(labels.size) { add(false) } } }
    val score = labels.indices.sumOf { if (checked[it]) labels[it].second else 0 }
    val (band, action) = when {
        score <= 0 -> "احتمال منخفض" to "D-dimer عالي الحساسية: إذا كان سلبيًا يُستبعد الخثار دون تصوير؛ وإذا كان إيجابيًا يُجرى إيكو دوبلكس مضغوط."
        score <= 2 -> "احتمال متوسط" to "يُجرى D-dimer مع إيكو دوبلكس مضغوط للأوردة القريبة حسب السياق السريري."
        else -> "احتمال مرتفع" to "إيكو دوبلكس مضغوط مباشرة؛ لا يُعتمد على D-dimer وحده لنفي الخثار."
    }
    val twoTier = if (score >= 2) "مرجح (Likely)" else "غير مرجح (Unlikely)"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("معيار Wells لخثار الأوردة العميقة (Wells DVT) — اختر الموجود لدى المريض:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
        labels.forEachIndexed { idx, (label, pts) ->
            CheckRow(label, if (pts >= 0) "+$pts" else "$pts", checked[idx]) { checked[idx] = it }
        }
        ScoreResultCard("$score", "$band — النموذج ثنائي المستوى: $twoTier", action)
        Text("المصدر: Wells et al., Lancet 1997 / Wells 2003 (ثلاثي المستوى: ≤0 منخفض، 1-2 متوسط، ≥3 مرتفع؛ ثنائي المستوى: ≤1 غير مرجح، ≥2 مرجح).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun WellsPeCalculator() {
    val labels = listOf(
        "علامات سريرية لخثار وريدي عميق (تورم ساق + ألم بالجس)" to 3.0,
        "تشخيص بديل أقل ترجيحًا من الانصمام الرئوي" to 3.0,
        "سرعة قلب > 100 ضربة/دقيقة" to 1.5,
        "تثبيت ≥ 3 أيام أو جراحة خلال الأسابيع الأربعة الماضية" to 1.5,
        "خثار أو انصمام سابق موثق" to 1.5,
        "نفث دموي" to 1.0,
        "خباثة (علاج جارٍ أو خلال 6 أشهر أو تلطيفي)" to 1.0
    )
    val checked = remember { mutableStateListOf<Boolean>().apply { repeat(labels.size) { add(false) } } }
    val score = labels.indices.sumOf { if (checked[it]) labels[it].second else 0.0 }
    val scoreText = if (score % 1.0 == 0.0) score.toInt().toString() else String.format(Locale.US, "%.1f", score)
    val (band, action) = when {
        score < 2.0 -> "احتمال منخفض" to "D-dimer عالي الحساسية: إذا كان سلبيًا يُستبعد الانصمام دون تصوير."
        score <= 6.0 -> "احتمال متوسط" to "D-dimer عالي الحساسية أو تصوير مقطعي وعائي رئوي (CTPA) حسب التوافر والسياق."
        else -> "احتمال مرتفع" to "تصوير مقطعي وعائي رئوي (CTPA) مباشرة؛ لا يُعتمد على D-dimer لنفي الانصمام."
    }
    val twoTier = if (score > 4.0) "مرجح (Likely)" else "غير مرجح (Unlikely)"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("معيار Wells للانصمام الرئوي (Wells PE) — اختر الموجود لدى المريض:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
        labels.forEachIndexed { idx, (label, pts) ->
            val ptsText = if (pts % 1.0 == 0.0) "+${pts.toInt()}" else "+$pts"
            CheckRow(label, ptsText, checked[idx]) { checked[idx] = it }
        }
        ScoreResultCard(scoreText, "$band — النموذج ثنائي المستوى: $twoTier", action)
        Text("المصدر: Wells et al., Thromb Haemost 2000 (ثلاثي المستوى: <2 منخفض، 2-6 متوسط، >6 مرتفع؛ ثنائي المستوى: ≤4 غير مرجح، >4 مرجح).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun GcsRadioGroup(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
        options.forEachIndexed { idx, opt ->
            val value = idx + 1
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == value, onClick = { onSelect(value) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(opt, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GcsCalculator() {
    var eye by remember { mutableIntStateOf(4) }
    var verbal by remember { mutableIntStateOf(5) }
    var motor by remember { mutableIntStateOf(6) }
    val total = eye + verbal + motor
    val (band, action) = when (total) {
        in 13..15 -> "إصابة خفيفة" to "مراقبة وتصوير حسب الموجودات؛ إعادة التقييم المتكررة."
        in 9..12 -> "إصابة متوسطة" to "مراقبة حثيثة وتصوير دماغي؛ استشارة جراحة الأعصاب حسب السبب."
        else -> "إصابة وخيمة (غيبوبة)" to "تُقيَّم الحاجة لتأمين الطريق الهوائي والتنبيب فورًا مع الإنعاش والتصوير الإسعافي."
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("مقياس غلاسكو للغيبوبة (GCS) — اختر أفضل استجابة في كل محور:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
        GcsRadioGroup("الاستجابة العينية (1-4)", listOf("لا يفتح عينيه (1)", "للألم (2)", "للصوت (3)", "تلقائيًا (4)"), eye) { eye = it }
        GcsRadioGroup("الاستجابة اللفظية (1-5)", listOf("لا استجابة (1)", "أصوات غير مفهومة (2)", "كلمات غير مناسبة (3)", "مرتبك (4)", "موجه (5)"), verbal) { verbal = it }
        GcsRadioGroup("الاستجابة الحركية (1-6)", listOf("لا استجابة (1)", "بسط (2)", "انثناء شاذ (3)", "انسحاب من الألم (4)", "يحدد الألم (5)", "يطيع الأوامر (6)"), motor) { motor = it }
        ScoreResultCard("$total / 15  (عيني $eye + لفظي $verbal + حركي $motor)", band, action)
        Text("المصدر: Teasdale & Jennett 1974 (13-15 خفيف، 9-12 متوسط، ≤8 وخيم).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun ChaCalculator() {
    var chf by remember { mutableStateOf(false) }
    var htn by remember { mutableStateOf(false) }
    var dm by remember { mutableStateOf(false) }
    var stroke by remember { mutableStateOf(false) }
    var vascular by remember { mutableStateOf(false) }
    var female by remember { mutableStateOf(false) }
    var ageGroup by remember { mutableIntStateOf(0) } // 0: <65, 1: 65-74 (+1), 2: ≥75 (+2)
    val score = (if (chf) 1 else 0) + (if (htn) 1 else 0) + (if (ageGroup == 2) 2 else 0) +
        (if (dm) 1 else 0) + (if (stroke) 2 else 0) + (if (vascular) 1 else 0) +
        (if (ageGroup == 1) 1 else 0) + (if (female) 1 else 0)
    val recommendation = when {
        !female && score == 0 -> "لا يُستطب مضاد التخثر."
        !female && score == 1 -> "يُنظر في مضاد التخثر الفموي حسب تفضيل المريض وخطر النزف."
        !female && score >= 2 -> "يُستطب مضاد التخثر الفموي (NOAC)."
        female && score <= 1 -> "لا يُستطب مضاد التخثر (نقطة الجنس وحدها لا تستدعي علاجًا)."
        female && score == 2 -> "يُنظر في مضاد التخثر الفموي حسب تفضيل المريض وخطر النزف."
        else -> "يُستطب مضاد التخثر الفموي (NOAC)."
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("مقياس CHA2DS2-VASc (رجفان أذيني) — اختر الموجود لدى المريض:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
        CheckRow("قصور قلب احتقاني أو خلل وظيفة بطين أيسر (C)" , "+1", chf) { chf = it }
        CheckRow("ارتفاع ضغط الدم (H)", "+1", htn) { htn = it }
        CheckRow("داء سكري (D)", "+1", dm) { dm = it }
        CheckRow("سكتة دماغية أو نوبة إقفارية عابرة أو انصمام جهازي سابق (S2)", "+2", stroke) { stroke = it }
        CheckRow("مرض وعائي (احتشاء سابق أو مرض شرياني محيطي أو لويحة أبهرية) (V)", "+1", vascular) { vascular = it }
        CheckRow("الجنس أنثى (Sc)", "+1", female) { female = it }
        Text("الفئة العمرية (A):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
        Row(modifier = Modifier.fillMaxWidth().clickable { ageGroup = 0 }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = ageGroup == 0, onClick = { ageGroup = 0 })
            Spacer(modifier = Modifier.width(8.dp))
            Text("أقل من 65 سنة (0)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Row(modifier = Modifier.fillMaxWidth().clickable { ageGroup = 1 }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = ageGroup == 1, onClick = { ageGroup = 1 })
            Spacer(modifier = Modifier.width(8.dp))
            Text("65-74 سنة (+1)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Row(modifier = Modifier.fillMaxWidth().clickable { ageGroup = 2 }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = ageGroup == 2, onClick = { ageGroup = 2 })
            Spacer(modifier = Modifier.width(8.dp))
            Text("75 سنة أو أكثر (+2)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        ScoreResultCard("$score", recommendation, "عتبات التوصية: الرجال ≥2 والنساء ≥3 → مضاد تخثر فموي؛ الرجال 1 والنساء 2 → يُنظر فيه؛ أقل من ذلك → لا يُستطب.")
        Text("المصدر: Lip et al. / ESC Atrial Fibrillation guideline (CHA2DS2-VASc).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

// Lab quiz (library tab + quiz tab pattern, mirroring AuscultationTrainerScreen).
data class LabQuizCase(
    val valuesText: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

val labQuizCases = listOf(
    LabQuizCase(
        valuesText = "Hb 7.2 g/dL, MCV 68 fL, ferritin 8 ng/mL",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("فقر دم بعوز فيتامين B12", "فقر دم بعوز الحديد", "فقر دم انحلالي", "فقر دم مرض مزمن"),
        correctIndex = 1,
        explanation = "فقر دم صغير الكريات (MCV منخفض) مع فيريتين منخفض جداً = عوز حديد."
    ),
    LabQuizCase(
        valuesText = "Hb 9.5 g/dL, MCV 118 fL, B12 90 pg/mL",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("فقر دم بعوز الحديد", "تالاسيميا", "فقر دم كبير الكريات بعوز B12", "فقر دم انحلالي"),
        correctIndex = 2,
        explanation = "فقر دم كبير الكريات (MCV مرتفع) مع B12 منخفض = فقر دم ضخم الأرومات بعوز B12."
    ),
    LabQuizCase(
        valuesText = "WBC 22,000 /μL, neutrophils 88%, CRP 180 mg/L",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("عدوى فيروسية", "قصور درق", "عدوى جرثومية حادة", "فقر دم"),
        correctIndex = 2,
        explanation = "كثرة بيض مع سيطرة عدلات وCRP مرتفع جداً = عدوى جرثومية حادة."
    ),
    LabQuizCase(
        valuesText = "Fasting glucose 210 mg/dL, HbA1c 9.2%",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("ما قبل السكري فقط", "نقص سكر", "متلازمة كوشينغ", "داء سكري"),
        correctIndex = 3,
        explanation = "غلوكوز صيام ≥ 126 mg/dL وHbA1c ≥ 6.5% = داء سكري، وليس ما قبل السكري."
    ),
    LabQuizCase(
        valuesText = "ALT 980 U/L, AST 720 U/L, ALP 90 U/L, bilirubin 3.5 mg/dL",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("أذية كبدية خلوية حادة (التهاب كبد)", "ركود صفراوي", "التهاب بنكرياس حاد", "قصور قلب"),
        correctIndex = 0,
        explanation = "ارتفاع هائل في الناقلات الأمينية مع ALP شبه طبيعية = أذية كبدية خلوية حادة."
    ),
    LabQuizCase(
        valuesText = "ALP 420 U/L, bilirubin 6.8 mg/dL, ALT 60 U/L, AST 55 U/L",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("أذية كبدية خلوية حادة", "التهاب كبد فيروسي حاد", "ركود صفراوي", "انحلال دم"),
        correctIndex = 2,
        explanation = "ارتفاع ALP والبيليروبين مع ارتفاع طفيف في الناقلات = نمط ركودي صفراوي."
    ),
    LabQuizCase(
        valuesText = "pH 7.52, HCO3 32 mEq/L, K 2.8 mEq/L, Cl 90 mEq/L",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("حماض تنفسي", "قلاء استقلابي ناقص البوتاسيوم", "حماض استقلابي عالي الفجوة", "قلاء تنفسي"),
        correctIndex = 1,
        explanation = "pH مرتفع مع HCO3 مرتفعة ونقص بوتاسيوم = قلاء استقلابي ناقص البوتاسيوم (إقياء أو مدرات)."
    ),
    LabQuizCase(
        valuesText = "K 6.8 mEq/L, Cr 4.2 mg/dL, urea 95 mg/dL",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("نقص بوتاسيوم", "فرط بوتاسيوم مع أذية كلوية حادة", "حماض تنفسي", "فرط كالسيوم"),
        correctIndex = 1,
        explanation = "فرط بوتاسيوم مهدد للحياة مع ارتفاع الكرياتينين واليوريا = أذية كلوية حادة مع فرط بوتاسيوم."
    ),
    LabQuizCase(
        valuesText = "Platelets 18,000 /μL, Hb 12.8 g/dL, WBC 6,500 /μL",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("فقر دم لا تنسجي", "ابيضاض دم حاد", "نقص صفيحات معزول شديد", "نقص صفيحات خفيف عابر"),
        correctIndex = 2,
        explanation = "صفيحات < 20,000 مع Hb وWBC طبيعيين = نقص صفيحات معزول شديد بخطر نزف عفوي."
    ),
    LabQuizCase(
        valuesText = "TSH 18 mIU/L, FT4 0.5 ng/dL",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("فرط درق", "قصور درق ثانوي (نخامي)", "متلازمة المريض السوي الدرق", "قصور درق أولي"),
        correctIndex = 3,
        explanation = "TSH مرتفع مع FT4 منخفض = قصور درق أولي (منشأ درقي)."
    ),
    LabQuizCase(
        valuesText = "TSH 0.05 mIU/L, FT4 3.2 ng/dL",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("قصور درق", "فرط درق", "قصور نخامي", "مقاومة هرمون درقي"),
        correctIndex = 1,
        explanation = "TSH مكبوت مع FT4 مرتفع = فرط درق."
    ),
    LabQuizCase(
        valuesText = "pH 7.18, HCO3 10 mEq/L, lactate 6.5 mmol/L, Na 140, Cl 102",
        question = "ما النمط المخبري الأرجح؟",
        options = listOf("حماض تنفسي", "قلاء استقلابي", "حماض استقلابي طبيعي الفجوة", "حماض لاكتيكي (حماض استقلابي عالي الفجوة)"),
        correctIndex = 3,
        explanation = "الفجوة الصواعدية = 140 − (102 + 10) = 28 mEq/L (مرتفعة) مع لاكتات 6.5 = حماض لاكتيكي."
    )
)

val abgQuizCases = listOf(
    LabQuizCase(
        valuesText = "pH 7.28, PaCO2 30 mmHg, HCO3 14 mEq/L, Na 140, Cl 108",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("حماض استقلابي عالي الفجوة", "حماض استقلابي طبيعي الفجوة", "حماض تنفسي", "قلاء استقلابي"),
        correctIndex = 0,
        explanation = "الفجوة الصواعدية = 140 − (108 + 14) = 18 mEq/L (مرتفعة) مع pH منخفض وHCO3 منخفضة = حماض استقلابي عالي الفجوة؛ وPaCO2 المتوقعة بمعادلة وينتر = 1.5×14+8±2 = 29±2، والقيمة الفعلية 30 ضمنها، فالتعويض التنفسي كافٍ."
    ),
    LabQuizCase(
        valuesText = "pH 7.30, PaCO2 34 mmHg, HCO3 16 mEq/L, Na 140, Cl 116",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("حماض استقلابي عالي الفجوة", "حماض استقلابي طبيعي الفجوة", "حماض تنفسي", "قلاء تنفسي"),
        correctIndex = 1,
        explanation = "الفجوة الصواعدية = 140 − (116 + 16) = 8 mEq/L (طبيعية) مع pH منخفض وHCO3 منخفضة = حماض استقلابي طبيعي الفجوة (كإسهال أو حماض أنبوبي كلوي)؛ وPaCO2 المتوقعة بمعادلة وينتر = 1.5×16+8±2 = 32±2، والقيمة الفعلية 34 ضمنها، فالتعويض كافٍ."
    ),
    LabQuizCase(
        valuesText = "pH 7.52, PaCO2 48 mmHg, HCO3 34 mEq/L, Na 140, Cl 98",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("قلاء تنفسي", "حماض استقلابي", "قلاء استقلابي", "حماض تنفسي"),
        correctIndex = 2,
        explanation = "الفجوة الصواعدية = 140 − (98 + 34) = 8 mEq/L (طبيعية)؛ pH مرتفع مع HCO3 مرتفعة = قلاء استقلابي أولي، وارتفاع PaCO2 إلى 48 هو التعويض التنفسي المتوقع."
    ),
    LabQuizCase(
        valuesText = "pH 7.30, PaCO2 60 mmHg, HCO3 27 mEq/L, Na 140, Cl 102",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("حماض تنفسي", "حماض استقلابي", "قلاء تنفسي", "قلاء استقلابي"),
        correctIndex = 0,
        explanation = "الفجوة الصواعدية = 140 − (102 + 27) = 11 mEq/L (طبيعية)؛ pH منخفض مع PaCO2 مرتفعة = حماض تنفسي أولي (نقص تهوية)، وHCO3 المرتفعة قليلاً تعويض كلوي مبكر."
    ),
    LabQuizCase(
        valuesText = "pH 7.50, PaCO2 26 mmHg, HCO3 20 mEq/L, Na 140, Cl 108",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("قلاء استقلابي", "قلاء تنفسي", "حماض تنفسي", "حماض استقلابي"),
        correctIndex = 1,
        explanation = "الفجوة الصواعدية = 140 − (108 + 20) = 12 mEq/L (ضمن الطبيعي)؛ pH مرتفع مع PaCO2 منخفضة = قلاء تنفسي أولي (فرط تهوية)، وانخفاض HCO3 تعويض كلوي."
    ),
    LabQuizCase(
        valuesText = "pH 7.38, PaCO2 22 mmHg, HCO3 12 mEq/L, Na 140, Cl 106",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("حماض استقلابي عالي الفجوة وحده معوض تعويضاً كافياً", "حماض استقلابي طبيعي الفجوة", "حماض استقلابي عالي الفجوة مع قلاء تنفسي مرافق", "قلاء استقلابي"),
        correctIndex = 2,
        explanation = "الفجوة الصواعدية = 140 − (106 + 12) = 22 mEq/L (مرتفعة) = حماض استقلابي عالي الفجوة، لكن PaCO2 المتوقعة بمعادلة وينتر = 1.5×12+8±2 = 26±2، والقيمة الفعلية 22 أقل منها، وpH شبه طبيعي رغم شدة الحماض = قلاء تنفسي مرافق (اضطراب مختلط)."
    ),
    LabQuizCase(
        valuesText = "pH 7.42, PaCO2 40 mmHg, HCO3 24 mEq/L, Na 144, Cl 100",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("حماض استقلابي عالي الفجوة وحده", "حماض استقلابي طبيعي الفجوة", "قلاء تنفسي", "حماض استقلابي عالي الفجوة مع قلاء استقلابي مرافق"),
        correctIndex = 3,
        explanation = "الفجوة الصواعدية = 144 − (100 + 24) = 20 mEq/L (مرتفعة) تثبت حماضاً استقلابياً عالي الفجوة، لكن HCO3 لم تنخفض كما يتوقع من الحماض وحده (بقيت 24 رغم ارتفاع الفجوة 8 فوق الطبيعي) وpH طبيعي = قلاء استقلابي مرافق (اضطراب مختلط)."
    ),
    LabQuizCase(
        valuesText = "pH 7.40, PaCO2 40 mmHg, HCO3 24 mEq/L, Na 140, Cl 104",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("حماض استقلابي", "غازات دم طبيعية", "قلاء تنفسي", "حماض تنفسي"),
        correctIndex = 1,
        explanation = "الفجوة الصواعدية = 140 − (104 + 24) = 12 mEq/L (طبيعية)؛ pH وPaCO2 وHCO3 كلها ضمن المجال الطبيعي = غازات دم شرياني طبيعية بلا اضطراب."
    ),
    LabQuizCase(
        valuesText = "pH 7.25, PaCO2 45 mmHg, HCO3 15 mEq/L, Na 140, Cl 110",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟ (فحص تعويض وينتر)",
        options = listOf("حماض استقلابي عالي الفجوة مع حماض تنفسي مرافق", "حماض استقلابي عالي الفجوة معوض تعويضاً كافياً", "حماض تنفسي وحده", "حماض استقلابي طبيعي الفجوة"),
        correctIndex = 0,
        explanation = "الفجوة الصواعدية = 140 − (110 + 15) = 15 mEq/L (مرتفعة) = حماض استقلابي عالي الفجوة، لكن PaCO2 المتوقعة بمعادلة وينتر = 1.5×15+8±2 = 30.5±2، والقيمة الفعلية 45 أعلى بكثير = تعويض تنفسي غير كافٍ لوجود حماض تنفسي مرافق (اضطراب مختلط)."
    ),
    LabQuizCase(
        valuesText = "pH 7.34, PaCO2 60 mmHg, HCO3 32 mEq/L, Na 140, Cl 100",
        question = "ما الاضطراب الحمضي-القاعدي الأرجح؟",
        options = listOf("حماض تنفسي حاد", "حماض استقلابي", "حماض تنفسي مزمن معوض كلوياً", "قلاء استقلابي"),
        correctIndex = 2,
        explanation = "الفجوة الصواعدية = 140 − (100 + 32) = 8 mEq/L (طبيعية)؛ PaCO2 مرتفعة جداً مع pH شبه طبيعي وHCO3 مرتفعة جداً = حماض تنفسي مزمن مع تعويض كلوي كامل تقريباً (لا حماضاً حاداً، لأن الحاد لا يرفع HCO3 إلى هذا الحد)."
    )
)

@Composable
private fun LabQuizTab(cases: List<LabQuizCase>) {
    var index by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val current = cases.getOrNull(index)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (finished) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaroonPrimary.copy(alpha = 0.1f)), border = BorderStroke(1.5.dp, MaroonPrimary), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("انتهى الاختبار!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                    Text("نتيجتك: $score من ${cases.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Button(onClick = { index = 0; selectedOption = null; submitted = false; score = 0; finished = false }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary), shape = MaterialTheme.shapes.medium) {
                        Text("إعادة الاختبار")
                    }
                }
            }
        } else if (current != null) {
            Text("السؤال ${index + 1} من ${cases.size} — النقاط: $score", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.valuesText, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                    Text(current.question, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            current.options.forEachIndexed { optIdx, opt ->
                val isSelected = selectedOption == optIdx
                val bg = when {
                    submitted && optIdx == current.correctIndex -> MaterialTheme.colorScheme.primaryContainer
                    submitted && isSelected && optIdx != current.correctIndex -> MaterialTheme.colorScheme.errorContainer
                    isSelected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) { selectedOption = optIdx }
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = { if (!submitted) selectedOption = optIdx })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(opt, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (!submitted) {
                Button(
                    onClick = {
                        if (selectedOption != null) {
                            submitted = true
                            if (selectedOption == current.correctIndex) score++
                        }
                    },
                    enabled = selectedOption != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("تأكيد الإجابة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (selectedOption == current.correctIndex) "إجابة صحيحة ✓" else "إجابة خاطئة — الصحيح: ${current.options[current.correctIndex]}",
                            fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = if (selectedOption == current.correctIndex) SuccessGreen else MaterialTheme.colorScheme.error
                        )
                        Text(current.explanation, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                Button(
                    onClick = {
                        if (index + 1 >= cases.size) finished = true
                        else { index++; selectedOption = null; submitted = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (index + 1 >= cases.size) "إنهاء الاختبار" else "السؤال التالي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// Specs whose result is closed-form arithmetic (verified against each spec's
// worked example). Point-based clinical scores are intentionally absent:
// their inputs are findings, not numbers, so no dead input fields are shown.
private val computableSpecIds = setOf(
    "egfr_ckd_epi_2021", "corrected_calcium", "anion_gap", "anion_gap_albumin_corrected",
    "serum_osmolality", "osmolar_gap", "winters_formula", "delta_ratio",
    "corrected_sodium_hyperglycemia", "fena", "feurea", "meld", "meld_na",
    "fib4", "apri", "qtc_bazett", "qtc_fridericia", "map", "bmi",
    "bsa_mosteller", "ibw_devine", "adjbw", "ca_phos_product", "free_water_deficit",
    "sodium_correction_limits", "adrogue_madias", "transferrin_saturation", "homa_ir",
    "ldl_friedewald", "pao2_fio2_ratio", "alveolar_gas_pao2", "aa_gradient",
    "oxygenation_index", "maintenance_fluids_421", "estimated_blood_volume",
    "shock_index", "reverse_shock_index"
)

// Saver for the per-spec input values: flattens the map to (key, value)
// pairs so rememberSaveable can persist it across category switches,
// scrolling (LazyColumn disposal) and configuration changes.
private val calcInputValuesSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { map -> map.entries.flatMap { (key, value) -> listOf(key, value) } },
    restore = { flat ->
        mutableStateMapOf<String, String>().apply {
            var index = 0
            while (index + 1 < flat.size) {
                put(flat[index], flat[index + 1])
                index += 2
            }
        }
    }
)

// Display label for an input: the sex input is keyed "female" and is always
// shown as «الجنس», whatever label the spec itself carries.
private fun calcInputDisplayLabel(input: CalcInputSpec): String =
    if (input.key == "female") "الجنس" else input.labelAr

// Bounds are printed via Double.toString: no thousands separators and the
// same rendering the spec header above uses for min/max.
private fun formatCalcBound(value: Double): String = value.toString()

// Dispatcher: the 19 point-based clinical scores go to the interactive
// score engine (scoreCompute.kt), the closed-form arithmetic specs keep
// the numeric block below, and anything else renders nothing here.
@Composable
private fun SpecComputeBlock(spec: CalcSpec) {
    when {
        spec.id in scoreSpecIds -> ScoreSpecBlock(spec)
        spec.id in computableSpecIds -> NumericSpecBlock(spec)
        else -> Unit
    }
}

// Counting units for a score definition: every plain item is one unit,
// while all items sharing an exclusiveGroup together form a single unit
// (answering any one of them answers the group).
private fun scoreRequiredUnits(items: List<ScoreItem>): Int {
    val groupCount = items.mapNotNull { it.exclusiveGroup }.distinct().size
    val plainCount = items.count { it.exclusiveGroup == null }
    return plainCount + groupCount
}

private fun scoreAnsweredUnits(items: List<ScoreItem>, answeredKeys: Set<String>): Int {
    val plainAnswered = items.count { it.exclusiveGroup == null && it.key in answeredKeys }
    val groupsAnswered = items.mapNotNull { it.exclusiveGroup }.distinct()
        .count { group -> items.any { it.exclusiveGroup == group && it.key in answeredKeys } }
    return plainAnswered + groupsAnswered
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreItemChips(
    item: ScoreItem,
    allItems: List<ScoreItem>,
    values: SnapshotStateMap<String, String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(item.labelAr, fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item.options.forEachIndexed { optionIndex, option ->
                val suffix = when {
                    option.points > 0 -> " (+${option.points})"
                    option.points < 0 -> " (${option.points})"
                    else -> ""
                }
                FilterChip(
                    selected = values[item.key] == optionIndex.toString(),
                    onClick = {
                        if (values[item.key] == optionIndex.toString()) {
                            // Tapping the chosen chip again clears the item,
                            // back to the honest "not assessed yet" state.
                            values.remove(item.key)
                        } else {
                            // Exclusive groups keep a single answer: picking
                            // an item clears every other item of its group.
                            val group = item.exclusiveGroup
                            if (group != null) {
                                allItems.forEach { other ->
                                    if (other.key != item.key && other.exclusiveGroup == group) {
                                        values.remove(other.key)
                                    }
                                }
                            }
                            values[item.key] = optionIndex.toString()
                        }
                    },
                    label = { Text(option.labelAr + suffix, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaroonPrimary, selectedLabelColor = Color.White)
                )
            }
        }
    }
}

@Composable
private fun ScoreSpecBlock(spec: CalcSpec) {
    val def = clinicalScoreDefinitions[spec.id] ?: return
    // Values are selected option indices as text ("2"); a missing key means
    // the item has not been assessed yet. The map starts completely empty —
    // no defaults — so an untouched score never presents a fake result.
    val values = rememberSaveable(inputs = arrayOf(spec.id), key = spec.id, saver = calcInputValuesSaver) {
        mutableStateMapOf<String, String>()
    }

    // Only selections that are valid indices of a known item count; a
    // corrupt restored value (non-numeric, unknown key, or out of range)
    // is dropped here exactly as the engine would reject it, so the
    // answered counter below never overstates completeness.
    val selections = values.entries.mapNotNull { (key, raw) ->
        val idx = raw.toIntOrNull() ?: return@mapNotNull null
        val item = def.items.firstOrNull { it.key == key } ?: return@mapNotNull null
        if (idx in item.options.indices) key to idx else null
    }.toMap()
    val outcome = evaluateScoreSpec(spec.id, selections)
    val required = scoreRequiredUnits(def.items)
    // Only valid numeric selections count as answered; a corrupt restored
    // value is ignored here exactly as it is ignored in selections above.
    val answered = scoreAnsweredUnits(def.items, selections.keys)
    val remaining = (required - answered).coerceAtLeast(0)
    val stages = def.stages.orEmpty()
    val stageLabels = def.stageLabels.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("قيّم البنود — اختر واحدًا لكل بند:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
        if (stages.isEmpty()) {
            def.items.forEach { item ->
                ScoreItemChips(item = item, allItems = def.items, values = values)
            }
        } else {
            stages.forEach { stage ->
                val stageItems = def.items.filter { it.stage == stage }
                if (stageItems.isNotEmpty()) {
                    Text(stageLabels[stage] ?: stage, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                    stageItems.forEach { item ->
                        ScoreItemChips(item = item, allItems = def.items, values = values)
                    }
                    if (scoreAnsweredUnits(stageItems, selections.keys) >= scoreRequiredUnits(stageItems)) {
                        val stageTotal = stageItems.sumOf { stageItem ->
                            selections[stageItem.key]?.let { idx -> stageItem.options.getOrNull(idx)?.points ?: 0 } ?: 0
                        }
                        Text(
                            "مجموع ${stageLabels[stage] ?: stage} حتى الآن: $stageTotal",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaroonPrimary
                        )
                    }
                }
            }
            // Items without a stage (or with an unlisted stage) still render,
            // after the staged groups, so no item can silently disappear.
            val stagedKeys = stages.toSet()
            def.items.filter { it.stage == null || it.stage !in stagedKeys }.forEach { item ->
                ScoreItemChips(item = item, allItems = def.items, values = values)
            }
        }

        // Live result: exactly one of the three surfaces is always visible —
        // nothing ever disappears silently. A Value (including the engine's
        // partial-stage Value for staged scores) goes on the result surface;
        // a Rejected whose cause is incompleteness — distinguished by the
        // answered-units comparison below, per the engine's completeness
        // rule (every plain item plus exactly one per exclusive group) —
        // goes on a neutral surface, never the red error surface; any other
        // Rejected goes on the error surface. The engine already appends
        // extraNote and any UN documentation to its Value text, so the UI
        // does not render them separately.
        if (outcome is CalcOutcome.Value) {
            Surface(color = MaroonPrimary.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("النتيجة: ${outcome.text}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
            }
        } else if (outcome is CalcOutcome.Rejected && answered < required) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "لم يكتمل التقييم بعد — أكمل كل البنود لعرض الدرجة (بقي $remaining من $required)",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (outcome.reason.contains("مرحلة لاحقة")) {
                        // The engine's stage-order rejection is specific and
                        // actionable; surface it instead of only the generic
                        // incompleteness text above.
                        Text(outcome.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    def.partialHint?.takeIf { it.isNotBlank() }?.let { hint ->
                        Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        } else if (outcome is CalcOutcome.Rejected) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("لا يمكن الحساب: ${outcome.reason}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun NumericSpecBlock(spec: CalcSpec) {
    if (spec.id !in computableSpecIds) return
    val values = rememberSaveable(inputs = arrayOf(spec.id), key = spec.id, saver = calcInputValuesSaver) {
        mutableStateMapOf<String, String>().apply {
            spec.inputs.forEach { input ->
                // Binary inputs start on their "0.0" option when the spec
                // gives no default, so the choice row and validation agree.
                val initial = input.default?.toString()
                    ?: if (input.key == "female" || input.key == "dialysis") "0.0" else ""
                put(input.key, initial)
            }
        }
    }

    // A restored map may miss keys (saved under a different spec shape):
    // fill any missing input with its spec default so no field or choice
    // row starts blank after a restore.
    LaunchedEffect(spec.id) {
        spec.inputs.forEach { input ->
            if (values[input.key] == null) {
                values[input.key] = input.default?.toString()
                    ?: if (input.key == "female" || input.key == "dialysis") "0.0" else ""
            }
        }
    }

    // Strict validation — the first failing input in spec order wins and the
    // compute layer is not called at all: blank, non-numeric and non-finite
    // values are rejected (a pasted "NaN"/"Infinity" parses as a Double, so
    // isFinite() is checked explicitly), and so is any value outside the
    // spec's min/max (a null bound means no bound on that side). No failure
    // is silent anymore: every one renders the error surface below.
    var inputError: String? = null
    val parsed = mutableMapOf<String, Double>()
    for (input in spec.inputs) {
        val label = calcInputDisplayLabel(input)
        val raw = values[input.key].orEmpty()
        if (raw.isBlank()) {
            inputError = "حقل «$label» فارغ — أدخل قيمة رقمية"
            break
        }
        val number = raw.toDoubleOrNull()
        if (number == null || !number.isFinite()) {
            inputError = "قيمة «$label» غير صالحة — أدخل رقمًا صحيحًا"
            break
        }
        if ((input.min != null && number < input.min) || (input.max != null && number > input.max)) {
            val range = when {
                input.min != null && input.max != null ->
                    "(${formatCalcBound(input.min)} – ${formatCalcBound(input.max)})"
                input.min != null -> "(الحد الأدنى ${formatCalcBound(input.min)})"
                else -> "(الحد الأقصى ${formatCalcBound(input.max!!)})"
            }
            inputError = "قيمة «$label» خارج المجال المسموح $range"
            break
        }
        parsed[input.key] = number
    }

    val outcome = if (inputError == null) evaluateCalcSpec(spec.id, parsed) else null
    val errorText = when {
        inputError != null -> inputError
        outcome is CalcOutcome.Rejected -> "لا يمكن الحساب: ${outcome.reason}"
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("جرّب بنفسك — أدخل القيم:", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
        spec.inputs.forEach { input ->
            if (input.key == "female" || input.key == "dialysis") {
                // Binary inputs render as a choice row, not a numeric field;
                // the value still lives in the same map as "0.0"/"1.0", so
                // parsing and the 0–1 range check above apply unchanged.
                val options: List<Pair<String, String>> = if (input.key == "female") {
                    listOf("ذكر" to "0.0", "أنثى" to "1.0")
                } else {
                    listOf("لا" to "0.0", "نعم" to "1.0")
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(calcInputDisplayLabel(input), fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (optionLabel, optionValue) ->
                            FilterChip(
                                selected = values[input.key] == optionValue,
                                onClick = { values[input.key] = optionValue },
                                label = { Text(optionLabel, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaroonPrimary, selectedLabelColor = Color.White)
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = values[input.key] ?: "",
                    onValueChange = { values[input.key] = it },
                    label = { Text("${input.labelAr} (${input.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }
        if (outcome is CalcOutcome.Value) {
            Surface(color = MaroonPrimary.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("النتيجة: ${outcome.text}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
            }
        }
        if (errorText != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(errorText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
