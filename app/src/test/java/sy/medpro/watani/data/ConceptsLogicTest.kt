package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.13 («ادرس مع ميد برو»): the pure concepts logic the
 * screens rely on — Arabic text normalization, subject grouping,
 * quiz-question -> unit matching, and the self-test key resolution.
 * Build 1.2.14 pins the ordering contract: subjects and topics sort
 * WATANI-first (national-exam counts lead, damascus breaks ties,
 * the combined pack weight is the last tiebreak). Each test pins
 * one branch of the display contract: the section exists only for
 * downloaded units, a question links only on an exact normalized
 * topic match, and no key is ever invented.
 */
class ConceptsLogicTest {

    private fun unit(
        id: String,
        subject: String?,
        topic: String?,
        weight: Int?,
        watani: Int? = null,
        damascus: Int? = null
    ) = ConceptUnitDto(
        id = id, subject = subject, topic = topic, weight = weight,
        watani = watani, damascus = damascus, explanation = null, traps = null,
        selfTest = null, diffTable = null, crossLinks = null, mnemonic = null
    )

    @Test
    fun normalizationFoldsArabicVariants() {
        assertEquals(
            normalizeConceptText("قصور  القلب"),
            normalizeConceptText("قصور القلب")
        )
        assertEquals(
            normalizeConceptText("الإقفار"),
            normalizeConceptText("الاقفار")
        )
        assertEquals(
            normalizeConceptText("المتلازمة الكلوية"),
            normalizeConceptText("المتلازمه الكلويه")
        )
        assertEquals("", normalizeConceptText(null))
        assertEquals("", normalizeConceptText("   "))
    }

    @Test
    fun groupingSortsSubjectsByWataniFirstThenDamascus() {
        val units = listOf(
            // Cardiology: the heavier unit is damascus-driven; the
            // watani-heavier topic must still lead the subject.
            unit("a1", "القلبية", "موضوع دمشق", 500, watani = 10, damascus = 490),
            unit("a2", "القلبية", "موضوع وطني", 40, watani = 30, damascus = 10),
            // Nephrology: small pack weight, biggest watani total.
            unit("b1", "الكلية والشوارد", "موضوع كلوي", 100, watani = 50, damascus = 5),
            // Endocrine: ties nephrology on watani, wins on damascus.
            unit("c1", "الغدية", "موضوع غدي", 250, watani = 50, damascus = 200)
        )
        val groups = groupConceptsBySubject(units)
        assertEquals(3, groups.size)
        // Watani totals: endocrine 50 = nephrology 50 > cardiology 40;
        // the tie breaks on damascus totals (200 > 5).
        assertEquals("الغدية", groups[0].subject)
        assertEquals(50, groups[0].totalWatani)
        assertEquals(200, groups[0].totalDamascus)
        assertEquals("الكلية والشوارد", groups[1].subject)
        assertEquals("القلبية", groups[2].subject)
        assertEquals(40, groups[2].totalWatani)
        assertEquals(500, groups[2].totalDamascus)
        // Topics inside cardiology: watani 30 beats watani 10 despite
        // the far smaller pack weight.
        assertEquals(listOf("a2", "a1"), groups[2].units.map { it.id })
    }

    @Test
    fun groupingFallsBackToWeightWhenBankCountsTie() {
        val units = listOf(
            unit("a", "القلبية", "قصور القلب", 100),
            unit("b", "القلبية", "الذبحة", 60),
            unit("c", "الكلية والشوارد", "القصور الكلوي", 120),
            unit("d", "الكلية والشوارد", "اضطراب البوتاسيوم", 10)
        )
        val groups = groupConceptsBySubject(units)
        assertEquals(2, groups.size)
        // No bank counts anywhere: cardiology total weight 160 beats
        // nephrology total 130, topics by weight descending.
        assertEquals("القلبية", groups[0].subject)
        assertEquals(160, groups[0].totalWeight)
        assertEquals(listOf("a", "b"), groups[0].units.map { it.id })
        assertEquals("الكلية والشوارد", groups[1].subject)
    }

    @Test
    fun groupingDropsUnitsWithoutSubjectOrTopic() {
        val groups = groupConceptsBySubject(
            listOf(
                unit("a", "القلبية", "قصور القلب", 100),
                unit("b", null, "بلا مادة", 999),
                unit("c", "القلبية", "  ", 999)
            )
        )
        assertEquals(1, groups.size)
        assertEquals(100, groups[0].totalWeight)
        assertTrue(groupConceptsBySubject(emptyList()).isEmpty())
    }

    @Test
    fun matchingUsesNormalizedExactTopic() {
        val units = listOf(
            unit("u1", "القلبية", "قصور القلب", 162),
            unit("u2", "الصدرية", "الربو", 90)
        )
        assertEquals(
            "u1",
            findConceptUnit(units, "القلبية", listOf("قصور  القلب"))?.id
        )
        // A question-level topic candidate matches too.
        assertEquals(
            "u2",
            findConceptUnit(units, null, listOf(null, "الربو"))?.id
        )
        // Uncovered topic: no link.
        assertNull(findConceptUnit(units, "القلبية", listOf("التهاب الكبد")))
        assertNull(findConceptUnit(units, "القلبية", emptyList()))
        assertNull(findConceptUnit(emptyList(), "القلبية", listOf("قصور القلب")))
    }

    @Test
    fun matchingPrefersSubjectWhenTopicIsShared() {
        val units = listOf(
            unit("cardio", "القلبية", "الوذمة", 50),
            unit("nephro", "الكلية والشوارد", "الوذمة", 80)
        )
        assertEquals(
            "cardio",
            findConceptUnit(units, "القلبية", listOf("الوذمة"))?.id
        )
        // No subject to disambiguate: the heaviest unit wins.
        assertEquals(
            "nephro",
            findConceptUnit(units, null, listOf("الوذمة"))?.id
        )
    }

    @Test
    fun selfTestKeyResolution() {
        // 0-based inside the range passes through.
        assertEquals(4, resolveSelfTestCorrectIndex(4, 5))
        assertEquals(0, resolveSelfTestCorrectIndex(0, 5))
        // The 1-based boundary value (== option count) is read 1-based.
        assertEquals(4, resolveSelfTestCorrectIndex(5, 5))
        // Anything else out of range resolves to null — never invented.
        assertNull(resolveSelfTestCorrectIndex(7, 5))
        assertNull(resolveSelfTestCorrectIndex(-1, 5))
        assertNull(resolveSelfTestCorrectIndex(null, 5))
        assertNull(resolveSelfTestCorrectIndex(0, 0))
    }

    @Test
    fun conceptsEnvelopeParsesThePublishedContract() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val json = """
            {"version":1,"units":[{"id":"concept-001","subject":"القلبية",
            "topic":"قصور القلب","weight":162,"watani":73,"damascus":89,
            "watani_topics":["قصور القلب"],
            "explanation":[{"h":"التعريف","body":"نص **مهم** هنا"}],
            "traps":["فخ أول"],
            "self_test":[{"qid":"NAT-QLB-0085","bank":"watani","text":"سؤال؟",
            "options":["أ","ب","ج","د","ه"],"correct_index":4,"note":"تعليل"}],
            "diff_table":{"columns":["وجه المقارنة","أ","ب"],
            "rows":[["السبب","س1","س2"]]},
            "cross_links":[{"subject":"الكلية والشوارد","text":"رابط"}],
            "mnemonic":{"body":"جسم الحيلة","hook":"خطاف"}}]}
        """.trimIndent()
        val envelope = moshi.adapter(ConceptsEnvelope::class.java).fromJson(json)
        assertEquals(1, envelope?.units?.size)
        val parsed = envelope!!.units!![0]
        assertEquals("concept-001", parsed.id)
        assertEquals(162, parsed.weight)
        assertEquals("NAT-QLB-0085", parsed.selfTest!![0].qid)
        assertEquals(4, parsed.selfTest!![0].correctIndex)
        assertEquals("خطاف", parsed.mnemonic!!.hook)
        assertEquals(1, parsed.diffTable!!.rows!!.size)
    }

    @Test
    fun sectionVerificationCountsConceptsUnits() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val json = """{"version":1,"units":[{"id":"a"},{"id":"b"}]}"""
        assertEquals(
            2,
            SectionVerification.verifySectionCount("concepts.json", json, moshi)
        )
        var threw = false
        try {
            SectionVerification.verifySectionCount(
                "concepts.json", """{"version":1,"units":[]}""", moshi
            )
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }
}
