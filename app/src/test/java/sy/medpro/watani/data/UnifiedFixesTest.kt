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

    // ---- Build 1.2.6: skills trainers (procedures ordering + dosing) ----

    @Test
    fun procedureOrderingPerfectScore() {
        val correct = (1..5).toList()
        val score = sy.medpro.watani.ui.osce.ProcedureScoring.score(
            correctOrders = correct,
            studentOrders = correct,
            criticalOrders = setOf(2, 4)
        )
        assertEquals(5, score.totalSteps)
        assertEquals(5, score.exactPositions)
        assertEquals(100, score.percent)
        assertTrue(score.positionCorrect.all { it })
        assertTrue(score.missedCriticalOrders.isEmpty())
        assertTrue(
            sy.medpro.watani.ui.osce.ProcedureScoring.isCompleteArrangement(correct, correct)
        )
    }

    @Test
    fun procedureOrderingSwappedCriticalStepIsFlagged() {
        // Steps 2 (critical) and 3 swapped; everything else exact.
        val score = sy.medpro.watani.ui.osce.ProcedureScoring.score(
            correctOrders = listOf(1, 2, 3, 4, 5),
            studentOrders = listOf(1, 3, 2, 4, 5),
            criticalOrders = setOf(2)
        )
        assertEquals(3, score.exactPositions)
        assertEquals(60, score.percent)
        assertEquals(listOf(true, false, false, true, true), score.positionCorrect)
        // The critical step 2 missed its position; step 3 is wrong but
        // not critical, so it must NOT appear in the critical list.
        assertEquals(listOf(2), score.missedCriticalOrders)
    }

    @Test
    fun procedureOrderingReversedScoresZero() {
        val score = sy.medpro.watani.ui.osce.ProcedureScoring.score(
            correctOrders = listOf(1, 2, 3, 4),
            studentOrders = listOf(4, 3, 2, 1),
            criticalOrders = setOf(1, 4)
        )
        assertEquals(0, score.exactPositions)
        assertEquals(0, score.percent)
        assertEquals(listOf(1, 4), score.missedCriticalOrders)
        // A partial arrangement (missing a step) is not complete.
        assertTrue(
            !sy.medpro.watani.ui.osce.ProcedureScoring.isCompleteArrangement(
                listOf(1, 2, 3, 4), listOf(4, 3, 2)
            )
        )
    }

    @Test
    fun dosingSessionScoringCountsAndMisses() {
        val answers = listOf(
            sy.medpro.watani.ui.dosing.DrillAnswer("dose-01", chosenIndex = 1, correctIndex = 1),
            sy.medpro.watani.ui.dosing.DrillAnswer("dose-02", chosenIndex = 0, correctIndex = 2),
            sy.medpro.watani.ui.dosing.DrillAnswer("dose-03", chosenIndex = 3, correctIndex = 3),
            sy.medpro.watani.ui.dosing.DrillAnswer("dose-04", chosenIndex = 2, correctIndex = 0)
        )
        val score = sy.medpro.watani.ui.dosing.DosingScoring.score(answers)
        assertEquals(4, score.total)
        assertEquals(2, score.correct)
        assertEquals(50, score.percent)
        assertEquals(listOf("dose-02", "dose-04"), score.missedDrillIds)

        val empty = sy.medpro.watani.ui.dosing.DosingScoring.score(emptyList())
        assertEquals(0, empty.total)
        assertEquals(0, empty.percent)
        assertTrue(empty.missedDrillIds.isEmpty())
    }

    @Test
    fun skillsPacksVerifyAsBareArrays() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val proceduresJson = """
            [{"id":"p1","ar":"إجراء","en":"Proc","category":"تشخيصي",
              "indicationsAr":[],"contraindicationsAr":[],"equipmentAr":[],
              "steps":[{"order":1,"ar":"خطوة","whyAr":"سبب","critical":true}],
              "aftercareAr":[],"complicationsAr":[],
              "sources":[{"name":"Src","url":"https://example.com"}],"sourceNote":"ملاحظة"}]
        """.trimIndent()
        assertEquals(
            1,
            SectionVerification.verifySectionCount("osce_procedures.json", proceduresJson, moshi)
        )
        val drillsJson = """
            [{"id":"dose-01","drug":"Drug","scenarioAr":"حالة",
              "patient":{"age":30,"sex":"M","weightKg":70},
              "focusAr":"سؤال","options":["a","b","c","d"],"correctIndex":1,
              "explanationAr":"شرح",
              "source":{"name":"Src","detail":"d","url":"https://example.com"}}]
        """.trimIndent()
        assertEquals(
            1,
            SectionVerification.verifySectionCount("dosing_drills.json", drillsJson, moshi)
        )
        // An empty pack must be rejected, never blessed as count 0.
        var rejected = false
        try {
            SectionVerification.verifySectionCount("osce_procedures.json", "[]", moshi)
        } catch (e: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
