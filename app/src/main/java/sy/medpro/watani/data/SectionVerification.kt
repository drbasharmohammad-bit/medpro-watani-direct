package sy.medpro.watani.data

import com.squareup.moshi.Moshi

/**
 * The downloadable file families [SectionVerification] knows how to
 * verify. Every path that flows through downloadSection classifies
 * into exactly one family via [SectionVerification.familyOf], and
 * the verifier dispatches on this enum with an EXHAUSTIVE `when` —
 * adding a family here without adding its verification branch is a
 * compile error, and a path in no known family is [UNKNOWN],
 * rejected loudly (see the guard note on [SectionVerification]).
 */
enum class SectionFamily {
    CASES,
    GUIDE,
    DDX,
    AUSCULTATION,
    OSCE,
    ECG,
    OSCE_PROCEDURES,
    DOSING_DRILLS,
    CONCEPTS,
    REVERSE,
    FIRST_AID,
    DAWRA_SESSIONS,
    YEARS,
    SUBJECT,
    UNKNOWN
}

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
 *   throws IllegalStateException with the user-facing reason when
 *   the content fails its own declared-count/structure checks. There
 *   is NO coerced count anywhere: zero parsed questions for a subject
 *   envelope is a failure, guide.json is really parsed, and the cases
 *   comparison is the honest declared-vs-actual check for any value.
 *
 * Family guard (build 1.2.45, cache audit): dispatch is by
 * [familyOf] over [SectionFamily], never by fall-through. The
 * defect this prevents — three documented occurrences (1.2.1:
 * auscultation, German cases and OSCE; 1.2.44: the university years
 * files) — was an envelope shape with NO branch silently landing in
 * the subject check, counting 0 and becoming un-downloadable
 * forever. Now the subject family is defined POSITIVELY (only the
 * bank prefixes whose files really are subject envelopes), and any
 * other unclassified path is [SectionFamily.UNKNOWN], rejected with
 * the distinct «عائلة ملف غير معروفة» message instead of a silent
 * zero count. SectionFamilyGuardTest pins the classification of
 * every family the live manifest carries.
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

    /**
     * The «حسب السنوات» twin path shape, mirror of [yearsPathFor]'s
     * output: `dawrat_jami3at/<university>_years/<subject>.json`
     * (exactly three segments, the university segment carrying the
     * `_years` suffix). Pure path check — no parsing.
     */
    fun isYearsFilePath(path: String): Boolean {
        val parts = path.split("/")
        return parts.size == YEARS_PATH_SEGMENT_COUNT &&
            parts[0] == "dawrat_jami3at" &&
            parts[1].endsWith("_years") &&
            parts[2].endsWith(".json")
    }

    /** Segment count of `dawrat_jami3at/<uni>_years/<file>.json`. */
    private const val YEARS_PATH_SEGMENT_COUNT = 3

    /**
     * Classifies a downloadable [path] into its [SectionFamily].
     * The check order mirrors the historical branch order of
     * [verifySectionCount] (the cases contains-check first), and the
     * SUBJECT family is POSITIVE: only the bank prefixes whose files
     * really are subject envelopes (questions/, questions_en/,
     * uworld/, and the non-years files of dawrat_jami3at/). Anything
     * else is UNKNOWN — never a silent subject fall-through.
     */
    fun familyOf(path: String): SectionFamily =
        rootPackFamily(path) ?: familyByPrefix(path)

    /** The root self-loading packs, by exact file name (plus the
     * cases contains-check the historical branch order starts
     * with). Null when [path] is not one of them. */
    private fun rootPackFamily(path: String): SectionFamily? = when {
        path.contains("cases.json") || path == "cases_de.json" -> SectionFamily.CASES
        path == "guide.json" -> SectionFamily.GUIDE
        path == "ddx.json" -> SectionFamily.DDX
        path == "auscultation.json" -> SectionFamily.AUSCULTATION
        path == "osce.json" -> SectionFamily.OSCE
        path == "ecg.json" -> SectionFamily.ECG
        path == "osce_procedures.json" -> SectionFamily.OSCE_PROCEDURES
        path == "dosing_drills.json" -> SectionFamily.DOSING_DRILLS
        path == "concepts.json" -> SectionFamily.CONCEPTS
        path == "reverse.json" -> SectionFamily.REVERSE
        else -> null
    }

    /** The prefix families: First Aid, the standalone dawrat, the
     * years twins, and — positively — the subject banks; anything
     * else is UNKNOWN. */
    private fun familyByPrefix(path: String): SectionFamily = when {
        path.startsWith("firstaid/") -> SectionFamily.FIRST_AID
        path.startsWith("dawrat_sessions/") -> SectionFamily.DAWRA_SESSIONS
        isYearsFilePath(path) -> SectionFamily.YEARS
        path.startsWith("questions/") || path.startsWith("questions_en/") ||
            path.startsWith("uworld/") || path.startsWith("dawrat_jami3at/") ->
            SectionFamily.SUBJECT
        else -> SectionFamily.UNKNOWN
    }

    /** F-02/F-03/F-04: parse + declared-count verification. The
     * per-family checks live in [SectionCountChecks] (root packs)
     * and [SectionBankCounts] (bank-shaped families). */
    fun verifySectionCount(path: String, jsonString: String, moshi: Moshi): Int {
        return when (familyOf(path)) {
            SectionFamily.CASES -> SectionCountChecks.verifyCasesCount(jsonString, moshi)
            SectionFamily.GUIDE -> SectionCountChecks.verifyGuideCount(jsonString, moshi)
            SectionFamily.DDX -> SectionCountChecks.verifyDdxCount(jsonString, moshi)
            SectionFamily.AUSCULTATION -> SectionCountChecks.verifyAuscultationCount(jsonString, moshi)
            SectionFamily.OSCE -> SectionCountChecks.verifyOsceCount(jsonString, moshi)
            SectionFamily.ECG -> SectionCountChecks.verifyEcgCount(jsonString, moshi)
            SectionFamily.OSCE_PROCEDURES -> SectionCountChecks.verifyProceduresCount(jsonString, moshi)
            SectionFamily.DOSING_DRILLS -> SectionCountChecks.verifyDosingDrillsCount(jsonString, moshi)
            SectionFamily.CONCEPTS -> SectionCountChecks.verifyConceptsCount(jsonString, moshi)
            SectionFamily.REVERSE -> SectionCountChecks.verifyReverseCount(jsonString, moshi)
            SectionFamily.FIRST_AID -> SectionBankCounts.verifyFirstAidCount(path, jsonString, moshi)
            SectionFamily.DAWRA_SESSIONS -> SectionBankCounts.verifyDawraSessionsCount(jsonString, moshi)
            SectionFamily.YEARS -> SectionBankCounts.verifyYearsCount(jsonString, moshi)
            SectionFamily.SUBJECT -> SectionBankCounts.verifySubjectCount(jsonString, moshi)
            SectionFamily.UNKNOWN -> error(
                "عائلة ملف غير معروفة لهذا القسم — أعد التحميل."
            )
        }
    }
}
