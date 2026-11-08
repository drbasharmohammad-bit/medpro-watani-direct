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
        if (path.contains("cases.json")) {
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
