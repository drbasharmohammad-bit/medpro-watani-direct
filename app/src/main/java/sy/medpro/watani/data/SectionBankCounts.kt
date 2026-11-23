package sy.medpro.watani.data

import com.squareup.moshi.Moshi

/**
 * Per-family count checks for the bank-shaped families — First
 * Aid, the standalone dawrat, the «حسب السنوات» twins and the
 * subject envelopes (the dispatch lives in
 * [SectionVerification.verifySectionCount]). Same honesty rules
 * as [SectionCountChecks]: real parse, real count, no coercion.
 * Failures are raised with check() — IllegalStateException with
 * the same user-facing messages as always.
 */
object SectionBankCounts {

    fun verifyDawraSessionsCount(jsonString: String, moshi: Moshi): Int {
        // Build 1.2.32: a standalone dawra file is a BARE ARRAY of
        // unified-schema question records (no envelope, no declared
        // count) — without this branch it would fall into the subject
        // check, count 0 and never download (the exact 1.2.1 defect
        // class). Honest check: the array must parse as dawra records
        // and be non-empty; the returned count is the real number of
        // questions in the file.
        val listType = com.squareup.moshi.Types.newParameterizedType(
            List::class.java, DawraSessionQuestionDto::class.java
        )
        val records = moshi.adapter<List<DawraSessionQuestionDto>>(listType).fromJson(jsonString)
        val actualCount = records?.size ?: 0
        check(records != null && actualCount > 0) {
            "ملف الدورة المستقل بلا أسئلة — أعد التحميل."
        }
        return actualCount
    }

    fun verifyYearsCount(jsonString: String, moshi: Moshi): Int {
        // Build 1.2.45 fix: the «حسب السنوات» twin file is a
        // years envelope ({count, years: [{questions}]}) — in
        // 1.2.44 it had NO branch here, fell into the subject
        // check below, counted 0 (no topics/sessions) against
        // its own declared count and could NEVER download
        // (doctor's device report 23 Nov 2026 — the exact 1.2
        // defect class this file's header warns about). Honest
        // check: the actual count is the sum of the years'
        // question lists, compared with the file's own declared
        // count exactly like the other envelopes.
        val yearsEnv = moshi.adapter(YearsEnvelope::class.java).fromJson(jsonString)
        val declaredCount = yearsEnv?.count ?: 0
        val actualCount = yearsEnv?.years?.sumOf { it.questions?.size ?: 0 } ?: 0
        // check() throws IllegalStateException with this exact
        // message — the type downloadSection catches — same
        // contract as the explicit throws in the other branches.
        check(actualCount > 0) {
            "خطأ في تطابق عدد أسئلة السنوات الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد أسئلة السنوات الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    fun verifySubjectCount(jsonString: String, moshi: Moshi): Int {
        val subjEnv = moshi.adapter(SubjectEnvelope::class.java).fromJson(jsonString)
        val declaredCount = subjEnv?.count ?: 0
        val tCount = subjEnv?.topics?.sumOf { it.questions?.size ?: 0 } ?: 0
        val sCount = subjEnv?.sessions?.sumOf { it.questions?.size ?: 0 } ?: 0
        val actualCount = if (tCount > 0) tCount else sCount
        check(subjEnv != null && actualCount > 0) {
            "خطأ في تطابق عدد أسئلة القسم الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد أسئلة القسم الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    // Build 1.2.36: the First Aid family — without this branch
    // every firstaid file would fall into the subject check,
    // count 0 and never download (the exact 1.2.1 defect class).
    // Honest per-file checks against each file's OWN declared
    // count where one exists; unit packages carry no declared
    // count, so their real quiz size is returned (the manifest
    // entry's count is built from it — verified against all 154
    // published packages at merge time). One helper per shape.
    fun verifyFirstAidCount(path: String, jsonString: String, moshi: Moshi): Int = when {
        path.endsWith("index_step1.json") || path.endsWith("index_step2.json") ->
            verifyFirstAidIndexCount(jsonString, moshi)
        path == "firstaid/integration.json" ->
            verifyFirstAidIntegrationCount(jsonString, moshi)
        path == "firstaid/banks_step2.json" ->
            verifyFirstAidBanksCount(jsonString, moshi)
        else -> verifyFirstAidUnitCount(jsonString, moshi)
    }

    private fun verifyFirstAidIndexCount(jsonString: String, moshi: Moshi): Int {
        val idx = moshi.adapter(FirstAidIndexDto::class.java).fromJson(jsonString)
        val declaredCount = idx?.count ?: 0
        val actualCount = idx?.units?.size ?: 0
        check(idx != null && actualCount > 0) {
            "خطأ في تطابق عدد وحدات فهرس First Aid الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد وحدات فهرس First Aid الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    private fun verifyFirstAidIntegrationCount(jsonString: String, moshi: Moshi): Int {
        val integ = moshi.adapter(FirstAidIntegrationDto::class.java).fromJson(jsonString)
        val declaredCount = integ?.count ?: 0
        val actualCount = integ?.links?.size ?: 0
        check(integ != null && actualCount > 0) {
            "خطأ في تطابق عدد روابط تكامل First Aid الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد روابط تكامل First Aid الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    private fun verifyFirstAidBanksCount(jsonString: String, moshi: Moshi): Int {
        val banks = moshi.adapter(FirstAidBanksDto::class.java).fromJson(jsonString)
        val declaredCount = banks?.count ?: 0
        val actualCount = banks?.tests?.sumOf { it.questions?.size ?: 0 } ?: 0
        check(banks != null && actualCount > 0) {
            "خطأ في تطابق عدد أسئلة بنوك First Aid الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        check(declaredCount <= 0 || actualCount == declaredCount) {
            "خطأ في تطابق عدد أسئلة بنوك First Aid الداخلي (المتوقع $declaredCount والفعلي $actualCount)"
        }
        return actualCount
    }

    private fun verifyFirstAidUnitCount(jsonString: String, moshi: Moshi): Int {
        val pkg = moshi.adapter(FirstAidUnitPackageDto::class.java).fromJson(jsonString)
        val actualCount = pkg?.quiz?.size ?: 0
        check(pkg != null && actualCount > 0) {
            "حزمة وحدة First Aid بلا أسئلة تدريب — أعد التحميل."
        }
        return actualCount
    }
}
