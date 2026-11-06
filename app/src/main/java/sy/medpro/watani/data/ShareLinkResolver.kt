package sy.medpro.watani.data

/**
 * Question sharing (Nov 2026, image-only): a question leaves the app
 * ONLY as a rendered share-card image (see ShareCardRenderer). There
 * is no text share, no per-question link, and no incoming-link /
 * guest-question flow anywhere in the app.
 *
 * The only text allowed to accompany the shared image is the channel
 * URL in [ShareLinks.FALLBACK_URL].
 *
 * This file keeps only the two helpers the share card itself needs:
 * the option labels drawn on the card and the card's subject label.
 */
object ShareLinks {
    /** The sole text attached to a shared card image (EXTRA_TEXT). */
    const val FALLBACK_URL = "https://t.me/medprowatain"

    private val DEFAULT_LABELS = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي")

    fun optionLabel(question: QuestionDto, index: Int): String {
        val provided = question.optionLabels?.getOrNull(index)?.trim().orEmpty()
        if (provided.isNotEmpty()) return provided
        return DEFAULT_LABELS.getOrNull(index) ?: "${index + 1}"
    }

    /** Subject label for the share card: envelope subject_name first,
     * then the section file name without extension, then the quiz title. */
    fun subjectLabel(envelopeSubjectName: String?, sourcePath: String?, quizTitle: String): String {
        if (!envelopeSubjectName.isNullOrBlank()) return envelopeSubjectName.trim()
        val fileBase = sourcePath?.substringAfterLast('/')?.substringBeforeLast('.')?.trim()
        if (!fileBase.isNullOrEmpty()) return fileBase
        return quizTitle
    }
}
