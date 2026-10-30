package sy.medpro.watani.ui.clinical

// SET 1 — renal, metabolic, endocrine & cardiac calculators.
// Data-driven spec only: it does not redefine any existing app class and it
// duplicates none of the existing calculators (ICU infusions, Parkland,
// Sanford renal antibiotics, Harriet Lane, Wells, GCS, CHA2DS2-VASc) nor
// CURB-65, HEART or Cockcroft-Gault. All formulas use the units stated in
// each formula string. Worked examples were computed numerically (see report).

data class CalcInputSpec(val key: String, val labelAr: String, val unit: String, val min: Double?, val max: Double?, val default: Double?)
data class CalcSpec(val id: String, val titleAr: String, val formula: String, val inputs: List<CalcInputSpec>, val interpretation: String, val source: String, val workedExample: String)

val calcSpecsSet1: List<CalcSpec> = listOf(
    CalcSpec(
        id = "egfr_ckd_epi_2021",
        titleAr = "معدل الترشيح الكبيبي المقدر CKD-EPI 2021 (كرياتينين، بلا عرق)",
        formula = "eGFR = 142 × min(Scr/κ, 1)^α × max(Scr/κ, 1)^-1.200 × 0.9938^Age × 1.012 إن كانت أنثى؛ حيث κ = 0.7 للإناث و0.9 للذكور، وα = -0.241 للإناث و-0.302 للذكور. Scr بوحدة mg/dL، والعمر بالسنوات، والناتج بوحدة mL/min/1.73 m². للبالغين فقط، والكرياتينين في حالة مستقرة.",
        inputs = listOf(
            CalcInputSpec("scr", "كرياتينين المصل", "mg/dL", 0.1, 15.0, 1.2),
            CalcInputSpec("age", "العمر", "سنة", 18.0, 110.0, 60.0),
            CalcInputSpec("female", "أنثى؟ (1 = نعم، 0 = لا)", "بدون وحدة", 0.0, 1.0, 0.0)
        ),
        interpretation = "تصنيف KDIGO: G1 ≥ 90 طبيعي أو مرتفع؛ G2 60-89 انخفاض خفيف؛ G3a 45-59 انخفاض خفيف إلى متوسط؛ G3b 30-44 انخفاض متوسط إلى شديد؛ G4 15-29 انخفاض شديد؛ G5 < 15 فشل كلوي. القيمة وحدها لا تشخص مرض الكلى المزمن؛ يلزم استمرار الشذوذ أكثر من 3 أشهر مع مؤشرات أخرى.",
        source = "Inker et al., N Engl J Med 2021 (CKD-EPI 2021 creatinine, race-free); KDIGO 2024 CKD Guideline",
        workedExample = "ذكر عمره 60 سنة، Scr = 1.2 mg/dL: Scr/κ = 1.2/0.9 = 1.333؛ min = 1، max^-1.200 = 1.333^-1.200 = 0.708؛ 0.9938^60 = 0.688؛ eGFR = 142 × 1 × 0.708 × 0.688 = 69.2 mL/min/1.73 m² (G2)."
    ),
    CalcSpec(
        id = "corrected_calcium",
        titleAr = "الكالسيوم المصحح بالألبومين",
        formula = "الكالسيوم المصحح (mg/dL) = الكالسيوم الكلي المقاس (mg/dL) + 0.8 × (4.0 - الألبومين (g/dL)). صالح فقط عندما يكون الألبومين منخفضا والكالسيوم الكلي مقاسا بوحدة mg/dL؛ الكالسيوم المتأين هو المرجع عند الشك.",
        inputs = listOf(
            CalcInputSpec("ca", "الكالسيوم الكلي", "mg/dL", 4.0, 16.0, 8.0),
            CalcInputSpec("albumin", "الألبومين", "g/dL", 1.0, 6.0, 2.5)
        ),
        interpretation = "المرجع الطبيعي للكالسيوم المصحح: 8.5-10.5 mg/dL. أقل من 8.5 نقص كلس الدم؛ أكثر من 10.5 فرط كلس الدم؛ أكثر من 12 فرط شديد يستدعي تقييما عاجلا. لا تستخدم المعادلة إن كان الألبومين طبيعيا أو عند اضطراب التوازن الحمضي القلوي الشديد؛ قس الكالسيوم المتأين حينها.",
        source = "Payne et al., Br Med J 1973 (albumin-adjusted calcium); standard laboratory reference intervals",
        workedExample = "كالسيوم كلي 8.0 mg/dL، ألبومين 2.5 g/dL: المصحح = 8.0 + 0.8 × (4.0 - 2.5) = 8.0 + 1.2 = 9.2 mg/dL (طبيعي رغم انخفاض الكلي ظاهريا)."
    ),
    CalcSpec(
        id = "anion_gap",
        titleAr = "الفجوة الأنيونية",
        formula = "الفجوة الأنيونية (mEq/L) = الصوديوم (mEq/L) - (الكلوريد (mEq/L) + البيكربونات (mEq/L)). بلا بوتاسيوم في هذه الصيغة.",
        inputs = listOf(
            CalcInputSpec("na", "الصوديوم", "mEq/L", 100.0, 180.0, 140.0),
            CalcInputSpec("cl", "الكلوريد", "mEq/L", 70.0, 140.0, 104.0),
            CalcInputSpec("hco3", "البيكربونات", "mEq/L", 2.0, 45.0, 22.0)
        ),
        interpretation = "المرجع الشائع: 8-16 mEq/L (يختلف حسب المختبر وطريقة القياس). أكثر من 16: حماض استقلابي مرتفع الفجوة (حماض لكتيكي، حماض كيتوني، فشل كلوي، تسمم بالميثانول أو الإيثيلين غليكول أو الساليسيلات). ضمن المرجع مع انخفاض البيكربونات: حماض طبيعي الفجوة (فقد بيكربونات أو حماض أنبوبي كلوي).",
        source = "Kraut & Madias, Clin J Am Soc Nephrol 2010; standard acid-base teaching (Emmett & Narins)",
        workedExample = "Na 140، Cl 104، HCO3 22 mEq/L: الفجوة = 140 - (104 + 22) = 14 mEq/L (طبيعية)."
    ),
    CalcSpec(
        id = "anion_gap_albumin_corrected",
        titleAr = "الفجوة الأنيونية المصححة بالألبومين",
        formula = "الفجوة المصححة (mEq/L) = الفجوة الأنيونية المقاسة (mEq/L) + 2.5 × (4.0 - الألبومين (g/dL)). الألبومين أنيون رئيسي، فنقصه يخفض الفجوة المقاسة ويخفي حماضا مرتفع الفجوة.",
        inputs = listOf(
            CalcInputSpec("ag", "الفجوة الأنيونية المقاسة", "mEq/L", -5.0, 50.0, 14.0),
            CalcInputSpec("albumin", "الألبومين", "g/dL", 1.0, 6.0, 2.0)
        ),
        interpretation = "تطبق عليها حدود الفجوة نفسها: 8-16 mEq/L مرجع شائع، وأكثر من 16 مرتفعة. إن كانت الفجوة المقاسة طبيعية لكن المصححة مرتفعة، فالحماض مرتفع الفجوة كان مخفيا بنقص الألبومين ويجب البحث عن سببه.",
        source = "Figge et al., Crit Care Med 1998 (albumin correction, factor 2.5); Kraut & Madias 2010",
        workedExample = "فجوة مقاسة 14 mEq/L، ألبومين 2.0 g/dL: المصححة = 14 + 2.5 × (4.0 - 2.0) = 14 + 5.0 = 19 mEq/L (مرتفعة؛ حماض مرتفع الفجوة كان مخفيا)."
    ),
    CalcSpec(
        id = "serum_osmolality",
        titleAr = "الأسمولية المصلية المحسوبة",
        formula = "الأسمولية المحسوبة (mOsm/kg) = 2 × الصوديوم (mEq/L) + الغلوكوز (mg/dL)/18 + نيتروجين يوريا الدم BUN (mg/dL)/2.8.",
        inputs = listOf(
            CalcInputSpec("na", "الصوديوم", "mEq/L", 100.0, 180.0, 140.0),
            CalcInputSpec("glucose", "الغلوكوز", "mg/dL", 20.0, 1500.0, 90.0),
            CalcInputSpec("bun", "نيتروجين يوريا الدم BUN", "mg/dL", 1.0, 200.0, 14.0)
        ),
        interpretation = "المرجع: 275-295 mOsm/kg. أقل من 275 نقص أسمولية؛ أكثر من 295 فرط أسمولية؛ أكثر من 320 فرط شديد (كما في حالة فرط الأسمولية السكري). قارنها دائما بالأسمولية المقاسة لحساب فجوة الأسمولية.",
        source = "Standard clinical formula (Dorwart & Chalmers); StatPearls: Serum Osmolality",
        workedExample = "Na 140، غلوكوز 90، BUN 14: المحسوبة = 280 + 90/18 + 14/2.8 = 280 + 5 + 5 = 290 mOsm/kg (طبيعية)."
    ),
    CalcSpec(
        id = "osmolar_gap",
        titleAr = "فجوة الأسمولية",
        formula = "فجوة الأسمولية (mOsm/kg) = الأسمولية المقاسة مخبريا (mOsm/kg) - الأسمولية المحسوبة (mOsm/kg) من معادلة 2×Na + غلوكوز/18 + BUN/2.8.",
        inputs = listOf(
            CalcInputSpec("measured", "الأسمولية المقاسة", "mOsm/kg", 200.0, 450.0, 310.0),
            CalcInputSpec("calculated", "الأسمولية المحسوبة", "mOsm/kg", 200.0, 450.0, 290.0)
        ),
        interpretation = "المرجع: أقل من 10 mOsm/kg. أكثر من 10: وجود مواد ذات أسمولية نشطة غير محسوبة — فكر بالميثانول أو الإيثيلين غليكول أو الإيثانول أو الأيزوبروبانول أو المانيتول، مع الفجوة الأنيونية وغازات الدم. فجوة طبيعية لا تنفي التسمم إن كان الابتلاع قديما.",
        source = "Kraut & Kurtz, Clin J Am Soc Nephrol 2008 (osmolar gap in toxic alcohol poisoning)",
        workedExample = "مقاسة 310، محسوبة 290 mOsm/kg: الفجوة = 310 - 290 = 20 mOsm/kg (مرتفعة؛ ابحث عن كحول سام)."
    ),
    CalcSpec(
        id = "winters_formula",
        titleAr = "معادلة وينتر (pCO2 المتوقعة في الحماض الاستقلابي)",
        formula = "pCO2 المتوقعة (mmHg) = 1.5 × البيكربونات (mEq/L) + 8 ± 2. تطبق فقط في الحماض الاستقلابي البسيط (تعويض تنفسي).",
        inputs = listOf(
            CalcInputSpec("hco3", "البيكربونات", "mEq/L", 2.0, 35.0, 12.0)
        ),
        interpretation = "إن كانت pCO2 المقاسة ضمن النطاق المتوقع ± 2 mmHg فالتعويض التنفسي مناسب. أعلى من المتوقع: حماض تنفسي مرافق. أقل من المتوقع: قلاء تنفسي مرافق. لا تستخدمها في القلاء الاستقلابي أو الاضطرابات المختلطة الأخرى.",
        source = "Winter et al., and standard acid-base teaching (Albert et al.); StatPearls: Winter's Formula",
        workedExample = "HCO3 = 12 mEq/L: المتوقعة = 1.5 × 12 + 8 = 26 mmHg، النطاق 24-28 mmHg. pCO2 مقاسة 35 مثلا تعني حماضا تنفسيا مرافقا."
    ),
    CalcSpec(
        id = "delta_ratio",
        titleAr = "نسبة دلتا (Delta Ratio)",
        formula = "نسبة دلتا = (الفجوة الأنيونية المقاسة - 12) / (24 - البيكربونات المقاسة)، وكلاهما بوحدة mEq/L. الرقمان 12 و24 هما القيمتان الطبيعيتان المرجعيتان للفجوة والبيكربونات في هذه الصيغة.",
        inputs = listOf(
            CalcInputSpec("ag", "الفجوة الأنيونية", "mEq/L", 0.0, 50.0, 26.0),
            CalcInputSpec("hco3", "البيكربونات", "mEq/L", 2.0, 35.0, 12.0)
        ),
        interpretation = "أقل من 0.4: حماض مرتفع الفجوة + حماض طبيعي الفجوة معا. 0.4-0.8: مختلط، وغالبا مرتفع الفجوة + طبيعي الفجوة. 0.8-2.0: حماض مرتفع الفجوة نقي. أكثر من 2.0: حماض مرتفع الفجوة + قلاء استقلابي (أو بيكربونات أساسية مرتفعة سابقا). تطبق فقط عند وجود حماض مرتفع الفجوة.",
        source = "Wrenn, Ann Emerg Med 1990 (delta ratio); Kraut & Madias 2010",
        workedExample = "فجوة 26، HCO3 12: النسبة = (26 - 12)/(24 - 12) = 14/12 = 1.17 (حماض مرتفع الفجوة نقي)."
    ),
    CalcSpec(
        id = "corrected_sodium_hyperglycemia",
        titleAr = "الصوديوم المصحح في فرط سكر الدم",
        formula = "الصوديوم المصحح (mEq/L) = الصوديوم المقاس (mEq/L) + 1.6 × (الغلوكوز (mg/dL) - 100)/100. معامل كاتز 1.6؛ بعض المراجع تستخدم 2.4 عند الغلوكوز الشديد الارتفاع، لكن 1.6 هو المعامل الكلاسيكي المستخدم هنا.",
        inputs = listOf(
            CalcInputSpec("na", "الصوديوم المقاس", "mEq/L", 100.0, 180.0, 130.0),
            CalcInputSpec("glucose", "الغلوكوز", "mg/dL", 100.0, 2000.0, 500.0)
        ),
        interpretation = "الصوديوم المصحح هو القيمة التي تقارب الصوديوم الحقيقي بعد زوال فرط السكر. إن كان المصحح طبيعيا فالانخفاض المقاس كاذب (نقل ماء خارج الخلايا). إن كان المصحح منخفضا أيضا فهناك نقص صوديوم حقيقي مرافق. لا تصحح الصوديوم بمحلول ملحي بناء على القيمة المقاسة وحدها.",
        source = "Katz, N Engl J Med 1973 (correction factor 1.6)",
        workedExample = "Na مقاس 130، غلوكوز 500 mg/dL: المصحح = 130 + 1.6 × (500 - 100)/100 = 130 + 6.4 = 136.4 mEq/L (طبيعي تقريبا؛ الانخفاض المقاس كاذب غالبا)."
    ),
    CalcSpec(
        id = "fena",
        titleAr = "الإفراز الكسري للصوديوم FENa",
        formula = "FENa (%) = (صوديوم البول × كرياتينين المصل) / (صوديوم المصل × كرياتينين البول) × 100. صوديوم البول والمصل بوحدة mEq/L، والكرياتينين بوحدة mg/dL في كلا العينتين.",
        inputs = listOf(
            CalcInputSpec("una", "صوديوم البول", "mEq/L", 1.0, 300.0, 50.0),
            CalcInputSpec("pcr", "كرياتينين المصل", "mg/dL", 0.2, 15.0, 1.0),
            CalcInputSpec("pna", "صوديوم المصل", "mEq/L", 100.0, 180.0, 140.0),
            CalcInputSpec("ucr", "كرياتينين البول", "mg/dL", 10.0, 400.0, 100.0)
        ),
        interpretation = "أقل من 1%: سبب قبل كلوي (نقص حجم، انخفاض التروية) في غياب المدرات. أكثر من 2%: نخر أنبوبي حاد غالبا. 1-2%: غير حاسم. تفقد مصداقيتها بعد المدرات أو في مرض الكلى المزمن أو البيلة السكرية؛ استخدم FEUrea حينها.",
        source = "Espinel, JAMA 1976 (FENa); standard nephrology teaching",
        workedExample = "UNa 50، PCr 1.0، PNa 140، UCr 100: FENa = (50 × 1.0)/(140 × 100) × 100 = 50/14000 × 100 = 0.36% (قبل كلوي)."
    ),
    CalcSpec(
        id = "feurea",
        titleAr = "الإفراز الكسري لليوريا FEUrea",
        formula = "FEUrea (%) = (يوريا البول × كرياتينين المصل) / (يوريا المصل × كرياتينين البول) × 100. اليوريا (أو BUN) بنفس الوحدة في البول والمصل، والكرياتينين بوحدة mg/dL في كليهما.",
        inputs = listOf(
            CalcInputSpec("uurea", "يوريا البول", "mg/dL", 10.0, 2000.0, 300.0),
            CalcInputSpec("pcr", "كرياتينين المصل", "mg/dL", 0.2, 15.0, 1.0),
            CalcInputSpec("purea", "يوريا المصل", "mg/dL", 2.0, 200.0, 30.0),
            CalcInputSpec("ucr", "كرياتينين البول", "mg/dL", 10.0, 400.0, 100.0)
        ),
        interpretation = "أقل من 35%: سبب قبل كلوي، وتبقى مفيدة بعد المدرات. أكثر من 50%: نخر أنبوبي حاد غالبا. 35-50%: غير حاسم. هي البديل المفضل على FENa عند استخدام المدرات.",
        source = "Carvounis et al., Kidney Int 2002 (FEUrea)",
        workedExample = "يوريا البول 300، PCr 1.0، يوريا المصل 30، UCr 100: FEUrea = (300 × 1.0)/(30 × 100) × 100 = 300/3000 × 100 = 10% (قبل كلوي)."
    ),
    CalcSpec(
        id = "meld",
        titleAr = "نموذج مرض الكبد في مراحله الأخيرة MELD",
        formula = "MELD = 3.78 × ln(بيليروبين mg/dL) + 11.2 × ln(INR) + 9.57 × ln(كرياتينين mg/dL) + 6.43. أي قيمة أقل من 1.0 ترفع إلى 1.0 قبل اللوغاريتم، والكرياتينين يسقف عند 4.0 mg/dL، ويعين 4.0 إن كان المريض على غسيل كلوي. الناتج يقرّب إلى عدد صحيح.",
        inputs = listOf(
            CalcInputSpec("bilirubin", "البيليروبين الكلي", "mg/dL", 0.1, 50.0, 2.0),
            CalcInputSpec("inr", "INR", "بدون وحدة", 0.5, 10.0, 1.5),
            CalcInputSpec("creatinine", "الكرياتينين", "mg/dL", 0.1, 15.0, 1.5)
        ),
        interpretation = "كلما ارتفع MELD ارتفعت وفيات 3 أشهر: أقل من 10 نحو 2%؛ 10-19 نحو 6%؛ 20-29 نحو 20%؛ 30-39 نحو 53%؛ 40 فأكثر نحو 71% (تقديرات تقريبية من الدراسات الأصلية). يستخدم لتحديد أولوية زراعة الكبد، ولا يغني عن التقييم السريري.",
        source = "Kamath et al., Hepatology 2001 (original MELD); Wiesner et al., Gastroenterology 2003",
        workedExample = "بيليروبين 2.0، INR 1.5، كرياتينين 1.5: MELD = 3.78×ln(2) + 11.2×ln(1.5) + 9.57×ln(1.5) + 6.43 = 2.62 + 4.54 + 3.88 + 6.43 = 17.47، أي MELD 17."
    ),
    CalcSpec(
        id = "meld_na",
        titleAr = "MELD-Na",
        formula = "MELD-Na = MELD + 1.32 × (137 - Na) - 0.033 × MELD × (137 - Na)، حيث Na هو صوديوم المصل (mEq/L) مقيدا بين 125 و137 قبل الحساب. الناتج يقرّب إلى عدد صحيح، ويسقف عند 40.",
        inputs = listOf(
            CalcInputSpec("meld", "MELD", "نقطة", 6.0, 40.0, 20.0),
            CalcInputSpec("na", "صوديوم المصل", "mEq/L", 110.0, 160.0, 130.0)
        ),
        interpretation = "يضيف الصوديوم لأنه يتنبأ بالوفاة مستقلا عن MELD، خاصة في MELD المنخفض والمتوسط. يفسر بنفس مقياس MELD للوفاة والأولوية، وهو المعتمد في تخصيص الكبد في الولايات المتحدة منذ 2016 (ثم استبدل جزئيا بـ MELD 3.0 لاحقا، لكن MELD-Na ما زال واسع الاستخدام).",
        source = "Biggins et al., Gastroenterology 2006 (MELD-Na); OPTN allocation policy 2016",
        workedExample = "MELD 20، Na 130: MELD-Na = 20 + 1.32×7 - 0.033×20×7 = 20 + 9.24 - 4.62 = 24.62، أي 25."
    ),
    CalcSpec(
        id = "child_pugh",
        titleAr = "تصنيف تشايلد-بو (Child-Pugh)",
        formula = "مجموع 5 بنود، كل منها 1-3 نقاط: بيليروبين (mg/dL): أقل من 2 = 1، 2-3 = 2، أكثر من 3 = 3. ألبومين (g/dL): أكثر من 3.5 = 1، 2.8-3.5 = 2، أقل من 2.8 = 3. INR: أقل من 1.7 = 1، 1.7-2.3 = 2، أكثر من 2.3 = 3. استسقاء: لا يوجد = 1، خفيف = 2، متوسط إلى شديد = 3. اعتلال دماغي: لا يوجد = 1، درجة I-II = 2، درجة III-IV = 3. أدخل درجتي الاستسقاء والاعتلال كأرقام 1 أو 2 أو 3.",
        inputs = listOf(
            CalcInputSpec("bilirubin", "البيليروبين الكلي", "mg/dL", 0.1, 50.0, 2.5),
            CalcInputSpec("albumin", "الألبومين", "g/dL", 1.0, 6.0, 3.2),
            CalcInputSpec("inr", "INR", "بدون وحدة", 0.5, 10.0, 1.9),
            CalcInputSpec("ascites", "درجة الاستسقاء (1 = لا يوجد، 2 = خفيف، 3 = متوسط-شديد)", "نقطة", 1.0, 3.0, 2.0),
            CalcInputSpec("encephalopathy", "درجة الاعتلال الدماغي (1 = لا يوجد، 2 = I-II، 3 = III-IV)", "نقطة", 1.0, 3.0, 1.0)
        ),
        interpretation = "المجموع 5-6: فئة A (تعويض جيد)، بقاء سنة واحدة نحو 100%. 7-9: فئة B (قصور متوسط)، بقاء سنة واحدة نحو 80%. 10-15: فئة C (قصور شديد)، بقاء سنة واحدة نحو 45%. يستخدم لتقدير شدة تشمع الكبد ومخاطر الجراحة، ولا يحدد أولوية الزراعة وحده.",
        source = "Child & Turcotte 1964; Pugh et al., Br J Surg 1973",
        workedExample = "بيليروبين 2.5 (نقطتان)، ألبومين 3.2 (نقطتان)، INR 1.9 (نقطتان)، استسقاء خفيف (نقطتان)، لا اعتلال (نقطة): المجموع 9 = فئة B."
    ),
    CalcSpec(
        id = "fib4",
        titleAr = "مؤشر FIB-4 لتليف الكبد",
        formula = "FIB-4 = (العمر بالسنوات × AST (U/L)) / (الصفيحات (×10^9/L) × الجذر التربيعي لـ ALT (U/L)).",
        inputs = listOf(
            CalcInputSpec("age", "العمر", "سنة", 18.0, 100.0, 50.0),
            CalcInputSpec("ast", "AST", "U/L", 5.0, 2000.0, 60.0),
            CalcInputSpec("alt", "ALT", "U/L", 5.0, 2000.0, 40.0),
            CalcInputSpec("platelets", "الصفيحات", "×10^9/L", 10.0, 800.0, 200.0)
        ),
        interpretation = "أقل من 1.30: احتمال تليف متقدم منخفض. 1.30-2.67: غير حاسم، يلزم اختبار إضافي. أكثر من 2.67: احتمال تليف متقدم مرتفع. العتبات أقل دقة تحت عمر 35 وفوق 65 سنة؛ لا يشخص وحده ويتأثر بأي ارتفاع عابر في الترانساميناز.",
        source = "Sterling et al., Hepatology 2006 (FIB-4 in HCV/HIV coinfection); EASL 2021 non-invasive tests guideline",
        workedExample = "عمر 50، AST 60، ALT 40، صفيحات 200: FIB-4 = (50 × 60)/(200 × √40) = 3000/(200 × 6.325) = 3000/1264.9 = 2.37 (غير حاسم)."
    ),
    CalcSpec(
        id = "apri",
        titleAr = "نسبة AST إلى الصفيحات APRI",
        formula = "APRI = ((AST (U/L) / الحد الأعلى الطبيعي لـ AST (U/L)) / الصفيحات (×10^9/L)) × 100.",
        inputs = listOf(
            CalcInputSpec("ast", "AST", "U/L", 5.0, 2000.0, 80.0),
            CalcInputSpec("uln", "الحد الأعلى الطبيعي لـ AST", "U/L", 20.0, 60.0, 40.0),
            CalcInputSpec("platelets", "الصفيحات", "×10^9/L", 10.0, 800.0, 150.0)
        ),
        interpretation = "0.5 أو أقل: تليف مهم مستبعد غالبا. 0.5-1.5: منطقة غير حاسمة. أكثر من 1.5: تليف مهم مرجح. أكثر من 2.0: تشمع مرجح. يعتمد على الحد الأعلى الطبيعي لمختبرك؛ أدخل قيمة مختبرك لا قيمة افتراضية إن اختلفت.",
        source = "Wai et al., Hepatology 2003 (APRI)",
        workedExample = "AST 80، الحد الأعلى 40، صفيحات 150: APRI = ((80/40)/150) × 100 = (2/150) × 100 = 1.33 (منطقة غير حاسمة)."
    ),
    CalcSpec(
        id = "qtc_bazett",
        titleAr = "QT المصحح QTc — بازيت (Bazett)",
        formula = "QTc (ms) = QT (ms) / الجذر التربيعي لـ RR (بالثواني). RR بالثواني = 60 / سرعة القلب (نبضة/دقيقة) إن لم تكن RR معروفة.",
        inputs = listOf(
            CalcInputSpec("qt", "فترة QT", "ms", 200.0, 700.0, 400.0),
            CalcInputSpec("rr", "فترة RR", "ثانية", 0.3, 2.0, 0.8)
        ),
        interpretation = "طبيعي: أقل من 450 ms للرجال وأقل من 470 ms للنساء. مطول: أكثر من 470-480 ms يستدعي مراجعة الأدوية والشوارد. أكثر من 500 ms: خطر مرتفع لاضطراب تورساد دي بوانت. معادلة بازيت تبالغ في التصحيح عند السرعات العالية والمنخفضة؛ عندها فضل فرايدريشيا.",
        source = "Bazett, Heart 1920; AHA/ACCF/HRS 2009 QT recommendations",
        workedExample = "QT 400 ms، RR 0.8 ثانية: QTc = 400/√0.8 = 400/0.894 = 447 ms (طبيعي لرجل، قريب من الحد الأعلى)."
    ),
    CalcSpec(
        id = "qtc_fridericia",
        titleAr = "QT المصحح QTc — فرايدريشيا (Fridericia)",
        formula = "QTc (ms) = QT (ms) / RR^(1/3)، حيث RR بالثواني. RR بالثواني = 60 / سرعة القلب (نبضة/دقيقة) إن لم تكن RR معروفة.",
        inputs = listOf(
            CalcInputSpec("qt", "فترة QT", "ms", 200.0, 700.0, 400.0),
            CalcInputSpec("rr", "فترة RR", "ثانية", 0.3, 2.0, 0.8)
        ),
        interpretation = "نفس حدود بازيت: أقل من 450 ms للرجال وأقل من 470 ms للنساء طبيعي، وأكثر من 500 ms خطر مرتفع لتورساد. فرايدريشيا أدق من بازيت عند سرعة القلب السريعة أو البطيئة، وهي المفضلة في الدراسات الدوائية.",
        source = "Fridericia, Acta Med Scand 1920; AHA/ACCF/HRS 2009 QT recommendations",
        workedExample = "QT 400 ms، RR 0.8 ثانية: QTc = 400/0.8^(1/3) = 400/0.928 = 431 ms (طبيعي)."
    ),
    CalcSpec(
        id = "map",
        titleAr = "متوسط الضغط الشرياني MAP",
        formula = "MAP (mmHg) = الضغط الانبساطي + (الضغط الانقباضي - الضغط الانبساطي)/3 = (الانقباضي + 2 × الانبساطي)/3.",
        inputs = listOf(
            CalcInputSpec("sbp", "الضغط الانقباضي", "mmHg", 50.0, 300.0, 120.0),
            CalcInputSpec("dbp", "الضغط الانبساطي", "mmHg", 20.0, 200.0, 80.0)
        ),
        interpretation = "الطبيعي نحو 70-110 mmHg. أقل من 65 mmHg: خطر نقص تروية الأعضاء، وهو الحد الأدنى المستهدف عادة في الصدمة. أكثر من 110 mmHg: ارتفاع ضغط متوسط يستدعي تقييما. MAP وحده لا يكفي؛ يقيم مع النتاج القلبي والتروية العضوية.",
        source = "Standard physiology; Surviving Sepsis Campaign 2021 (MAP target ≥ 65 mmHg in shock)",
        workedExample = "120/80 mmHg: MAP = 80 + (120 - 80)/3 = 80 + 13.3 = 93.3 mmHg (طبيعي)."
    ),
    CalcSpec(
        id = "bmi",
        titleAr = "مؤشر كتلة الجسم BMI",
        formula = "BMI (kg/m²) = الوزن (kg) / (الطول بالمتر)².",
        inputs = listOf(
            CalcInputSpec("weight", "الوزن", "kg", 2.0, 400.0, 80.0),
            CalcInputSpec("height", "الطول", "m", 0.4, 2.5, 1.75)
        ),
        interpretation = "تصنيف منظمة الصحة العالمية: أقل من 18.5 نقص وزن؛ 18.5-24.9 طبيعي؛ 25-29.9 زيادة وزن؛ 30-34.9 بدانة درجة أولى؛ 35-39.9 درجة ثانية؛ 40 فأكثر درجة ثالثة. لا يميز الكتلة العضلية عن الدهون، ولا يصلح وحده للأطفال أو الرياضيين أو الحوامل.",
        source = "WHO BMI classification",
        workedExample = "وزن 80 kg، طول 1.75 m: BMI = 80/(1.75²) = 80/3.0625 = 26.1 kg/m² (زيادة وزن)."
    ),
    CalcSpec(
        id = "bsa_mosteller",
        titleAr = "مساحة سطح الجسم BSA — موستيلر (Mosteller)",
        formula = "BSA (m²) = الجذر التربيعي لـ (الطول (cm) × الوزن (kg) / 3600).",
        inputs = listOf(
            CalcInputSpec("height", "الطول", "cm", 40.0, 250.0, 175.0),
            CalcInputSpec("weight", "الوزن", "kg", 2.0, 400.0, 80.0)
        ),
        interpretation = "متوسط البالغ نحو 1.7-1.9 m². تستخدم لتحديد جرعات العلاج الكيميائي وبعض الأدوية ومؤشرات القلب. لا توجد نطاقات مرضية لها بذاتها؛ القيمة مدخل لحسابات أخرى.",
        source = "Mosteller, N Engl J Med 1987",
        workedExample = "طول 175 cm، وزن 80 kg: BSA = √(175 × 80/3600) = √3.889 = 1.97 m²."
    ),
    CalcSpec(
        id = "ibw_devine",
        titleAr = "الوزن المثالي IBW — ديفاين (Devine)",
        formula = "للرجال: IBW (kg) = 50 + 2.3 × (الطول بالبوصة - 60). للنساء: IBW (kg) = 45.5 + 2.3 × (الطول بالبوصة - 60). الطول بالبوصة = الطول (cm)/2.54. الصيغة مبنية على طول 5 أقدام (60 بوصة) أساسا.",
        inputs = listOf(
            CalcInputSpec("height", "الطول", "cm", 100.0, 250.0, 175.0),
            CalcInputSpec("female", "أنثى؟ (1 = نعم، 0 = لا)", "بدون وحدة", 0.0, 1.0, 0.0)
        ),
        interpretation = "وزن مرجعي لحساب الجرعات (كالمضادات الحيوية المحبة للماء) ولحجم التهوية الوقائي، وليس هدفا صحيا بذاته. إن كان الوزن الفعلي أقل من المثالي يستخدم الفعلي عادة للجرعات؛ وإن كان أعلى بكثير يستخدم الوزن المعدل.",
        source = "Devine, Drug Intell Clin Pharm 1974",
        workedExample = "رجل طوله 175 cm = 68.90 بوصة: IBW = 50 + 2.3 × (68.90 - 60) = 50 + 20.46 = 70.5 kg."
    ),
    CalcSpec(
        id = "adjbw",
        titleAr = "الوزن المعدل Adjusted Body Weight",
        formula = "AdjBW (kg) = IBW (kg) + 0.4 × (الوزن الفعلي (kg) - IBW (kg)). يطبق عندما يكون الوزن الفعلي أكبر من المثالي؛ وإن كان الفعلي أقل من المثالي فالوزن الفعلي هو المستخدم عادة لا المعدل.",
        inputs = listOf(
            CalcInputSpec("ibw", "الوزن المثالي IBW", "kg", 20.0, 150.0, 70.5),
            CalcInputSpec("actual", "الوزن الفعلي", "kg", 20.0, 400.0, 100.0)
        ),
        interpretation = "يستخدم لجرعات الأدوية المحبة للماء عند البدانة، كبديل وسط بين المثالي والفعلي. المعامل 0.4 هو الشائع؛ بعض البروتوكولات تستخدم 0.3-0.4 حسب الدواء، فاتبع بروتوكول الدواء نفسه.",
        source = "Standard clinical dosing practice (Winter et al., Basic Clinical Pharmacokinetics)",
        workedExample = "IBW 70.5 kg، فعلي 100 kg: AdjBW = 70.5 + 0.4 × (100 - 70.5) = 70.5 + 11.8 = 82.3 kg."
    ),
    CalcSpec(
        id = "ca_phos_product",
        titleAr = "حاصل الكالسيوم × الفوسفور",
        formula = "الحاصل (mg²/dL²) = الكالسيوم الكلي (mg/dL) × الفوسفور (mg/dL). يستخدم الكالسيوم الكلي في الصيغة الكلاسيكية؛ عند نقص الألبومين استخدم الكالسيوم المصحح واذكر ذلك.",
        inputs = listOf(
            CalcInputSpec("ca", "الكالسيوم الكلي", "mg/dL", 4.0, 16.0, 9.5),
            CalcInputSpec("phos", "الفوسفور", "mg/dL", 0.5, 15.0, 4.0)
        ),
        interpretation = "الهدف في مرض الكلى المزمن والغسيل: أقل من 55 mg²/dL². أكثر من 55: خطر مرتفع للتكلس الوعائي والأنسجة الرخوة وفرط جارات الدرق الثانوي، ويستدعي خفض الفوسفور والكالسيوم. أكثر من 70: خطر شديد. هو مؤشر خطر لا تشخيص بذاته.",
        source = "KDOQI Clinical Practice Guidelines for Bone Metabolism in CKD 2003 (Ca × P < 55 mg²/dL² target)",
        workedExample = "كالسيوم 9.5، فوسفور 4.0 mg/dL: الحاصل = 9.5 × 4.0 = 38 mg²/dL² (ضمن الهدف)."
    ),
    CalcSpec(
        id = "free_water_deficit",
        titleAr = "عجز الماء الحر",
        formula = "عجز الماء الحر (L) = مجموع ماء الجسم TBW (L) × (الصوديوم المقاس (mEq/L)/140 - 1)، حيث TBW = الوزن (kg) × 0.6 للرجال و0.5 للنساء (0.5 و0.45 على التوالي عند كبار السن). يطبق في فرط صوديوم الدم فقط.",
        inputs = listOf(
            CalcInputSpec("weight", "الوزن", "kg", 2.0, 400.0, 70.0),
            CalcInputSpec("na", "الصوديوم المقاس", "mEq/L", 141.0, 200.0, 155.0),
            CalcInputSpec("tbw_factor", "معامل ماء الجسم (0.6 رجل، 0.5 امرأة)", "L/kg", 0.4, 0.7, 0.6)
        ),
        interpretation = "الناتج هو حجم الماء الحر اللازم نظريا لإعادة الصوديوم إلى 140، لا سرعة إعطائه. التعويض يجب أن يحترم حدود تصحيح الصوديوم (لا يتجاوز 8-10 mEq/L في 24 ساعة) وإلا خطر وذمة الدماغ، خاصة في فرط الصوديوم المزمن. أعد قياس الصوديوم والبول أثناء التعويض؛ الصيغة لا تحسب الفقد المستمر.",
        source = "Standard formula (Adrogué & Madias, N Engl J Med 2000, hypernatremia review)",
        workedExample = "رجل 70 kg (TBW = 42 L)، Na 155: العجز = 42 × (155/140 - 1) = 42 × 0.107 = 4.5 L ماء حر (يقسم على أكثر من يوم حسب حد التصحيح)."
    ),
    CalcSpec(
        id = "sodium_correction_limits",
        titleAr = "حدود تصحيح الصوديوم الآمنة",
        formula = "الهدف الآمن بعد 24 ساعة (mEq/L) = الصوديوم الحالي (mEq/L) + 8 في الحالات عالية الخطر (مزمن، سوء تغذية، إدمان كحول، مرض كبدي)، وبحد أقصى مطلق +10 في 24 ساعة في غيرها. وبعد 48 ساعة: لا يتجاوز +18 إجمالا. يطبق على نقص صوديوم الدم المزمن؛ النقص الحاد العرضي يصحح أسرع بإشراف مختص.",
        inputs = listOf(
            CalcInputSpec("na", "الصوديوم الحالي", "mEq/L", 100.0, 140.0, 115.0)
        ),
        interpretation = "تجاوز الحد يرفع خطر متلازمة إزالة الميالين التناضحية (ODS)، وهي إصابة دماغية قد تكون غير عكسية. إن ارتفع الصوديوم أسرع من الحد: أوقف التصحيح، وأعد خفضه بماء حر أو ديزموبريسين بإشراف مختص. الهدف ليس الوصول إلى الطبيعي سريعا بل عدم تجاوز السقف.",
        source = "Verbalis et al., Am J Med 2013 (US Expert Panel hyponatremia); European Clinical Practice Guideline on Hyponatraemia 2014",
        workedExample = "Na حالي 115 mEq/L: سقف 24 ساعة المحافظ = 115 + 8 = 123 mEq/L، والسقف المطلق = 125 mEq/L. الوصول إلى 140 في يوم واحد ممنوع."
    ),
    CalcSpec(
        id = "adrogue_madias",
        titleAr = "معادلة أدروغي-مادياس (تغير الصوديوم لكل لتر محلول)",
        formula = "تغير الصوديوم المتوقع لكل لتر (mEq/L) = (صوديوم المحلول (mEq/L) - صوديوم المصل (mEq/L)) / (مجموع ماء الجسم TBW (L) + 1). TBW = الوزن (kg) × 0.6 للرجال و0.5 للنساء.",
        inputs = listOf(
            CalcInputSpec("infusate_na", "صوديوم المحلول الوريدي", "mEq/L", 0.0, 1000.0, 154.0),
            CalcInputSpec("serum_na", "صوديوم المصل", "mEq/L", 100.0, 180.0, 130.0),
            CalcInputSpec("tbw", "مجموع ماء الجسم TBW", "L", 5.0, 80.0, 42.0)
        ),
        interpretation = "الناتج موجب إن كان المحلول أعلى صوديوم من المصل (يرفع الصوديوم) وسالب إن كان أخفض (يخفضه). اضربه بحجم المحلول باللترات لتقدير التغير الكلي، ثم قارنه بسقف التصحيح الآمن. تقدير فقط: لا يحسب فقد البول أو الماء المستمر، فيجب إعادة القياس المتكرر.",
        source = "Adrogué & Madias, N Engl J Med 2000",
        workedExample = "محلول ملحي نظامي 154، مصل 130، TBW 42 L: التغير = (154 - 130)/(42 + 1) = 24/43 = 0.56 mEq/L لكل لتر."
    ),
    CalcSpec(
        id = "transferrin_saturation",
        titleAr = "إشباع الترانسفيرين",
        formula = "إشباع الترانسفيرين (%) = (حديد المصل (µg/dL) / السعة الكلية لربط الحديد TIBC (µg/dL)) × 100. يجب أن تكون الوحدتان متطابقتين.",
        inputs = listOf(
            CalcInputSpec("iron", "حديد المصل", "µg/dL", 5.0, 400.0, 60.0),
            CalcInputSpec("tibc", "السعة الكلية لربط الحديد TIBC", "µg/dL", 100.0, 600.0, 300.0)
        ),
        interpretation = "المرجع الشائع: 20-50%. أقل من 20% مع فيريتين منخفض: عوز حديد. أقل من 20% مع فيريتين طبيعي أو مرتفع: فكر بفقر دم الأمراض المزمنة. أكثر من 50%: فرط حديد أو داء ترسب الأصبغة الدموية يستدعي تأكيدا جينيا وسريريا. لا يفسر وحده بلا فيريتين وتعداد دم.",
        source = "Standard iron studies interpretation; ACG Guideline on Iron Deficiency Anemia 2020",
        workedExample = "حديد 60، TIBC 300 µg/dL: الإشباع = (60/300) × 100 = 20% (عند الحد الأدنى الطبيعي)."
    ),
    CalcSpec(
        id = "homa_ir",
        titleAr = "HOMA-IR (تقدير مقاومة الإنسولين — ليس تشخيصا)",
        formula = "HOMA-IR = (إنسولين الصيام (µU/mL) × غلوكوز الصيام (mg/dL)) / 405. بالوحدات الدولية: (إنسولين (µU/mL) × غلوكوز (mmol/L)) / 22.5. عينة صيام حقيقية شرط للصيغة.",
        inputs = listOf(
            CalcInputSpec("insulin", "إنسولين الصيام", "µU/mL", 1.0, 100.0, 10.0),
            CalcInputSpec("glucose", "غلوكوز الصيام", "mg/dL", 40.0, 400.0, 90.0)
        ),
        interpretation = "تحذير صريح: هذا تقدير بحثي وليس اختبارا تشخيصيا، وتختلف عتباته باختلاف المقايسة والسكان. شائعا: أقل من 1.0 حساسية جيدة؛ 1.0-2.5 منطقة رمادية؛ أكثر من 2.5 يرجح مقاومة إنسولين في كثير من المختبرات. لا يشخص السكري ولا متلازمة الأيض وحده، ولا يبنى عليه علاج منفرد.",
        source = "Matthews et al., Diabetologia 1985 (original HOMA model)",
        workedExample = "إنسولين صيام 10 µU/mL، غلوكوز صيام 90 mg/dL: HOMA-IR = (10 × 90)/405 = 900/405 = 2.22 (منطقة رمادية/ارتفاع خفيف، تقدير فقط)."
    ),
    CalcSpec(
        id = "ldl_friedewald",
        titleAr = "كولسترول LDL — معادلة فرايدوالد (Friedewald)",
        formula = "LDL (mg/dL) = الكولسترول الكلي (mg/dL) - HDL (mg/dL) - الغليسيريدات الثلاثية (mg/dL)/5. صالحة فقط عندما تكون الغليسيريدات أقل من 400 mg/dL وفي عينة صيام، وبلا اضطراب شحوم نادر.",
        inputs = listOf(
            CalcInputSpec("tc", "الكولسترول الكلي", "mg/dL", 50.0, 600.0, 200.0),
            CalcInputSpec("hdl", "HDL", "mg/dL", 10.0, 150.0, 50.0),
            CalcInputSpec("tg", "الغليسيريدات الثلاثية", "mg/dL", 10.0, 390.0, 150.0)
        ),
        interpretation = "تصنيف ATP III: أقل من 100 مثالي؛ 100-129 قريب من المثالي؛ 130-159 مرتفع حديا؛ 160-189 مرتفع؛ 190 فأكثر مرتفع جدا. إن كانت الغليسيريدات 400 فأكثر فالمعادلة غير صالحة ويجب قياس LDL مباشرة أو استخدام معادلة بديلة معتمدة.",
        source = "Friedewald et al., Clin Chem 1972; NCEP ATP III",
        workedExample = "كلي 200، HDL 50، غليسيريدات 150 mg/dL: LDL = 200 - 50 - 150/5 = 200 - 50 - 30 = 120 mg/dL (قريب من المثالي)."
    )
)
