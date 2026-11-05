package sy.medpro.watani.ui.clinical

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Integrated compute layer for the descriptive calc specs (set1/set2).
// Computes only the closed-form arithmetic specs from set1.kt / set2.kt.
// Every formula below is the one named in its spec's formula string, and
// every branch was verified against that spec's own workedExample
// (see the task report). Point-based clinical scores have no arithmetic
// compute: they need a score UI, not this layer.
// No Android imports, no side effects.
//
// عقد النتيجة: evaluateCalcSpec هي نقطة الحساب الوحيدة. النجاح Value بنص
// جاهز للعرض، والفشل Rejected بسبب عربي واضح يظهر للمستخدم — لا فراغ صامت.
// computeCalcSpec غلاف توافق قديم: Value ← النص، Rejected ← null.
//
// قاعدة التصنيف والعرض الموحّدة (ملزمة لكل فرع له تصنيف band وقيمة معروضة):
// التصنيف يُشتق من القيمة المقرّبة بنفس دقة العرض، لا من القيمة الخام.
// أي: rv = roundTo(القيمة الخام، عدد أرقام العرض)، والـband يُشتق من rv،
// والمعروض هو fmt(rv). بهذا لا يعرض التطبيق «12» مصنّفة «مرتفعة» أبداً.

sealed interface CalcOutcome {
    data class Value(val text: String) : CalcOutcome
    data class Rejected(val reason: String) : CalcOutcome
}

private fun fmt(v: Double, digits: Int): String =
    String.format(java.util.Locale.US, "%.${digits}f", v)

// Rounds to the given number of decimal digits (same precision fmt shows)
// and normalizes -0.0 to 0.0. Uses HALF_UP (away from zero at .5) so the
// result always matches what String.format in fmt() prints — Kotlin's
// roundToInt ties toward +infinity instead, which disagreed with fmt on
// exact negative halves (e.g. -12.5 displayed -13 but rounded to -12).
// floor/abs also avoids roundToInt's Int saturation on huge values.
private fun roundTo(v: Double, digits: Int): Double {
    val factor = 10.0.pow(digits)
    val scaled = v * factor
    val magnitude = kotlin.math.floor(kotlin.math.abs(scaled) + 0.5)
    val r = (if (scaled < 0) -magnitude else magnitude) / factor
    return if (r == 0.0) 0.0 else r
}

private fun missingInputs(): CalcOutcome =
    CalcOutcome.Rejected("أدخل جميع القيم")

fun evaluateCalcSpec(specId: String, inputs: Map<String, Double>): CalcOutcome {
    // Any non-finite entered value invalidates the whole computation.
    if (inputs.values.any { !it.isFinite() }) {
        return CalcOutcome.Rejected("قيمة غير صالحة (NaN أو ما لا نهاية)")
    }
    return when (specId) {
        "egfr_ckd_epi_2021" -> {
            val scr = inputs["scr"] ?: return missingInputs()
            val age = inputs["age"] ?: return missingInputs()
            val female = (inputs["female"] ?: return missingInputs()) >= 0.5
            if (scr <= 0.0) return CalcOutcome.Rejected("الكرياتينين يجب أن يكون أكبر من صفر لحساب eGFR")
            val kappa = if (female) 0.7 else 0.9
            val alpha = if (female) -0.241 else -0.302
            val ratio = scr / kappa
            var v = 142.0 * minOf(ratio, 1.0).pow(alpha) *
                maxOf(ratio, 1.0).pow(-1.200) *
                0.9938.pow(age)
            if (female) v *= 1.012
            val rv = roundTo(v, 1)
            val band = when {
                rv >= 90.0 -> "G1 (طبيعي أو مرتفع)"
                rv >= 60.0 -> "G2 (انخفاض خفيف)"
                rv >= 45.0 -> "G3a (انخفاض خفيف إلى متوسط)"
                rv >= 30.0 -> "G3b (انخفاض متوسط إلى شديد)"
                rv >= 15.0 -> "G4 (انخفاض شديد)"
                else -> "G5 (فشل كلوي)"
            }
            CalcOutcome.Value("eGFR = ${fmt(rv, 1)} mL/min/1.73 m² — $band")
        }
        "corrected_calcium" -> {
            val ca = inputs["ca"] ?: return missingInputs()
            val albumin = inputs["albumin"] ?: return missingInputs()
            if (albumin >= 4.0) return CalcOutcome.Rejected("الألبومين طبيعي أو مرتفع (≥ 4.0 g/dL): معادلة التصحيح غير صالحة هنا — قس الكالسيوم المتأين")
            val v = ca + 0.8 * (4.0 - albumin)
            val rv = roundTo(v, 1)
            val band = when {
                rv < 8.5 -> "نقص كلس الدم"
                rv <= 10.2 -> "طبيعي"
                rv <= 12.0 -> "فرط كلس الدم"
                else -> "فرط كلس شديد يستدعي تقييماً عاجلاً"
            }
            CalcOutcome.Value("الكالسيوم المصحح = ${fmt(rv, 1)} mg/dL — $band")
        }
        "anion_gap" -> {
            val na = inputs["na"] ?: return missingInputs()
            val cl = inputs["cl"] ?: return missingInputs()
            val hco3 = inputs["hco3"] ?: return missingInputs()
            val v = na - (cl + hco3)
            val rv = roundTo(v, 0)
            val band = when {
                rv < 8.0 -> "أقل من المرجع"
                rv <= 12.0 -> "طبيعية"
                else -> "مرتفعة (حماض استقلابي مرتفع الفجوة)"
            }
            CalcOutcome.Value("الفجوة الأنيونية = ${fmt(rv, 0)} mEq/L — $band")
        }
        "anion_gap_albumin_corrected" -> {
            val ag = inputs["ag"] ?: return missingInputs()
            val albumin = inputs["albumin"] ?: return missingInputs()
            if (albumin >= 4.0) return CalcOutcome.Rejected("الألبومين طبيعي أو مرتفع (≥ 4.0 g/dL): التصحيح بالألبومين يطبق عند نقص الألبومين فقط")
            val v = ag + 2.5 * (4.0 - albumin)
            val rv = roundTo(v, 1)
            val band = when {
                rv < 8.0 -> "أقل من المرجع"
                rv <= 12.0 -> "طبيعية"
                else -> "مرتفعة (حماض مرتفع الفجوة كان مخفياً بنقص الألبومين)"
            }
            CalcOutcome.Value("الفجوة المصححة = ${fmt(rv, 1)} mEq/L — $band")
        }
        "serum_osmolality" -> {
            val na = inputs["na"] ?: return missingInputs()
            val glucose = inputs["glucose"] ?: return missingInputs()
            val bun = inputs["bun"] ?: return missingInputs()
            val v = 2.0 * na + glucose / 18.0 + bun / 2.8
            val rv = roundTo(v, 0)
            val band = when {
                rv < 275.0 -> "نقص أسمولية"
                rv <= 295.0 -> "طبيعية"
                rv <= 320.0 -> "فرط أسمولية"
                else -> "فرط أسمولية شديد"
            }
            CalcOutcome.Value("الأسمولية المحسوبة = ${fmt(rv, 0)} mOsm/kg — $band")
        }
        "osmolar_gap" -> {
            val measured = inputs["measured"] ?: return missingInputs()
            val calculated = inputs["calculated"] ?: return missingInputs()
            val v = measured - calculated
            val rv = roundTo(v, 0)
            val band = when {
                rv < 10.0 -> "طبيعية"
                rv == 10.0 -> "عند الحد الأعلى"
                else -> "مرتفعة (ابحث عن كحول سام)"
            }
            CalcOutcome.Value("فجوة الأسمولية = ${fmt(rv, 0)} mOsm/kg — $band")
        }
        "winters_formula" -> {
            val hco3 = inputs["hco3"] ?: return missingInputs()
            if (hco3 >= 24.0) return CalcOutcome.Rejected("معادلة وينتر للحماض الاستقلابي فقط؛ البيكربونات ≥ 24 ليست حماضًا")
            val v = 1.5 * hco3 + 8.0
            CalcOutcome.Value("pCO2 المتوقعة = ${fmt(v, 0)} mmHg (النطاق ${fmt(v - 2.0, 0)}–${fmt(v + 2.0, 0)} mmHg)")
        }
        "delta_ratio" -> {
            val ag = inputs["ag"] ?: return missingInputs()
            val hco3 = inputs["hco3"] ?: return missingInputs()
            if (ag <= 12.0) return CalcOutcome.Rejected("لا حماض مرتفع الفجوة — الفجوة الأنيونية ≤ 12، فنسبة دلتا لا تُطبق")
            if (hco3 >= 24.0) return CalcOutcome.Rejected("البيكربونات غير منخفض (≥ 24) — نسبة دلتا لا تُطبق")
            val denom = 24.0 - hco3
            if (denom <= 0.0) return CalcOutcome.Rejected("البيكربونات تساوي 24 أو أكثر — مقام نسبة دلتا غير صالح")
            val v = (ag - 12.0) / denom
            val rv = roundTo(v, 2)
            val band = when {
                rv < 0.4 -> "حماض مرتفع الفجوة + حماض طبيعي الفجوة معاً"
                rv < 0.8 -> "مختلط (غالباً مرتفع الفجوة + طبيعي الفجوة)"
                rv <= 2.0 -> "حماض مرتفع الفجوة نقي"
                else -> "حماض مرتفع الفجوة + قلاء استقلابي"
            }
            CalcOutcome.Value("نسبة دلتا = ${fmt(rv, 2)} — $band")
        }
        "corrected_sodium_hyperglycemia" -> {
            val na = inputs["na"] ?: return missingInputs()
            val glucose = inputs["glucose"] ?: return missingInputs()
            if (glucose <= 100.0) return CalcOutcome.Rejected("التصحيح يطبق عند فرط سكر الدم فقط (غلوكوز > 100 mg/dL)")
            val v = na + 1.6 * (glucose - 100.0) / 100.0
            CalcOutcome.Value("الصوديوم المصحح = ${fmt(v, 1)} mEq/L — قارنه بمرجع مختبرك؛ إن كان طبيعياً فالانخفاض المقاس كاذب غالباً")
        }
        "fena" -> {
            val una = inputs["una"] ?: return missingInputs()
            val pcr = inputs["pcr"] ?: return missingInputs()
            val pna = inputs["pna"] ?: return missingInputs()
            val ucr = inputs["ucr"] ?: return missingInputs()
            if (pcr <= 0.0) return CalcOutcome.Rejected("كرياتينين المصل يجب أن يكون أكبر من صفر")
            if (pna <= 0.0) return CalcOutcome.Rejected("صوديوم المصل يجب أن يكون أكبر من صفر")
            if (ucr <= 0.0) return CalcOutcome.Rejected("كرياتينين البول يجب أن يكون أكبر من صفر")
            val v = (una * pcr) / (pna * ucr) * 100.0
            val rv = roundTo(v, 2)
            val band = when {
                rv < 1.0 -> "قبل كلوي"
                rv <= 2.0 -> "غير حاسم"
                else -> "نخر أنبوبي حاد غالباً"
            }
            CalcOutcome.Value("FENa = ${fmt(rv, 2)}% — $band")
        }
        "feurea" -> {
            val uurea = inputs["uurea"] ?: return missingInputs()
            val pcr = inputs["pcr"] ?: return missingInputs()
            val purea = inputs["purea"] ?: return missingInputs()
            val ucr = inputs["ucr"] ?: return missingInputs()
            if (pcr <= 0.0) return CalcOutcome.Rejected("كرياتينين المصل يجب أن يكون أكبر من صفر")
            if (purea <= 0.0) return CalcOutcome.Rejected("يوريا المصل يجب أن تكون أكبر من صفر")
            if (ucr <= 0.0) return CalcOutcome.Rejected("كرياتينين البول يجب أن يكون أكبر من صفر")
            val v = (uurea * pcr) / (purea * ucr) * 100.0
            val rv = roundTo(v, 0)
            val band = when {
                rv < 35.0 -> "قبل كلوي"
                rv <= 50.0 -> "غير حاسم"
                else -> "نخر أنبوبي حاد غالباً"
            }
            CalcOutcome.Value("FEUrea = ${fmt(rv, 0)}% — $band")
        }
        "meld" -> {
            val bili = inputs["bilirubin"] ?: return missingInputs()
            val inr = inputs["inr"] ?: return missingInputs()
            val creat = inputs["creatinine"] ?: return missingInputs()
            val dialysis = (inputs["dialysis"] ?: return missingInputs()) >= 0.5
            if (bili <= 0.0) return CalcOutcome.Rejected("البيليروبين يجب أن يكون أكبر من صفر (تُستخدم قيمته في لوغاريتم)")
            if (inr <= 0.0) return CalcOutcome.Rejected("INR يجب أن يكون أكبر من صفر (تُستخدم قيمته في لوغاريتم)")
            if (creat <= 0.0) return CalcOutcome.Rejected("الكرياتينين يجب أن يكون أكبر من صفر (تُستخدم قيمته في لوغاريتم)")
            // On dialysis the effective creatinine is set to 4.0 by definition.
            val effCreat = if (dialysis) 4.0 else creat
            val b = maxOf(bili, 1.0)
            val i = maxOf(inr, 1.0)
            val c = minOf(maxOf(effCreat, 1.0), 4.0)
            val raw = 3.78 * ln(b) + 11.2 * ln(i) + 9.57 * ln(c) + 6.43
            val v = minOf(maxOf(raw.roundToInt(), 6), 40)
            val band = when {
                v < 10 -> "وفيات 3 أشهر نحو 2%"
                v <= 19 -> "وفيات 3 أشهر نحو 6%"
                v <= 29 -> "وفيات 3 أشهر نحو 20%"
                v <= 39 -> "وفيات 3 أشهر نحو 53%"
                else -> "وفيات 3 أشهر نحو 71%"
            }
            CalcOutcome.Value("MELD = $v — $band")
        }
        "meld_na" -> {
            val meld = inputs["meld"] ?: return missingInputs()
            val na = inputs["na"] ?: return missingInputs()
            val naC = minOf(maxOf(na, 125.0), 137.0)
            val diff = 137.0 - naC
            val raw = meld + 1.32 * diff - 0.033 * meld * diff
            val v = minOf(raw, 40.0).roundToInt()
            val band = when {
                v < 10 -> "وفيات 3 أشهر نحو 2% (بمقياس MELD)"
                v <= 19 -> "وفيات 3 أشهر نحو 6% (بمقياس MELD)"
                v <= 29 -> "وفيات 3 أشهر نحو 20% (بمقياس MELD)"
                v <= 39 -> "وفيات 3 أشهر نحو 53% (بمقياس MELD)"
                else -> "وفيات 3 أشهر نحو 71% (بمقياس MELD)"
            }
            CalcOutcome.Value("MELD-Na = $v — $band")
        }
        "fib4" -> {
            val age = inputs["age"] ?: return missingInputs()
            val ast = inputs["ast"] ?: return missingInputs()
            val alt = inputs["alt"] ?: return missingInputs()
            val platelets = inputs["platelets"] ?: return missingInputs()
            if (alt <= 0.0) return CalcOutcome.Rejected("ALT يجب أن يكون أكبر من صفر (يُستخدم تحت الجذر التربيعي وفي المقام)")
            if (platelets <= 0.0) return CalcOutcome.Rejected("الصفيحات يجب أن تكون أكبر من صفر (تُستخدم في المقام)")
            val v = (age * ast) / (platelets * sqrt(alt))
            val rv = roundTo(v, 2)
            val band = when {
                rv < 1.30 -> "احتمال تليف متقدم منخفض"
                rv <= 2.67 -> "غير حاسم، يلزم اختبار إضافي"
                else -> "احتمال تليف متقدم مرتفع"
            }
            CalcOutcome.Value("FIB-4 = ${fmt(rv, 2)} — $band")
        }
        "apri" -> {
            val ast = inputs["ast"] ?: return missingInputs()
            val uln = inputs["uln"] ?: return missingInputs()
            val platelets = inputs["platelets"] ?: return missingInputs()
            if (uln <= 0.0) return CalcOutcome.Rejected("الحد الأعلى الطبيعي لـ AST يجب أن يكون أكبر من صفر (يُستخدم في المقام)")
            if (platelets <= 0.0) return CalcOutcome.Rejected("الصفيحات يجب أن تكون أكبر من صفر (تُستخدم في المقام)")
            val v = ((ast / uln) / platelets) * 100.0
            val rv = roundTo(v, 2)
            val band = when {
                rv > 2.0 -> "تشمع مرجح"
                rv > 1.5 -> "تليف مهم مرجح"
                rv > 0.5 -> "منطقة غير حاسمة"
                else -> "تليف مهم مستبعد غالباً"
            }
            CalcOutcome.Value("APRI = ${fmt(rv, 2)} — $band")
        }
        "qtc_bazett" -> {
            val qt = inputs["qt"] ?: return missingInputs()
            val rr = inputs["rr"] ?: return missingInputs()
            if (rr <= 0.0) return CalcOutcome.Rejected("فترة RR يجب أن تكون أكبر من صفر")
            val v = qt / sqrt(rr)
            val rv = roundTo(v, 0)
            val band = when {
                rv > 500.0 -> "خطر مرتفع لتورساد دي بوانت"
                rv >= 470.0 -> "مطول"
                rv >= 450.0 -> "قريب من الحد الأعلى (طبيعي للنساء حتى 470)"
                else -> "طبيعي"
            }
            CalcOutcome.Value("QTc (Bazett) = ${fmt(rv, 0)} ms — $band")
        }
        "qtc_fridericia" -> {
            val qt = inputs["qt"] ?: return missingInputs()
            val rr = inputs["rr"] ?: return missingInputs()
            if (rr <= 0.0) return CalcOutcome.Rejected("فترة RR يجب أن تكون أكبر من صفر")
            val v = qt / rr.pow(1.0 / 3.0)
            val rv = roundTo(v, 0)
            val band = when {
                rv > 500.0 -> "خطر مرتفع لتورساد دي بوانت"
                rv >= 470.0 -> "مطول"
                rv >= 450.0 -> "قريب من الحد الأعلى (طبيعي للنساء حتى 470)"
                else -> "طبيعي"
            }
            CalcOutcome.Value("QTc (Fridericia) = ${fmt(rv, 0)} ms — $band")
        }
        "map" -> {
            val sbp = inputs["sbp"] ?: return missingInputs()
            val dbp = inputs["dbp"] ?: return missingInputs()
            val v = dbp + (sbp - dbp) / 3.0
            val rv = roundTo(v, 1)
            val band = when {
                rv < 65.0 -> "أقل من 65: خطر نقص تروية الأعضاء"
                rv < 70.0 -> "دون الطبيعي"
                rv <= 110.0 -> "طبيعي"
                else -> "مرتفع"
            }
            CalcOutcome.Value("MAP = ${fmt(rv, 1)} mmHg — $band")
        }
        "bmi" -> {
            val weight = inputs["weight"] ?: return missingInputs()
            val height = inputs["height"] ?: return missingInputs()
            if (height <= 0.0) return CalcOutcome.Rejected("الطول يجب أن يكون أكبر من صفر")
            val v = weight / (height * height)
            val rv = roundTo(v, 1)
            val band = when {
                rv < 18.5 -> "نقص وزن"
                rv < 25.0 -> "طبيعي"
                rv < 30.0 -> "زيادة وزن"
                rv < 35.0 -> "بدانة درجة أولى"
                rv < 40.0 -> "بدانة درجة ثانية"
                else -> "بدانة درجة ثالثة"
            }
            CalcOutcome.Value("BMI = ${fmt(rv, 1)} kg/m² — $band")
        }
        "bsa_mosteller" -> {
            val height = inputs["height"] ?: return missingInputs()
            val weight = inputs["weight"] ?: return missingInputs()
            if (height < 0.0 || weight < 0.0) return CalcOutcome.Rejected("الطول والوزن يجب ألا يكونا قيمتين سالبتين")
            val v = sqrt(height * weight / 3600.0)
            CalcOutcome.Value("BSA (Mosteller) = ${fmt(v, 2)} m²")
        }
        "ibw_devine" -> {
            val height = inputs["height"] ?: return missingInputs()
            val female = (inputs["female"] ?: return missingInputs()) >= 0.5
            val inches = height / 2.54
            val base = if (female) 45.5 else 50.0
            val v = base + 2.3 * (inches - 60.0)
            CalcOutcome.Value("IBW (Devine) = ${fmt(v, 1)} kg")
        }
        "adjbw" -> {
            val ibw = inputs["ibw"] ?: return missingInputs()
            val actual = inputs["actual"] ?: return missingInputs()
            if (actual <= ibw) {
                CalcOutcome.Value("الوزن الفعلي (${fmt(actual, 1)} kg) لا يتجاوز المثالي — يُستخدم الوزن الفعلي عادة للجرعات بدل الوزن المعدل في هذه الحالة")
            } else {
                val v = ibw + 0.4 * (actual - ibw)
                CalcOutcome.Value("الوزن المعدل = ${fmt(v, 1)} kg")
            }
        }
        "ca_phos_product" -> {
            val ca = inputs["ca"] ?: return missingInputs()
            val phos = inputs["phos"] ?: return missingInputs()
            val v = ca * phos
            val rv = roundTo(v, 0)
            val band = when {
                rv < 55.0 -> "ضمن الهدف"
                rv <= 70.0 -> "أعلى من الهدف: خطر تكلس"
                else -> "خطر شديد"
            }
            CalcOutcome.Value("حاصل الكالسيوم × الفوسفور = ${fmt(rv, 0)} mg²/dL² — $band")
        }
        "free_water_deficit" -> {
            val weight = inputs["weight"] ?: return missingInputs()
            val na = inputs["na"] ?: return missingInputs()
            val factor = inputs["tbw_factor"] ?: return missingInputs()
            if (weight <= 0.0) return CalcOutcome.Rejected("الوزن يجب أن يكون أكبر من صفر")
            if (na <= 140.0) return CalcOutcome.Rejected("الصيغة لفرط صوديوم الدم فقط (Na > 140)؛ لا عجز ماء حر هنا")
            val tbw = weight * factor
            val v = tbw * (na / 140.0 - 1.0)
            CalcOutcome.Value("عجز الماء الحر = ${fmt(v, 1)} L (TBW = ${fmt(tbw, 1)} L) — يقسّم على أكثر من يوم حسب حد التصحيح الآمن")
        }
        "sodium_correction_limits" -> {
            val na = inputs["na"] ?: return missingInputs()
            val conservative = na + 8.0
            val absolute = na + 10.0
            val h48 = na + 18.0
            CalcOutcome.Value("سقف 24 ساعة (محافظ، عالي الخطر) = ${fmt(conservative, 0)} mEq/L؛ السقف المطلق 24 ساعة = ${fmt(absolute, 0)} mEq/L؛ سقف 48 ساعة = ${fmt(h48, 0)} mEq/L")
        }
        "adrogue_madias" -> {
            val infNa = inputs["infusate_na"] ?: return missingInputs()
            val serumNa = inputs["serum_na"] ?: return missingInputs()
            val tbw = inputs["tbw"] ?: return missingInputs()
            if (tbw + 1.0 <= 0.0) return CalcOutcome.Rejected("مجموع ماء الجسم TBW غير صالح: المقام (TBW + 1) يجب أن يكون أكبر من صفر")
            val v = (infNa - serumNa) / (tbw + 1.0)
            val rv = roundTo(v, 2)
            val dir = if (rv >= 0.0) "يرفع الصوديوم" else "يخفض الصوديوم"
            CalcOutcome.Value("تغير الصوديوم المتوقع = ${fmt(rv, 2)} mEq/L لكل لتر — $dir")
        }
        "transferrin_saturation" -> {
            val iron = inputs["iron"] ?: return missingInputs()
            val tibc = inputs["tibc"] ?: return missingInputs()
            if (tibc <= 0.0) return CalcOutcome.Rejected("السعة الكلية لربط الحديد TIBC يجب أن تكون أكبر من صفر (تُستخدم في المقام)")
            val v = (iron / tibc) * 100.0
            val rv = roundTo(v, 0)
            val band = when {
                rv < 20.0 -> "منخفض"
                rv <= 50.0 -> "طبيعي"
                else -> "مرتفع (فرط حديد محتمل)"
            }
            CalcOutcome.Value("إشباع الترانسفيرين = ${fmt(rv, 0)}% — $band")
        }
        "homa_ir" -> {
            val insulin = inputs["insulin"] ?: return missingInputs()
            val glucose = inputs["glucose"] ?: return missingInputs()
            val v = (insulin * glucose) / 405.0
            val rv = roundTo(v, 2)
            val band = when {
                rv < 1.0 -> "حساسية جيدة"
                rv <= 2.5 -> "منطقة رمادية"
                else -> "يرجح مقاومة إنسولين"
            }
            CalcOutcome.Value("HOMA-IR = ${fmt(rv, 2)} — $band (تقدير فقط، ليس تشخيصاً)")
        }
        "ldl_friedewald" -> {
            val tc = inputs["tc"] ?: return missingInputs()
            val hdl = inputs["hdl"] ?: return missingInputs()
            val tg = inputs["tg"] ?: return missingInputs()
            if (tg >= 400.0) return CalcOutcome.Rejected("الغليسيريدات ≥ 400 mg/dL: معادلة فرايدوالد غير صالحة — قس LDL مباشرة")
            val v = tc - hdl - tg / 5.0
            val rv = roundTo(v, 0)
            // Guard on the rounded (displayed) value: a raw result in (0, 0.5)
            // would otherwise display as "0 mg/dL — مثالي", the exact picture
            // this guard exists to prevent.
            if (rv <= 0.0) return CalcOutcome.Rejected("النتيجة المحسوبة ≤ 0: الصيغة غير صالحة لهذه القيم — قس LDL مباشرة")
            val band = when {
                rv < 100.0 -> "مثالي"
                rv < 130.0 -> "قريب من المثالي"
                rv < 160.0 -> "مرتفع حدياً"
                rv < 190.0 -> "مرتفع"
                else -> "مرتفع جداً"
            }
            CalcOutcome.Value("LDL = ${fmt(rv, 0)} mg/dL — $band")
        }
        "pao2_fio2_ratio" -> {
            val pao2 = inputs["pao2"] ?: return missingInputs()
            val fio2 = inputs["fio2"] ?: return missingInputs()
            if (fio2 <= 0.0) return CalcOutcome.Rejected("FiO2 يجب أن تكون أكبر من صفر (تُستخدم في المقام)")
            val v = pao2 / fio2
            val rv = roundTo(v, 0)
            val band = when {
                rv > 300.0 -> "طبيعي تقريباً"
                rv > 200.0 -> "خفيف"
                rv > 100.0 -> "متوسط"
                else -> "شديد"
            }
            CalcOutcome.Value("PaO2/FiO2 = ${fmt(rv, 0)} — $band")
        }
        "alveolar_gas_pao2" -> {
            val fio2 = inputs["fio2"] ?: return missingInputs()
            val paco2 = inputs["paco2"] ?: return missingInputs()
            val v = fio2 * 713.0 - paco2 / 0.8
            CalcOutcome.Value("PAO2 = ${fmt(v, 1)} mmHg")
        }
        "aa_gradient" -> {
            val fio2 = inputs["fio2"] ?: return missingInputs()
            val paco2 = inputs["paco2"] ?: return missingInputs()
            val pao2 = inputs["pao2"] ?: return missingInputs()
            val age = inputs["age"] ?: return missingInputs()
            val pao2Alv = fio2 * 713.0 - paco2 / 0.8
            val v = pao2Alv - pao2
            val normal = age / 4.0 + 4.0
            val rv = roundTo(v, 1)
            val rNormal = roundTo(normal, 1)
            val band = if (rv <= rNormal) "ضمن الطبيعي التقريبي" else "مرتفع"
            CalcOutcome.Value("التدرج A-a = ${fmt(rv, 1)} mmHg (PAO2 = ${fmt(pao2Alv, 1)} mmHg؛ الطبيعي التقريبي لعمر ${fmt(age, 0)} ≈ ${fmt(rNormal, 1)} mmHg) — $band")
        }
        "oxygenation_index" -> {
            val fio2Pct = inputs["fio2_pct"] ?: return missingInputs()
            val mapAirway = inputs["map"] ?: return missingInputs()
            val pao2 = inputs["pao2"] ?: return missingInputs()
            if (pao2 <= 0.0) return CalcOutcome.Rejected("PaO2 يجب أن يكون أكبر من صفر (يُستخدم في المقام)")
            val v = (fio2Pct * mapAirway) / pao2
            val rv = roundTo(v, 0)
            val band = when {
                rv < 10.0 -> "خفيف نسبياً"
                rv <= 20.0 -> "متوسط"
                else -> "شديد"
            }
            val extra = if (rv > 40.0) " (فوق 40 كان عتبة تقليدية للنظر في ECMO عند حديثي الولادة والأطفال ضمن سياق كامل)" else ""
            CalcOutcome.Value("مؤشر الأكسجة = ${fmt(rv, 0)} — $band$extra")
        }
        "maintenance_fluids_421" -> {
            val weight = inputs["weight"] ?: return missingInputs()
            val first10 = minOf(weight, 10.0) * 100.0
            val second10 = minOf(maxOf(weight - 10.0, 0.0), 10.0) * 50.0
            val rest = maxOf(weight - 20.0, 0.0) * 20.0
            val daily = first10 + second10 + rest
            val hourly = daily / 24.0
            CalcOutcome.Value("سوائل الصيانة = ${fmt(daily, 0)} مل/يوم (≈ ${fmt(hourly, 1)} مل/ساعة)")
        }
        "estimated_blood_volume" -> {
            val weight = inputs["weight"] ?: return missingInputs()
            val factor = inputs["factor"] ?: return missingInputs()
            val v = weight * factor
            CalcOutcome.Value("حجم الدم المقدر = ${fmt(v, 0)} مل (≈ ${fmt(v / 1000.0, 1)} لتر)")
        }
        "shock_index" -> {
            val hr = inputs["hr"] ?: return missingInputs()
            val sbp = inputs["sbp"] ?: return missingInputs()
            if (sbp <= 0.0) return CalcOutcome.Rejected("الضغط الانقباضي يجب أن يكون أكبر من صفر (يُستخدم في المقام)")
            val v = hr / sbp
            val rv = roundTo(v, 2)
            val band = when {
                rv < 0.7 -> "طبيعي تقريباً"
                rv < 0.9 -> "حدودي ويستدعي انتباهاً"
                rv < 1.0 -> "غير طبيعي"
                else -> "خطر مرتفع واضح"
            }
            CalcOutcome.Value("مؤشر الصدمة = ${fmt(rv, 2)} — $band")
        }
        "reverse_shock_index" -> {
            val sbp = inputs["sbp"] ?: return missingInputs()
            val hr = inputs["hr"] ?: return missingInputs()
            if (hr <= 0.0) return CalcOutcome.Rejected("النبض يجب أن يكون أكبر من صفر (يُستخدم في المقام)")
            val v = sbp / hr
            val rv = roundTo(v, 2)
            val band = if (rv >= 1.0) "ضمن المقبول تقريباً" else "غير طبيعي"
            CalcOutcome.Value("مؤشر الصدمة المعكوس = ${fmt(rv, 2)} — $band")
        }
        // The 19 point-based clinical scores (child_pugh, qsofa,
        // sirs_criteria, sofa_score, nihss, spesi_simplified, perc_rule,
        // hasbled, padua_vte, timi_ua_nstemi, ranson_pancreatitis,
        // bisap_pancreatitis, glasgow_blatchford, rockall_bleeding,
        // alvarado_appendicitis, air_appendicitis, centor_modified,
        // mews_score, news2_score) are no longer descriptive-only: they are
        // computed by the dedicated score engine in scoreCompute.kt, and the
        // UI routes them to evaluateScoreSpec with per-item option
        // selections, never to this numeric layer. Reaching evaluateCalcSpec
        // with one of them (or with any unknown id) is therefore an error,
        // rejected explicitly below instead of returning a silent blank.
        else -> CalcOutcome.Rejected("هذه الحاسبة وصفية بلا حساب")
    }
}

// Backwards-compatible wrapper: Value ← its text, Rejected ← null.
fun computeCalcSpec(specId: String, inputs: Map<String, Double>): String? =
    when (val outcome = evaluateCalcSpec(specId, inputs)) {
        is CalcOutcome.Value -> outcome.text
        is CalcOutcome.Rejected -> null
    }
