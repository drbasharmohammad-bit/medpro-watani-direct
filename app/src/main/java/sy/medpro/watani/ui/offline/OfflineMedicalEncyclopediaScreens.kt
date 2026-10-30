package sy.medpro.watani.ui.offline

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.R
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.ui.theme.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

data class MedicalEponymEntry(
    val id: String,
    val arabicName: String,
    val englishName: String,
    val specialty: String,
    val clinicalDescription: String,
    val associatedCondition: String,
    val nationalExamKey: String,
    val reference: String
)

enum class EcgWavePattern {
    STEMI_ANTERIOR,
    STEMI_INFERIOR,
    AFIB,
    AFLUTTER_SAWTOOTH,
    VTACH_MONOMORPHIC,
    HYPERKALEMIA_PEAKED_T,
    PERICARDITIS_CONCAVE_ST,
    COMPLETE_HEART_BLOCK
}

data class EcgAtlasCase(
    val id: String,
    val title: String,
    val pattern: EcgWavePattern,
    val imageRes: Int,
    val heartRateAndRhythm: String,
    val culpritOrMechanism: String,
    val ecgCriteria: List<String>,
    val firstLineManagement: String,
    val examTrap: String,
    val reference: String,
    val attribution: String
)

data class StudyPhaseAllocation(
    val id: String,
    val specialtyTitle: String,
    val examWeightPercent: Int,
    val allocatedDays: Int,
    val totalHours: Int,
    val keyTopicsChecklist: String
)

private val offlineEponymsList = listOf(
    MedicalEponymEntry(
        id = "ep_courvoisier",
        arabicName = "قانون وعلامة كورفوازييه",
        englishName = "Courvoisier's Law",
        specialty = "الجراحة العامة",
        clinicalDescription = "مرارة مجسوسة متضخمة غير مؤلمة مترافقة مع يرقان انسدادي.",
        associatedCondition = "سرطان رأس البنكرياس أو أورام الطرق الصفراوية القاصية (وليس الحصيات الصفراوية لأن التهاب المرارة الحصوي المزمن يؤدي لتليف المرارة فلا تتمدد).",
        nationalExamKey = "يرقان انسدادي غير مؤلم + مرارة مجسوسة غير مؤلمة + نقص وزن = سرطان رأس البنكرياس حتى يثبت العكس.",
        reference = "Bailey & Love's Surgery"
    ),
    MedicalEponymEntry(
        id = "ep_rovsing",
        arabicName = "علامة روفزينغ وعلامة البسواس والسادة",
        englishName = "Rovsing, Psoas & Obturator Signs",
        specialty = "الجراحة العامة",
        clinicalDescription = "• روفزينغ: ألم في الحفرة الحرقفية اليمنى عند جس الحفرة الحرقفية اليسرى.\n• البسواس: ألم عند بسط الفخذ الأيمن (زائدة خلف الأعور).\n• السادة: ألم عند الدوران الداخلي للفخذ الأيمن المثني (زائدة حوضية).",
        associatedCondition = "التهاب الزائدة الدودية الحاد وتحديد توضعها التشريحي.",
        nationalExamKey = "إيجابية علامة البسواس تدل على زائدة خلف الأعور (Retrocecal)، بينما إيجابية السادة تدل على زائدة حوضية (Pelvic).",
        reference = "Sabiston Textbook of Surgery"
    ),
    MedicalEponymEntry(
        id = "ep_whipple",
        arabicName = "ثلاثية ويبل",
        englishName = "Whipple's Triad",
        specialty = "الباطنة والغدد",
        clinicalDescription = "1) أعراض نقص سكر الدم (تعرق، رجفان، تخليط) + 2) توثيق سكر دم منخفض (< 50 ملغ/دل) أثناء النوبة + 3) زوال الأعراض فوراً بعد إعطاء الغلوكوز.",
        associatedCondition = "الورم الجزيري المفرز للأنسولين (Insulinoma).",
        nationalExamKey = "للتفريق بين الورم المفرز للأنسولين وحقن الأنسولين الخارجي: الببتيد C (C-peptide) يكون مرتفعاً في الـ Insulinoma ومنخفضاً في الحقن الخارجي.",
        reference = "Harrison's Principles of Internal Medicine"
    ),
    MedicalEponymEntry(
        id = "ep_cushing_triad",
        arabicName = "ثلاثية كوشينغ العصبية",
        englishName = "Cushing's Triad",
        specialty = "العصبية والجراحة العصبية",
        clinicalDescription = "1) فرط التوتر الشرياني (اتساع ضغط النبض) + 2) بطء القلب الانعكاسي + 3) اضطراب نظم التنفس (Cheyne-Stokes).",
        associatedCondition = "ارتفاع التوتر داخل القحف الشديد (Increased ICP) والانفتاق الدماغي الوشيك.",
        nationalExamKey = "تعاكس تماماً علامات الصدمة النزفية (التي تتظاهر بهبوط ضغط وتسرع قلب). التدبير الإسعافي: رفع الرأس 30 درجة، فرط تهوية معتدل، ومانيتول أو ملحي مفرط التوتر.",
        reference = "ATLS & Greenberg Neurosurgery"
    ),
    MedicalEponymEntry(
        id = "ep_virchow_node",
        arabicName = "عقدة فيرخوف وعلامة تروازييه",
        englishName = "Virchow's Node & Troisier's Sign",
        specialty = "الباطنة الهضمية والجراحة",
        clinicalDescription = "ضخامة عقدة لمفاوية فوق الترقوة اليسرى قاسية غير مؤلمة، وعقدة الأخت ماري جوزيف (Sister Mary Joseph) حول السرة.",
        associatedCondition = "سرطان المعدة المتقدم المنتقل عبر القناة الصدرية (أو أورام البطن والحوض).",
        nationalExamKey = "تضخم العقدة فوق الترقوة اليسرى لدى مريض يعاني من عسر هضم ونقص وزن وفقر دم بعوز الحديد يوجه لسرطان المعدة.",
        reference = "Oxford Handbook of Clinical Medicine"
    ),
    MedicalEponymEntry(
        id = "ep_horner",
        arabicName = "متلازمة هورنر",
        englishName = "Horner's Syndrome",
        specialty = "الباطنة الصدرية والعصبية",
        clinicalDescription = "الثلاثية الكلاسيكية: 1) تقبض الحدقة (Miosis) + 2) إطراق الجفن العلوي (Ptosis) + 3) غياب التعرق في نصف الوجه الموافق (Anhidrosis) مع خوص ظاهري.",
        associatedCondition = "ورم قمة الرئة (ورم بانكوست Pancoast Tumor) لغزوه السلسلة الودية الرقبية، أو تسلخ الشريان السباتي، أو متلازمة فالنبرغ.",
        nationalExamKey = "مدخن لديه ألم كتف ينتشر للجانب الإنسي للذراع (C8-T1) مع ثلاثية هورنر = ورم بانكوست في قمة الرئة.",
        reference = "Harrison's Internal Medicine"
    ),
    MedicalEponymEntry(
        id = "ep_trousseau_chvostek",
        arabicName = "علامتا تروسو وشفوستيك",
        englishName = "Trousseau & Chvostek Signs",
        specialty = "الباطنة والجراحة",
        clinicalDescription = "• تروسو: تشنج رسغي مشطي (يد المولدة) عند نفخ كم جهاز الضغط فوق الضغط الانقباضي لمدة 3 دقائق.\n• شفوستيك: تقلص عضلات الوجه المماثلة عند القرع على العصب الوجهي أمام الأذن.",
        associatedCondition = "نقص كالسيوم الدم الحاد (Hypocalcemia) — أشيع أسبابه الجراحية أذية أو استئصال جارات الدرق بالخطأ أثناء استئصال الدرق التام.",
        nationalExamKey = "علامة تروسو أكثر نوعية من علامة شفوستيك لنقص الكالسيوم. على تخطيط القلب يسبب نقص الكالسيوم تطاول القطعة QT.",
        reference = "Sabiston & Harrison's"
    ),
    MedicalEponymEntry(
        id = "ep_kawasaki",
        arabicName = "داء كاواساكي (المتلازمة الجلدية المخاطية العقدية)",
        englishName = "Kawasaki Disease (CRASH & Burn)",
        specialty = "طب الأطفال",
        clinicalDescription = "حمى مستمرة ≥ 5 أيام + 4 من 5 معايير: التهاب ملتحمة ثنائي الجانب غير قيحي، تغيرات فموية (لسان الفريز وتشقق الشفاه)، طفح متعدد الأشكال، تغيرات بالأطراف (وذمة وتوسف)، وضخامة عقدة رقبية.",
        associatedCondition = "التهاب أوعية متوسطة الحجم عند الأطفال يصيب الشرايين الإكليلية.",
        nationalExamKey = "الاختلاط الأخطر هو أمهات دم الشرايين الإكليلية (تُكشف بالإيكو القلبي). العلاج النوعي الفوري: الغلوبولين المناعي الوريدي (IVIG) + الأسبرين (الحالة الاستثنائية لإعطاء الأسبرين عند الأطفال).",
        reference = "Nelson Textbook of Pediatrics"
    ),
    MedicalEponymEntry(
        id = "ep_plummer_vinson",
        arabicName = "متلازمة بلومر-فينسون (باترسون-كيلي)",
        englishName = "Plummer-Vinson Syndrome",
        specialty = "الباطنة الهضمية والدمويات",
        clinicalDescription = "الثلاثية: 1) فقر دم بعوز الحديد + 2) عسر بلع للجوامل بسبب وترات المريء العلوية (Esophageal Webs) + 3) التهاب لسان ضموري وتقعر الأظافر (Koilonychia).",
        associatedCondition = "عامل خطورة مؤهب لسرطان المريء حرشفي الخلايا (Squamous Cell Carcinoma) في القسم العلوي/المتوسط للمريء.",
        nationalExamKey = "تعالج الوترات وعسر البلع غالباً بتعويض الحديد وتوسيع المريء بالتنظير عند الحاجة مع المراقبة لنفي التسرطن.",
        reference = "Harrison's Internal Medicine"
    )
)

private val offlineEcgCases = listOf(
    EcgAtlasCase(
        id = "ecg_stemi_ant",
        title = "احتشاء العضلة القلبية الأمامي الواسع (Anterior STEMI)",
        pattern = EcgWavePattern.STEMI_ANTERIOR,
        imageRes = R.drawable.ecg_stemi_ant,
        heartRateAndRhythm = "نظم جيبي منتظم • 88 نبضة/دقيقة",
        culpritOrMechanism = "انسداد الشريان الأمامي بين البطينين النازل (LAD - Left Anterior Descending)",
        ecgCriteria = listOf(
            "ارتفاع القطعة ST المحدب في المساري الصدرية الأمامية (V1 إلى V4 أو V1 إلى V6).",
            "تغيرات مرآوية (تزحل ST للأسفل) في المساري السفلية (II, III, aVF).",
            "ظهور موجات Q المرضية لاحقاً في المساري الأمامية."
        ),
        firstLineManagement = "تفعيل بروتوكول القثطرة القلبية البدئية الفورية (Primary PCI خلال ≤ 90 دقيقة) + أسبرين مضغاً + مثبط P2Y12 + هيبارين.",
        examTrap = "الاحتشاء الأمامي الواسع يترافق مع صدمة قلبية المنشأ وحصار فرع الحزمة الأيسر الحديث (New LBBB يعامل معاملة STEMI).",
        reference = "ESC / AHA STEMI Guidelines",
        attribution = "المصدر: Cureus 2018 — CC BY",
    ),
    EcgAtlasCase(
        id = "ecg_stemi_inf",
        title = "احتشاء العضلة القلبية السفلي (Inferior STEMI)",
        pattern = EcgWavePattern.STEMI_INFERIOR,
        imageRes = R.drawable.ecg_stemi_inf,
        heartRateAndRhythm = "بطء جيبي أو حصار أذيني بطيني • 56 نبضة/دقيقة",
        culpritOrMechanism = "انسداد الشريان الإكليلي الأيمن (RCA في 80% من الحالات) المغذي للعقدة AV والجدار السفلي",
        ecgCriteria = listOf(
            "ارتفاع القطعة ST في المساري السفلية (II, III, aVF).",
            "تزحل ST المرآوي للأسفل في المساري الجانبية (I, aVL).",
            "يجب طلب المساري اليمنى (V4R) لنفي إشراك البطين الأيمن."
        ),
        firstLineManagement = "إعادة التروية الفورية + سوائل وريدية في حال إشراك البطين الأيمن.",
        examTrap = "يُمنع إعطاء النترات (Nitroglycerin) والمورفين والمدرات عند ترافق الاحتشاء السفلي باحتشاء البطين الأيمن لتجنب الهبوط الصاعق للضغط.",
        reference = "Braunwald's Heart Disease",
        attribution = "المصدر: Patho — CC BY-SA 3.0",
    ),
    EcgAtlasCase(
        id = "ecg_afib",
        title = "الرجفان الأذيني (Atrial Fibrillation - AFib)",
        pattern = EcgWavePattern.AFIB,
        imageRes = R.drawable.ecg_afib,
        heartRateAndRhythm = "لانظمي بشكل غير منتظم إطلاقاً (Irregularly Irregular) • 115 نبضة/دقيقة",
        culpritOrMechanism = "دارات عودة دخول متعددة وعشوائية في الأذينتين (غالباً من فوهات الأوردة الرئوية)",
        ecgCriteria = listOf(
            "غياب تام لموجات P الواضحة واستبدالها بتموجات رجفانية دقيقة للخط الأساسي.",
            "المسافات R-R غير متساوية إطلاقاً وبلا أي نمط متكرر.",
            "مركبات QRS ضيقة (إلا بوجود حصار غصن مرافق أو ممر إضافي WPW)."
        ),
        firstLineManagement = "إذا كان غير مستقر هيموديناميكياً -> صدمة كهربائية متزامنة فورية. إذا كان مستقراً -> ضبط السرعة بحاصرات بيتا أو حاصرات الكالسيوم اللاثنائية الهيدروبيريدين (Diltiazem/Verapamil) + تقييم التميع بحرز CHA2DS2-VASc.",
        examTrap = "الرجفان الأذيني الصمامي (تضيق التاجي المتوسط/الشديد أو صمام صنعي ميكانيكي) يُميّع حصراً بالوارفارين وليس بمضادات التخثر الفموية المباشرة (DOACs).",
        reference = "ESC Atrial Fibrillation Guidelines",
        attribution = "المصدر: R.W. Koster (ECGPedia) — CC BY-SA 3.0",
    ),
    EcgAtlasCase(
        id = "ecg_flutter",
        title = "الرفرفة الأذينية (Atrial Flutter - Sawtooth Pattern)",
        pattern = EcgWavePattern.AFLUTTER_SAWTOOTH,
        imageRes = R.drawable.ecg_flutter,
        heartRateAndRhythm = "نظم منتظم غالباً بسرعة بطينية ~150 نبضة/دقيقة (نقل 2:1)",
        culpritOrMechanism = "دارة عودة دخول كبيرة (Macro-reentry) في الأذينة اليمنى عبر البرزخ الأجوفي مثلث الشرف (Cavotricuspid Isthmus)",
        ecgCriteria = listOf(
            "موجات رفرفة F نموذجية بشكل «أسنان المنشار» (Sawtooth Waves) بسرعة أذينية 300/دقيقة أوضح ما تكون في المساري II, III, aVF.",
            "عند وجود تسرع قلب منتظم عريض أو ضيق بسرعة 150 نبضة/دقيقة بالضبط، فكّر فوراً بالرفرفة الأذينية مع حصار 2:1."
        ),
        firstLineManagement = "العلاج الشافي النهائي والأكثر فعالية هو الكي بالقثطرة للبرزخ الأجوفي مثلث الشرف (Catheter Ablation).",
        examTrap = "قواعد منع التخثر والوقاية من الصمات الدماغية في الرفرفة الأذينية مطابقة تماماً لقواعد الرجفان الأذيني.",
        reference = "AHA / ESC Supraventricular Tachycardia Guidelines",
        attribution = "المصدر: James Heilman, MD — CC BY-SA 4.0",
    ),
    EcgAtlasCase(
        id = "ecg_vtach",
        title = "تسرع القلب البطيني وحيد الشكل (Monomorphic Ventricular Tachycardia)",
        pattern = EcgWavePattern.VTACH_MONOMORPHIC,
        imageRes = R.drawable.ecg_vtach,
        heartRateAndRhythm = "تسرع منتظم عريض المركب • 170 نبضة/دقيقة",
        culpritOrMechanism = "دارة عودة دخول حول ندبة احتشاء عضلي قلبي سابق في البطين",
        ecgCriteria = listOf(
            "مركبات QRS عريضة (> 120 ميلي ثانية) ومتشابهة الشكل ومتتالية بسرعة > 100/د.",
            "افتراق أذيني بطيني (AV Dissociation) + ضربات التحام (Fusion Beats) وضربات اقتناص (Capture Beats) وهي علامات واسمة لـ VT."
        ),
        firstLineManagement = "• نبض غائب (Pulseless VT): صدمة غير متزامنة (Defibrillation) + إنعاش قلبي رئوي.\n• نبض موجود وغير مستقر: صدمة كهربائية متزامنة.\n• نبض موجود ومستقر: Amiodarone أو Procainamide وريدياً.",
        examTrap = "أي تسرع قلب عريض المركب لدى مريض لديه قصة احتشاء عضلة قلبية سابق يُعامل على أنه تسرع بطيني (VT) حتى يثبت العكس، ويُمنع إعطاء الفيراباميل.",
        reference = "AHA ACLS Guidelines",
        attribution = "المصدر: Glenlarson — ملكية عامة",
    ),
    EcgAtlasCase(
        id = "ecg_hyperk",
        title = "فرط بوتاسيوم الدم الحرج (Severe Hyperkalemia ECG)",
        pattern = EcgWavePattern.HYPERKALEMIA_PEAKED_T,
        imageRes = R.drawable.ecg_hyperk,
        heartRateAndRhythm = "بطء توصيل متصاعد • 62 نبضة/دقيقة",
        culpritOrMechanism = "ارتفاع البوتاسيوم المصلي (> 6.5 mEq/L) بسبب قصور كلوي أو انحلال خلوي أو أدوية",
        ecgCriteria = listOf(
            "المرحلة المبكرة: موجات T مؤنفة طويلة كالخيمة (Tall Peaked T Waves) متناظرة القاعدة.",
            "المرحلة المتوسطة: تطاول PR، تسطح واختفاء موجة P، وتعريض مركب QRS.",
            "المرحلة النهائية قبل الرجفان البطيني: موجة الجيب (Sine Wave Pattern)."
        ),
        firstLineManagement = "الخطوة الأولى الفورية لحماية غشاء الخلية القلبية هي: غلوكونات الكالسيوم الوريدية (IV Calcium Gluconate)، يتبعها الأنسولين مع الغلوكوز لإدخال البوتاسيوم للخلايا.",
        examTrap = "غلوكونات الكالسيوم لا تخفض رقم البوتاسيوم في الدم بل تثبت غشاء العضلة القلبية فوراً ريثما تعمل الأدوية الخافضة للبوتاسيوم.",
        reference = "Harrison's Internal Medicine",
        attribution = "المصدر: Agbayani وGonzales — CC BY 4.0",
    ),
    EcgAtlasCase(
        id = "ecg_pericarditis",
        title = "التهاب التامور الحاد (Acute Pericarditis)",
        pattern = EcgWavePattern.PERICARDITIS_CONCAVE_ST,
        imageRes = R.drawable.ecg_pericarditis,
        heartRateAndRhythm = "تسرع جيبي • 96 نبضة/دقيقة",
        culpritOrMechanism = "التهاب وريقات التامور (غالباً فيروسي كوكساكي أو مجهول السبب أو بعد احتشاء متلازمة دريسلر)",
        ecgCriteria = listOf(
            "ارتفاع القطعة ST مقعر نحو الأعلى (Saddle-shaped / Concave) معمم في جميع المساري عدا aVR و V1.",
            "تزحل القطعة PR نحو الأسفل (PR Segment Depression) وهي العلامة الأكثر نوعية.",
            "غياب التغيرات المرآوية (عكس الـ STEMI)."
        ),
        firstLineManagement = "مضادات الالتهاب غير الستيروئيدية (NSAIDs أو الأسبرين في التهاب التامور تلو الاحتشاء) + الكولشيسين (Colchicine) لمنع النكس.",
        examTrap = "تجنب الستيروئيدات الجهازية كخط أول لأنها تزيد معدل نكس التهاب التامور عند سحبها.",
        reference = "ESC Pericardial Diseases Guidelines",
        attribution = "المصدر: James Heilman, MD — CC BY-SA 3.0",
    ),
    EcgAtlasCase(
        id = "ecg_chb",
        title = "حصار القلب التام من الدرجة الثالثة (3rd-Degree Complete Heart Block)",
        pattern = EcgWavePattern.COMPLETE_HEART_BLOCK,
        imageRes = R.drawable.ecg_chb,
        heartRateAndRhythm = "بطء بطيني شديد منتظم • 35 نبضة/دقيقة مع افتراق أذيني بطيني تام",
        culpritOrMechanism = "انقطاع كامل للنقل الكهربائي بين الأذينتين والبطينين",
        ecgCriteria = listOf(
            "موجات P منتظمة بسرعتها الخاصة (~75-85/د) ومركبات QRS منتظمة بسرعتها الهروبية البطيئة (30-45/د).",
            "لا توجد أي علاقة ثابتة بين موجات P ومركبات QRS (مسافات PR متغيرة عشوائياً تماماً)."
        ),
        firstLineManagement = "تنظيم الخطى عبر الجلد مؤقتاً (Transcutaneous Pacing) ثم زرع ناظم خطى دائم (Permanent Pacemaker - PPM).",
        examTrap = "في حصار الدرجة الثانية Mobitz II وحصار الدرجة الثالثة التام يستطب زرع ناظم الخطى الدائم حتى لو كان المريض لا عرضياً.",
        reference = "ESC Cardiac Pacing Guidelines",
        attribution = "المصدر: James Heilman, MD — CC BY-SA 3.0",
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EponymsAtlasScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
    }
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSpecialty by remember { mutableStateOf("الكل") }
    val specialties = listOf("الكل", "الجراحة العامة", "الباطنة الهضمية والجراحة", "الباطنة والغدد", "العصبية والجراحة العصبية", "الباطنة الصدرية والعصبية", "طب الأطفال")

    val filtered = remember(searchQuery, selectedSpecialty) {
        offlineEponymsList.filter { item ->
            val matchSpec = selectedSpecialty == "الكل" || item.specialty.contains(selectedSpecialty)
            val matchSearch = searchQuery.isBlank() ||
                item.arabicName.contains(searchQuery, ignoreCase = true) ||
                item.englishName.contains(searchQuery, ignoreCase = true) ||
                item.associatedCondition.contains(searchQuery, ignoreCase = true) ||
                item.clinicalDescription.contains(searchQuery, ignoreCase = true)
            matchSpec && matchSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("موسوعة المتلازمات والعلامات المسماة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("تعمل أوفلاين 100% • Eponyms & Named Signs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaroonPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث عن علامة أو متلازمة (مثال: كورفوازييه، ويبل، هورنر)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaroonPrimary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(specialties) { spec ->
                        FilterChip(
                            selected = selectedSpecialty == spec,
                            onClick = { selectedSpecialty = spec },
                            label = { Text(spec, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaroonPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    val isMastered = stats.masteredEponymIds.contains(item.id)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (isMastered) SuccessGreen else MaroonPrimary.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaroonPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = item.specialty,
                                        color = MaroonPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = item.englishName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldAccentDark
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(item.arabicName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(item.clinicalDescription, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("المرض أو الآلية الموافقة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaroonDeep)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(item.associatedCondition, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = GoldAccent.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccentDark, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(item.nationalExamKey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaroonDeep)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("المرجع: ${item.reference}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                FilterChip(
                                    selected = isMastered,
                                    onClick = { OfflineStudyVault.toggleEponymMastered(context, item.id) },
                                    label = { Text(if (isMastered) "محفوظ بذاكرتي ✓" else "تحديد كـ متقن", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SuccessGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineEcgAtlasScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedCase by remember { mutableStateOf(offlineEcgCases.first()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("أطلس تخطيط القلب التفاعلي (ECG)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("تخطيطات حقيقية أوفلاين 100% للحالات الامتحانية الحرجة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaroonPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text("اختر التخطيط الامتحاني للمعاينة:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(offlineEcgCases, key = { it.id }) { ecgCase ->
                        val isSelected = selectedCase.id == ecgCase.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCase = ecgCase },
                            label = { Text(ecgCase.title.substringBefore("(").trim(), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F6)),
                    border = BorderStroke(1.5.dp, MaroonPrimary.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(selectedCase.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonDeep)
                            }
                            Text("25 mm/s • 10 mm/mV", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Image(
                            painter = painterResource(id = selectedCase.imageRes),
                            contentDescription = selectedCase.title,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(selectedCase.heartRateAndRhythm, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaroonPrimary)
                        Text(selectedCase.attribution, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("التحليل التخطيطي والتدبير السريري المعتمد", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)

                        Surface(
                            color = MaroonPrimary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("الشريان المصاب / الآلية الإمراضية:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaroonPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(selectedCase.culpritOrMechanism, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Text("المعايير التشخيصية على ورقة التخطيط:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonDeep)
                        selectedCase.ecgCriteria.forEach { crit ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("• ", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                                Text(crit, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }

                        Surface(
                            color = SuccessGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("خط التدبير الإسعافي الأول المعتمد:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SuccessGreen)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(selectedCase.firstLineManagement, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Surface(
                            color = WarningAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فخ الامتحان الوطني: ${selectedCase.examTrap}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaroonDeep)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("جدول توضع الاحتشاء السريع والشريان الموافق (STEMI Localization)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        val rows = listOf(
                            Triple("V1 – V2", "حاجزي (Septal)", "الفرع الحاجزي من LAD"),
                            Triple("V3 – V4", "أمامي (Anterior)", "الشريان الأمامي النازل LAD"),
                            Triple("I, aVL, V5 – V6", "جانبي (Lateral)", "الشريان المنعكس LCx"),
                            Triple("II, III, aVF", "سفلي (Inferior)", "الشريان الإكليلي الأيمن RCA"),
                            Triple("تزحل ST بـ V1-V3 مع R طويلة", "خلفي (Posterior)", "الشريان الإكليلي الأيمن RCA / LCx")
                        )
                        rows.forEachIndexed { idx, (leads, wall, artery) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent)
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(leads, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaroonPrimary, modifier = Modifier.weight(1f))
                                Text(wall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(artery, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaroonDeep, modifier = Modifier.weight(1.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartStudyPlannerScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
    }
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    val allocations = remember(stats.examDaysRemaining, stats.dailyStudyHours) {
        val d = stats.examDaysRemaining
        val h = stats.dailyStudyHours
        val internalDays = (d * 0.30f).toInt().coerceAtLeast(2)
        val surgeryDays = (d * 0.25f).toInt().coerceAtLeast(2)
        val pedsDays = (d * 0.15f).toInt().coerceAtLeast(1)
        val obgynDays = (d * 0.15f).toInt().coerceAtLeast(1)
        val minorDays = (d * 0.05f).toInt().coerceAtLeast(1)
        val revDays = (d - (internalDays + surgeryDays + pedsDays + obgynDays + minorDays)).coerceAtLeast(1)

        listOf(
            StudyPhaseAllocation(
                id = "phase_internal",
                specialtyTitle = "المرحلة 1: الأمراض الباطنية (القلبية، الصدرية، الهضمية، الغدد، الكلية، العصبية)",
                examWeightPercent = 30,
                allocatedDays = internalDays,
                totalHours = internalDays * h,
                keyTopicsChecklist = "التركيز على ACS، قصور القلب، الربو و COPD، تشمع الكبد، DKA، والتهاب السحايا + حل دورات الباطنة."
            ),
            StudyPhaseAllocation(
                id = "phase_surgery",
                specialtyTitle = "المرحلة 2: الجراحة العامة والرضوض والجراحات التخصصية",
                examWeightPercent = 25,
                allocatedDays = surgeryDays,
                totalHours = surgeryDays * h,
                keyTopicsChecklist = "بروتوكول ATLS، البطن الحاد، انسداد الأمعاء، أورام الكولون والثدي والدرق، والطرق الصفراوية."
            ),
            StudyPhaseAllocation(
                id = "phase_pediatrics",
                specialtyTitle = "المرحلة 3: طب الأطفال والوليد",
                examWeightPercent = 15,
                allocatedDays = pedsDays,
                totalHours = pedsDays * h,
                keyTopicsChecklist = "اليرقان الوليدي، تضيق البواب، الإنتانات التنفسية، المعالم التطورية، اللقاحات، وداء كاواساكي."
            ),
            StudyPhaseAllocation(
                id = "phase_obgyn",
                specialtyTitle = "المرحلة 4: النسائية والتوليد",
                examWeightPercent = 15,
                allocatedDays = obgynDays,
                totalHours = obgynDays * h,
                keyTopicsChecklist = "نزوف الثلث الأول والثالث، الانسمام الحملي و HELLP، السكري الحملي، وأورام عنق وبطانة الرحم."
            ),
            StudyPhaseAllocation(
                id = "phase_minors",
                specialtyTitle = "المرحلة 5: الجلدية، العينية، والأذنية (ENT)",
                examWeightPercent = 5,
                allocatedDays = minorDays,
                totalHours = minorDays * h,
                keyTopicsChecklist = "العين الحمراء الحادة والزرق، التهاب الأذن الوسطى والدوار، والاندفاعات الجلدية الفقاعية والحمامية."
            ),
            StudyPhaseAllocation(
                id = "phase_revision",
                specialtyTitle = "المرحلة 6: المراجعة الشاملة وحل الامتحانات التجريبية والأخطاء المحفوظة",
                examWeightPercent = 10,
                allocatedDays = revDays,
                totalHours = revDays * h,
                keyTopicsChecklist = "مراجعة النقاط الذهبية، سجل الأخطاء المحفوظة أوفلاين، وإجراء محاكاة امتحان كاملة يومياً."
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مخطط جدول الدراسة الذكي للوطني", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("يعمل أوفلاين 100% • توزيع معياري حسب ثقل المواد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaroonPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        Text("ضبط الأيام المتبقية وساعات الدراسة اليومية:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("الأيام المتبقية للامتحان الوطني: ${stats.examDaysRemaining} يوماً", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(15, 30, 45, 60, 75, 90)) { days ->
                                FilterChip(
                                    selected = stats.examDaysRemaining == days,
                                    onClick = { OfflineStudyVault.updateStudyPlannerConfig(context, days, stats.dailyStudyHours) },
                                    label = { Text("$days يوماً", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaroonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("ساعات الدراسة الصافية يومياً: ${stats.dailyStudyHours} ساعات", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(4, 6, 8, 10, 12)) { hrs ->
                                FilterChip(
                                    selected = stats.dailyStudyHours == hrs,
                                    onClick = { OfflineStudyVault.updateDailyHours(context, hrs) },
                                    label = { Text("$hrs ساعات/يوم", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaroonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        Spacer(modifier = Modifier.height(10.dp))
                        // Exam date: the student's own date wins, else the
                        // admin panel's date, else manual days. The old
                        // frozen "60" never counted down, so it was wrong
                        // the moment time passed — a date counts down daily.
                        var showExamDatePicker by remember { mutableStateOf(false) }
                        val examDateMillis = if (stats.examModeManual) null else (stats.examDateMillis ?: stats.adminExamDateMillis)
                        Text("تاريخ الامتحان الوطني:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (examDateMillis != null) {
                            val examDateText = try {
                                val zone = java.time.ZoneId.systemDefault()
                                val d = java.time.Instant.ofEpochMilli(examDateMillis).atZone(zone).toLocalDate()
                                "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
                            } catch (_: Exception) { "" }
                            Text(
                                "الامتحان: $examDateText — باقي ${stats.examDaysRemaining} يوماً (${if (stats.examDateMillis != null) "اخترته أنت" else "من لوحة الإدارة"})",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "العداد يدوي حاليًا: ${stats.examDaysRemaining} يوماً — حدّد تاريخ الامتحان ليحسب العداد تلقائيًا كل يوم.",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showExamDatePicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("تحديد تاريخ الامتحان", fontSize = 13.sp)
                            }
                            if (stats.examDateMillis != null) {
                                OutlinedButton(
                                    onClick = { OfflineStudyVault.setExamDate(context, null) },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("إلغاء تاريخي", fontSize = 13.sp)
                                }
                            }
                        }
                        if (showExamDatePicker) {
                            val pickerState = rememberDatePickerState(
                                initialSelectedDateMillis = examDateMillis?.let {
                                    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                                        .atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
                                }
                            )
                            DatePickerDialog(
                                onDismissRequest = { showExamDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        pickerState.selectedDateMillis?.let { utcMillis ->
                                            val zone = java.time.ZoneId.systemDefault()
                                            val localDate = java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                                            OfflineStudyVault.setExamDate(context, localDate.atStartOfDay(zone).toInstant().toEpochMilli())
                                        }
                                        showExamDatePicker = false
                                    }) { Text("تأكيد") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showExamDatePicker = false }) { Text("إلغاء") }
                                }
                            ) {
                                DatePicker(state = pickerState, showModeToggle = false)
                            }
                        }
                        }
                    }
                }
            }

            item {
                val completedCount = stats.completedPlanPhaseIds.size
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إجمالي الساعات التخصصية المخططة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "${stats.examDaysRemaining * stats.dailyStudyHours} ساعة دراسية صافية • أُنجزت $completedCount من 6 مراحل",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                        Surface(color = GoldAccent, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "${(completedCount * 100) / 6}%",
                                color = MaroonDeep,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            items(allocations, key = { it.id }) { phase ->
                val isDone = stats.completedPlanPhaseIds.contains(phase.id)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isDone) SuccessGreen else MaroonPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaroonPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "الثقل الامتحاني: ${phase.examWeightPercent}%",
                                    color = MaroonPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = GoldAccent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${phase.allocatedDays} أيام (${phase.totalHours} ساعة)",
                                    color = MaroonDeep,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(phase.specialtyTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(phase.keyTopicsChecklist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            FilterChip(
                                selected = isDone,
                                onClick = { OfflineStudyVault.togglePlanPhaseCompleted(context, phase.id) },
                                label = { Text(if (isDone) "تم إنجاز المرحلة ✓" else "تأشير كمكتملة", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SuccessGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineClinicalCalculatorsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedTab by remember { mutableStateOf(0) }

    // CURB-65 states
    var cConfusion by remember { mutableStateOf(false) }
    var cUrea by remember { mutableStateOf(false) }
    var cResp by remember { mutableStateOf(false) }
    var cBp by remember { mutableStateOf(false) }
    var cAge65 by remember { mutableStateOf(false) }
    val curbScore = listOf(cConfusion, cUrea, cResp, cBp, cAge65).count { it }

    // ABG states
    var phInput by remember { mutableStateOf("7.28") }
    var pco2Input by remember { mutableStateOf("26") }
    var hco3Input by remember { mutableStateOf("12") }
    var naInput by remember { mutableStateOf("138") }
    var clInput by remember { mutableStateOf("100") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("الحاسبات والمعايير السريرية الأوفلاين", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("CURB-65 • مفسر غازات الدم الشرياني ABG والفجوة الأنيونية", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaroonPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("معيار CURB-65 لذات الرئة", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("مفسر غازات الدم ABG", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("مجموع حرز CURB-65: $curbScore من 5", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                val recommendation = when (curbScore) {
                                    0, 1 -> "خطورة منخفضة — التدبير خارج المشفى (Outpatient) بصادات فموية."
                                    2 -> "خطورة متوسطة — استشفاء قصير في الجناح العام أو مراقبة حثيثة."
                                    else -> "ذات رئة وخيمة عالية الخطورة (3-5) — استشفاء فوري وتقييم للقبول في العناية المشددة (ICU)."
                                }
                                Text(recommendation, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    val criteria = listOf(
                        Triple("C — تخليط ذهني حديث البدء (Confusion)", cConfusion) { v: Boolean -> cConfusion = v },
                        Triple("U — يوريا الدم > 42 ملغ/دل أو BUN > 19 mg/dL", cUrea) { v: Boolean -> cUrea = v },
                        Triple("R — تسرع التنفس ≥ 30 مرة/دقيقة", cResp) { v: Boolean -> cResp = v },
                        Triple("B — هبوط الضغط (الانقباضي < 90 أو الانبساطي ≤ 60 mmHg)", cBp) { v: Boolean -> cBp = v },
                        Triple("65 — العمر ≥ 65 سنة", cAge65) { v: Boolean -> cAge65 = v }
                    )

                    items(criteria) { (label, checked, onToggle) ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(!checked) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { onToggle(it) })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            } else {
                val ph = phInput.toFloatOrNull() ?: 7.40f
                val pco2 = pco2Input.toFloatOrNull() ?: 40f
                val hco3 = hco3Input.toFloatOrNull() ?: 24f
                val na = naInput.toFloatOrNull() ?: 140f
                val cl = clInput.toFloatOrNull() ?: 104f
                val anionGap = na - (cl + hco3)

                val disorder = when {
                    ph < 7.35f && hco3 < 22f -> if (anionGap > 12f) "حماض استقلابي مرتفع الفجوة الأنيونية (High Anion Gap Metabolic Acidosis - MUDPILES/DKA)" else "حماض استقلابي طبيعي الفجوة الأنيونية (مفرط الكلور - إسهال أو RTA)"
                    ph < 7.35f && pco2 > 45f -> "حماض تنفسي (Respiratory Acidosis - نقص تهوية / COPD)"
                    ph > 7.45f && hco3 > 26f -> "قلاء استقلابي (Metabolic Alkalosis - إقياءات متكررة / تضيق بواب / مدرات)"
                    ph > 7.45f && pco2 < 35f -> "قلاء تنفسي (Respiratory Alkalosis - فرط تهوية / صمة رئوية)"
                    else -> "توازن حمضي قلوي ضمن الحدود الطبيعية أو اضطراب مختلط معوض"
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("النتيجة الفورية لتحليل ABG:", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(disorder, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("الفجوة الأنيونية (Anion Gap): ${String.format("%.1f", anionGap)} mEq/L (الطبيعي 8–12)", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = phInput, onValueChange = { phInput = it }, label = { Text("pH") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = pco2Input, onValueChange = { pco2Input = it }, label = { Text("PaCO2") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = hco3Input, onValueChange = { hco3Input = it }, label = { Text("HCO3-") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = naInput, onValueChange = { naInput = it }, label = { Text("Na+ (mEq/L)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = clInput, onValueChange = { clInput = it }, label = { Text("Cl- (mEq/L)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                }
            }
        }
    }
}
