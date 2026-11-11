package sy.medpro.watani.data

import com.squareup.moshi.Moshi

/**
 * Pure verification core for section downloads (F-01/F-02/F-03/F-04),
 * extracted from WataniRepository.downloadSection so the EXACT logic
 * the app runs is unit-testable off-device. No Android imports.
 *
 * Contract:
 * - [manifestMismatch] returns a user-facing Arabic reason when the
 *   downloaded bytes disagree with the manifest entry (size first,
 *   then the family hash), or null when they agree / nothing is
 *   declared to check.
 * - [verifySectionCount] returns the file's ACTUAL parsed count, or
 *   throws IllegalStateException with the user-facing reason when the
 *   content fails its own declared-count/structure checks. There is
 *   NO coerced count anywhere: zero parsed questions for a subject
 *   envelope is a failure, guide.json is really parsed, and the cases
 *   comparison is the honest declared-vs-actual check for any value.
 *   Every non-subject envelope downloaded through downloadSection has
 *   its OWN branch here (cases/cases_de, guide, ddx, reverse,
 *   auscultation, osce, ecg, osce_procedures, dosing_drills): an
 *   envelope shape without a branch falls into
 *   the subject check, counts 0 and can NEVER download (the 1.2 defect
 *   fixed in 1.2.1 — auscultation, German cases and OSCE all failed
 *   this way on device).
 */
object SectionVerification {

    /**
     * The manifest hash of [bytes] for [path]'s family: blake2b-8 for
     * any path starting with "questions" (shards + the root index),
     * sha256 truncated to 16 hex chars for every other family — the
     * algorithms the publishing pipeline built the manifest with.
     */
    fun manifestHashHex(path: String, bytes: ByteArray): String {
        return if (path.startsWith("questions")) {
            Blake2b.hashHex(bytes, digestSize = 8)
        } else {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString("") { "%02x".format(it) }.take(16)
        }
    }

    /** F-01: size + hash agreement with the manifest entry. */
    fun manifestMismatch(path: String, bytes: ByteArray, declaredSize: Long?, declaredHash: String?): String? {
        if (declaredSize != null && bytes.size.toLong() != declaredSize) {
            return "حجم ملف القسم غير مطابق للفهرس (المتوقع $declaredSize والفعلي ${bytes.size}) — أعد التحميل."
        }
        val hash = declaredHash?.trim().orEmpty()
        if (hash.isNotEmpty()) {
            val computed = manifestHashHex(path, bytes)
            if (!computed.equals(hash, ignoreCase = true)) {
                return "بصمة ملف القسم غير مطابقة للفهرس — أعد التحميل."
            }
        }
        return null
    }

    /** F-02/F-03/F-04: parse + declared-count verification. */
    fun verifySectionCount(path: String, jsonString: String, moshi: Moshi): Int {
        if (path.contains("cases.json") || path == "cases_de.json") {
            val casesEnv = moshi.adapter(CasesEnvelope::class.java).fromJson(jsonString)
            val declaredCount = casesEnv?.count
            val actualCount = casesEnv?.cases?.size ?: 0
            if (casesEnv == null || declaredCount == null || actualCount != declaredCount) {
                throw IllegalStateException("خطأ في تطابق عدد الحالات الداخلي (المتوقع $declaredCount والفعلي $actualCount)")
            }
            return actualCount
        }
        if (path == "guide.json") {
            val guideEnv = moshi.adapter(GuideEnvelope::class.java).fromJson(jsonString)
                ?: throw IllegalStateException("ملف الدليل غير صالح — أعد التحميل.")
            val actualCount = guideEnv.groups?.size ?: 0
            if (actualCount == 0) {
                throw IllegalStateException("ملف الدليل بلا مجموعات — أعد التحميل.")
            }
            return actualCount
        }
        if (path == "ddx.json") {
            val ddxEnv = moshi.adapter(DdxEnvelope::class.java).fromJson(jsonString)
            val declaredCount = ddxEnv?.count ?: 0
            val actualCount = ddxEnv?.cases?.size ?: 0
            if (ddxEnv == null || (declaredCount > 0 && actualCount != declaredCount)) {
                throw IllegalStateException("خطأ في تطابق عدد حالات التشخيص التفريقي الداخلي (المتوقع $declaredCount والفعلي $actualCount)")
            }
            return actualCount
        }
        if (path == "auscultation.json") {
            // Fix 1.2.1: the auscultation pack is a clips envelope, not a
            // subject envelope — before this branch existed it fell into
            // the subject check below, counted 0 questions and EVERY pack
            // download was deleted as "corrupt" (doctor's device report,
            // 10 Nov 2026). Declared count vs actual clips, same honesty
            // rule as the other envelopes.
            val auscEnv = moshi.adapter(AuscultationEnvelope::class.java).fromJson(jsonString)
            val declaredCount = auscEnv?.count ?: 0
            val actualCount = auscEnv?.clips?.size ?: 0
            if (auscEnv == null || actualCount == 0 || (declaredCount > 0 && actualCount != declaredCount)) {
                throw IllegalStateException("خطأ في تطابق عدد مقاطع الإصغاء الداخلي (المتوقع $declaredCount والفعلي $actualCount)")
            }
            return actualCount
        }
        if (path == "osce.json") {
            // Fix 1.2.1: same defect class as auscultation — a stations
            // envelope fell into the subject check and could never pass.
            val osceEnv = moshi.adapter(OsceEnvelope::class.java).fromJson(jsonString)
            val declaredCount = osceEnv?.count ?: 0
            val actualCount = osceEnv?.stations?.size ?: 0
            if (osceEnv == null || actualCount == 0 || (declaredCount > 0 && actualCount != declaredCount)) {
                throw IllegalStateException("خطأ في تطابق عدد محطات الأوسكي الداخلي (المتوقع $declaredCount والفعلي $actualCount)")
            }
            return actualCount
        }
        if (path == "ecg.json") {
            // Build 1.2.4: the ECG course pack is a method/patterns/
            // strips envelope with NO declared count field in its
            // contract — without this branch it would fall into the
            // subject check, count 0 and never download (the exact
            // 1.2.1 defect class). Honest check: the pack must parse
            // and carry teaching content (patterns or strips); the
            // returned count is the number of strips, or patterns
            // when the pack ships patterns ahead of strips.
            val ecgEnv = moshi.adapter(EcgEnvelope::class.java).fromJson(jsonString)
                ?: throw IllegalStateException("ملف كورس تخطيط القلب غير صالح — أعد التحميل.")
            val patternCount = ecgEnv.patterns?.size ?: 0
            val stripCount = ecgEnv.strips?.size ?: 0
            if (patternCount == 0 && stripCount == 0) {
                throw IllegalStateException("ملف كورس تخطيط القلب بلا أنماط أو شرائط — أعد التحميل.")
            }
            return if (stripCount > 0) stripCount else patternCount
        }
        if (path == "osce_procedures.json") {
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
            if (procedures == null || actualCount == 0) {
                throw IllegalStateException("ملف إجراءات الأوسكي بلا إجراءات — أعد التحميل.")
            }
            return actualCount
        }
        if (path == "dosing_drills.json") {
            // Build 1.2.6: same bare-array shape as osce_procedures —
            // the dosing drills pack must parse and be non-empty; the
            // returned count is the real number of drills.
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, DosingDrillDto::class.java
            )
            val drills = moshi.adapter<List<DosingDrillDto>>(listType).fromJson(jsonString)
            val actualCount = drills?.size ?: 0
            if (drills == null || actualCount == 0) {
                throw IllegalStateException("ملف تدريبات الجرعات بلا تدريبات — أعد التحميل.")
            }
            return actualCount
        }
        if (path == "reverse.json") {
            val revEnv = moshi.adapter(ReverseEnvelope::class.java).fromJson(jsonString)
            val declaredCount = revEnv?.count ?: 0
            val actualCount = revEnv?.rounds?.size ?: 0
            if (revEnv == null || (declaredCount > 0 && actualCount != declaredCount)) {
                throw IllegalStateException("خطأ في تطابق عدد جولات اقلبها الداخلي (المتوقع $declaredCount والفعلي $actualCount)")
            }
            return actualCount
        }
        val subjEnv = moshi.adapter(SubjectEnvelope::class.java).fromJson(jsonString)
        val declaredCount = subjEnv?.count ?: 0
        val tCount = subjEnv?.topics?.sumOf { it.questions?.size ?: 0 } ?: 0
        val sCount = subjEnv?.sessions?.sumOf { it.questions?.size ?: 0 } ?: 0
        val actualCount = if (tCount > 0) tCount else sCount
        if (subjEnv == null || actualCount == 0 || (declaredCount > 0 && actualCount != declaredCount)) {
            throw IllegalStateException("خطأ في تطابق عدد أسئلة القسم الداخلي (المتوقع $declaredCount والفعلي $actualCount)")
        }
        return actualCount
    }
}
