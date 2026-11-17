package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.31 pure-logic pins — the doctor's four device reports
 * on 1.2.30:
 *
 * 1. The question-id index resolves every id to the FIRST section
 *    (registry order, topics before sessions) that claims it —
 *    exactly where the old per-lookup linear scan found it, so
 *    «افتح السؤال في البنك» opens the same record it always did,
 *    just without rescanning every shard per question.
 * 2. Playback errors render as friendly Arabic by media3 error
 *    code family — network, local-file, decoding, generic — and
 *    never as a raw exception message.
 */
class Build1231LogicTest {

    // ---------------- Question-id index ----------------

    @Test
    fun indexMapsIdsToTheirSection() {
        val index = buildQuestionIdIndex(
            listOf(
                "questions/القلبية.json" to listOf("NAT-QLB-0001", "NAT-QLB-0002"),
                "questions/العصبية.json" to listOf("NAT-ASB-0001")
            )
        )
        assertEquals("questions/القلبية.json", index["NAT-QLB-0001"])
        assertEquals("questions/القلبية.json", index["NAT-QLB-0002"])
        assertEquals("questions/العصبية.json", index["NAT-ASB-0001"])
        assertEquals(3, index.size)
    }

    @Test
    fun indexFirstClaimWinsAcrossSections() {
        // The same question can live in two banks (a dawra question
        // in both watani and damascus envelopes): the old linear
        // scan returned the first envelope in registry order, and
        // the index must keep that winner.
        val index = buildQuestionIdIndex(
            listOf(
                "questions/القلبية.json" to listOf("Q-1"),
                "dawrat_jami3at/القلبية.json" to listOf("Q-1", "Q-2")
            )
        )
        assertEquals("questions/القلبية.json", index["Q-1"])
        assertEquals("dawrat_jami3at/القلبية.json", index["Q-2"])
    }

    @Test
    fun indexFirstClaimWinsInsideOneSection() {
        // Topics are collected before sessions within an envelope;
        // a duplicated id inside one list still maps to that list's
        // section exactly once.
        val index = buildQuestionIdIndex(
            listOf("questions/الأطفال 1.json" to listOf("T-1", "T-1", "S-1"))
        )
        assertEquals("questions/الأطفال 1.json", index["T-1"])
        assertEquals("questions/الأطفال 1.json", index["S-1"])
        assertEquals(2, index.size)
    }

    @Test
    fun indexEmptyAndMissing() {
        assertTrue(buildQuestionIdIndex(emptyList()).isEmpty())
        assertTrue(buildQuestionIdIndex(listOf("a/b.json" to emptyList())).isEmpty())
        val index = buildQuestionIdIndex(listOf("a/b.json" to listOf("X")))
        assertNull(index["NOT-THERE"])
    }

    // ---------------- Playback error text ----------------

    @Test
    fun networkErrorsSpeakOfConnection() {
        // media3: 2001 connection failed, 2002 timeout,
        // 2003 invalid HTTP content type, 2004 bad HTTP status.
        for (code in listOf(2001, 2002, 2003, 2004)) {
            assertTrue(
                "code $code",
                playbackErrorMessage(code).contains("الاتصال")
            )
        }
    }

    @Test
    fun fileErrorsSpeakOfTheLocalFile() {
        // media3: 2005 file not found, 2006 no permission,
        // 2007 cleartext not permitted, 2008 read position.
        for (code in listOf(2005, 2006, 2007, 2008)) {
            assertTrue(
                "code $code",
                playbackErrorMessage(code).contains("ملف الفيديو")
            )
        }
    }

    @Test
    fun decodingFamilyAndFallback() {
        assertTrue(playbackErrorMessage(3001).contains("فكّ تشفير"))
        assertTrue(playbackErrorMessage(3003).contains("فكّ تشفير"))
        assertTrue(playbackErrorMessage(3999).contains("فكّ تشفير"))
        // Unknown / non-IO codes fall back to the generic sentence.
        assertEquals("حدث خطأ أثناء تشغيل الفيديو.", playbackErrorMessage(0))
        assertEquals("حدث خطأ أثناء تشغيل الفيديو.", playbackErrorMessage(1001))
        assertEquals("حدث خطأ أثناء تشغيل الفيديو.", playbackErrorMessage(5001))
    }
}
