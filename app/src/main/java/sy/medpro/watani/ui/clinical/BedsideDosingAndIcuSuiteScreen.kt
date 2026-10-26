package sy.medpro.watani.ui.clinical

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.*
import java.util.Locale

enum class BedsideClinicalTab(val titleAr: String) {
    ICU_INFUSIONS("تسريب العناية (ICU Drips)"),
    FLUIDS_AND_BURNS("السوائل والشوارد والحروق"),
    RENAL_ANTIBIOTICS("الصادات والقصور الكلوي (Sanford)"),
    PEDIATRIC_DOSING("جرعات الأطفال (Harriet Lane)")
}

data class VasopressorDrug(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val standardDilutionMg: Float,
    val standardVolumeMl: Float,
    val minDoseMcgKgMin: Float,
    val maxDoseMcgKgMin: Float,
    val defaultDoseMcgKgMin: Float,
    val receptorProfile: String,
    val firstLineIndicationAr: String,
    val clinicalWarningAr: String
)

data class RenalDrugGuide(
    val nameEn: String,
    val classAr: String,
    val normalDose: String,
    val crCl30To50: String,
    val crCl10To29: String,
    val crClUnder10OrHd: String,
    val clinicalNoteAr: String
)

data class PediatricMedication(
    val nameAr: String,
    val nameEn: String,
    val indicationAr: String,
    val doseMgPerKg: Float,
    val frequencyAr: String,
    val maxSingleDoseMg: Float,
    val suspensionMgPer5Ml: Float, // 0f if IV/IM only
    val noteAr: String
)

object AccreditedBedsideCatalog {
    val icuDrugs = listOf(
        VasopressorDrug(
            id = "norepinephrine",
            nameAr = "النورإيبينفرين (Norepinephrine / Levophed)",
            nameEn = "Norepinephrine",
            standardDilutionMg = 4f,
            standardVolumeMl = 250f, // 16 mcg/mL
            minDoseMcgKgMin = 0.05f,
            maxDoseMcgKgMin = 0.50f,
            defaultDoseMcgKgMin = 0.10f,
            receptorProfile = "α1 >>>> β1 (مقبض وعائي قوي مع أثر إيجابي التقلص المعتدل)",
            firstLineIndicationAr = "الخط الأول المطلق في الصدمة الإنتانية (Septic Shock) والصدمة التوزعية ومعظم حالات الصدمة القلبية.",
            clinicalWarningAr = "يُسرّب عبر قثطرة وريدية مركزية؛ في حال التسرب تحت الجلد (Extravasation) يُحقن الفينتولامين (Phentolamine) موضعياً فوراً لمنع النخر."
        ),
        VasopressorDrug(
            id = "epinephrine",
            nameAr = "الإيبينفرين / الأدرينالين (Epinephrine)",
            nameEn = "Epinephrine",
            standardDilutionMg = 4f,
            standardVolumeMl = 250f, // 16 mcg/mL
            minDoseMcgKgMin = 0.02f,
            maxDoseMcgKgMin = 0.40f,
            defaultDoseMcgKgMin = 0.08f,
            receptorProfile = "β1 = β2 (بجرعات منخفضة) و α1 (بجرعات عالية)",
            firstLineIndicationAr = "الخط الأول في الصدمة التأقية (Anaphylactic Shock) والخط الثاني المضاف للنورإيبينفرين في الصدمة الإنتانية المعندة.",
            clinicalWarningAr = "قد يسبب تسرع قلب واضطرابات نظم وارتفاع لاكتات المصل العابر (تحلل الغليكوجين بوساطة β2)."
        ),
        VasopressorDrug(
            id = "dobutamine",
            nameAr = "الدوبوتامين (Dobutamine)",
            nameEn = "Dobutamine",
            standardDilutionMg = 250f,
            standardVolumeMl = 250f, // 1000 mcg/mL
            minDoseMcgKgMin = 2.0f,
            maxDoseMcgKgMin = 20.0f,
            defaultDoseMcgKgMin = 5.0f,
            receptorProfile = "β1 > β2 (مقوٍّ لعضلة القلب Inotrope مع توسع وعائي محيطي خفيف)",
            firstLineIndicationAr = "الصدمة القلبية (Cardiogenic Shock) وقصور القلب الحاد المترافق مع نقص نتاج قلبي شديد.",
            clinicalWarningAr = "قد يخفض الضغط الشرياني إذا كان المريض ناقص الحجم؛ يُشارك مع النورإيبينفرين إذا كان الضغط الانقباضي < 85 mmHg."
        ),
        VasopressorDrug(
            id = "dopamine",
            nameAr = "الدوبامين (Dopamine)",
            nameEn = "Dopamine",
            standardDilutionMg = 400f,
            standardVolumeMl = 250f, // 1600 mcg/mL
            minDoseMcgKgMin = 2.0f,
            maxDoseMcgKgMin = 20.0f,
            defaultDoseMcgKgMin = 7.0f,
            receptorProfile = "D1 (2-5) • β1 (5-10) • α1 (> 10 mcg/kg/min)",
            firstLineIndicationAr = "بطء القلب العرضي غير المستجيب للأتروبين أو بديل في الصدمة المترافقة ببطء قلب.",
            clinicalWarningAr = "لم يعد خطاً أول في الصدمة الإنتانية (Surviving Sepsis) لأنه يسبب تسرع قلب ولانظميات أكثر من النورإيبينفرين."
        )
    )

    val renalAntibiotics = listOf(
        RenalDrugGuide(
            nameEn = "Vancomycin (IV)",
            classAr = "Glycopeptide (MRSA & إيجابيات الغرام المعندة)",
            normalDose = "15–20 mg/kg كل 8–12 ساعة",
            crCl30To50 = "15 mg/kg كل 24 ساعة",
            crCl10To29 = "15 mg/kg كل 24–48 ساعة (حسب التركيز الأصغري Trough)",
            crClUnder10OrHd = "جرعة تحميل 15–25 mg/kg ثم إعادة الجرعة حسب Trough بعد التحال",
            clinicalNoteAr = "الهدف العلاجي لـ Trough هو 15–20 mcg/mL في الإنتانات الخطيرة (ذات الرئة، التهاب الشغاف، السحايا)."
        ),
        RenalDrugGuide(
            nameEn = "Meropenem (IV)",
            classAr = "Carbapenem (طيف واسع ومقاوم لـ ESBL)",
            normalDose = "1 g كل 8 ساعات (2 g كل 8 ساعات في التهاب السحايا)",
            crCl30To50 = "1 g كل 12 ساعة",
            crCl10To29 = "500 mg كل 12 ساعة",
            crClUnder10OrHd = "500 mg كل 24 ساعة (يُعطى بعد جلسة التحال الدموي)",
            clinicalNoteAr = "أقل إحداثاً للاختلاجات من الـ Imipenem."
        ),
        RenalDrugGuide(
            nameEn = "Piperacillin-Tazobactam (Zosyn)",
            classAr = "Antipseudomonal Penicillin + β-Lactamase Inhibitor",
            normalDose = "4.5 g IV كل 6 ساعات (أو كل 8 ساعات)",
            crCl30To50 = "3.375 g كل 6 ساعات (أو 4.5 g كل 8 ساعات)",
            crCl10To29 = "2.25 g كل 6–8 ساعات",
            crClUnder10OrHd = "2.25 g كل 8–12 ساعة + جرعة إضافية 0.75 g بعد التحال",
            clinicalNoteAr = "مشاركته مع Vancomycin تزيد خطر الأذية الكلوية الحادة؛ يفضل Cefepime أو Meropenem عند الإمكان."
        ),
        RenalDrugGuide(
            nameEn = "Ciprofloxacin (IV / PO)",
            classAr = "Fluoroquinolone (سلبيات الغرام والزائفة الزنجارية)",
            normalDose = "500–750 mg PO كل 12 ساعة (أو 400 mg IV كل 8–12 ساعة)",
            crCl30To50 = "500 mg PO كل 12 ساعة (لا تغيير غالباً إلا بالجرعات القصوى)",
            crCl10To29 = "250–500 mg PO كل 18–24 ساعة (أو 400 mg IV كل 24 ساعة)",
            crClUnder10OrHd = "250–500 mg كل 24 ساعة بعد جلسة التحال",
            clinicalNoteAr = "تجنبه في الحمل والأطفال ومرضى الوهن العضلي الوخيم وتطاول QT."
        ),
        RenalDrugGuide(
            nameEn = "Enoxaparin (Clexane - العلاجي)",
            classAr = "هيبارين منخفض الوزن الجزيئي (LMWH)",
            normalDose = "1 mg/kg تحت الجلد SC كل 12 ساعة",
            crCl30To50 = "1 mg/kg SC كل 12 ساعة (مراقبة حذرة)",
            crCl10To29 = "1 mg/kg SC كل 24 ساعة فقط! (نصف الجرعة اليومية)",
            crClUnder10OrHd = "يفضل التحويل إلى الهيبارين غير المجزأ (UFH) الوريدي",
            clinicalNoteAr = "في القصور الكلوي الشديد (CrCl < 30) يتراكم الـ Enoxaparin ويرتفع خطر النزف، لذا يُفضل الهيبارين العادي UFH لأن إطراحه غير كلوي."
        ),
        RenalDrugGuide(
            nameEn = "Ceftriaxone (Rocephin)",
            classAr = "سيفالوسبورين الجيل الثالث",
            normalDose = "1–2 g IV/IM كل 24 ساعة (2 g كل 12 ساعة في السحايا)",
            crCl30To50 = "لا يحتاج إلى أي تعديل كلوي!",
            crCl10To29 = "لا يحتاج إلى أي تعديل كلوي!",
            crClUnder10OrHd = "لا يحتاج تعديلاً (بشرط عدم تجاوز 2 g/day في القصور الكبدي والكلوي المشترك)",
            clinicalNoteAr = "يُطرح عبر الصفراء والكبد والكلية معاً، لذلك فهو الصاد المفضل في القصور الكلوي دون حاجة لتعديل الجرعة."
        )
    )

    val pediatricDrugs = listOf(
        PediatricMedication(
            nameAr = "باراسيتامول (Paracetamol / Acetaminophen)",
            nameEn = "Paracetamol",
            indicationAr = "خافض حرارة ومسكن ألم (الخط الأول الآمن منذ الولادة)",
            doseMgPerKg = 15f,
            frequencyAr = "كل 4–6 ساعات (بحد أقصى 5 جرعات/24 ساعة)",
            maxSingleDoseMg = 1000f,
            suspensionMgPer5Ml = 120f, // 120 mg / 5 mL standard syrup
            noteAr = "الجرعة السمية الحادة التي تستدعي N-Acetylcysteine هي > 150 mg/kg."
        ),
        PediatricMedication(
            nameAr = "إيبوبروفين (Ibuprofen - للأطفال > 6 أشهر)",
            nameEn = "Ibuprofen",
            indicationAr = "خافض حرارة ومضاد التهاب غير ستيروئيدي",
            doseMgPerKg = 10f,
            frequencyAr = "كل 6–8 ساعات بعد الطعام",
            maxSingleDoseMg = 400f,
            suspensionMgPer5Ml = 100f, // 100 mg / 5 mL
            noteAr = "يُتجنب عند الرضع دون 6 أشهر وفي حالات التجفاف أو الحماق."
        ),
        PediatricMedication(
            nameAr = "أموكسيسيلين عالي الجرعة (High-Dose Amoxicillin)",
            nameEn = "Amoxicillin (Otitis Media / Sinusitis)",
            indicationAr = "الخط الأول في التهاب الأذن الوسطى الحاد وذات الرئة المكتسبة بالمجتمع",
            doseMgPerKg = 45f, // 45 mg/kg per dose q12h = 90 mg/kg/day
            frequencyAr = "كل 12 ساعة (إجمالي 90 mg/kg/day)",
            maxSingleDoseMg = 1000f,
            suspensionMgPer5Ml = 250f, // 250 mg / 5 mL
            noteAr = "الجرعة العالية (80–90 mg/kg/day) ضرورية للتغلب على المكورات الرئوية ناقصة الحساسية للبنسلين."
        ),
        PediatricMedication(
            nameAr = "سيفترياكسون (Ceftriaxone IV/IM)",
            nameEn = "Ceftriaxone",
            indicationAr = "الإنتانات الجرثومية الشديدة والتهاب السحايا الجرثومي",
            doseMgPerKg = 50f,
            frequencyAr = "كل 24 ساعة (أو 50 mg/kg كل 12 ساعة في التهاب السحايا)",
            maxSingleDoseMg = 2000f,
            suspensionMgPer5Ml = 0f,
            noteAr = "مضاد استطباب عند الولدان (< 28 يوماً) لخطر اليرقان النووي وترسب أملاح الكالسيوم؛ يُستبدل بـ Cefotaxime."
        ),
        PediatricMedication(
            nameAr = "بريدنيزولون فموي (Prednisolone - نوبة الربو / الخانوق)",
            nameEn = "Prednisolone",
            indicationAr = "نوبات الربو الحادة والتهاب الحنجرة والرغامى (الخانوق)",
            doseMgPerKg = 1.5f,
            frequencyAr = "مرة واحدة صباحاً لمدة 3–5 أيام دون حاجة لسحب تدريجي",
            maxSingleDoseMg = 60f,
            suspensionMgPer5Ml = 15f, // 15 mg / 5 mL
            noteAr = "في الخانوق (Croup) يُفضل الديكساميثازون جرعة وحيدة 0.6 mg/kg."
        )
    )
}

@Composable
fun BedsideDosingAndIcuSuiteScreen(
    initialTab: BedsideClinicalTab = BedsideClinicalTab.ICU_INFUSIONS,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var selectedTab by remember { mutableStateOf(initialTab) }

    DrawerScreenScaffold(
        title = "حاسبات العناية المشددة والسوائل والجرعات السريرية",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BedsideClinicalTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.titleAr, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaroonPrimary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("bedside_tab_${tab.name.lowercase()}")
                    )
                }
            }

            when (selectedTab) {
                BedsideClinicalTab.ICU_INFUSIONS -> IcuInfusionCalculatorPane()
                BedsideClinicalTab.FLUIDS_AND_BURNS -> FluidsAndParklandCalculatorPane()
                BedsideClinicalTab.RENAL_ANTIBIOTICS -> SanfordRenalAntibioticsPane()
                BedsideClinicalTab.PEDIATRIC_DOSING -> HarrietLanePediatricDosingPane()
            }
        }
    }
}

@Composable
private fun IcuInfusionCalculatorPane() {
    var selectedDrug by remember { mutableStateOf(AccreditedBedsideCatalog.icuDrugs.first()) }
    var weightKg by remember { mutableFloatStateOf(75f) }
    var doseMcgKgMin by remember(selectedDrug) { mutableFloatStateOf(selectedDrug.defaultDoseMcgKgMin) }

    val concentrationMcgPerMl = remember(selectedDrug) {
        (selectedDrug.standardDilutionMg * 1000f) / selectedDrug.standardVolumeMl
    }
    val totalMcgPerMin = weightKg * doseMcgKgMin
    val rateMlPerHour = (totalMcgPerMin * 60f) / concentrationMcgPerMl
    val microDropsPerMin = rateMlPerHour // In microdrip set (60 gtt/mL), gtt/min == mL/hr

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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
                    Text(
                        text = "اختر الدواء الوعائي الفعال (Vasopressor / Inotrope):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaroonPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AccreditedBedsideCatalog.icuDrugs.forEach { drug ->
                        val isSelected = selectedDrug.id == drug.id
                        Surface(
                            color = if (isSelected) MaroonPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) BorderStroke(1.5.dp, MaroonPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedDrug = drug }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(drug.nameAr, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) MaroonPrimary else MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        text = "التمديد القياسي: ${drug.standardDilutionMg.toInt()} mg في ${drug.standardVolumeMl.toInt()} mL (${concentrationMcgPerMl.toInt()} mcg/mL)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                RadioButton(selected = isSelected, onClick = { selectedDrug = drug })
                            }
                        }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("وزن المريض:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${weightKg.toInt()} كغ", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                    }
                    Slider(
                        value = weightKg,
                        onValueChange = { weightKg = it },
                        valueRange = 40f..140f,
                        colors = SliderDefaults.colors(thumbColor = MaroonPrimary, activeTrackColor = MaroonPrimary)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الجرعة المطلوبة (mcg/kg/min):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = String.format(Locale.US, "%.2f mcg/kg/min", doseMcgKgMin),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaroonPrimary
                        )
                    }
                    Slider(
                        value = doseMcgKgMin,
                        onValueChange = { doseMcgKgMin = it },
                        valueRange = selectedDrug.minDoseMcgKgMin..selectedDrug.maxDoseMcgKgMin,
                        colors = SliderDefaults.colors(thumbColor = MaroonPrimary, activeTrackColor = MaroonPrimary)
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
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("نتيجة سرعة مضخة التسريب الوريدي الفورية", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = String.format(Locale.US, "%.1f mL/hr", rateMlPerHour),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            )
                            Text(
                                text = "أو ${microDropsPerMin.toInt()} قطرة/دقيقة (بجهاز الميكرودريب 60 gtt/mL)",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            color = GoldAccent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f mcg/min", totalMcgPerMin),
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• الطيف المستقبلات: ${selectedDrug.receptorProfile}", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• الاستطباب الأول: ${selectedDrug.firstLineIndicationAr}", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• تنبيه سريري: ${selectedDrug.clinicalWarningAr}", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun FluidsAndParklandCalculatorPane() {
    var patientWeightKg by remember { mutableFloatStateOf(70f) }
    var burnTbsaPercent by remember { mutableFloatStateOf(25f) }
    var measuredSerumNa by remember { mutableFloatStateOf(156f) }

    // Parkland Formula = 4 mL * Weight(kg) * %TBSA (2nd & 3rd degree burns)
    val totalParklandMl = 4f * patientWeightKg * burnTbsaPercent
    val first8HoursMl = totalParklandMl / 2f
    val first8HoursRate = first8HoursMl / 8f
    val next16HoursRate = (totalParklandMl / 2f) / 16f

    // Free Water Deficit in Hypernatremia = TBW (0.6 * Wt) * ((Na / 140) - 1)
    val totalBodyWaterL = 0.6f * patientWeightKg
    val freeWaterDeficitL = if (measuredSerumNa > 140f) {
        totalBodyWaterL * ((measuredSerumNa / 140f) - 1f)
    } else 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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
                    Text(
                        text = "1. معادلة باركلاند لإنعاش الحروق (Parkland Burn Resuscitation - ATLS)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaroonPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("وزن المريض:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${patientWeightKg.toInt()} كغ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                    }
                    Slider(
                        value = patientWeightKg,
                        onValueChange = { patientWeightKg = it },
                        valueRange = 15f..130f,
                        colors = SliderDefaults.colors(thumbColor = MaroonPrimary, activeTrackColor = MaroonPrimary)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("نسبة مساحة الحرق (TBSA % للدرجة 2 و 3):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${burnTbsaPercent.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                    }
                    Slider(
                        value = burnTbsaPercent,
                        onValueChange = { burnTbsaPercent = it },
                        valueRange = 10f..80f,
                        colors = SliderDefaults.colors(thumbColor = ErrorRed, activeTrackColor = ErrorRed)
                    )

                    Surface(
                        color = MaroonPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "إجمالي رينجر لاكتات (Ringer's Lactate) لأول 24 ساعة: ${totalParklandMl.toInt()} mL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaroonPrimary
                            )
                            Text(
                                text = "• أول 8 ساعات (من لحظة حدوث الحرق): ${first8HoursMl.toInt()} mL بمعدل ${first8HoursRate.toInt()} mL/hr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• الـ 16 ساعة التالية: ${first8HoursMl.toInt()} mL بمعدل ${next16HoursRate.toInt()} mL/hr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "ملاحظة ATLS: المعيار الأهم لكفاية الإنعاش هو الصبيب البولي (0.5 mL/kg/hr عند البالغ و 1 mL/kg/hr عند الطفل).",
                                fontSize = 11.sp,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                    Text(
                        text = "2. حاسبة عجز الماء الحر في فرط صوديوم الدم (Free Water Deficit)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaroonPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("صوديوم المصل الحالي (Na+):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${measuredSerumNa.toInt()} mEq/L", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                    }
                    Slider(
                        value = measuredSerumNa,
                        onValueChange = { measuredSerumNa = it },
                        valueRange = 142f..175f,
                        colors = SliderDefaults.colors(thumbColor = MaroonPrimary, activeTrackColor = MaroonPrimary)
                    )

                    Surface(
                        color = WarningAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = String.format(Locale.US, "عجز الماء الحر المقدر: %.2f ليتر (%.0f mL)", freeWaterDeficitL, freeWaterDeficitL * 1000f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaroonDeep
                            )
                            Text(
                                text = "• قاعدة الأمان الذهبية (Harrison's): لا يجوز خفض أو رفع صوديوم المصل بأكثر من 8–10 mEq/L خلال 24 ساعة لتجنب الوذمة الدماغية (في فرط الصوديوم) أو انحلال النخاعين الجسري المركزي ODS (في نقص الصوديوم).",
                                fontSize = 12.sp,
                                color = MaroonPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SanfordRenalAntibioticsPane() {
    var ageYears by remember { mutableFloatStateOf(65f) }
    var weightKg by remember { mutableFloatStateOf(72f) }
    var serumCreatinine by remember { mutableFloatStateOf(2.1f) }
    var isFemale by remember { mutableStateOf(false) }

    // Cockcroft-Gault Formula: ((140 - Age) * Weight) / (72 * SCr) * (0.85 if female)
    val crCl = remember(ageYears, weightKg, serumCreatinine, isFemale) {
        val base = ((140f - ageYears) * weightKg) / (72f * serumCreatinine.coerceAtLeast(0.4f))
        if (isFemale) base * 0.85f else base
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حاسبة تصفية الكرياتينين (Cockcroft-Gault CrCl)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaroonPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !isFemale,
                                onClick = { isFemale = false },
                                label = { Text("ذكر", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = isFemale,
                                onClick = { isFemale = true },
                                label = { Text("أنثى (×0.85)", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("العمر: ${ageYears.toInt()} سنة • الوزن: ${weightKg.toInt()} كغ • الكرياتينين: ${String.format(Locale.US, "%.1f", serumCreatinine)} mg/dL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = serumCreatinine,
                        onValueChange = { serumCreatinine = it },
                        valueRange = 0.6f..6.0f,
                        colors = SliderDefaults.colors(thumbColor = MaroonPrimary, activeTrackColor = MaroonPrimary)
                    )

                    Surface(
                        color = if (crCl < 30f) ErrorRed.copy(alpha = 0.12f) else if (crCl < 50f) WarningAmber.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "تصفية الكرياتينين المقدرة: %.0f mL/min", crCl),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (crCl < 30f) ErrorRed else MaroonPrimary
                            )
                            Text(
                                text = when {
                                    crCl >= 50f -> "لا حاجة لتعديل معظم الصادات"
                                    crCl >= 30f -> "قصور متوسط (تعديل واجب)"
                                    else -> "قصور شديد (تخفيض حاسم)"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        items(AccreditedBedsideCatalog.renalAntibiotics, key = { it.nameEn }) { drug ->
            val recommendedDose = when {
                crCl >= 50f -> drug.normalDose
                crCl >= 30f -> drug.crCl30To50
                crCl >= 10f -> drug.crCl10To29
                else -> drug.crClUnder10OrHd
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(drug.nameEn, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    Text(drug.classAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = MaroonPrimary.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "الجرعة الموصى بها لمريضك (CrCl = ${crCl.toInt()} mL/min):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaroonPrimary
                            )
                            Text(recommendedDose, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• الجرعة النظامية: ${drug.normalDose}", fontSize = 12.sp)
                    Text("• CrCl 30–50: ${drug.crCl30To50}", fontSize = 12.sp)
                    Text("• CrCl 10–29: ${drug.crCl10To29}", fontSize = 12.sp)
                    Text("• CrCl < 10 / HD: ${drug.crClUnder10OrHd}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ملاحظة Sanford: ${drug.clinicalNoteAr}", fontSize = 11.sp, color = GoldAccentDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HarrietLanePediatricDosingPane() {
    var childWeightKg by remember { mutableFloatStateOf(14f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("وزن الطفل (Harriet Lane Weight Dosing):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                        Text(
                            text = String.format(Locale.US, "%.1f كغ", childWeightKg),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaroonPrimary
                        )
                    }
                    Slider(
                        value = childWeightKg,
                        onValueChange = { childWeightKg = it },
                        valueRange = 4f..45f,
                        colors = SliderDefaults.colors(thumbColor = MaroonPrimary, activeTrackColor = MaroonPrimary)
                    )
                }
            }
        }

        items(AccreditedBedsideCatalog.pediatricDrugs, key = { it.nameEn }) { med ->
            val rawDoseMg = (childWeightKg * med.doseMgPerKg).coerceAtMost(med.maxSingleDoseMg)
            val suspensionMl = if (med.suspensionMgPer5Ml > 0f) {
                (rawDoseMg * 5f) / med.suspensionMgPer5Ml
            } else 0f

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(med.nameAr, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                    Text(med.indicationAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = SuccessGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "الجرعة الواحدة: ${rawDoseMg.toInt()} mg",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SuccessGreen
                                )
                                Text(med.frequencyAr, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (suspensionMl > 0f) {
                                Surface(
                                    color = MaroonPrimary,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f mL شراب", suspensionMl),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• المعادلة المعتمدة: ${med.doseMgPerKg} mg/kg/dose (الحد الأقصى للجرعة: ${med.maxSingleDoseMg.toInt()} mg)", fontSize = 11.sp)
                    Text("• تنبيه Harriet Lane: ${med.noteAr}", fontSize = 11.sp, color = GoldAccentDark, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
