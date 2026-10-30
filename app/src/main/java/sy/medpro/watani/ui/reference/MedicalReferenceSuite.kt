package sy.medpro.watani.ui.reference

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.*

data class PearlItem(
    val title: String,
    val specialty: String = "الباطنة والجراحة",
    val classicPresentation: String,
    val diagnosisOrAction: String,
    val sourceRef: String = "Harrison's 21st Ed • Oxford Handbook"
)

data class ClinicalAlgorithmModel(
    val id: String,
    val title: String,
    val specialty: String,
    val guidelineSource: String,
    val initialAssessment: String,
    val steps: List<String>,
    val redFlags: List<String>,
    val ddxComparison: List<Triple<String, String, String>> // Condition, Key Hallmark, Definitive Test/Action
)

data class FirstLineDrugModel(
    val conditionAr: String,
    val specialty: String,
    val firstLineDrug: String,
    val mechanismOrDose: String,
    val alternativeOrAllergy: String,
    val pregnancyOrContraindication: String,
    val sourceRef: String
)

data class NormalLabReference(
    val category: String,
    val analyteName: String,
    val normalRange: String,
    val criticalClinicalNote: String
)

object AccreditedMedicalData {
    val goldenPearls: List<PearlItem> = listOf(
        PearlItem(
            title = "ثلاثية بيك (Beck's Triad) في السطام التاموري الحاد",
            specialty = "القلبية والإسعاف",
            classicPresentation = "هبوط ضغط شرياني + احتقان أوردة الوداجي (JVD) + خفوت أصوات القلب مع النبض العجائبي (انخفاض الانقباضي > 10 mmHg بالشهيق).",
            diagnosisOrAction = "التشخيص المؤكد بالإيكو القلبي السريري (انخماص الأذينة والبطين الأيمن بالانبساط) والتدبير الإسعافي هو بزل التامور الفوري (Pericardiocentesis).",
            sourceRef = "ATLS 10th Ed • Braunwald's Heart Disease"
        ),
        PearlItem(
            title = "خماسية رينولدز (Reynolds' Pentad) في التهاب الطرق الصفراوية الصاعد",
            specialty = "الجراحة والهضمية",
            classicPresentation = "ثلاثية شاركو (حمى وعرواءات + يرقان + ألم مراق أيمن) مضافاً إليها صدمة إنتانية وتغيم وعي.",
            diagnosisOrAction = "إنعاش بالسوائل الوريدية + صادات واسعة الطيف (Piperacillin-Tazobactam) + تصريف صفراوي إسعافي عبر ERCP.",
            sourceRef = "Sabiston Textbook of Surgery • Harrison's"
        ),
        PearlItem(
            title = "ثلاثية ويبل (Whipple's Triad) في الورم الجزيري المفرز للأنسولين (Insulinoma)",
            specialty = "الغدد الصم",
            classicPresentation = "أعراض نقص سكر الدم الودية والعصبية + غلوكوز المصل الموثق < 50 mg/dL + زوال الأعراض فوراً بعد إعطاء الغلوكوز.",
            diagnosisOrAction = "اختبار الصيام المراقب لمدة 72 ساعة مع معايرة الأنسولين والـ C-Peptide (كلاهما مرتفع في الورم الداخلي، بينما C-Peptide منخفض في الحقن الخارجي).",
            sourceRef = "Harrison's Principles of Internal Medicine"
        ),
        PearlItem(
            title = "التبول الدموي غير المؤلم عند مسن مدخن",
            specialty = "البولية والجراحة",
            classicPresentation = "بيلة دموية عيانية غير مؤلمة لدى مريض فوق 50 عاماً مع قصة تدخين أو تعرض للأصبغة العطرية.",
            diagnosisOrAction = "سرطان مثانة بولي (Urothelial Carcinoma) حتى يثبت العكس؛ الاستقصاء الذهبي هو تنظير المثانة مع الخزعة (Cystoscopy + Biopsy).",
            sourceRef = "Campbell-Walsh Urology • Oxford Handbook"
        ),
        PearlItem(
            title = "انفكاك المشيمة الباكر مقابل المشيمة المنزاحة في الثلث الثالث",
            specialty = "التوليد والنسائية",
            classicPresentation = "نزف تناسلي مؤلم داكن مع رحم متخشب (Woody uterus) وتألم جنيني = انفكاك مشيمة؛ نزف أحمر قانٍ غير مؤلم = مشيمة منزاحة.",
            diagnosisOrAction = "الفحص الإصبعي المهبلي مضاد استطباب مطلق قبل نفي المشيمة المنزاحة بالإيكو عبر المهبل!",
            sourceRef = "Williams Obstetrics 26th Ed"
        )
    )

    val algorithms: List<ClinicalAlgorithmModel> = listOf(
        ClinicalAlgorithmModel(
            id = "acute_chest_pain",
            title = "خوارزمية مقاربة ألم الصدر الحاد والمتلازمة الإكليلية (ACS)",
            specialty = "القلبية والإسعاف",
            guidelineSource = "ESC / AHA Acute Coronary Syndrome Guidelines",
            initialAssessment = "تسجيل تخطيط قلب كهربائي 12 مسرى خلال ≤ 10 دقائق من وصول المريض + تقييم العلامات الحيوية بجانبي الجسم.",
            steps = listOf(
                "1. إعطاء الأسبرين مضغاً (160–325 mg) فوراً ما لم يكن هناك شك بتسلخ الأبهر.",
                "2. إعطاء الأكسجين فقط إذا كان الإشباع SpO2 < 90% (تجنب فرط الأكسجة الروتيني).",
                "3. ارتفاع قطعة ST المستمر (STEMI) أو حصار غصن أيسر حديث ← فتح الشريان بالقثطرة الإسعافية (Primary PCI) خلال ≤ 90 دقيقة (أو حالة الخثرة خلال ≤ 30 دقيقة إذا تعذرت القثطرة خلال 120 دقيقة).",
                "4. غياب ارتفاع ST ← سحب تروبونين عالي الحساسية (hs-cTroponin) عند القبول وبعد ساعة-ساعتين لتصنيف NSTEMI مقابل خناق غير مستقر."
            ),
            redFlags = listOf(
                "ألم صدري طاعن ينتشر للظهر بين الكتفين مع فرق ضغط بين الذراعين > 20 mmHg ← نفي تسلخ الأبهر بالطبقي المحوري الوعائي قبل أي مميع!",
                "احتشاء سفلي (II, III, aVF) ← يمنع إعطاء النيتروجليسرين قبل إجراء مساري يمنى (V4R) لنفي احتشاء البطين الأيمن المعتمد على الحمل القبلي."
            ),
            ddxComparison = listOf(
                Triple("احتشاء العضلة القلبية (STEMI)", "ألم عاصر + ارتفاع ST مقعر/محدب مع تغيرات مرآتية", "Primary PCI + Dual Antiplatelet"),
                Triple("تسلخ الأبهر الصدري الحاد", "ألم ممزق بشدة قصوى منذ اللحظة الأولى + توسع منصف", "CT Angiography + Labetalol IV (HR < 60)"),
                Triple("الصمة الرئوية الحادة (PE)", "زلّة مفاجئة + تسرع قلب + S1Q3T3 ونقص أكسجة", "CTPA (أو D-dimer عند الاحتمال المنخفض)")
            )
        ),
        ClinicalAlgorithmModel(
            id = "upper_gi_bleed",
            title = "خوارزمية تدبير النزف الهضمي العلوي الحاد (UGIB)",
            specialty = "الهضمية والإسعاف",
            guidelineSource = "ACG / ESGE Upper GI Bleeding Guidelines",
            initialAssessment = "تقييم الحالة الهيموديناميكية أولاً (تسرع القلب وهبوط الضغط الانتصابي) وفتح خطين وريديين بقطر كبير (16–18G).",
            steps = listOf(
                "1. الإنعاش بالسوائل البلورية ونقل الكريات الحمر بطريقة متحفظة (الهدف Hb 7–9 g/dL، وعند مرضى الإكليلية Hb > 8–9 g/dL).",
                "2. البدء بتسريب مثبط مضخة البروتون وريدياً (Omeprazole / Pantoprazole 80 mg دفش ثم 8 mg/hr).",
                "3. عند مريض التشمع (شك بنزف دوالي المريء): إضافة مقبض وعائي حشوي فوراً (Octreotide أو Terlipressin) + صاد وقائي (Ceftriaxone 1g IV).",
                "4. إجراء التنظير الهضمي العلوي خلال ≤ 24 ساعة (وخلال ≤ 12 ساعة في نزف الدوالي) للإرقاء التنظيري."
            ),
            redFlags = listOf(
                "لا تنتظر انخفاض الهيموغلوبين في الساعات الأولى من النزف الحاد لأن المريض ينزف دماً كاملاً قبل حدوث التمدد الدموي!"
            ),
            ddxComparison = listOf(
                Triple("القرحة الهضمية النازفة", "السبب الأكثر شيوعاً (قصة NSAIDs أو H. pylori)", "إرقاء تنظيري (Clips + Epinephrine) + PPI"),
                Triple("دوالي المريء النازفة", "علامات تشمع كبد وارتفاع توتر وريد الباب", "Octreotide + Ceftriaxone + ربط تنظيري EVL"),
                Triple("متلازمة مالوري-فايس", "إقياءات متكررة شديدة تليها قيء دموي (تمزق مخاطي)", "تتوقف تلقائياً في 90% أو كلبس تنظيري")
            )
        ),
        ClinicalAlgorithmModel(
            id = "dka_management",
            title = "خوارزمية تدبير الحماض الكيتوني السكري (DKA Protocol)",
            specialty = "الغدد الصم والعناية",
            guidelineSource = "ADA Standards of Care in Diabetes",
            initialAssessment = "ثلاثية التشخيص: غلوكوز > 250 mg/dL + حماض استقلابي (pH < 7.30 و HCO3 < 18) + إيجابية الكيتون في الدم/البول.",
            steps = listOf(
                "1. السوائل الوريدية أولاً قبل الأنسولين: 1 ليتر محلول ملحي نظامي 0.9% NaCl خلال الساعة الأولى.",
                "2. فحص بوتاسيوم المصل (K+) قبل بدء الأنسولين: إذا كان K+ < 3.3 mEq/L يؤجل الأنسولين ويعوض البوتاسيوم أولاً لمنع توقف القلب.",
                "3. بدء تسريب الأنسولين النظامي الوريدي (0.1 U/kg/hr) بعد التأكد من أن البوتاسيوم ≥ 3.3 mEq/L.",
                "4. عندما يصل سكر الدم إلى 200 mg/dL: يُضاف محلول السكري 5% (D5W) إلى السيروم الملحي مع الاستمرار بتسريب الأنسولين بنصف السرعة حتى إغلاق الفجوة الصواعدية (Anion Gap) وزوال الحماض!"
            ),
            redFlags = listOf(
                "الخطأ القاتل في DKA هو إيقاف تسريب الأنسولين الوريدي بمجرد عودة سكر الدم للطبيعي قبل إغلاق الفجوة الصواعدية (Anion Gap ≤ 12)."
            ),
            ddxComparison = listOf(
                Triple("الحماض الكيتوني (DKA)", "النمط الأول غالباً • pH < 7.30 • كيتون إيجابي بشدة", "سوائل + بوتاسيوم + أنسولين حتى إغلاق الفجوة"),
                Triple("السبات فرط الأسمولية (HHS)", "النمط الثاني • غلوكوز > 600 • أسمولية > 320 • غياب الحماض", "تعويض سوائل غزير هو حجر الزاوية + أنسولين")
            )
        )
    )

    val firstLineDrugs: List<FirstLineDrugModel> = listOf(
        FirstLineDrugModel(
            conditionAr = "فرط التوتر الشرياني عند الحامل (Gestational / Chronic HTN in Pregnancy)",
            specialty = "التوليد والقلبية",
            firstLineDrug = "Labetalol أو Nifedipine مديد التحرر أو Methyldopa",
            mechanismOrDose = "ضبط الضغط تدريجياً لتجنب نقص التروية المشيمية.",
            alternativeOrAllergy = "Hydralazine وريدياً في النوبات الإسعافية الشديدة.",
            pregnancyOrContraindication = "يمنع منعاً باتاً استخدام ACEi و ARBs ومدرات الثيازيد ومثبطات الرينين أثناء الحمل (تسبب سوء تصنع كلوي وقلة سائل سلوي).",
            sourceRef = "Williams Obstetrics • ESC Guidelines"
        ),
        FirstLineDrugModel(
            conditionAr = "التهاب السحايا الجرثومي المكتسب بالمجتمع (البالغين 18–50 سنة)",
            specialty = "الإنتانية والعصبية",
            firstLineDrug = "Ceftriaxone (2g IV q12h) + Vancomycin + Dexamethasone",
            mechanismOrDose = "يُعطى الديكساميثازون قبل أو مع أول جرعة صاد لتقليل الصمم والعقابيل العصبية في سحايا المكورات الرئوية.",
            alternativeOrAllergy = "فوق عمر 50 سنة أو مضعفي المناعة والحوامل: يُضاف Ampicillin لتغطية الليستيريا المستوحدة (Listeria monocytogenes).",
            pregnancyOrContraindication = "لا تؤخر إعطاء الصادات الوريدية بانتظار التصوير الطبقي أو البزل القطني!",
            sourceRef = "IDSA Bacterial Meningitis Guidelines • Sanford Guide"
        ),
        FirstLineDrugModel(
            conditionAr = "الحالة الصرعية المستمرة (Status Epilepticus)",
            specialty = "العصبية والإسعاف",
            firstLineDrug = "Lorazepam IV (0.1 mg/kg) أو Midazolam IM",
            mechanismOrDose = "الخط الأول الفوري خلال أول 5 دقائق لإيقاف الاختلاج.",
            alternativeOrAllergy = "الخط الثاني (إذا استمرت): Levetiracetam IV أو Fosphenytoin أو Valproate.",
            pregnancyOrContraindication = "فحص غلوكوز الإصبع فوراً وإعطاء الثيامين قبل الغلوكوز عند الكحوليين وناقصي التغذية لمنع اعتلال دماغ فيرنيكه.",
            sourceRef = "American Epilepsy Society Guidelines • Harrison's"
        ),
        FirstLineDrugModel(
            conditionAr = "التهاب المثانة الحاد غير المختلط (Uncomplicated Cystitis)",
            specialty = "البولية والإنتانية",
            firstLineDrug = "Nitrofurantoin ( لمدة 5 أيام) أو Fosfomycin (جرعة وحيدة 3g)",
            mechanismOrDose = "تركيز بولي ممتاز مع مقاومة منخفضة.",
            alternativeOrAllergy = "TMP-SMX لمدة 3 أيام (إذا كانت المقاومة المحلية < 20%).",
            pregnancyOrContraindication = "Nitrofurantoin لا يُستخدم في التهاب الحويضة والكلية (لأنه لا يحقق تراكيز نسيجية كلوية كافية) ويُتجنب عند CrCl < 30.",
            sourceRef = "IDSA / EAU Urological Infections Guidelines"
        )
    )

    val normalLabs: List<NormalLabReference> = listOf(
        NormalLabReference("الدمويات (CBC)", "الهيموغلوبين (Hemoglobin)", "ذكر: 13.5–17.5 g/dL • أنثى: 12.0–15.5 g/dL", "نقل الدم المحافظ في المرضى المستقرين يُستطب عند Hb < 7 g/dL."),
        NormalLabReference("الدمويات (CBC)", "الحجم الكريوي الوسطي (MCV)", "80–100 fL", "< 80 صغير الكريات (نقص حديد، تالاسيميا) • > 100 كبير الكريات (عوز B12/فولات)."),
        NormalLabReference("الدمويات (CBC)", "الصفيحات الدموية (Platelets)", "150,000–450,000 /μL", "خطر النزف العفوي يرتفع عندما تنخفض الصفيحات دون 10,000–20,000 /μL."),
        NormalLabReference("الشوارد والكلية", "صوديوم المصل (Na+)", "135–145 mEq/L", "التصحيح الآمن لا يتجاوز 8–10 mEq/L في 24 ساعة."),
        NormalLabReference("الشوارد والكلية", "بوتاسيوم المصل (K+)", "3.5–5.0 mEq/L", "فرط البوتاسيوم مع تغيرات تخطيطية يستدعي غلوكونات الكالسيوم وريدياً أولاً لحماية عضلة القلب."),
        NormalLabReference("الشوارد والكلية", "الكالسيوم الكلي (Total Ca2+)", "8.5–10.2 mg/dL", "يُصحح بإضافة 0.8 mg/dL لكل انخفاض 1 g/dL في ألبومين المصل عن 4.0."),
        NormalLabReference("الشوارد والكلية", "الكرياتينين (Serum Creatinine)", "0.6–1.2 mg/dL", "ارتفاعه ≥ 0.3 mg/dL خلال 48 ساعة يشخص الأذية الكلوية الحادة (AKI)."),
        NormalLabReference("غازات الدم الشرياني (ABG)", "درجة الحموضة (pH)", "7.35–7.45", "< 7.35 حماض (Acidemia) • > 7.45 قلاء (Alkalemia)."),
        NormalLabReference("غازات الدم الشرياني (ABG)", "PaCO2 و HCO3-", "PaCO2: 35–45 mmHg • HCO3-: 22–26 mEq/L", "الفجوة الصواعدية الطبيعية (Na - (Cl + HCO3)) هي 8–12 mEq/L.")
    )
}

@Composable
fun ClinicalAlgorithmsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var selectedAlgorithm by remember { mutableStateOf(AccreditedMedicalData.algorithms.first()) }

    DrawerScreenScaffold(
        title = "خوارزميات التدبير السريري والتشخيص التفريقي",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccreditedMedicalData.algorithms.forEach { alg ->
                        val selected = selectedAlgorithm.id == alg.id
                        FilterChip(
                            selected = selected,
                            onClick = { selectedAlgorithm = alg },
                            label = { Text(alg.specialty, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaroonPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(selectedAlgorithm.guidelineSource, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(selectedAlgorithm.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("التقييم الأولي الفوري: ${selectedAlgorithm.initialAssessment}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            }

            items(selectedAlgorithm.steps) { step ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(step, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.09f)),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("الرايات الحمراء والتحذيرات السريرية الحاسمة (Red Flags):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ErrorRed)
                        Spacer(modifier = Modifier.height(6.dp))
                        selectedAlgorithm.redFlags.forEach { flag ->
                            Text("• $flag", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaroonDeep)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
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
                        Text("جدول التشخيص التفريقي السريري المعتمد:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        selectedAlgorithm.ddxComparison.forEach { (cond, hallmark, action) ->
                            Surface(
                                color = MaroonPrimary.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(cond, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                                    Text("العلامة المميزة: $hallmark", fontSize = 12.sp)
                                    Text("الاستقصاء/التدبير الحاسم: $action", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
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
fun FirstLineDrugsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    DrawerScreenScaffold(
        title = "دليل الخطوط العلاجية الأولى والأدوية الأساسية (DOC)",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(AccreditedMedicalData.firstLineDrugs) { drug ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(drug.conditionAr, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary, modifier = Modifier.weight(1f))
                            Surface(color = MaroonPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                Text(drug.specialty, color = MaroonPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("خط العلاج الأول (Drug of Choice):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                Text(drug.firstLineDrug, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(drug.mechanismOrDose, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• البديل / الحالات الخاصة: ${drug.alternativeOrAllergy}", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• تحذير ومضاد استطباب: ${drug.pregnancyOrContraindication}", fontSize = 12.sp, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("المرجع: ${drug.sourceRef}", fontSize = 11.sp, color = GoldAccentDark)
                    }
                }
            }
        }
    }
}

@Composable
fun GoldenPearlsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    DrawerScreenScaffold(
        title = "النقاط الذهبية والثلاثيات السريرية المعتمدة",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(AccreditedMedicalData.goldenPearls) { pearl ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(pearl.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary, modifier = Modifier.weight(1f))
                            Surface(color = GoldAccent.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text(pearl.specialty, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaroonDeep, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("التظاهر الكلاسيكي: ${pearl.classicPresentation}", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "الحسم والتدبير: ${pearl.diagnosisOrAction}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("المصدر: ${pearl.sourceRef}", fontSize = 11.sp, color = GoldAccentDark)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickMedicalReferenceModal(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, contentDescription = null, tint = MaroonPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("القيم المخبرية المرجعية المعتمدة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AccreditedMedicalData.normalLabs) { lab ->
                    Surface(
                        color = MaroonPrimary.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lab.analyteName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                                Text(lab.category, fontSize = 10.sp, color = GoldAccentDark, fontWeight = FontWeight.Bold)
                            }
                            Text("المجال الطبيعي: ${lab.normalRange}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(lab.criticalClinicalNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(shape = MaterialTheme.shapes.medium,
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("إغلاق")
            }
        }
    )
}
