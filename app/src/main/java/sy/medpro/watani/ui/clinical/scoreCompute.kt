package sy.medpro.watani.ui.clinical

// محرك المقاييس النقطية (Point-based clinical scores) للمقاييس الوصفية الـ19
// في set1.kt / set2.kt التي لا تملك حساباً حسابياً في calcCompute.kt.
// كل البنود والخيارات والنقاط والفئات والتفسيرات هنا منقولة حرفياً من مواصفات
// set1.kt (child_pugh) و set2.kt (الـ18 الباقية) ومن تقرير تدقيق الحاسبات
// المؤرخ 5 نوفمبر 2026 (build-checks/calc-audit/final-report.md)، بما فيه
// تصحيحات الفجوات النصية الموثقة فيه:
//  - MEWS: انقباضي 70 أو أقل = 3، ونبض 40 أو أقل = 2 (لا فجوة عند 70 و40).
//  - SOFA: بيليروبين 12.0 أو أكثر = 4، وكرياتينين 5.0 أو أكثر = 4 (لا فجوة).
// لا أرقام مخترعة: أي قيمة غير موجودة في المواصفة أو التقرير غير موجودة هنا.
// لا Android imports، لا آثار جانبية — مثل calcCompute.kt تماماً.
//
// عقد النتيجة هو CalcOutcome نفسه من calcCompute.kt: النجاح Value بنص جاهز
// للعرض، والفشل Rejected بسبب عربي واضح — لا فراغ صامت أبداً.

data class ScoreOption(val labelAr: String, val points: Int, val untestable: Boolean = false)

data class ScoreItem(
    val key: String,
    val labelAr: String,
    val options: List<ScoreOption>,
    val exclusiveGroup: String? = null,
    val stage: String? = null
)

data class ScoreBand(val minTotal: Int, val maxTotal: Int, val text: String)

data class ClinicalScoreDef(
    val specId: String,
    val items: List<ScoreItem>,
    val bands: List<ScoreBand>,
    val resultLabel: String,
    val stages: List<String> = emptyList(),
    val stageLabels: Map<String, String> = emptyMap(),
    val partialHint: String? = null,
    // الخريطة الممررة إلى extraNote و bandOverride هي نقاط البنود المختارة
    // (itemKey -> نقاط الخيار المختار)، لا فهارس الخيارات — بهذا تعني عبارة
    // «أي بند = 3» في NEWS2/MEWS ثلاث نقاط فعلاً كما في المواصفات.
    val extraNote: ((Map<String, Int>) -> String?)? = null,
    val bandOverride: ((Int, Map<String, Int>) -> String)? = null
)

// مختصرات بناء التعريفات (تسميات الخيارات وصفية فقط، بلا أرقام نقاط داخل
// النص إلا أرقام النطاقات السريرية نفسها؛ الواجهة تُلحق النقاط منفصلة).
private fun opt(label: String, points: Int, untestable: Boolean = false): ScoreOption =
    ScoreOption(label, points, untestable)

private fun yn(yesPoints: Int = 1): List<ScoreOption> =
    listOf(ScoreOption("لا", 0), ScoreOption("نعم", yesPoints))

private fun item(
    key: String,
    labelAr: String,
    options: List<ScoreOption>,
    exclusiveGroup: String? = null,
    stage: String? = null
): ScoreItem = ScoreItem(key, labelAr, options, exclusiveGroup, stage)

private fun band(minTotal: Int, maxTotal: Int, text: String): ScoreBand =
    ScoreBand(minTotal, maxTotal, text)

val clinicalScoreDefinitions: Map<String, ClinicalScoreDef> = listOf(

    // 1) Child-Pugh — من set1.kt (المثال المحلول: 2+2+2+2+1 = 9 فئة B).
    ClinicalScoreDef(
        specId = "child_pugh",
        resultLabel = "مجموع Child-Pugh",
        items = listOf(
            item("bilirubin", "البيليروبين الكلي (mg/dL)", listOf(opt("أقل من 2", 1), opt("2–3", 2), opt("أكثر من 3", 3))),
            item("albumin", "الألبومين (g/dL)", listOf(opt("أكثر من 3.5", 1), opt("2.8–3.5", 2), opt("أقل من 2.8", 3))),
            item("inr", "INR", listOf(opt("أقل من 1.7", 1), opt("1.7–2.3", 2), opt("أكثر من 2.3", 3))),
            item("ascites", "الاستسقاء", listOf(opt("لا يوجد", 1), opt("خفيف", 2), opt("متوسط إلى شديد", 3))),
            item("encephalopathy", "الاعتلال الدماغي", listOf(opt("لا يوجد", 1), opt("درجة I–II", 2), opt("درجة III–IV", 3)))
        ),
        bands = listOf(
            band(5, 6, "فئة A (تعويض جيد) — بقاء سنة واحدة نحو 100٪"),
            band(7, 9, "فئة B (قصور متوسط) — بقاء سنة واحدة نحو 80٪"),
            band(10, 15, "فئة C (قصور شديد) — بقاء سنة واحدة نحو 45٪")
        )
    ),

    // 2) Ranson — مرحلتان: عند القبول ثم خلال 48 ساعة (المجموع الكلي 0–11).
    ClinicalScoreDef(
        specId = "ranson_pancreatitis",
        resultLabel = "مجموع Ranson",
        stages = listOf("adm", "h48"),
        stageLabels = mapOf("adm" to "عند القبول", "h48" to "خلال 48 ساعة"),
        partialHint = "الدرجة الكاملة تتطلب بنود 48 ساعة الستة أيضاً؛ تُفسَّر نسب الوفيات أدناه على المجموع الكلي 0–11 بعد اكتمال المرحلتين، فلا تُطبَّق على مجموع القبول وحده. وهذه النسخة لالتهاب البنكرياس غير الحصوي (نسخة الحصوي تختلف في بعض العتبات).",
        extraNote = { "تنبيه: هذه النسخة لالتهاب البنكرياس غير الحصوي؛ نسخة الحصوي تختلف في بعض العتبات." },
        items = listOf(
            item("adm_age", "العمر أعلى من 55 سنة", yn(), stage = "adm"),
            item("adm_wbc", "كريات بيض أعلى من 16000/مم³", yn(), stage = "adm"),
            item("adm_glucose", "سكر أعلى من 200 ملغ/دل", yn(), stage = "adm"),
            item("adm_ldh", "LDH أعلى من 350 وحدة/لتر", yn(), stage = "adm"),
            item("adm_ast", "AST أعلى من 250 وحدة/لتر", yn(), stage = "adm"),
            item("hct_drop", "هبوط الهيماتوكريت أكثر من 10٪", yn(), stage = "h48"),
            item("bun_rise", "ارتفاع البولة أكثر من 5 ملغ/دل", yn(), stage = "h48"),
            item("calcium", "كالسيوم أقل من 8 ملغ/دل", yn(), stage = "h48"),
            item("pao2", "PaO2 أقل من 60 mmHg", yn(), stage = "h48"),
            item("base_deficit", "عجز قاعدة أعلى من 4 مكافئ/لتر", yn(), stage = "h48"),
            item("fluid", "احتجاز سوائل أكثر من 6 لتر", yn(), stage = "h48")
        ),
        bands = listOf(
            band(0, 2, "وفيات حوالي 2٪"),
            band(3, 4, "وفيات حوالي 15٪"),
            band(5, 6, "وفيات حوالي 40٪"),
            band(7, 11, "وفيات تقترب من 100٪ في السلسلة الكلاسيكية")
        )
    ),

    // 3) BISAP — خمسة معايير، نقطة لكل معيار.
    ClinicalScoreDef(
        specId = "bisap_pancreatitis",
        resultLabel = "مجموع BISAP",
        items = listOf(
            item("bun", "BUN أعلى من 25 ملغ/دل", yn()),
            item("mental", "اضطراب الحالة العقلية (GCS أقل من 15)", yn()),
            item("sirs", "SIRS بمعيارين أو أكثر", yn()),
            item("age60", "العمر أعلى من 60 سنة", yn()),
            item("effusion", "انصباب جنبي", yn())
        ),
        bands = listOf(
            band(0, 0, "وفيات حوالي 0.1٪"),
            band(1, 1, "وفيات حوالي 0.5٪"),
            band(2, 2, "وفيات حوالي 2٪"),
            band(3, 3, "وفيات حوالي 5٪ — خطر مرتفع ومراقبة مكثفة"),
            band(4, 4, "وفيات حوالي 13٪ — خطر مرتفع ومراقبة مكثفة"),
            band(5, 5, "وفيات حوالي 22٪ — خطر مرتفع ومراقبة مكثفة")
        )
    ),

    // 4) Glasgow-Blatchford — الهيموغلوبين بند بديل حسب الجنس (مجموعة حصرية hb).
    ClinicalScoreDef(
        specId = "glasgow_blatchford",
        resultLabel = "مجموع Glasgow-Blatchford",
        items = listOf(
            item("bun", "بولة الدم (مليمول/لتر)", listOf(opt("أقل من 6.5", 0), opt("6.5–7.9", 2), opt("8.0–9.9", 3), opt("10.0–24.9", 4), opt("25 أو أكثر", 6))),
            item("hb_male", "الهيموغلوبين — للرجال (غ/لتر)", listOf(opt("130 أو أكثر", 0), opt("120–129", 1), opt("100–119", 3), opt("أقل من 100", 6)), exclusiveGroup = "hb"),
            item("hb_female", "الهيموغلوبين — للنساء (غ/لتر)", listOf(opt("120 أو أكثر", 0), opt("100–119", 1), opt("أقل من 100", 6)), exclusiveGroup = "hb"),
            item("sbp", "الضغط الانقباضي (mmHg)", listOf(opt("110 أو أكثر", 0), opt("100–109", 1), opt("90–99", 2), opt("أقل من 90", 3))),
            item("hr", "النبض 100/دقيقة أو أكثر", yn()),
            item("melena", "ميلينا", yn()),
            item("syncope", "إغماء", yn(2)),
            item("liver", "مرض كبدي", yn(2)),
            item("cardiac", "قصور قلبي", yn(2))
        ),
        bands = listOf(
            band(0, 0, "خطر منخفض جداً — يمكن النظر في العلاج الخارجي دون تنظير عاجل"),
            band(1, 1, "خطر منخفض نسبياً"),
            band(2, 5, "ليست فئة منخفضة — تُفسَّر مع التقييم السريري وعلامات النزف المستمر؛ و6 أو أكثر تعني أن أكثر من 50٪ يحتاجون تداخلاً"),
            band(6, 23, "خطر مرتفع — أكثر من 50٪ يحتاجون تداخلاً (نقل دم أو تنظير علاجي أو جراحة)")
        )
    ),

    // 5) Rockall — مرحلتان: قبل التنظير (0–7) ثم بعد التنظير (الكلي 0–11).
    ClinicalScoreDef(
        specId = "rockall_bleeding",
        resultLabel = "مجموع Rockall",
        stages = listOf("pre", "post"),
        stageLabels = mapOf("pre" to "قبل التنظير (سريري)", "post" to "بعد التنظير"),
        partialHint = "هذه درجة ما قبل التنظير (0–7) فقط؛ الدرجة الكاملة (0–11) تحتاج بندَي التنظير، ودرجة ما قبل التنظير وحدها لا تكفي للقرار النهائي.",
        items = listOf(
            item("age", "العمر", listOf(opt("أقل من 60", 0), opt("60–79", 1), opt("80 أو أكثر", 2)), stage = "pre"),
            item("shock", "الصدمة", listOf(opt("لا صدمة (انقباضي 100 أو أكثر ونبض أقل من 100)", 0), opt("تسرع قلب (انقباضي 100 أو أكثر ونبض 100 أو أكثر)", 1), opt("انخفاض ضغط (انقباضي أقل من 100)", 2)), stage = "pre"),
            item("comorbidity", "المراضة المصاحبة", listOf(opt("لا مراضة كبيرة", 0), opt("قصور قلبي أو مرض قلبي إقفاري أو مراضة كبيرة أخرى", 2), opt("قصور كلوي أو كبدي أو خباثة منتشرة", 3)), stage = "pre"),
            item("diagnosis", "التشخيص التنظيري", listOf(opt("Mallory-Weiss أو لا آفة أو لا علامة نزف حديث", 0), opt("أي تشخيص آخر", 1), opt("خباثة هضمية علوية", 2)), stage = "post"),
            item("stigmata", "علامات النزف الحديث بالتنظير", listOf(opt("لا شيء أو بقعة داكنة", 0), opt("دم أو خثرة ملتصقة أو وعاء نازف مرئي", 2)), stage = "post")
        ),
        bands = listOf(
            band(0, 2, "خطر منخفض"),
            band(3, 4, "خطر متوسط"),
            band(5, 11, "خطر مرتفع لإعادة النزف والوفاة — مراقبة مكثفة")
        )
    ),

    // 6) Alvarado (MANTRELS) — المجموع 0–10.
    ClinicalScoreDef(
        specId = "alvarado_appendicitis",
        resultLabel = "مجموع Alvarado",
        items = listOf(
            item("migration", "انتقال الألم", yn(1)),
            item("anorexia", "فقدان الشهية", yn(1)),
            item("nausea", "غثيان أو إقياء", yn(1)),
            item("tenderness", "إيلام الربع السفلي الأيمن", yn(2)),
            item("rebound", "إيلام ارتدادي", yn(1)),
            item("fever", "حرارة 37.3° أو أكثر", yn(1)),
            item("leukocytosis", "كريات بيض أعلى من 10000", yn(2)),
            item("shift", "انزياح لليسار (عدلات أعلى من 75٪)", yn(1))
        ),
        bands = listOf(
            band(0, 4, "احتمال منخفض — غالباً لا حاجة لجراحة عاجلة"),
            band(5, 6, "ممكن — يستدعي مراقبة وتصويراً"),
            band(7, 8, "محتمل — الجراحة مرجحة"),
            band(9, 10, "محتمل جداً")
        )
    ),

    // 7) AIR — المجموع 0–12.
    ClinicalScoreDef(
        specId = "air_appendicitis",
        resultLabel = "مجموع AIR",
        items = listOf(
            item("vomiting", "إقياء", yn()),
            item("rlq_pain", "ألم الربع السفلي الأيمن", yn()),
            item("rebound", "إيلام ارتدادي", listOf(opt("لا يوجد", 0), opt("خفيف", 1), opt("متوسط", 2), opt("قوي", 3))),
            item("fever", "حرارة 38.5° أو أكثر", yn()),
            item("wbc", "الكريات البيض", listOf(opt("أقل من 10000", 0), opt("10000–14900", 1), opt("15000 أو أكثر", 2))),
            item("neutrophils", "العدلات", listOf(opt("أقل من 70٪", 0), opt("70–84٪", 1), opt("85٪ أو أكثر", 2))),
            item("crp", "CRP (ملغ/لتر)", listOf(opt("أقل من 10", 0), opt("10–49", 1), opt("50 أو أكثر", 2)))
        ),
        bands = listOf(
            band(0, 4, "احتمال منخفض — يمكن متابعة المريض خارجياً مع تعليمات العودة"),
            band(5, 8, "احتمال متوسط — مراقبة داخل المستشفى وتصوير"),
            band(9, 12, "احتمال مرتفع — التداخل الجراحي مرجح")
        )
    ),

    // 8) HAS-BLED — المجموع 0–9. تعريفا الخلل الكلوي والكبدي من تعريف
    // Pisters 2010 الأصلي كما وثّق التقرير غيابهما عن المواصفة.
    ClinicalScoreDef(
        specId = "hasbled",
        resultLabel = "مجموع HAS-BLED",
        items = listOf(
            item("htn", "ضغط انقباضي غير مضبوط (أعلى من 160)", yn()),
            item("renal", "خلل كلوي (غسيل مزمن أو زرع كلى أو كرياتينين 200 ميكرومول/لتر أو أكثر)", yn()),
            item("liver", "خلل كبدي (تشمع، أو بيليروبين أعلى من ضعفي الطبيعي مع خمائر أعلى من 3 أضعاف الطبيعي)", yn()),
            item("stroke", "سكتة سابقة", yn()),
            item("bleed", "نزف سابق أو استعداد للنزف", yn()),
            item("inr", "INR متقلب", yn()),
            item("elderly", "عمر أعلى من 65 سنة", yn()),
            item("drugs", "أدوية مؤهبة للنزف (مضادات صفيحات أو مضادات التهاب غير ستيرويدية)", yn()),
            item("alcohol", "شرب الكحول", yn())
        ),
        bands = listOf(
            band(0, 2, "خطر نزف أقل نسبياً"),
            band(3, 9, "خطر نزف مرتفع — مراجعة دورية ومعالجة العوامل القابلة للتعديل؛ الدرجة المرتفعة وحدها لا تمنع مضاد التخثر")
        )
    ),

    // 9) Padua — المجموع 0–20.
    ClinicalScoreDef(
        specId = "padua_vte",
        resultLabel = "مجموع Padua",
        items = listOf(
            item("cancer", "سرطان فعال", yn(3)),
            item("prior_vte", "خثار وريدي سابق", yn(3)),
            item("mobility", "حركة محدودة (ملازمة فراش 3 أيام أو أكثر)", yn(3)),
            item("thrombophilia", "أهبة خثارية معروفة", yn(3)),
            item("trauma", "رض أو جراحة حديثة (خلال الشهر السابق)", yn(2)),
            item("age70", "عمر 70 سنة أو أكثر", yn()),
            item("heart_resp", "قصور قلبي أو تنفسي", yn()),
            item("mi_stroke", "احتشاء عضلة قلب أو سكتة حادة", yn()),
            item("infection", "عدوى حادة أو مرض روماتزمي", yn()),
            item("obesity", "بدانة (BMI 30 أو أكثر)", yn()),
            item("hormonal", "علاج هرموني", yn())
        ),
        bands = listOf(
            band(0, 3, "خطر منخفض"),
            band(4, 20, "خطر مرتفع — الوقاية الدوائية ما لم يوجد مانع نزف")
        )
    ),

    // 10) TIMI (UA/NSTEMI) — المجموع 0–7، خطر 14 يوماً لكل درجة.
    ClinicalScoreDef(
        specId = "timi_ua_nstemi",
        resultLabel = "مجموع TIMI",
        items = listOf(
            item("age65", "العمر 65 سنة أو أكثر", yn()),
            item("risk3", "ثلاثة عوامل خطر تاجية أو أكثر", yn()),
            item("cad", "تضيق تاجي معروف 50٪ أو أكثر", yn()),
            item("st", "انحراف ST بمقدار 0.5 مم أو أكثر", yn()),
            item("angina2", "نوبتا ذبحة أو أكثر خلال 24 ساعة", yn()),
            item("aspirin", "أسبرين خلال الأيام السبعة الماضية", yn()),
            item("biomarker", "واسمات قلبية مرتفعة", yn())
        ),
        bands = listOf(
            band(0, 1, "خطر الوفاة أو الاحتشاء أو إعادة التوعي العاجلة خلال 14 يوماً حوالي 5٪"),
            band(2, 2, "خطر الوفاة أو الاحتشاء أو إعادة التوعي العاجلة خلال 14 يوماً حوالي 8٪"),
            band(3, 3, "خطر الوفاة أو الاحتشاء أو إعادة التوعي العاجلة خلال 14 يوماً حوالي 13٪"),
            band(4, 4, "خطر الوفاة أو الاحتشاء أو إعادة التوعي العاجلة خلال 14 يوماً حوالي 20٪"),
            band(5, 5, "خطر الوفاة أو الاحتشاء أو إعادة التوعي العاجلة خلال 14 يوماً حوالي 26٪"),
            band(6, 7, "خطر الوفاة أو الاحتشاء أو إعادة التوعي العاجلة خلال 14 يوماً حوالي 41٪")
        )
    ),

    // 11) qSOFA — المجموع 0–3.
    ClinicalScoreDef(
        specId = "qsofa",
        resultLabel = "مجموع qSOFA",
        items = listOf(
            item("rr_high", "معدل التنفس 22/دقيقة أو أكثر", yn()),
            item("sbp_low", "الضغط الانقباضي 100 mmHg أو أقل", yn()),
            item("gcs_low", "GCS أقل من 15", yn())
        ),
        bands = listOf(
            band(0, 1, "خطر أقل"),
            band(2, 3, "خطر مرتفع لسوء النتائج — تقييم إنتان عاجل وSOFA كامل")
        )
    ),

    // 12) SIRS — عدد المعايير 0–4.
    ClinicalScoreDef(
        specId = "sirs_criteria",
        resultLabel = "عدد معايير SIRS",
        items = listOf(
            item("temp", "حرارة أعلى من 38° أو أقل من 36°", yn()),
            item("hr", "نبض أعلى من 90/دقيقة", yn()),
            item("rr", "تنفس أعلى من 20/دقيقة أو PaCO2 أقل من 32 mmHg", yn()),
            item("wbc", "كريات بيض أعلى من 12000 أو أقل من 4000/مم³ أو أشكال غير ناضجة أعلى من 10٪", yn())
        ),
        bands = listOf(
            band(0, 1, "معايير SIRS غير محققة (يلزم معياران أو أكثر)"),
            band(2, 4, "SIRS محققة — غير نوعية ولا تساوي الإنتان وحدها")
        )
    ),

    // 13) SOFA — ستة أجهزة، كل منها 0–4، المجموع 0–24.
    ClinicalScoreDef(
        specId = "sofa_score",
        resultLabel = "مجموع SOFA",
        items = listOf(
            item("resp", "التنفس (PaO2/FiO2)", listOf(opt("400 أو أكثر", 0), opt("أقل من 400", 1), opt("أقل من 300", 2), opt("أقل من 200 مع دعم تنفسي", 3), opt("أقل من 100 مع دعم تنفسي", 4))),
            item("coag", "التخثر (الصفيحات ×1000/مم³)", listOf(opt("150 أو أكثر", 0), opt("أقل من 150", 1), opt("أقل من 100", 2), opt("أقل من 50", 3), opt("أقل من 20", 4))),
            item("liver", "الكبد (بيليروبين ملغ/دل)", listOf(opt("أقل من 1.2", 0), opt("1.2–1.9", 1), opt("2.0–5.9", 2), opt("6.0–11.9", 3), opt("12.0 أو أكثر", 4))),
            item("cardio", "القلب والأوعية (الجرعات مكغ/كغ/دقيقة)", listOf(opt("MAP 70 أو أكثر", 0), opt("MAP أقل من 70", 1), opt("دوبامين 5 أو أقل أو دوبوتامين بأي جرعة", 2), opt("دوبامين أعلى من 5 أو أدرينالين 0.1 أو أقل أو نورأدرينالين 0.1 أو أقل", 3), opt("دوبامين أعلى من 15 أو أدرينالين أعلى من 0.1 أو نورأدرينالين أعلى من 0.1", 4))),
            item("cns", "الجهاز العصبي (GCS)", listOf(opt("15", 0), opt("13–14", 1), opt("10–12", 2), opt("6–9", 3), opt("أقل من 6", 4))),
            item("renal", "الكلى (كرياتينين ملغ/دل أو بول)", listOf(opt("أقل من 1.2", 0), opt("1.2–1.9", 1), opt("2.0–3.4", 2), opt("3.5–4.9 أو بول أقل من 500 مل/يوم", 3), opt("5.0 أو أكثر أو بول أقل من 200 مل/يوم", 4)))
        ),
        bands = listOf(
            band(0, 24, "ارتفاع SOFA نقطتين أو أكثر عن خط الأساس مع عدوى مشتبهة = إنتان وفق Sepsis-3؛ وكلما ارتفع المجموع ارتفعت الوفيات (6–7 يقارب وفيات 20٪، و10 يقارب 40–50٪ في السلاسل الأصلية، مع اختلاف كبير حسب السياق)")
        )
    ),

    // 14) NIHSS — المجموع 0–42. بنود الأطراف الأربعة تحمل خياراً أخيراً
    // «غير قابل للاختبار (UN)» يُوثَّق ولا يُحتسب (0 نقاط) كما في المواصفة.
    ClinicalScoreDef(
        specId = "nihss",
        resultLabel = "مجموع NIHSS",
        items = listOf(
            item("loc", "1أ مستوى الوعي", listOf(opt("يقظ", 0), opt("نعاس", 1), opt("ذهول/غيبوبة خفيفة", 2), opt("غيبوبة عميقة", 3))),
            item("loc_q", "1ب أسئلة الوعي (الشهر والعمر)", listOf(opt("كلاهما صحيح", 0), opt("أحدهما صحيح", 1), opt("لا شيء صحيح", 2))),
            item("loc_c", "1ج أوامر الوعي (فتح/إغلاق العينين وقبض اليد)", listOf(opt("كلاهما صحيح", 0), opt("أحدهما صحيح", 1), opt("لا شيء صحيح", 2))),
            item("gaze", "2 النظر الأفقي", listOf(opt("طبيعي", 0), opt("شلل جزئي", 1), opt("انحراف ثابت", 2))),
            item("visual", "3 المجال البصري", listOf(opt("طبيعي", 0), opt("عمى نصفي جزئي", 1), opt("عمى نصفي كامل", 2), opt("عمى ثنائي", 3))),
            item("facial", "4 شلل الوجه", listOf(opt("طبيعي", 0), opt("خفيف", 1), opt("جزئي", 2), opt("كامل", 3))),
            item("arm_l", "5أ حركة الذراع اليسرى", nihssLimbOptions()),
            item("arm_r", "5ب حركة الذراع اليمنى", nihssLimbOptions()),
            item("leg_l", "6أ حركة الساق اليسرى", nihssLimbOptions()),
            item("leg_r", "6ب حركة الساق اليمنى", nihssLimbOptions()),
            item("ataxia", "7 الرنح", listOf(opt("غائب", 0), opt("في طرف واحد", 1), opt("في طرفين", 2))),
            item("sensory", "8 الحس", listOf(opt("طبيعي", 0), opt("فقد خفيف إلى متوسط", 1), opt("فقد شديد أو كامل", 2))),
            item("language", "9 اللغة", listOf(opt("طبيعي", 0), opt("حبسة خفيفة إلى متوسطة", 1), opt("حبسة شديدة", 2), opt("صمت أو حبسة شاملة", 3))),
            item("dysarthria", "10 عسر التلفظ", listOf(opt("طبيعي", 0), opt("خفيف إلى متوسط", 1), opt("شديد", 2))),
            item("neglect", "11 الإهمال والانطفاء", listOf(opt("طبيعي", 0), opt("إهمال في جهة/نمط واحد", 1), opt("إهمال شديد في أكثر من جهة", 2)))
        ),
        bands = listOf(
            band(0, 0, "لا عجز"),
            band(1, 4, "سكتة خفيفة"),
            band(5, 15, "سكتة متوسطة"),
            band(16, 20, "سكتة متوسطة إلى شديدة"),
            band(21, 42, "سكتة شديدة")
        )
    ),

    // 15) sPESI — المجموع 0–6.
    ClinicalScoreDef(
        specId = "spesi_simplified",
        resultLabel = "مجموع sPESI",
        items = listOf(
            item("age80", "العمر أعلى من 80 سنة", yn()),
            item("cancer", "سرطان فعال أو سابق", yn()),
            item("cardiopulm", "مرض قلبي رئوي مزمن", yn()),
            item("hr", "نبض 110/دقيقة أو أكثر", yn()),
            item("sbp", "ضغط انقباضي أقل من 100 mmHg", yn()),
            item("o2", "إشباع أكسجين أقل من 90٪", yn())
        ),
        bands = listOf(
            band(0, 0, "خطر منخفض — وفيات 30 يوماً حوالي 1٪"),
            band(1, 6, "خطر مرتفع — وفيات 30 يوماً حوالي 11٪ في الدراسة الأصلية")
        )
    ),

    // 16) PERC — كل بند عبارة أمان: «ينطبق» = 0، «لا ينطبق» = 1، والمجموع
    // هو عدد البنود غير المتحققة (0 = القاعدة مستوفاة).
    ClinicalScoreDef(
        specId = "perc_rule",
        resultLabel = "عدد بنود PERC غير المتحققة",
        items = listOf(
            item("age50", "العمر أقل من 50 سنة", percOptions()),
            item("hr100", "النبض أقل من 100/دقيقة", percOptions()),
            item("o2_95", "إشباع الأكسجين 95٪ أو أكثر", percOptions()),
            item("no_hemoptysis", "لا نفث دم", percOptions()),
            item("no_estrogen", "لا استخدام إستروجين", percOptions()),
            item("no_prior_vte", "لا خثار وريدي عميق أو انصمام رئوي سابق", percOptions()),
            item("no_swelling", "لا تورم أحادي في الساق", percOptions()),
            item("no_surgery", "لا جراحة أو رض خلال الأسابيع الأربعة الماضية", percOptions())
        ),
        bands = listOf(
            band(0, 0, "PERC مستوفاة — عند احتمال قبلي منخفض يمكن استبعاد الانصمام الرئوي دون تصوير أو D-dimer إضافي"),
            band(1, 8, "PERC غير مستوفاة — لا تستبعد الانصمام؛ اتبع المسار التشخيصي المعتاد")
        )
    ),

    // 17) MEWS — المجموع 0–14 (حدود 70 و40 مصححة وفق التقرير: أو أقل).
    ClinicalScoreDef(
        specId = "mews_score",
        resultLabel = "مجموع MEWS",
        items = listOf(
            item("sbp", "الضغط الانقباضي (mmHg)", listOf(opt("70 أو أقل", 3), opt("71–80", 2), opt("81–100", 1), opt("101–199", 0), opt("200 أو أكثر", 2))),
            item("hr", "النبض (/دقيقة)", listOf(opt("40 أو أقل", 2), opt("41–50", 1), opt("51–100", 0), opt("101–110", 1), opt("111–129", 2), opt("130 أو أكثر", 3))),
            item("rr", "معدل التنفس (/دقيقة)", listOf(opt("أقل من 9", 2), opt("9–14", 0), opt("15–20", 1), opt("21–29", 2), opt("30 أو أكثر", 3))),
            item("temp", "الحرارة (°C)", listOf(opt("أقل من 35", 2), opt("35–38.4", 0), opt("38.5 أو أكثر", 2))),
            item("avpu", "الوعي AVPU", listOf(opt("يقظ", 0), opt("يستجيب للصوت", 1), opt("يستجيب للألم", 2), opt("لا يستجيب", 3)))
        ),
        bands = listOf(
            band(0, 2, "خطر منخفض — مراقبة روتينية"),
            band(3, 4, "زيادة وتيرة المراقبة وإبلاغ الطبيب"),
            band(5, 14, "تقييم عاجل وطلب مساعدة حرجة")
        ),
        extraNote = { chosen ->
            if (chosen.values.sum() < 5 && chosen.values.any { it == 3 })
                "ملاحظة: أي متغير منفرد يساوي 3 يستدعي وحده تقييماً عاجلاً وطلب مساعدة حرجة."
            else null
        }
    ),

    // 18) NEWS2 — المجموع 0–20. الإشباع بند بديل بين Scale 1 وScale 2
    // (مجموعة حصرية spo2)، والتصنيف عبر bandOverride لا عبر bands.
    ClinicalScoreDef(
        specId = "news2_score",
        resultLabel = "مجموع NEWS2",
        items = listOf(
            item("rr", "معدل التنفس (/دقيقة)", listOf(opt("8 أو أقل", 3), opt("9–11", 1), opt("12–20", 0), opt("21–24", 2), opt("25 أو أكثر", 3))),
            item("spo2_s1", "إشباع الأكسجين — Scale 1 (٪)", listOf(opt("91 أو أقل", 3), opt("92–93", 2), opt("94–95", 1), opt("96 أو أكثر", 0)), exclusiveGroup = "spo2"),
            item("spo2_s2", "إشباع الأكسجين — Scale 2 لمرضى احتباس CO2 بهدف 88–92٪ (٪)", listOf(opt("83 أو أقل", 3), opt("84–85", 2), opt("86–87", 1), opt("88–92، أو 93 أو أكثر على هواء الغرفة", 0), opt("93–94 على أكسجين", 1), opt("95–96 على أكسجين", 2), opt("97 أو أكثر على أكسجين", 3)), exclusiveGroup = "spo2"),
            item("oxygen", "الأكسجين", listOf(opt("هواء الغرفة", 0), opt("أي أكسجين إضافي", 2))),
            item("sbp", "الضغط الانقباضي (mmHg)", listOf(opt("90 أو أقل", 3), opt("91–100", 2), opt("101–110", 1), opt("111–219", 0), opt("220 أو أكثر", 3))),
            item("hr", "النبض (/دقيقة)", listOf(opt("40 أو أقل", 3), opt("41–50", 1), opt("51–90", 0), opt("91–110", 1), opt("111–130", 2), opt("131 أو أكثر", 3))),
            item("consciousness", "الوعي", listOf(opt("يقظ", 0), opt("ارتباك جديد أو استجابة للصوت أو للألم أو لا استجابة", 3))),
            item("temp", "الحرارة (°C)", listOf(opt("35.0 أو أقل", 3), opt("35.1–36.0", 1), opt("36.1–38.0", 0), opt("38.1–39.0", 1), opt("39.1 أو أكثر", 2)))
        ),
        bands = emptyList(),
        bandOverride = { total, chosen ->
            when {
                total >= 7 -> "خطر مرتفع — تقييم طارئ وفريق استجابة حرجة"
                total >= 5 || chosen.values.any { it == 3 } -> "خطر متوسط — مراجعة عاجلة من طبيب"
                else -> "خطر سريري منخفض"
            }
        }
    ),

    // 19) Centor المعدّل (McIsaac) — المجموع من -1 إلى 5.
    ClinicalScoreDef(
        specId = "centor_modified",
        resultLabel = "مجموع Centor المعدّل",
        items = listOf(
            item("exudate", "إفرازات لوزية", yn()),
            item("nodes", "عقد لمفية أمامية رقبية مؤلمة", yn()),
            item("fever", "حرارة أعلى من 38°", yn()),
            item("no_cough", "غياب السعال", yn()),
            item("age_adj", "تعديل العمر (McIsaac)", listOf(opt("3–14 سنة", 1), opt("15–44 سنة", 0), opt("45 سنة أو أكثر", -1)))
        ),
        bands = listOf(
            band(-1, 0, "احتمال عقدي منخفض جداً (حوالي 1–2٪) — لا حاجة لفحص أو مضاد حيوي عادة"),
            band(1, 1, "احتمال عقدي حوالي 5–10٪"),
            band(2, 2, "احتمال عقدي حوالي 11–17٪ (تقدير تقريبي يختلف بين الدراسات)"),
            band(3, 3, "احتمال عقدي حوالي 28–35٪ — يجرى اختبار عقدي"),
            band(4, 5, "احتمال عقدي حوالي 51–53٪ — يجرى اختبار عقدي قبل المضاد حسب الإرشادات")
        )
    )
).associateBy { it.specId }

private fun nihssLimbOptions(): List<ScoreOption> = listOf(
    opt("لا انجراف", 0),
    opt("انجراف", 1),
    opt("بعض الجهد ضد الجاذبية", 2),
    opt("لا جهد ضد الجاذبية", 3),
    opt("لا حركة", 4),
    opt("غير قابل للاختبار (UN) — يُوثّق ولا يُحتسب", 0, untestable = true)
)

private fun percOptions(): List<ScoreOption> = listOf(
    ScoreOption("ينطبق", 0),
    ScoreOption("لا ينطبق", 1)
)

val scoreSpecIds: Set<String> get() = clinicalScoreDefinitions.keys

/**
 * يقيّم مقياساً نقطياً من اختيارات المستخدم.
 * selections: itemKey -> optionIndex ضمن خيارات ذلك البند.
 *
 * - specId غير معروف، أو مفتاح بند غريب، أو فهرس خارج المجال: Rejected.
 * - أكثر من اختيار واحد داخل مجموعة حصرية واحدة (exclusiveGroup): Rejected.
 * - الاكتمال: كل البنود العادية مختارة + بند واحد بالضبط من كل مجموعة حصرية.
 * - المقاييس ذات المراحل (Ranson وRockall): تُكمل المراحل بالترتيب؛ مرحلة
 *   أولى ناقصة = Rejected، ومرحلة لاحقة ناقصة بعد اكتمال سابقاتها = نتيجة
 *   جزئية بمجموع آخر مرحلة مكتملة، واختيار من مرحلة لاحقة قبل إكمال سابقة
 *   = Rejected. (إن بدأ المستخدم مرحلة لاحقة ولم يكملها، تُحتسب النتيجة
 *   الجزئية من المراحل المكتملة فقط ولا تُحتسب اختياراتها الجزئية.)
 * - خيار untestable (UN في NIHSS) يُعدّ مُجاباً بنقاط 0 ويُوثَّق في النص.
 */
fun evaluateScoreSpec(specId: String, selections: Map<String, Int>): CalcOutcome {
    val def = clinicalScoreDefinitions[specId]
        ?: return CalcOutcome.Rejected("مقياس غير معروف: $specId")
    val itemsByKey = def.items.associateBy { it.key }

    for (key in selections.keys) {
        if (key !in itemsByKey) {
            return CalcOutcome.Rejected("بند غير معروف في هذا المقياس: $key")
        }
    }
    for ((key, idx) in selections) {
        val item = itemsByKey.getValue(key)
        if (idx !in item.options.indices) {
            return CalcOutcome.Rejected("اختيار غير صالح للبند «${item.labelAr}»")
        }
    }

    val exclusiveGroups = def.items.mapNotNull { it.exclusiveGroup }.distinct()
    for (group in exclusiveGroups) {
        val chosenInGroup = def.items.count { it.exclusiveGroup == group && it.key in selections }
        if (chosenInGroup > 1) {
            return CalcOutcome.Rejected("لا يمكن اختيار أكثر من بند واحد من مجموعة بنود بديلة — اختر بنداً واحداً فقط")
        }
    }

    fun pointsOf(key: String): Int {
        val item = itemsByKey.getValue(key)
        return item.options[selections.getValue(key)].points
    }

    if (def.stages.isNotEmpty()) {
        fun stageItems(stage: String): List<ScoreItem> = def.items.filter { it.stage == stage }
        fun stageComplete(stage: String): Boolean {
            val items = stageItems(stage)
            val plainOk = items.filter { it.exclusiveGroup == null }.all { it.key in selections }
            val groupsOk = items.mapNotNull { it.exclusiveGroup }.distinct().all { g ->
                items.count { it.exclusiveGroup == g && it.key in selections } == 1
            }
            return plainOk && groupsOk
        }
        fun stageTouched(stage: String): Boolean = stageItems(stage).any { it.key in selections }

        for (i in def.stages.indices) {
            val stage = def.stages[i]
            if (stageComplete(stage)) continue
            val laterTouched = def.stages.drop(i + 1).any { stageTouched(it) }
            if (laterTouched) {
                return CalcOutcome.Rejected("اختيار من مرحلة لاحقة قبل إكمال المرحلة السابقة — أكمل التقييم بالترتيب")
            }
            if (i == 0) {
                return CalcOutcome.Rejected("أكمل التقييم")
            }
            val completedStages = def.stages.subList(0, i)
            val partialTotal = def.items
                .filter { it.stage != null && it.stage in completedStages && it.key in selections }
                .sumOf { pointsOf(it.key) }
            val lastStage = completedStages.last()
            val lastLabel = def.stageLabels[lastStage] ?: lastStage
            val sb = StringBuilder("نتيجة جزئية — مجموع ")
                .append(lastLabel).append(" = ").append(partialTotal)
            def.partialHint?.let { sb.append(" — ").append(it) }
            if (stageTouched(stage)) {
                // The user has started the incomplete stage: say plainly
                // that those partial picks are not in this subtotal yet,
                // instead of silently ignoring them.
                val stageLabel = def.stageLabels[stage] ?: stage
                sb.append(" — تنبيه: اختيارات «").append(stageLabel)
                    .append("» الحالية جزئية ولم تُحتسب في هذا المجموع؛ أكملها لعرض الدرجة الكاملة.")
            }
            return CalcOutcome.Value(sb.toString())
        }
    }

    val plainMissing = def.items.any { it.exclusiveGroup == null && it.key !in selections }
    val groupMissing = exclusiveGroups.any { g ->
        def.items.count { it.exclusiveGroup == g && it.key in selections } != 1
    }
    if (plainMissing || groupMissing) {
        return CalcOutcome.Rejected("لم يكتمل التقييم بعد — أكمل كل البنود لعرض الدرجة")
    }

    val pointsByKey: Map<String, Int> = selections.keys.associateWith { pointsOf(it) }
    val total = pointsByKey.values.sum()
    val bandText: String? = def.bandOverride?.invoke(total, pointsByKey)
        ?: def.bands.firstOrNull { total in it.minTotal..it.maxTotal }?.text

    val sb = StringBuilder(def.resultLabel).append(" = ").append(total)
    if (!bandText.isNullOrBlank()) sb.append(" — ").append(bandText)
    def.extraNote?.invoke(pointsByKey)?.let { sb.append(" ").append(it) }

    val unLabels = def.items.filter { item ->
        val idx = selections[item.key]
        idx != null && item.options[idx].untestable
    }.map { it.labelAr }
    if (unLabels.isNotEmpty()) {
        sb.append(" بنود غير قابلة للاختبار (UN) وُثّقت ولم تُحتسب في المجموع: ")
            .append(unLabels.joinToString("، "))
    }
    return CalcOutcome.Value(sb.toString())
}
