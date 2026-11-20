package sy.medpro.watani.data

/**
 * «دورة كاملة» (build 1.2.26, doctor's order 16 Nov 2026): assemble
 * one named national dawra (e.g. «أيار 2026») from EVERY subject
 * shard the student has downloaded, in the official subject order,
 * and feed the combined list to the one existing QuizScreen — no
 * parallel engine.
 *
 * Everything in this file is pure (no Android, no I/O) so the
 * naming, assembly, ordering and coverage rules are unit-testable
 * off-device.
 */

/** The 27 official وطني subjects in their canonical order
 * (final_topics.json — the same order the topic classification and
 * the published bank use). Assembly follows this order; a subject
 * missing from the list sorts after all listed ones, by name. */
val OFFICIAL_SUBJECT_ORDER: List<String> = listOf(
    "الأذنية", "الأطفال 1", "الأطفال 2", "التوليد",
    "الجراحة البولية", "الجراحة التجميلية", "الجراحة العامة",
    "الجراحة العصبية", "الجراحة العظمية", "الجلدية", "الخمجية",
    "الدموية", "الرثوية", "الصدرية", "الطب النفسي", "العصبية",
    "العينية", "الغدية والسكري", "القلبية", "الكلية والشوارد",
    "النسائية", "الهضمية والكبد", "جراحة الأطفال", "جراحة الأورام",
    "جراحة الأوعية", "جراحة الصدر", "جراحة القلب"
)

/** Rank of a subject in the official order; unlisted subjects rank
 * after every listed one. */
fun officialSubjectRank(subjectName: String): Int {
    val idx = OFFICIAL_SUBJECT_ORDER.indexOf(subjectName.trim())
    return if (idx >= 0) idx else OFFICIAL_SUBJECT_ORDER.size
}

private val DAWRA_YEAR_SUFFIX = Regex("""^(.+?)\s+(20\d{2})$""")

/**
 * A session is a named «دورة» (a real dated national session) when
 * its name is Arabic month word(s) + a 20XX year and nothing else:
 * «أيار 2026», «آب وتموز 2025», «نيسان وكانون 2018». The bank's
 * archival buckets never match: «دورات قديمة» and its ranged
 * variants carry no such clean suffix (and are excluded explicitly),
 * «دورة الدكتوراه 2025» starts with «دورة», and the «بدون شهر محدد»
 * and «بنك …» buckets fail the shape. Validated against the whole
 * published bank (Nov 2026): the rule yields exactly the 17 named
 * dawrat and nothing else.
 */
fun isNamedDawra(sessionName: String?): Boolean {
    val name = sessionName?.trim().orEmpty()
    if (name.isEmpty()) return false
    if (name.startsWith("دورة") || name.startsWith("دورات") || name.startsWith("بنك")) return false
    if (name.contains("قديمة")) return false
    val match = DAWRA_YEAR_SUFFIX.matchEntire(name) ?: return false
    val head = match.groupValues[1]
    // The head must be Arabic words/spaces only — no digits, dashes
    // or parentheses (those mark the archival/range buckets).
    return head.all { it.isLetter() || it.isWhitespace() }
}

/** The 4-digit year a named dawra carries, or null. */
fun dawraYear(sessionName: String?): Int? {
    val name = sessionName?.trim().orEmpty()
    return DAWRA_YEAR_SUFFIX.matchEntire(name)?.groupValues?.get(2)?.toIntOrNull()
}

/** One downloaded subject's session table, counts only — the light
 * input for the dawra list and the coverage math (question objects
 * are loaded only when a dawra is actually opened). */
data class DawraSubjectSessions(
    val subjectName: String,
    val path: String,
    val sessionCounts: Map<String, Int>
)

/** A named dawra as the list screen shows it: its literal bank name,
 * how many of its questions are on this device, and in how many of
 * the downloaded subjects it appears. [standaloneFile] (1.2.32)
 * marks a dawra whose totals come from its downloaded standalone
 * dawrat_sessions file — it is complete on the device from that one
 * file alone; [inManifest] marks a dawra the manifest carries a
 * standalone file for (downloadable from its detail page). */
data class DawraInfo(
    val name: String,
    val totalQuestions: Int,
    val subjectsPresent: Int,
    val standaloneFile: Boolean = false,
    val inManifest: Boolean = false
)

/**
 * Lists the named dawrat available locally. [inputs] must already be
 * in official subject order (the caller sorts); dawrat appear in
 * first-appearance order across the inputs — the shards store
 * sessions newest-first, so the list reads newest dawra first,
 * exactly like the in-subject «حسب السنوات» view. Sessions that are
 * not named dawrat (archival buckets) never appear.
 */
fun listDawrat(inputs: List<DawraSubjectSessions>): List<DawraInfo> {
    val totals = LinkedHashMap<String, Int>()
    val present = HashMap<String, Int>()
    for (input in inputs) {
        for ((sessionName, count) in input.sessionCounts) {
            if (count <= 0) continue
            if (!isNamedDawra(sessionName)) continue
            totals[sessionName] = (totals[sessionName] ?: 0) + count
            present[sessionName] = (present[sessionName] ?: 0) + 1
        }
    }
    return totals.map { (name, total) ->
        DawraInfo(name = name, totalQuestions = total, subjectsPresent = present[name] ?: 0)
    }
}

/** Subjects in the bank (the manifest's questions/ stems) that are
 * NOT downloaded — the only «missing» subjects for coverage. A
 * downloaded subject that simply has no session for a dawra is not
 * missing: its contribution at the source is zero. */
fun missingSubjects(allSubjects: List<String>, downloadedSubjects: Collection<String>): List<String> {
    val downloaded = downloadedSubjects.toSet()
    return allSubjects.filter { it !in downloaded }
}

/**
 * Standalone-file counts from the manifest (1.2.32): dawra name ->
 * declared question count, for every `dawrat_sessions/<name>.json`
 * entry. The index file itself (dawrat_sessions.json) is not a unit
 * and never appears here.
 */
fun manifestDawraCounts(files: Map<String, ManifestFileEntry>): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()
    for ((path, entry) in files) {
        val name = dawraSessionNameOfPath(path) ?: continue
        counts[name] = entry.count ?: 0
    }
    return counts
}

/**
 * The dawra list merge (1.2.32): one entry per named dawra across
 * the three sources, best source winning per dawra —
 *  1. the downloaded standalone file's own totals (complete alone),
 *  2. the downloaded subject shards' assembly totals,
 *  3. the manifest's standalone-file count (nothing downloaded yet;
 *     the dawra is still listed so its file can be downloaded).
 * Order: shard-appearance order first (newest dawra first, as
 * [listDawrat] yields), then file-only dawrat in their input order,
 * then manifest-only dawrat newest-year first.
 */
fun mergeDawraInfos(
    shardInfos: List<DawraInfo>,
    fileInfos: List<DawraInfo>,
    manifestCounts: Map<String, Int>
): List<DawraInfo> {
    val fileByName = fileInfos.associateBy { it.name }
    val merged = ArrayList<DawraInfo>()
    val seen = HashSet<String>()
    for (info in shardInfos) {
        seen += info.name
        val file = fileByName[info.name]
        merged += if (file != null) {
            file.copy(standaloneFile = true, inManifest = info.name in manifestCounts)
        } else {
            info.copy(inManifest = info.name in manifestCounts)
        }
    }
    for (info in fileInfos) {
        if (info.name in seen) continue
        seen += info.name
        merged += info.copy(standaloneFile = true, inManifest = info.name in manifestCounts)
    }
    val manifestOnly = manifestCounts.keys
        .filter { it !in seen && isNamedDawra(it) }
        .sortedWith(
            compareByDescending<String> { dawraYear(it) ?: Int.MIN_VALUE }
                .thenBy { it }
        )
    for (name in manifestOnly) {
        merged += DawraInfo(
            name = name,
            totalQuestions = manifestCounts[name] ?: 0,
            subjectsPresent = 0,
            standaloneFile = false,
            inManifest = true
        )
    }
    return merged
}

/**
 * The inputs a dawra's detail screen computes coverage from
 * (1.2.32): when the dawra's standalone file is downloaded, ITS
 * per-subject tables are the whole story (the file is complete
 * alone); otherwise the subject-shard tables, exactly as before.
 */
fun resolveDawraInputs(
    shardInputs: List<DawraSubjectSessions>,
    fileInputs: List<DawraSubjectSessions>,
    dawraName: String
): List<DawraSubjectSessions> {
    val fromFile = fileInputs.filter { (it.sessionCounts[dawraName] ?: 0) > 0 }
    return if (fromFile.isNotEmpty()) fromFile else shardInputs
}

/** A contiguous run of one subject's questions inside an assembled
 * full-dawra quiz: [startIndex] into the flat question list. */
data class QuizSectionSpan(
    val subjectName: String,
    val startIndex: Int,
    val count: Int
) {
    operator fun contains(index: Int): Boolean = index in startIndex until (startIndex + count)
}

/** The assembled full-dawra quiz: the flat question list plus the
 * per-subject spans over it (same order). */
data class DawraQuiz(
    val sections: List<QuizSectionSpan>,
    val questions: List<QuestionDto>
) {
    /** The subject a flat-list [index] belongs to, or null. */
    fun subjectAt(index: Int): String? =
        sections.firstOrNull { index in it }?.subjectName
}

/**
 * Builds the full-dawra quiz from per-subject question lists:
 * subjects are placed in the official order regardless of input
 * order, empty subjects are skipped, and each subject's questions
 * keep their in-shard order (the session's own order).
 */
fun buildDawraQuiz(subjectQuestions: List<Pair<String, List<QuestionDto>>>): DawraQuiz {
    val ordered = subjectQuestions
        .filter { it.second.isNotEmpty() }
        .sortedWith(compareBy({ officialSubjectRank(it.first) }, { it.first }))
    val sections = ArrayList<QuizSectionSpan>(ordered.size)
    val flat = ArrayList<QuestionDto>()
    for ((subjectName, questions) in ordered) {
        sections += QuizSectionSpan(
            subjectName = subjectName,
            startIndex = flat.size,
            count = questions.size
        )
        flat += questions
    }
    return DawraQuiz(sections = sections, questions = flat)
}
