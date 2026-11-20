package sy.medpro.watani.data

import java.text.Normalizer
import kotlin.math.ln

/**
 * «الباحث المعتمد» — the pure search core (build 1.2.33).
 *
 * This is the Kotlin third of ONE shared spec whose other thirds are
 * the bundle builder (release-prep/ref-assistant/build_refsearch.py)
 * and the gateway Worker (watani-api worker.js): normalization,
 * tokenization, stopwords, FNV-1a shard mapping and BM25-idf scoring
 * MUST agree across all three, and the shared test vectors
 * (refsearch_vectors.json) pin the agreement — RefAssistantLogicTest
 * runs them in CI. Nothing here touches Android or I/O.
 */
object RefAssistantLogic {

    /** Index shards in the bundle (term -> shard = fnv1a32 % 64). */
    const val INDEX_SHARDS = 64

    /** Chunks per chunk shard (idx -> shard = idx / 300). */
    const val CHUNKS_PER_SHARD = 300

    /**
     * The effective stopword set — NORMALIZED forms only, identical to
     * the builder's exported list (130 entries). Query tokens are
     * compared after normalization, so the set must be normalized too.
     */
    val STOPWORDS: Set<String> = setOf(
        "about", "after", "against", "all", "also", "among", "and", "any", "are", "based",
        "been", "before", "being", "between", "both", "can", "chapter", "could", "did", "does",
        "during", "each", "few", "fig", "following", "for", "from", "given", "had", "has", "have",
        "having", "her", "his", "how", "however", "into", "its", "many", "may", "might", "more",
        "most", "much", "must", "not", "one", "other", "others", "our", "over", "page", "per",
        "section", "see", "shall", "she", "should", "some", "such", "table", "than", "that", "the",
        "their", "then", "these", "they", "this", "those", "three", "through", "two", "under",
        "upon", "use", "used", "using", "via", "was", "were", "what", "when", "where", "which",
        "who", "whom", "will", "with", "within", "without", "would", "yes", "you", "your",
        "اثناء", "اقل", "اكثر", "التي", "الذي", "الذين", "الي", "ايضا", "اين", "بعد",
        "بين", "تكون", "تلك", "حيث", "خلال", "ذات", "ذلك", "عبر", "علي", "عند",
        "غير", "قبل", "كان", "كانت", "كيف", "لدي", "لقد", "لكن", "لماذا", "متي",
        "نحو", "هذا", "هذه", "يتم", "يكون"
    )

    private val MARKS = Regex("\\p{M}+")
    private val TOKEN = Regex("[\\p{L}\\p{N}]+")

    /**
     * lowercase -> NFD -> strip all Mark chars -> Arabic folds
     * (ى->ي, ة->ه, ٱ->ا, drop tatweel). Display text is never
     * normalized — this is for matching only, on both sides.
     */
    fun normalizeRefText(s: String): String {
        var t = s.lowercase()
        t = Normalizer.normalize(t, Normalizer.Form.NFD)
        t = MARKS.replace(t, "")
        t = t.replace('ى', 'ي').replace('ة', 'ه').replace('ٱ', 'ا').replace("ـ", "")
        return t
    }

    /** Distinct normalized query terms (length >= 3, not stopwords),
     * in first-appearance order — the query side of the shared spec. */
    fun tokenizeRefQuery(s: String): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        for (m in TOKEN.findAll(normalizeRefText(s))) {
            val t = m.value
            if (t.length >= 3 && t !in STOPWORDS && seen.add(t)) out += t
        }
        return out
    }

    /** FNV-1a 32-bit over the UTF-8 bytes, returned unsigned. */
    fun fnv1a32(s: String): Long {
        var h = 2166136261L
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xFF)
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return h
    }

    fun indexShardOf(term: String): Int = (fnv1a32(term) % INDEX_SHARDS).toInt()

    fun chunkShardOf(idx: Int): Int = idx / CHUNKS_PER_SHARD

    /**
     * BM25-idf scoring identical to the builder/Worker: each entry is
     * (df, postings of [idx, tf]); score = sum of (1+ln tf)*idf with
     * idf = ln(1 + (N - df + 0.5)/(df + 0.5)). Ranked by score desc,
     * ties by idx asc.
     */
    fun scoreCandidates(
        entries: List<Pair<Int, List<Pair<Int, Int>>>>,
        totalChunks: Int
    ): List<Pair<Int, Double>> {
        val scores = HashMap<Int, Double>()
        for ((df, postings) in entries) {
            if (df <= 0) continue
            val idf = ln(1.0 + (totalChunks - df + 0.5) / (df + 0.5))
            for ((idx, tf) in postings) {
                scores[idx] = (scores[idx] ?: 0.0) + (1.0 + ln(tf.toDouble())) * idf
            }
        }
        return scores.entries
            .map { it.key to it.value }
            .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })
    }
}
