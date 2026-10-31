package sy.medpro.watani.ui.clinical

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Integrated compute layer for the descriptive calc specs (set1/set2).
// Computes only the closed-form arithmetic specs from set1.kt / set2.kt.
// Every formula below is the one named in its spec's formula string, and
// every branch was verified against that spec's own workedExample
// (see the task report). Point-based clinical scores return null:
// they need a score UI, not arithmetic compute.
// No Android imports, no side effects.

private fun fmt(v: Double, digits: Int): String =
    String.format(java.util.Locale.US, "%.${digits}f", v)

fun computeCalcSpec(specId: String, inputs: Map<String, Double>): String? {
    return when (specId) {
        "egfr_ckd_epi_2021" -> {
            val scr = inputs["scr"] ?: return null
            val age = inputs["age"] ?: return null
            val female = (inputs["female"] ?: return null) >= 0.5
            if (scr <= 0.0) return null
            val kappa = if (female) 0.7 else 0.9
            val alpha = if (female) -0.241 else -0.302
            val ratio = scr / kappa
            var v = 142.0 * minOf(ratio, 1.0).pow(alpha) *
                maxOf(ratio, 1.0).pow(-1.200) *
                0.9938.pow(age)
            if (female) v *= 1.012
            val band = when {
                v >= 90.0 -> "G1 (طبيعي أو مرتفع)"
                v >= 60.0 -> "G2 (انخفاض خفيف)"
                v >= 45.0 -> "G3a (انخفاض خفيف إلى متوسط)"
                v >= 30.0 -> "G3b (انخفاض متوسط إلى شديد)"
                v >= 15.0 -> "G4 (انخفاض شديد)"
                else -> "G5 (فشل كلوي)"
            }
            "eGFR = ${fmt(v, 1)} mL/min/1.73 m² — $band"
        }
        "corrected_calcium" -> {
            val ca = inputs["ca"] ?: return null
            val albumin = inputs["albumin"] ?: return null
            val v = ca + 0.8 * (4.0 - albumin)
            val band = when {
                v < 8.5 -> "نقص كلس الدم"
                v <= 10.2 -> "طبيعي"
                v <= 12.0 -> "فرط كلس الدم"
                else -> "فرط كلس شديد يستدعي تقييماً عاجلاً"
            }
            "الكالسيوم المصحح = ${fmt(v, 1)} mg/dL — $band"
        }
        "anion_gap" -> {
            val na = inputs["na"] ?: return null
            val cl = inputs["cl"] ?: return null
            val hco3 = inputs["hco3"] ?: return null
            val v = na - (cl + hco3)
            val band = when {
                v < 8.0 -> "أقل من المرجع"
                v <= 12.0 -> "طبيعية"
                else -> "مرتفعة (حماض استقلابي مرتفع الفجوة)"
            }
            "الفجوة الأنيونية = ${fmt(v, 0)} mEq/L — $band"
        }
        "anion_gap_albumin_corrected" -> {
            val ag = inputs["ag"] ?: return null
            val albumin = inputs["albumin"] ?: return null
            val v = ag + 2.5 * (4.0 - albumin)
            val band = when {
                v < 8.0 -> "أقل من المرجع"
                v <= 12.0 -> "طبيعية"
                else -> "مرتفعة (حماض مرتفع الفجوة كان مخفياً بنقص الألبومين)"
            }
            "الفجوة المصححة = ${fmt(v, 1)} mEq/L — $band"
        }
        "serum_osmolality" -> {
            val na = inputs["na"] ?: return null
            val glucose = inputs["glucose"] ?: return null
            val bun = inputs["bun"] ?: return null
            val v = 2.0 * na + glucose / 18.0 + bun / 2.8
            val band = when {
                v < 275.0 -> "نقص أسمولية"
                v <= 295.0 -> "طبيعية"
                v <= 320.0 -> "فرط أسمولية"
                else -> "فرط أسمولية شديد"
            }
            "الأسمولية المحسوبة = ${fmt(v, 0)} mOsm/kg — $band"
        }
        "osmolar_gap" -> {
            val measured = inputs["measured"] ?: return null
            val calculated = inputs["calculated"] ?: return null
            val v = measured - calculated
            val band = when {
                v < 10.0 -> "طبيعية"
                v == 10.0 -> "عند الحد الأعلى"
                else -> "مرتفعة (ابحث عن كحول سام)"
            }
            "فجوة الأسمولية = ${fmt(v, 0)} mOsm/kg — $band"
        }
        "winters_formula" -> {
            val hco3 = inputs["hco3"] ?: return null
            val v = 1.5 * hco3 + 8.0
            "pCO2 المتوقعة = ${fmt(v, 0)} mmHg (النطاق ${fmt(v - 2.0, 0)}–${fmt(v + 2.0, 0)} mmHg)"
        }
        "delta_ratio" -> {
            val ag = inputs["ag"] ?: return null
            val hco3 = inputs["hco3"] ?: return null
            val denom = 24.0 - hco3
            if (denom == 0.0) return null
            val v = (ag - 12.0) / denom
            val band = when {
                v < 0.4 -> "حماض مرتفع الفجوة + حماض طبيعي الفجوة معاً"
                v < 0.8 -> "مختلط (غالباً مرتفع الفجوة + طبيعي الفجوة)"
                v <= 2.0 -> "حماض مرتفع الفجوة نقي"
                else -> "حماض مرتفع الفجوة + قلاء استقلابي"
            }
            "نسبة دلتا = ${fmt(v, 2)} — $band"
        }
        "corrected_sodium_hyperglycemia" -> {
            val na = inputs["na"] ?: return null
            val glucose = inputs["glucose"] ?: return null
            val v = na + 1.6 * (glucose - 100.0) / 100.0
            "الصوديوم المصحح = ${fmt(v, 1)} mEq/L — قارنه بمرجع مختبرك؛ إن كان طبيعياً فالانخفاض المقاس كاذب غالباً"
        }
        "fena" -> {
            val una = inputs["una"] ?: return null
            val pcr = inputs["pcr"] ?: return null
            val pna = inputs["pna"] ?: return null
            val ucr = inputs["ucr"] ?: return null
            if (pna == 0.0 || ucr == 0.0) return null
            val v = (una * pcr) / (pna * ucr) * 100.0
            val band = when {
                v < 1.0 -> "قبل كلوي"
                v <= 2.0 -> "غير حاسم"
                else -> "نخر أنبوبي حاد غالباً"
            }
            "FENa = ${fmt(v, 2)}% — $band"
        }
        "feurea" -> {
            val uurea = inputs["uurea"] ?: return null
            val pcr = inputs["pcr"] ?: return null
            val purea = inputs["purea"] ?: return null
            val ucr = inputs["ucr"] ?: return null
            if (purea == 0.0 || ucr == 0.0) return null
            val v = (uurea * pcr) / (purea * ucr) * 100.0
            val band = when {
                v < 35.0 -> "قبل كلوي"
                v <= 50.0 -> "غير حاسم"
                else -> "نخر أنبوبي حاد غالباً"
            }
            "FEUrea = ${fmt(v, 0)}% — $band"
        }
        "meld" -> {
            val bili = inputs["bilirubin"] ?: return null
            val inr = inputs["inr"] ?: return null
            val creat = inputs["creatinine"] ?: return null
            if (bili <= 0.0 || inr <= 0.0 || creat <= 0.0) return null
            val b = maxOf(bili, 1.0)
            val i = maxOf(inr, 1.0)
            val c = minOf(maxOf(creat, 1.0), 4.0)
            val raw = 3.78 * ln(b) + 11.2 * ln(i) + 9.57 * ln(c) + 6.43
            val v = raw.roundToInt()
            val band = when {
                v < 10 -> "وفيات 3 أشهر نحو 2%"
                v <= 19 -> "وفيات 3 أشهر نحو 6%"
                v <= 29 -> "وفيات 3 أشهر نحو 20%"
                v <= 39 -> "وفيات 3 أشهر نحو 53%"
                else -> "وفيات 3 أشهر نحو 71%"
            }
            "MELD = $v — $band"
        }
        "meld_na" -> {
            val meld = inputs["meld"] ?: return null
            val na = inputs["na"] ?: return null
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
            "MELD-Na = $v — $band"
        }
        "fib4" -> {
            val age = inputs["age"] ?: return null
            val ast = inputs["ast"] ?: return null
            val alt = inputs["alt"] ?: return null
            val platelets = inputs["platelets"] ?: return null
            if (alt <= 0.0 || platelets == 0.0) return null
            val v = (age * ast) / (platelets * sqrt(alt))
            val band = when {
                v < 1.30 -> "احتمال تليف متقدم منخفض"
                v <= 2.67 -> "غير حاسم، يلزم اختبار إضافي"
                else -> "احتمال تليف متقدم مرتفع"
            }
            "FIB-4 = ${fmt(v, 2)} — $band"
        }
        "apri" -> {
            val ast = inputs["ast"] ?: return null
            val uln = inputs["uln"] ?: return null
            val platelets = inputs["platelets"] ?: return null
            if (uln == 0.0 || platelets == 0.0) return null
            val v = ((ast / uln) / platelets) * 100.0
            val band = when {
                v > 2.0 -> "تشمع مرجح"
                v > 1.5 -> "تليف مهم مرجح"
                v > 0.5 -> "منطقة غير حاسمة"
                else -> "تليف مهم مستبعد غالباً"
            }
            "APRI = ${fmt(v, 2)} — $band"
        }
        "qtc_bazett" -> {
            val qt = inputs["qt"] ?: return null
            val rr = inputs["rr"] ?: return null
            if (rr <= 0.0) return null
            val v = qt / sqrt(rr)
            val band = when {
                v > 500.0 -> "خطر مرتفع لتورساد دي بوانت"
                v >= 470.0 -> "مطول"
                v >= 450.0 -> "قريب من الحد الأعلى (طبيعي للنساء حتى 470)"
                else -> "طبيعي"
            }
            "QTc (Bazett) = ${fmt(v, 0)} ms — $band"
        }
        "qtc_fridericia" -> {
            val qt = inputs["qt"] ?: return null
            val rr = inputs["rr"] ?: return null
            if (rr <= 0.0) return null
            val v = qt / rr.pow(1.0 / 3.0)
            val band = when {
                v > 500.0 -> "خطر مرتفع لتورساد دي بوانت"
                v >= 470.0 -> "مطول"
                v >= 450.0 -> "قريب من الحد الأعلى (طبيعي للنساء حتى 470)"
                else -> "طبيعي"
            }
            "QTc (Fridericia) = ${fmt(v, 0)} ms — $band"
        }
        "map" -> {
            val sbp = inputs["sbp"] ?: return null
            val dbp = inputs["dbp"] ?: return null
            val v = dbp + (sbp - dbp) / 3.0
            val band = when {
                v < 65.0 -> "أقل من 65: خطر نقص تروية الأعضاء"
                v < 70.0 -> "دون الطبيعي"
                v <= 110.0 -> "طبيعي"
                else -> "مرتفع"
            }
            "MAP = ${fmt(v, 1)} mmHg — $band"
        }
        "bmi" -> {
            val weight = inputs["weight"] ?: return null
            val height = inputs["height"] ?: return null
            if (height <= 0.0) return null
            val v = weight / (height * height)
            val band = when {
                v < 18.5 -> "نقص وزن"
                v < 25.0 -> "طبيعي"
                v < 30.0 -> "زيادة وزن"
                v < 35.0 -> "بدانة درجة أولى"
                v < 40.0 -> "بدانة درجة ثانية"
                else -> "بدانة درجة ثالثة"
            }
            "BMI = ${fmt(v, 1)} kg/m² — $band"
        }
        "bsa_mosteller" -> {
            val height = inputs["height"] ?: return null
            val weight = inputs["weight"] ?: return null
            if (height < 0.0 || weight < 0.0) return null
            val v = sqrt(height * weight / 3600.0)
            "BSA (Mosteller) = ${fmt(v, 2)} m²"
        }
        "ibw_devine" -> {
            val height = inputs["height"] ?: return null
            val female = (inputs["female"] ?: return null) >= 0.5
            val inches = height / 2.54
            val base = if (female) 45.5 else 50.0
            val v = base + 2.3 * (inches - 60.0)
            "IBW (Devine) = ${fmt(v, 1)} kg"
        }
        "adjbw" -> {
            val ibw = inputs["ibw"] ?: return null
            val actual = inputs["actual"] ?: return null
            val v = ibw + 0.4 * (actual - ibw)
            "الوزن المعدل = ${fmt(v, 1)} kg"
        }
        "ca_phos_product" -> {
            val ca = inputs["ca"] ?: return null
            val phos = inputs["phos"] ?: return null
            val v = ca * phos
            val band = when {
                v < 55.0 -> "ضمن الهدف"
                v <= 70.0 -> "أعلى من الهدف: خطر تكلس"
                else -> "خطر شديد"
            }
            "حاصل الكالسيوم × الفوسفور = ${fmt(v, 0)} mg²/dL² — $band"
        }
        "free_water_deficit" -> {
            val weight = inputs["weight"] ?: return null
            val na = inputs["na"] ?: return null
            val factor = inputs["tbw_factor"] ?: return null
            val tbw = weight * factor
            val v = tbw * (na / 140.0 - 1.0)
            "عجز الماء الحر = ${fmt(v, 1)} L (TBW = ${fmt(tbw, 1)} L) — يقسّم على أكثر من يوم حسب حد التصحيح الآمن"
        }
        "sodium_correction_limits" -> {
            val na = inputs["na"] ?: return null
            val conservative = na + 8.0
            val absolute = na + 10.0
            val h48 = na + 18.0
            "سقف 24 ساعة (محافظ، عالي الخطر) = ${fmt(conservative, 0)} mEq/L؛ السقف المطلق 24 ساعة = ${fmt(absolute, 0)} mEq/L؛ سقف 48 ساعة = ${fmt(h48, 0)} mEq/L"
        }
        "adrogue_madias" -> {
            val infNa = inputs["infusate_na"] ?: return null
            val serumNa = inputs["serum_na"] ?: return null
            val tbw = inputs["tbw"] ?: return null
            if (tbw + 1.0 == 0.0) return null
            val v = (infNa - serumNa) / (tbw + 1.0)
            val dir = if (v >= 0.0) "يرفع الصوديوم" else "يخفض الصوديوم"
            "تغير الصوديوم المتوقع = ${fmt(v, 2)} mEq/L لكل لتر — $dir"
        }
        "transferrin_saturation" -> {
            val iron = inputs["iron"] ?: return null
            val tibc = inputs["tibc"] ?: return null
            if (tibc == 0.0) return null
            val v = (iron / tibc) * 100.0
            val band = when {
                v < 20.0 -> "منخفض"
                v <= 50.0 -> "طبيعي"
                else -> "مرتفع (فرط حديد محتمل)"
            }
            "إشباع الترانسفيرين = ${fmt(v, 0)}% — $band"
        }
        "homa_ir" -> {
            val insulin = inputs["insulin"] ?: return null
            val glucose = inputs["glucose"] ?: return null
            val v = (insulin * glucose) / 405.0
            val band = when {
                v < 1.0 -> "حساسية جيدة"
                v <= 2.5 -> "منطقة رمادية"
                else -> "يرجح مقاومة إنسولين"
            }
            "HOMA-IR = ${fmt(v, 2)} — $band (تقدير فقط، ليس تشخيصاً)"
        }
        "ldl_friedewald" -> {
            val tc = inputs["tc"] ?: return null
            val hdl = inputs["hdl"] ?: return null
            val tg = inputs["tg"] ?: return null
            if (tg >= 400.0) return null
            val v = tc - hdl - tg / 5.0
            val band = when {
                v < 100.0 -> "مثالي"
                v < 130.0 -> "قريب من المثالي"
                v < 160.0 -> "مرتفع حدياً"
                v < 190.0 -> "مرتفع"
                else -> "مرتفع جداً"
            }
            "LDL = ${fmt(v, 0)} mg/dL — $band"
        }
        "pao2_fio2_ratio" -> {
            val pao2 = inputs["pao2"] ?: return null
            val fio2 = inputs["fio2"] ?: return null
            if (fio2 == 0.0) return null
            val v = pao2 / fio2
            val band = when {
                v > 300.0 -> "طبيعي تقريباً"
                v >= 200.0 -> "خفيف"
                v >= 100.0 -> "متوسط"
                else -> "شديد"
            }
            val boundary = if (v == 200.0) " (على حد الخفيف/المتوسط)" else ""
            "PaO2/FiO2 = ${fmt(v, 0)} — $band$boundary"
        }
        "alveolar_gas_pao2" -> {
            val fio2 = inputs["fio2"] ?: return null
            val paco2 = inputs["paco2"] ?: return null
            val v = fio2 * 713.0 - paco2 / 0.8
            "PAO2 = ${fmt(v, 1)} mmHg"
        }
        "aa_gradient" -> {
            val fio2 = inputs["fio2"] ?: return null
            val paco2 = inputs["paco2"] ?: return null
            val pao2 = inputs["pao2"] ?: return null
            val age = inputs["age"] ?: return null
            val pao2Alv = fio2 * 713.0 - paco2 / 0.8
            val v = pao2Alv - pao2
            val normal = age / 4.0 + 4.0
            val band = if (v <= normal) "ضمن الطبيعي التقريبي" else "مرتفع"
            "التدرج A-a = ${fmt(v, 1)} mmHg (PAO2 = ${fmt(pao2Alv, 1)} mmHg؛ الطبيعي التقريبي لعمر ${fmt(age, 0)} ≈ ${fmt(normal, 1)} mmHg) — $band"
        }
        "oxygenation_index" -> {
            val fio2Pct = inputs["fio2_pct"] ?: return null
            val mapAirway = inputs["map"] ?: return null
            val pao2 = inputs["pao2"] ?: return null
            if (pao2 == 0.0) return null
            val v = (fio2Pct * mapAirway) / pao2
            val band = when {
                v < 10.0 -> "خفيف نسبياً"
                v <= 20.0 -> "متوسط"
                else -> "شديد"
            }
            val extra = if (v > 40.0) " (فوق 40 كان عتبة تقليدية للنظر في ECMO عند حديثي الولادة والأطفال ضمن سياق كامل)" else ""
            "مؤشر الأكسجة = ${fmt(v, 0)} — $band$extra"
        }
        "maintenance_fluids_421" -> {
            val weight = inputs["weight"] ?: return null
            val first10 = minOf(weight, 10.0) * 100.0
            val second10 = minOf(maxOf(weight - 10.0, 0.0), 10.0) * 50.0
            val rest = maxOf(weight - 20.0, 0.0) * 20.0
            val daily = first10 + second10 + rest
            val hourly = daily / 24.0
            "سوائل الصيانة = ${fmt(daily, 0)} مل/يوم (≈ ${fmt(hourly, 1)} مل/ساعة)"
        }
        "estimated_blood_volume" -> {
            val weight = inputs["weight"] ?: return null
            val factor = inputs["factor"] ?: return null
            val v = weight * factor
            "حجم الدم المقدر = ${fmt(v, 0)} مل (≈ ${fmt(v / 1000.0, 1)} لتر)"
        }
        "shock_index" -> {
            val hr = inputs["hr"] ?: return null
            val sbp = inputs["sbp"] ?: return null
            if (sbp == 0.0) return null
            val v = hr / sbp
            val band = when {
                v < 0.7 -> "طبيعي تقريباً"
                v < 0.9 -> "حدودي ويستدعي انتباهاً"
                v < 1.0 -> "غير طبيعي"
                else -> "خطر مرتفع واضح"
            }
            "مؤشر الصدمة = ${fmt(v, 2)} — $band"
        }
        "reverse_shock_index" -> {
            val sbp = inputs["sbp"] ?: return null
            val hr = inputs["hr"] ?: return null
            if (hr == 0.0) return null
            val v = sbp / hr
            val band = if (v >= 1.0) "ضمن المقبول تقريباً" else "غير طبيعي"
            "مؤشر الصدمة المعكوس = ${fmt(v, 2)} — $band"
        }
        // Point-based clinical scores: inputs are findings / sub-scores,
        // not arithmetic inputs. A score UI must collect them; no compute here.
        "child_pugh" -> null
        "qsofa" -> null
        "sirs_criteria" -> null
        "sofa_score" -> null
        "nihss" -> null
        "spesi_simplified" -> null
        "perc_rule" -> null
        "hasbled" -> null
        "padua_vte" -> null
        "timi_ua_nstemi" -> null
        "ranson_pancreatitis" -> null
        "bisap_pancreatitis" -> null
        "glasgow_blatchford" -> null
        "rockall_bleeding" -> null
        "alvarado_appendicitis" -> null
        "air_appendicitis" -> null
        "centor_modified" -> null
        "mews_score" -> null
        "news2_score" -> null
        else -> null
    }
}
