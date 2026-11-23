package sy.medpro.watani.data

import java.security.MessageDigest

/**
 * The free-bank subjects (build 1.2.47 — the doctor's activation
 * policy of 23 Nov 2026): the question banks are LOCKED by default
 * for signed-in-but-not-activated students, with exactly THREE
 * subjects open in full (questions + explanations + every view +
 * offline download) in the national bank and in EVERY university
 * bank alike:
 *
 *   القلبية — التوليد — الكلية والشوارد
 *   (cardiology, obstetrics, nephrology)
 *
 * This object is the ONE place the list lives: to change the free
 * subjects later, edit [FREE_SUBJECTS] only — every gate (quiz
 * entry, section fetch, explanation shards, the revocation wipe)
 * derives from it, and the worker gateway carries the mirror list
 * (watani-api/worker.js, FREE_BANK_* — keep the two in sync).
 *
 * Matching is by the subject's FILE NAME (the last path segment,
 * without ".json"), because the banks name the same subject
 * differently: the national bank and Hama/Idlib use the canonical
 * names (القلبية / التوليد / الكلية والشوارد), Damascus uses the
 * short forms (قلبية / توليد / كلية), and Aleppo/Homs/Lattakia use
 * their own forms (باطنة قلبية / طب التوليد / أمراض الكلية
 * الباطنة). Each free subject therefore carries its full alias
 * set, and matching is EXACT against the whole file name — never
 * a substring — so lookalikes stay locked: «جراحة القلب» and
 * «جراحة القلب والصدر» are NOT cardiology, «الأمراض النسائية» is
 * NOT obstetrics, and «الجراحة العامة» is not free (it was in the
 * first draft of the policy and the doctor replaced it with
 * nephrology the same evening — the final list above is his).
 *
 * Banks that simply do not carry a subject grant nothing for it
 * (documented in RELEASE_1_2_47.md): Lattakia has none of the
 * three except nephrology (أمراض الكلية الباطنة), Tartus only
 * obstetrics, Aleppo carries cardiology as «باطنة قلبية» and
 * obstetrics as «طب التوليد» (no nephrology file at all).
 *
 * What is NEVER free, by the same order: UWorld (locked in full),
 * the video library (locked in full), the standalone dawra-session
 * files (dawrat_sessions/ — one file mixes every subject, so it
 * cannot be freed per subject; a full-dawra run stays locked),
 * books/, and every non-listed subject. First Aid and the clinical
 * cases keep their own pre-existing gates and are not governed
 * here at all.
 */
object FreeBankSubjects {

    /** One free subject: its canonical (national-bank) name plus
     * every file-name alias the banks use for it. */
    data class FreeSubject(
        val canonical: String,
        val aliases: Set<String>
    )

    /** THE free list — the single editable constant. */
    val FREE_SUBJECTS: List<FreeSubject> = listOf(
        FreeSubject(
            canonical = "القلبية",
            aliases = setOf("القلبية", "قلبية", "باطنة قلبية")
        ),
        FreeSubject(
            canonical = "التوليد",
            aliases = setOf("التوليد", "توليد", "طب التوليد")
        ),
        FreeSubject(
            canonical = "الكلية والشوارد",
            aliases = setOf("الكلية والشوارد", "كلية", "أمراض الكلية الباطنة")
        )
    )

    /** Every accepted file-name form of every free subject. */
    val ALL_ALIASES: Set<String> = FREE_SUBJECTS.flatMap { it.aliases }.toSet()

    /** Path heads (under v1/) whose per-subject files can be free:
     * the national bank (Arabic + English copies), the university
     * banks (including the Damascus years twin, which lives one
     * level deeper under dawrat_jami3at/), and the explanation
     * layer (one shard per subject, named like the banks). */
    private val FREE_HEADS = setOf("questions", "questions_en", "dawrat_jami3at", "explanations")

    /**
     * True when [rawPath] is one of the free subjects' own files:
     * `questions/<alias>.json`, `questions_en/<alias>.json`,
     * `dawrat_jami3at/<university>/<alias>.json` (the university
     * segment is free-form — damascus, damascus_years, hama, …),
     * `explanations/<alias>.json`, plus `explanations/index.json`
     * (the layer index is metadata the free shards cannot be
     * reached without; the shards themselves stay gated one by
     * one). Everything else — other subjects, dawrat_sessions/,
     * uworld/, library.json, books/ — is false.
     */
    fun isFreeBankPath(rawPath: String): Boolean {
        val rel = rawPath.trim().trimStart('/').substringBefore('?').substringBefore('#')
        if (rel == "explanations/index.json") return true
        val segments = rel.split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return false
        if (segments.first() !in FREE_HEADS) return false
        // Shape: head/<file> or head/<university>/<file> — the
        // university banks are the only three-segment family.
        val shapeOk = when (segments.first()) {
            "dawrat_jami3at" -> segments.size == 3
            else -> segments.size == 2
        }
        if (!shapeOk) return false
        val fileName = segments.last()
        if (!fileName.endsWith(".json")) return false
        return fileName.removeSuffix(".json") in ALL_ALIASES
    }

    /** Bank section-file prefixes inside sections_cache (the
     * sanitized form of the path heads above). */
    private val CACHE_PREFIXES = listOf(
        "questions_", "questions_en_", "dawrat_jami3at_", "explanations_"
    )

    /**
     * True when a sections_cache file name (path with '/' → '_',
     * plus the appended ".json" — see WataniRepository.getSectionFile)
     * stores one of the free subjects' files. Used by the
     * revocation wipe: free-subject downloads are free content now
     * and are never confiscated. The subject name is the segment
     * after the LAST '_' (subject names contain spaces, never
     * underscores; the only underscored segment, damascus_years,
     * is never last).
     */
    fun isFreeBankCacheFile(fileName: String): Boolean {
        val base = fileName.removeSuffix(".tmp")
        if (!base.endsWith(".json")) return false
        val core = base.removeSuffix(".json")
        if (!core.endsWith(".json")) return false
        if (CACHE_PREFIXES.none { core.startsWith(it) }) return false
        val subjectFile = core.substringAfterLast('_')
        return subjectFile.removeSuffix(".json") in ALL_ALIASES
    }

    /** Hex chars kept from the shard-name hash (mirrors
     * ExplanationRepository.shardHash exactly). */
    private const val SHARD_HASH_HEX_LENGTH = 16

    /** sha256 hex prefix — mirrors ExplanationRepository.shardHash. */
    private fun shardHash(shardName: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(shardName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(SHARD_HASH_HEX_LENGTH)

    /**
     * The explanation_layers cache names that belong to the free
     * subjects: each free shard's hashed file (+ its fingerprint
     * sidecar, resume temp and encrypted-write temp), and the
     * layer index itself. The revocation wipe keeps these — the
     * shards are free content and re-serve any signed-in student.
     */
    val freeExplanationCacheFileNames: Set<String> by lazy {
        val names = mutableSetOf("index.json", "index.json.enc.tmp")
        for (alias in ALL_ALIASES) {
            val h = shardHash("$alias.json")
            names += "$h.json"
            names += "$h.fp"
            names += "$h.tmp"
            names += "$h.json.enc.tmp"
        }
        names
    }

    /** True when an explanation_layers file name is free content. */
    fun isFreeExplanationCacheFile(fileName: String): Boolean =
        fileName in freeExplanationCacheFileNames
}
