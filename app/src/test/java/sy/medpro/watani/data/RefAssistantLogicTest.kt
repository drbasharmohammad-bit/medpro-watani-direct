package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Kotlin third of the shared «الباحث المعتمد» search spec
 * (build 1.2.33): the same normalization, tokenizer, stopwords,
 * FNV-1a shard mapping and BM25-idf scoring as the bundle builder
 * (build_refsearch.py) and the gateway Worker (worker.js). The
 * builder/Worker pair is pinned against the real shipped index by
 * watani-api/test_assistant.mjs (refsearch_vectors.json); the cases
 * below are the identical vectors and constants, so a drift in any
 * of the three implementations fails a test somewhere.
 */
class RefAssistantLogicTest {

    @Test
    fun stopwordsAreTheBuildersNormalizedSet() {
        assertEquals(130, RefAssistantLogic.STOPWORDS.size)
        // Normalized Arabic forms (hamza stripped, ى folded): the raw
        // forms («إلى», «أيضا») must NOT be what the set carries.
        assertTrue("الي" in RefAssistantLogic.STOPWORDS)
        assertTrue("ايضا" in RefAssistantLogic.STOPWORDS)
        assertTrue("إلى" !in RefAssistantLogic.STOPWORDS)
        assertTrue("the" in RefAssistantLogic.STOPWORDS)
    }

    @Test
    fun tokenizeEnglishSentence() {
        assertEquals(
            listOf("treatment", "heart", "failure"),
            RefAssistantLogic.tokenizeRefQuery("What is the treatment of heart failure?")
        )
    }

    @Test
    fun tokenizeArabicFolds() {
        // ة -> ه fold, and «ما» is a stopword.
        assertEquals(
            listOf("علاج", "المدرسه"),
            RefAssistantLogic.tokenizeRefQuery("ما علاج المدرسة؟")
        )
    }

    @Test
    fun tokenizeDropsShortAndDuplicates() {
        assertEquals(
            listOf("dka", "icu"),
            RefAssistantLogic.tokenizeRefQuery("DKA dka DKA in ICU")
        )
        assertEquals(emptyList<String>(), RefAssistantLogic.tokenizeRefQuery("what is the"))
    }

    @Test
    fun normalizeStripsDiacriticsAndTatweel() {
        assertEquals("التهاب", RefAssistantLogic.normalizeRefText("التهــابٌ"))
    }

    @Test
    fun fnvConstantsMatchTheBuilder() {
        assertEquals(2294697792L, RefAssistantLogic.fnv1a32("metformin"))
        assertEquals(0, RefAssistantLogic.indexShardOf("metformin"))
        assertEquals(246031843L, RefAssistantLogic.fnv1a32("heart"))
        assertEquals(35, RefAssistantLogic.indexShardOf("heart"))
        assertEquals(849107526L, RefAssistantLogic.fnv1a32("التهاب"))
        assertEquals(6, RefAssistantLogic.indexShardOf("التهاب"))
    }

    @Test
    fun scoringMatchesTheSharedSpec() {
        val entries = listOf(
            10 to listOf(5 to 3, 9 to 1),
            100 to listOf(5 to 1, 7 to 2)
        )
        val ranked = RefAssistantLogic.scoreCandidates(entries, 14350)
        assertEquals(3, ranked.size)
        assertEquals(5, ranked[0].first)
        // Ties break by idx ascending.
        val tie = RefAssistantLogic.scoreCandidates(listOf(5 to listOf(9 to 1, 3 to 1)), 1000)
        assertEquals(listOf(3, 9), tie.map { it.first })
    }

    @Test
    fun shardMapping() {
        assertEquals(0, RefAssistantLogic.chunkShardOf(0))
        assertEquals(0, RefAssistantLogic.chunkShardOf(299))
        assertEquals(1, RefAssistantLogic.chunkShardOf(300))
        assertEquals(47, RefAssistantLogic.chunkShardOf(14349))
    }
}
