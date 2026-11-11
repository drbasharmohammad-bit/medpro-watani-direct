package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.clinical.evaluateCalcSpec
import sy.medpro.watani.ui.clinical.CalcOutcome

/**
 * Unified fixes build (Nov 2026) regression tests:
 * - Blake2b vectors generated with Python hashlib.blake2b (the exact
 *   call the publishing pipeline used for the questions/ manifest).
 * - MELD-Na OPTN floor rule (F-10).
 * - Answer-key resolution (F-08): valid keys resolve exactly as the
 *   legacy normalizer did; invalid keys are rejected, never mapped to 0.
 */
class UnifiedFixesTest {

    @Test
    fun blake2b8VectorsMatchPython() {
        assertEquals("e4a6a0577479b2b4", Blake2b.hashHex(ByteArray(0)))
        assertEquals("d8bb14d833d59559", Blake2b.hashHex("abc".toByteArray(Charsets.UTF_8)))
        assertEquals(
            "572983d150ac98cb",
            Blake2b.hashHex(ByteArray(300) { (it % 251).toByte() })
        )
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87" +
                "c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            Blake2b.hashHex("abc".toByteArray(Charsets.UTF_8), digestSize = 64)
        )
    }

    @Test
    fun meldNaFloorRule() {
        // Below/at the floor: MELD-Na == MELD (OPTN), NOT the formula.
        val floored = evaluateCalcSpec("meld_na", mapOf("meld" to 8.0, "na" to 125.0))
        assertTrue(floored is CalcOutcome.Value && floored.text.startsWith("MELD-Na = 8 "))
        val floored10 = evaluateCalcSpec("meld_na", mapOf("meld" to 10.0, "na" to 130.0))
        assertTrue(floored10 is CalcOutcome.Value && floored10.text.startsWith("MELD-Na = 10 "))
        // Above the floor: the documented formula example, unchanged.
        val normal = evaluateCalcSpec("meld_na", mapOf("meld" to 20.0, "na" to 130.0))
        assertTrue(normal is CalcOutcome.Value && normal.text.startsWith("MELD-Na = 25 "))
    }

    private fun q(correct: Any?, indices: List<Any>? = null) = QuestionDto(
        id = "t", text = "q", options = listOf("a", "b", "c", "d"),
        optionLabels = null, correct = correct, correctIndices = indices,
        explanation = null, years = null, source = null, session = null,
        topics = null, free = null
    )

    @Test
    fun validKeysResolveExactlyAsLegacy() {
        val opts = listOf("a", "b", "c", "d")
        assertEquals(listOf(2), getCorrectIndices(q(2.0), opts))
        assertEquals(listOf(1), getCorrectIndices(q("1"), opts))
        assertEquals(listOf(3), getCorrectIndices(q("د"), opts))
        assertEquals(listOf(0), getCorrectIndices(q("أ"), opts))
        assertEquals(listOf(2), getCorrectIndices(q("c"), opts))
        assertEquals(listOf(1, 3), getCorrectIndices(q(null, listOf(1.0, 3.0)), opts))
        // Option-text match path of the legacy normalizer.
        assertEquals(listOf(1), getCorrectIndices(q("b"), opts))
    }

    @Test
    fun invalidKeysAreRejectedNotMappedToZero() {
        val opts = listOf("a", "b", "c", "d")
        val before = invalidCorrectKeyCount.get()
        assertTrue(getCorrectIndices(q(null), opts).isEmpty())
        assertTrue(getCorrectIndices(q(9.0), opts).isEmpty())
        assertTrue(getCorrectIndices(q("غير موجود"), opts).isEmpty())
        // A partially corrupt correctIndices list rejects the question.
        assertTrue(getCorrectIndices(q(null, listOf(1.0, 9.0)), opts).isEmpty())
        assertEquals(before + 4, invalidCorrectKeyCount.get())
    }

    @Test
    fun libraryVideosOnlyStripsBooksAndBooksOnlySubjects() {
        // Doctor's order (11 Nov 2026): books are published outside the
        // app and must never surface in the in-app Library.
        fun item(id: String) = LibraryItem(id, id, "https://x/$id", 1L, null, null)
        val envelope = LibraryEnvelope(
            listOf(
                // Books-only subject (the real library.json shape: all 17
                // books live in one such subject) — must vanish entirely.
                LibrarySubject("books", "كتب", listOf(item("b1"), item("b2")), emptyList()),
                // Mixed subject — videos survive untouched, books dropped.
                LibrarySubject("cardio", "قلبية", listOf(item("b3")), listOf(item("v1"), item("v2"))),
                // Videos-only subject — unchanged.
                LibrarySubject("neuro", "عصبية", emptyList(), listOf(item("v3"))),
                // Null-books subject — unchanged.
                LibrarySubject("renal", "كلية", null, listOf(item("v4")))
            )
        )
        val filtered = envelope.videosOnly()
        assertEquals(listOf("cardio", "neuro", "renal"), filtered.subjects!!.map { it.id })
        assertTrue(filtered.subjects!!.all { it.books.isNullOrEmpty() })
        assertEquals(listOf("v1", "v2"), filtered.subjects!![0].videos!!.map { it.id })
        assertEquals(4, filtered.subjects!!.sumOf { it.videos!!.size })
        // Original envelope is not mutated (repository caches stay raw).
        assertEquals(4, envelope.subjects!!.size)
        assertEquals(2, envelope.subjects!![0].books!!.size)
    }
    // -----------------------------------------------------------------
    // بنك أخطائي (1.2.5): identity composition + SM-2-lite scheduling.
    // -----------------------------------------------------------------

    @Test
    fun mistakeKeyPrefersStableId() {
        assertEquals(
            "questions|questions/القلبية.json|id:NAT-CAR-0001",
            MistakeKeys.composeKey("questions", "questions/القلبية.json", "NAT-CAR-0001", 5, "نص السؤال")
        )
        // Blank/whitespace ids count as missing and fall through.
        assertEquals(
            "uworld|uworld/القلبية.json|idx:3",
            MistakeKeys.composeKey("uworld", "uworld/القلبية.json", "  ", 3, "stem")
        )
    }

    @Test
    fun mistakeKeyFallsBackToIndexThenStemHash() {
        assertEquals(
            "dawrat_jami3at|dawrat_jami3at/damascus/قلبية.json|idx:42",
            MistakeKeys.composeKey("dawrat_jami3at", "dawrat_jami3at/damascus/قلبية.json", null, 42, "أي نص")
        )
        val a = MistakeKeys.composeKey("daily", "", null, -1, "ما هو التشخيص؟")
        val b = MistakeKeys.composeKey("daily", "", null, -1, "ما هو التشخيص؟")
        val c = MistakeKeys.composeKey("daily", "", null, -1, "سؤال مختلف تمامًا")
        assertEquals(a, b) // deterministic for the same stem
        assertTrue(a.startsWith("daily||stem:"))
        assertTrue(a != c)
        // Normalization: extra whitespace does not change the hash key.
        assertEquals(
            a,
            MistakeKeys.composeKey("daily", "", null, -1, "  ما  هو   التشخيص؟ ")
        )
    }

    @Test
    fun mistakeBankOfSplitsSourcePath() {
        assertEquals(
            "dawrat_jami3at" to "dawrat_jami3at/hama/القلبية.json",
            MistakeKeys.bankOf("dawrat_jami3at/hama/القلبية.json", "عنوان")
        )
        assertEquals(
            "questions_en" to "questions_en/القلبية.json",
            MistakeKeys.bankOf("questions_en/القلبية.json", "عنوان")
        )
        assertEquals("quiz" to "اختبار سريع", MistakeKeys.bankOf(null, "اختبار سريع"))
        assertEquals("quiz" to "اختبار سريع", MistakeKeys.bankOf("  ", "اختبار سريع"))
    }

    @Test
    fun srsLadderGrowsThreeSevenTwentyOne() {
        var s = MistakesSrs.initial()
        assertEquals(MistakeSrsState(1, 0, false), s)
        s = MistakesSrs.onCorrect(s)
        assertEquals(MistakeSrsState(3, 0, false), s)
        s = MistakesSrs.onCorrect(s)
        assertEquals(MistakeSrsState(7, 0, false), s)
        // First correct at a >=7d interval: streak 1, interval grows to 21.
        s = MistakesSrs.onCorrect(s)
        assertEquals(MistakeSrsState(21, 1, false), s)
        // Second consecutive correct at a >=7d interval: mastered.
        s = MistakesSrs.onCorrect(s)
        assertEquals(MistakeSrsState(21, 2, true), s)
        // Mastered is terminal for scheduling.
        assertEquals(s, MistakesSrs.onCorrect(s))
    }

    @Test
    fun srsWrongResetsToOneDayAndUnmasters() {
        val progressed = MistakeSrsState(21, 1, false)
        assertEquals(MistakeSrsState(1, 0, false), MistakesSrs.onWrong(progressed))
        val mastered = MistakeSrsState(21, 2, true)
        assertEquals(MistakeSrsState(1, 0, false), MistakesSrs.onWrong(mastered))
        assertEquals(MistakeSrsState(1, 0, false), MistakesSrs.onWrong(MistakesSrs.initial()))
    }

    @Test
    fun srsDueMathAndQueueOrder() {
        val t0 = 1_700_000_000_000L
        assertEquals(t0 + MistakesSrs.DAY_MS, MistakesSrs.dueAtMillis(t0, 1))
        assertEquals(t0 + 21 * MistakesSrs.DAY_MS, MistakesSrs.dueAtMillis(t0, 21))

        fun entry(key: String, dueAt: Long, lastWrongAt: Long, mastered: Boolean = false) =
            MistakeEntry(
                key = key, bank = "questions", file = "questions/x.json", questionId = key,
                subject = "القلبية", stem = "stem $key", options = listOf("a", "b"),
                correctIndex = 0, explanation = "", whyCorrect = null, whyWrong = null,
                summary = null, ltr = false, firstWrongAt = lastWrongAt,
                lastWrongAt = lastWrongAt, wrongCount = 1, intervalDays = 1,
                longIntervalStreak = 0, mastered = mastered, dueAt = dueAt
            )

        val now = t0 + 10 * MistakesSrs.DAY_MS
        val overdueLong = entry("a", dueAt = t0 + 1 * MistakesSrs.DAY_MS, lastWrongAt = t0)
        val overdueShort = entry("b", dueAt = t0 + 5 * MistakesSrs.DAY_MS, lastWrongAt = t0)
        val futureRecent = entry("c", dueAt = t0 + 30 * MistakesSrs.DAY_MS, lastWrongAt = t0 + 9 * MistakesSrs.DAY_MS)
        val futureOld = entry("d", dueAt = t0 + 30 * MistakesSrs.DAY_MS, lastWrongAt = t0 + 2 * MistakesSrs.DAY_MS)
        val masteredOne = entry("e", dueAt = t0, lastWrongAt = t0, mastered = true)

        assertTrue(MistakesSrs.isDue(overdueLong, now))
        assertTrue(!MistakesSrs.isDue(futureRecent, now))
        assertTrue(!MistakesSrs.isDue(masteredOne, now))

        val queue = MistakesSrs.buildSessionQueue(
            listOf(futureOld, masteredOne, overdueShort, futureRecent, overdueLong),
            nowMillis = now,
            limit = 20
        )
        // Due first (most overdue first), then recent non-due; mastered out.
        assertEquals(listOf("a", "b", "c", "d"), queue.map { it.key })
        // Limit is honored.
        assertEquals(
            listOf("a", "b"),
            MistakesSrs.buildSessionQueue(listOf(overdueLong, overdueShort, futureRecent), now, limit = 2).map { it.key }
        )
    }
}
