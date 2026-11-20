package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Standalone per-dawra files (build 1.2.32, doctor's order
 * 2026-11-20): every named dawra is ALSO published as one complete,
 * independent file under `dawrat_sessions/<session>.json` — a bare
 * JSON list of the dawra's questions in the unified bank's own record
 * shape — so «دورة كاملة» can download ONE file and run the whole
 * dawra offline from it alone, instead of assembling it on-device
 * from the 27 subject shards. The shard assembly stays as the
 * fallback whenever the standalone file is absent or unreadable.
 *
 * The records carry the unified schema (question / correct_index /
 * correct_label / explanation{why_correct, why_wrong, summary}),
 * NOT the shard schema, so this file owns the parse model and the
 * conversion to [QuestionDto]. The conversion is a faithful port of
 * the publishing pipeline's own rendering (build_unified_dawrat.py:
 * question_obj / merged_explanation / zubda_text) — the student sees
 * the very same question, key and explanation text in the full-dawra
 * sitting as in the per-subject session view of the same bank.
 *
 * Everything here is pure (no Android, no I/O) and unit-testable.
 */

/** Path prefix of the standalone dawra family under v1/. */
const val DAWRA_SESSIONS_PREFIX = "dawrat_sessions/"

/** Manifest path of one dawra's standalone file. */
fun dawraSessionPath(sessionName: String): String =
    DAWRA_SESSIONS_PREFIX + sessionName.trim() + ".json"

/** The dawra name a standalone-file path belongs to, or null when
 * [path] is not a dawrat_sessions unit file. */
fun dawraSessionNameOfPath(path: String): String? {
    if (!path.startsWith(DAWRA_SESSIONS_PREFIX) || !path.endsWith(".json")) return null
    val name = path.removePrefix(DAWRA_SESSIONS_PREFIX).removeSuffix(".json").trim()
    return name.takeIf { it.isNotEmpty() }
}

/** Option labels by position — the pipeline's ARAB list verbatim
 * (build_unified_dawrat.py); optionLabels are its prefix of the
 * option count, exactly as the shards carry them. */
val DAWRA_OPTION_LABELS: List<String> =
    listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح")

/** One question record of a standalone dawra file (unified schema). */
@JsonClass(generateAdapter = true)
data class DawraSessionQuestionDto(
    @Json(name = "id") val id: String?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "session") val session: String?,
    @Json(name = "question") val question: String?,
    @Json(name = "options") val options: List<String>?,
    @Json(name = "correct_index") val correctIndex: Int?,
    @Json(name = "correct_label") val correctLabel: String?,
    @Json(name = "explanation") val explanation: DawraSessionExplanationDto?,
    @Json(name = "sources") val sources: List<String>?,
    @Json(name = "confidence") val confidence: String?
)

/** The structured explanation block of a unified record. */
@JsonClass(generateAdapter = true)
data class DawraSessionExplanationDto(
    @Json(name = "why_correct") val whyCorrect: String?,
    @Json(name = "why_wrong") val whyWrong: Map<String, String>?,
    @Json(name = "summary") val summary: String?
)

/**
 * The merged explanation string, ported line-for-line from the
 * pipeline's merged_explanation(rec, include_summary=False):
 * «الإجابة الصحيحة (label): why_correct», then the wrong-options
 * block in label order, then sources and confidence trailers —
 * joined by single newlines. A record with no explanation content
 * at all yields "" (no explanation card), exactly like the shards.
 */
fun mergedDawraExplanation(record: DawraSessionQuestionDto): String {
    val ex = record.explanation
    val whyCorrect = ex?.whyCorrect?.trim().orEmpty()
    val whyWrong = ex?.whyWrong.orEmpty()
    val summary = ex?.summary?.trim().orEmpty()
    val sources = record.sources.orEmpty()
    val confidence = record.confidence?.trim().orEmpty()
    if (whyCorrect.isEmpty() && whyWrong.isEmpty() && summary.isEmpty() &&
        sources.isEmpty() && confidence.isEmpty()
    ) {
        return ""
    }
    val parts = ArrayList<String>()
    val label = record.correctLabel?.trim().orEmpty()
    parts += if (label.isNotEmpty()) "الإجابة الصحيحة ($label): $whyCorrect" else whyCorrect
    if (whyWrong.isNotEmpty()) {
        val lines = ArrayList<String>()
        for (lab in DAWRA_OPTION_LABELS) {
            whyWrong[lab]?.let { lines += "$lab: ${it.trim()}" }
        }
        for ((lab, txt) in whyWrong) {
            if (lab !in DAWRA_OPTION_LABELS) lines += "$lab: ${txt.trim()}"
        }
        parts += "لماذا الخيارات الأخرى خاطئة:\n" + lines.joinToString("\n")
    }
    if (sources.isNotEmpty()) {
        parts += "المصادر: " + sources.joinToString("؛ ")
    }
    if (confidence.isNotEmpty()) {
        parts += "الثقة: $confidence"
    }
    return parts.joinToString("\n")
}

/** The «الزبدة» text (the pipeline's zubda_text): the record's
 * summary carried as «الخلاصة: …», or null when it has none — a
 * question with no summary shows no «الزبدة» tab, as in the shards. */
fun dawraZubda(record: DawraSessionQuestionDto): String? {
    val summary = record.explanation?.summary?.trim().orEmpty()
    return if (summary.isNotEmpty()) "الخلاصة: $summary" else null
}

/**
 * Converts one unified record to the app's [QuestionDto] — the
 * pipeline's question_obj mapping: text = question, correct = the
 * correct option's TEXT plus correctIndices = [correct_index]
 * (both carried, exactly like the shards), the merged explanation
 * string, and the zubda field.
 */
fun DawraSessionQuestionDto.toQuestionDto(): QuestionDto {
    val opts = options.orEmpty()
    val ci = correctIndex
    return QuestionDto(
        id = id,
        text = question,
        options = opts,
        optionLabels = DAWRA_OPTION_LABELS.take(opts.size),
        correct = ci?.let { opts.getOrNull(it) },
        correctIndices = ci?.let { listOf(it) },
        explanation = mergedDawraExplanation(this),
        whyCorrect = null,
        whyWrong = null,
        summary = null,
        zubda = dawraZubda(this),
        years = null,
        source = null,
        session = session,
        topics = null,
        free = null
    )
}

/**
 * Assembles the full-dawra quiz from a standalone file's records:
 * questions grouped by their record subject (per-subject relative
 * order preserved from the file), converted, and placed by
 * [buildDawraQuiz] in the official subject order with spans — the
 * very same assembly the shard path produces.
 */
fun buildDawraQuizFromRecords(records: List<DawraSessionQuestionDto>): DawraQuiz {
    val bySubject = LinkedHashMap<String, MutableList<DawraSessionQuestionDto>>()
    for (record in records) {
        val subject = record.subject?.trim().orEmpty()
        if (subject.isEmpty()) continue
        bySubject.getOrPut(subject) { ArrayList() } += record
    }
    return buildDawraQuiz(
        bySubject.map { (subject, recs) -> subject to recs.map { it.toQuestionDto() } }
    )
}

/**
 * Light per-subject session tables of ONE standalone dawra file —
 * the [DawraSubjectSessions] inputs the dawra list/detail screens
 * consume, built from the file instead of the subject shards. The
 * session name is taken from the file's path (the name the UI
 * navigates by); counts are the file's real per-subject counts.
 */
fun dawraSessionInputs(
    records: List<DawraSessionQuestionDto>,
    path: String
): List<DawraSubjectSessions> {
    val sessionName = dawraSessionNameOfPath(path) ?: return emptyList()
    val counts = LinkedHashMap<String, Int>()
    for (record in records) {
        val subject = record.subject?.trim().orEmpty()
        if (subject.isEmpty()) continue
        counts[subject] = (counts[subject] ?: 0) + 1
    }
    return counts.map { (subject, count) ->
        DawraSubjectSessions(
            subjectName = subject,
            path = path,
            sessionCounts = mapOf(sessionName to count)
        )
    }
}
