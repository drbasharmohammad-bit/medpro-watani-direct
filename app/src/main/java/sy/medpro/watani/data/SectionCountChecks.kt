package sy.medpro.watani.data

import com.squareup.moshi.Moshi

/**
 * Per-family count checks for the root self-loading packs (the
 * dispatch lives in [SectionVerification.verifySectionCount]).
 * Each function parses its family's REAL envelope shape and
 * compares the file's own declared count honestly — the exact
 * checks that used to be branches of the verifier, moved to their
 * own object so no single object carries every family (and so a
 * new family lands here with its branch, or the guard test fails).
 * Failures are raised with check()/error() — IllegalStateException
 * with the same user-facing messages as always (the pattern the
 * years branch established in 13083c1).
 */
object SectionCountChecks {

    fun verifyCasesCount(jsonString: String, moshi: Moshi): Int {
        val casesEnv = moshi.adapter(CasesEnvelope::class.java).fromJson(jsonString)
        val declaredCount = casesEnv?.count
        val actualCount = casesEnv?.cases?.size ?: 0
        check(casesEnv != null && declaredCount != null) {
            "خطأ في تطابق عدد الحالات الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(actualCount == declaredCount) {
            "خطأ في تطابق عدد الحالات الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    fun verifyGuideCount(jsonString: String, moshi: Moshi): Int {
        val guideEnv = moshi.adapter(GuideEnvelope::class.java).fromJson(jsonString)
            ?: error("ملف الدليل غير صالح — أعد التحميل.")
        val actualCount = guideEnv.groups?.size ?: 0
        check(actualCount > 0) { "ملف الدليل بلا مجموعات — أعد التحميل." }
        return actualCount
    }

    fun verifyDdxCount(jsonString: String, moshi: Moshi): Int {
        val ddxEnv = moshi.adapter(DdxEnvelope::class.java).fromJson(jsonString)
        val declaredCount = ddxEnv?.count ?: 0
        val actualCount = ddxEnv?.cases?.size ?: 0
        check(ddxEnv != null) {
            "خطأ في تطابق عدد حالات التشخيص التفريقي الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد حالات التشخيص التفريقي الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    fun verifyAuscultationCount(jsonString: String, moshi: Moshi): Int {
        // Fix 1.2.1: the auscultation pack is a clips envelope, not a
        // subject envelope — before this branch existed it fell into
        // the subject check below, counted 0 questions and EVERY pack
        // download was deleted as "corrupt" (doctor's device report,
        // 10 Nov 2026). Declared count vs actual clips, same honesty
        // rule as the other envelopes.
        val auscEnv = moshi.adapter(AuscultationEnvelope::class.java).fromJson(jsonString)
        val declaredCount = auscEnv?.count ?: 0
        val actualCount = auscEnv?.clips?.size ?: 0
        check(auscEnv != null && actualCount > 0) {
            "خطأ في تطابق عدد مقاطع الإصغاء الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد مقاطع الإصغاء الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    fun verifyOsceCount(jsonString: String, moshi: Moshi): Int {
        // Fix 1.2.1: same defect class as auscultation — a stations
        // envelope fell into the subject check and could never pass.
        val osceEnv = moshi.adapter(OsceEnvelope::class.java).fromJson(jsonString)
        val declaredCount = osceEnv?.count ?: 0
        val actualCount = osceEnv?.stations?.size ?: 0
        check(osceEnv != null && actualCount > 0) {
            "خطأ في تطابق عدد محطات الأوسكي الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد محطات الأوسكي الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    fun verifyEcgCount(jsonString: String, moshi: Moshi): Int {
        // Build 1.2.4: the ECG course pack is a method/patterns/
        // strips envelope with NO declared count field in its
        // contract — without this branch it would fall into the
        // subject check, count 0 and never download (the exact
        // 1.2.1 defect class). Honest check: the pack must parse
        // and carry teaching content (patterns or strips); the
        // returned count is the number of strips, or patterns
        // when the pack ships patterns ahead of strips.
        val ecgEnv = moshi.adapter(EcgEnvelope::class.java).fromJson(jsonString)
            ?: error("ملف كورس تخطيط القلب غير صالح — أعد التحميل.")
        val patternCount = ecgEnv.patterns?.size ?: 0
        val stripCount = ecgEnv.strips?.size ?: 0
        check(patternCount > 0 || stripCount > 0) {
            "ملف كورس تخطيط القلب بلا أنماط أو شرائط — أعد التحميل."
        }
        return if (stripCount > 0) stripCount else patternCount
    }

    fun verifyProceduresCount(jsonString: String, moshi: Moshi): Int {
        // Build 1.2.6: the OSCE procedures pack is a BARE ARRAY of
        // procedures (no envelope, no declared count) — without this
        // branch it would fall into the subject check, count 0 and
        // never download (the exact 1.2.1 defect class). Honest
        // check: the array must parse and be non-empty; the returned
        // count is the real number of procedures.
        val listType = com.squareup.moshi.Types.newParameterizedType(
            List::class.java, ProcedureDto::class.java
        )
        val procedures = moshi.adapter<List<ProcedureDto>>(listType).fromJson(jsonString)
        val actualCount = procedures?.size ?: 0
        check(procedures != null && actualCount > 0) {
            "ملف إجراءات الأوسكي بلا إجراءات — أعد التحميل."
        }
        return actualCount
    }

    fun verifyDosingDrillsCount(jsonString: String, moshi: Moshi): Int {
        // Build 1.2.6: same bare-array shape as osce_procedures —
        // the dosing drills pack must parse and be non-empty; the
        // returned count is the real number of drills.
        val listType = com.squareup.moshi.Types.newParameterizedType(
            List::class.java, DosingDrillDto::class.java
        )
        val drills = moshi.adapter<List<DosingDrillDto>>(listType).fromJson(jsonString)
        val actualCount = drills?.size ?: 0
        check(drills != null && actualCount > 0) {
            "ملف تدريبات الجرعات بلا تدريبات — أعد التحميل."
        }
        return actualCount
    }

    fun verifyConceptsCount(jsonString: String, moshi: Moshi): Int {
        // Build 1.2.13: the «ادرس مع ميد برو» pack is a units
        // envelope with NO declared count field in its contract —
        // without this branch it would fall into the subject check,
        // count 0 and never download (the exact 1.2.1 defect
        // class). Honest check: the pack must parse and carry at
        // least one unit; the returned count is the real number
        // of units (the manifest entry's count is built from it).
        val conceptsEnv = moshi.adapter(ConceptsEnvelope::class.java).fromJson(jsonString)
        val actualCount = conceptsEnv?.units?.size ?: 0
        check(conceptsEnv != null && actualCount > 0) {
            "ملف مفاهيم «ادرس مع ميد برو» بلا وحدات — أعد التحميل."
        }
        return actualCount
    }

    fun verifyReverseCount(jsonString: String, moshi: Moshi): Int {
        val revEnv = moshi.adapter(ReverseEnvelope::class.java).fromJson(jsonString)
        val declaredCount = revEnv?.count ?: 0
        val actualCount = revEnv?.rounds?.size ?: 0
        check(revEnv != null) {
            "خطأ في تطابق عدد جولات اقلبها الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد جولات اقلبها الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }
}
