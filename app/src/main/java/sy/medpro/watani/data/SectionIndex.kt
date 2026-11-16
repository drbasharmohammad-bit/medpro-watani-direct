package sy.medpro.watani.data

/**
 * Light per-session extraction over an already-parsed subject
 * envelope (build 1.2.27). These are the exact merge/selection
 * rules the «دورة كاملة» paths used inline in MainViewModel —
 * extracted unchanged into pure functions so the entry path reads
 * from the envelope cache through one tested core:
 *
 * - [sessionCounts]: sessions merged by trimmed name (duplicate
 *   names add up), count = questions size when the question list
 *   is present, else the declared questionCount — the identical
 *   fallback order the inline code used.
 * - [sessionQuestions]: every session whose trimmed name equals
 *   the target, in shard order, questions concatenated — the
 *   identical selection the assembly used.
 */
object SectionIndex {

    fun sessionCounts(envelope: SubjectEnvelope): LinkedHashMap<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        envelope.sessions.orEmpty().forEach { session ->
            val name = session.name?.trim().orEmpty()
            if (name.isNotEmpty()) {
                counts[name] = (counts[name] ?: 0) +
                    (session.questions?.size ?: session.questionCount ?: 0)
            }
        }
        return counts
    }

    fun sessionQuestions(envelope: SubjectEnvelope, sessionName: String): List<QuestionDto> =
        envelope.sessions.orEmpty()
            .filter { it.name?.trim() == sessionName }
            .flatMap { it.questions.orEmpty() }
}
