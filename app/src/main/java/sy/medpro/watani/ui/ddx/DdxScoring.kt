package sy.medpro.watani.ui.ddx

import sy.medpro.watani.data.DdxCaseDto
import sy.medpro.watani.data.DdxReferenceDto

/**
 * Where one reference diagnosis landed in the student's ranking.
 * [studentPosition] is 1-based; null means the diagnosis was absent
 * from the student's probability list.
 */
data class DdxReferencePlacement(
    val rank: Int?,
    val diagnosisAr: String?,
    val diagnosisEn: String?,
    val studentPosition: Int?
)

/**
 * Whether the student flagged one reference must-not-miss diagnosis
 * in their danger set.
 */
data class DdxMnmStatus(
    val rank: Int?,
    val diagnosisAr: String?,
    val diagnosisEn: String?,
    val markedByStudent: Boolean
)

/**
 * Factual scoring breakdown for one committed attempt.
 *
 * Only component A1 carries documented points in this version, so
 * [total] is always null here: no 0-to-100 aggregate is computed or
 * shown. Everything else is reported factually (positions and flags),
 * without invented weights.
 */
data class DdxScoreBreakdown(
    val a1Points: Int,
    val topDiagnosisEn: String?,
    val topDiagnosisAr: String?,
    val topStudentPosition: Int?,
    val referencePlacements: List<DdxReferencePlacement>,
    val mustNotMissStatus: List<DdxMnmStatus>,
    val extraStudentCount: Int,
    val total: Int? = null
)

/** Normalised matching key for a diagnosis_en value. */
fun ddxMatchKey(diagnosisEn: String?): String = diagnosisEn?.trim()?.lowercase().orEmpty()

/**
 * The reference top (most likely) diagnosis: the differential entry
 * whose rank equals scoring_hints.top_rank when present, otherwise
 * the entry with rank 1, otherwise the lowest-ranked entry available.
 */
fun ddxTopReference(case: DdxCaseDto): DdxReferenceDto? {
    val refs = case.differentialReference.orEmpty().filter { !it.diagnosisEn.isNullOrBlank() }
    if (refs.isEmpty()) return null
    val wantedRank = case.scoringHints?.topRank ?: 1
    return refs.firstOrNull { it.rank == wantedRank }
        ?: refs.firstOrNull { it.rank == 1 }
        ?: refs.minByOrNull { it.rank ?: Int.MAX_VALUE }
}

/**
 * The adjudicated reference danger set used for display and scoring.
 *
 * Prefers scoring_hints.must_not_miss_ranks when present and non-empty
 * (the overseer-adjudicated set, which may differ from the raw flags),
 * and falls back to the entries flagged must_not_miss otherwise.
 * Returned in reference-rank order.
 */
fun ddxReferenceMustNotMiss(case: DdxCaseDto): List<DdxReferenceDto> {
    val refs = case.differentialReference.orEmpty()
    val hintRanks = case.scoringHints?.mustNotMissRanks.orEmpty()
    val chosen = if (hintRanks.isNotEmpty()) {
        refs.filter { it.rank != null && it.rank in hintRanks }
    } else {
        refs.filter { it.mustNotMiss == true }
    }
    return chosen.sortedBy { it.rank ?: Int.MAX_VALUE }
}

/**
 * Scores one committed DDx attempt.
 *
 * A1 — position of the most likely diagnosis (documented table,
 * design section 4.2, component A1, worth up to 30 points):
 * position 1 gives 30, position 2 gives 20, position 3 gives 12,
 * positions 4 to 5 give 5, and an absent diagnosis or a position
 * beyond 5 gives 0. The position is taken from [studentRankingEn],
 * matched on the diagnosis_en identity key (trimmed, case-insensitive).
 *
 * A2 and the remaining scoring components are NOT documented in the
 * published DESIGN.md (the file ends in the middle of section 4.2),
 * so they are not invented here: no weights are guessed for them and
 * no total score is produced. They will be added when the
 * specification is completed. Until then this function reports the
 * remaining outcomes factually: each reference diagnosis's student
 * position (or absence), the marked or unmarked state of every
 * reference danger-set entry, and the count of extra student entries.
 * Extra entries carry 0 points and 0 penalty, as documented in the
 * scoring principles (design section 4.1).
 *
 * [studentRankingEn] mirrors the student's probability list exactly
 * as displayed, one slot per entry: a null slot is a free-text entry.
 * Free text can never match a reference diagnosis, but it still
 * occupies its position in the list — A1 uses the position of the
 * most likely diagnosis in the student's list as it stands (design
 * section 4.2), so free-text entries are never compressed out before
 * positions are counted. They count as extra entries (0 points, 0
 * penalty, section 4.1).
 */
fun scoreDdxCommit(
    case: DdxCaseDto,
    studentRankingEn: List<String?>,
    studentMnmEn: Set<String>
): DdxScoreBreakdown {
    val studentKeys = studentRankingEn.map { ddxMatchKey(it) }
    val studentKeySet = studentKeys.filter { it.isNotEmpty() }.toSet()
    val mnmKeys = studentMnmEn.map { ddxMatchKey(it) }.filter { it.isNotEmpty() }.toSet()

    fun positionOf(diagnosisEn: String?): Int? {
        val key = ddxMatchKey(diagnosisEn)
        if (key.isEmpty()) return null
        val idx = studentKeys.indexOf(key)
        return if (idx >= 0) idx + 1 else null
    }

    val top = ddxTopReference(case)
    val topPosition = positionOf(top?.diagnosisEn)
    val a1Points = when {
        topPosition == null -> 0
        topPosition == 1 -> 30
        topPosition == 2 -> 20
        topPosition == 3 -> 12
        topPosition in 4..5 -> 5
        else -> 0
    }

    val references = case.differentialReference.orEmpty()
        .sortedBy { it.rank ?: Int.MAX_VALUE }
    val placements = references.map { ref ->
        DdxReferencePlacement(
            rank = ref.rank,
            diagnosisAr = ref.diagnosisAr,
            diagnosisEn = ref.diagnosisEn,
            studentPosition = positionOf(ref.diagnosisEn)
        )
    }

    val mnmStatus = ddxReferenceMustNotMiss(case).map { ref ->
        DdxMnmStatus(
            rank = ref.rank,
            diagnosisAr = ref.diagnosisAr,
            diagnosisEn = ref.diagnosisEn,
            markedByStudent = ddxMatchKey(ref.diagnosisEn) in mnmKeys
        )
    }

    val referenceKeys = references.map { ddxMatchKey(it.diagnosisEn) }
        .filter { it.isNotEmpty() }.toSet()
    // Extra = every student slot that matches no reference diagnosis,
    // including free-text slots (empty key after normalisation).
    val extraStudentCount = studentKeys.count { it.isEmpty() || it !in referenceKeys }

    return DdxScoreBreakdown(
        a1Points = a1Points,
        topDiagnosisEn = top?.diagnosisEn,
        topDiagnosisAr = top?.diagnosisAr,
        topStudentPosition = topPosition,
        referencePlacements = placements,
        mustNotMissStatus = mnmStatus,
        extraStudentCount = extraStudentCount,
        total = null
    )
}
