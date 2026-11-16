package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.26 pure-logic pins for «دورة كاملة»: the named-dawra
 * naming rule (validated against the published bank: exactly the 17
 * canonical dawrat, no archival bucket), the local dawra listing,
 * the coverage rule, and the cross-subject assembly (official order,
 * span math). The list screen, the detail screen and the quiz
 * integration all read these exact functions.
 */
class FullDawraLogicTest {

    private fun q(id: String) = QuestionDto(
        id = id, text = "stem $id", options = listOf("أ", "ب"),
        optionLabels = null, correct = 0, correctIndices = null,
        explanation = null, years = null, source = null, session = null,
        topics = null, free = null
    )

    // ---------------- the naming rule ----------------

    @Test
    fun namedDawraAcceptsExactlyTheSeventeenCanonicalNames() {
        val canonical = listOf(
            "نيسان وكانون 2018", "تشرين 2018",
            "شباط 2019", "تشرين 2019",
            "حزيران 2020", "تشرين 2020",
            "حزيران 2021", "تشرين 2021",
            "آذار 2022", "أيلول 2022",
            "آذار 2023", "تشرين 2023",
            "أيار 2024", "تشرين 2024",
            "آب وتموز 2025", "كانون 2025",
            "أيار 2026"
        )
        assertEquals(17, canonical.size)
        canonical.forEach { assertTrue("must accept: $it", isNamedDawra(it)) }
    }

    @Test
    fun namedDawraRejectsEveryArchivalBucketShape() {
        val buckets = listOf(
            "دورات قديمة", "دورات قديمة 2010–2012", "دورات قديمة 2013–2015",
            "دورات قديمة 2016–2018", "دورات قديمة 2010–2018",
            "دورات قبل المرجع 2013–2015", "دورات قديمة قبل المرجع 2010–2012",
            "دورة الدكتوراه 2025",
            "دورة 2023 (تكميلية) — بدون شهر محدد",
            "دورة 2025 (تكميلية) — بدون شهر محدد",
            "دورة 2022 (أساسية) — بدون شهر محدد",
            "بنك الجراحة الصدرية", "بنك الجراحة العصبية", "بنك جراحة القلب",
            "جلسة", "", "   "
        )
        buckets.forEach { assertFalse("must reject: $it", isNamedDawra(it)) }
        assertFalse(isNamedDawra(null))
    }

    @Test
    fun dawraYearParsesTheTrailingYear() {
        assertEquals(2026, dawraYear("أيار 2026"))
        assertEquals(2018, dawraYear("نيسان وكانون 2018"))
        assertEquals(2025, dawraYear("آب وتموز 2025"))
        assertNull(dawraYear("دورات قديمة"))
        assertNull(dawraYear(null))
    }

    // ---------------- listing ----------------

    @Test
    fun listDawratAggregatesCountsAndPresenceInFirstAppearanceOrder() {
        val inputs = listOf(
            DawraSubjectSessions(
                "الأذنية", "questions/الأذنية.json",
                linkedMapOf("أيار 2026" to 10, "كانون 2025" to 8, "دورات قديمة" to 90)
            ),
            DawraSubjectSessions(
                "القلبية", "questions/القلبية.json",
                linkedMapOf("أيار 2026" to 15, "آب وتموز 2025" to 16, "كانون 2025" to 25)
            )
        )
        val dawrat = listDawrat(inputs)
        assertEquals(listOf("أيار 2026", "كانون 2025", "آب وتموز 2025"), dawrat.map { it.name })
        assertEquals(25, dawrat[0].totalQuestions)
        assertEquals(2, dawrat[0].subjectsPresent)
        assertEquals(33, dawrat[1].totalQuestions)
        assertEquals(16, dawrat[2].totalQuestions)
        assertEquals(1, dawrat[2].subjectsPresent)
    }

    @Test
    fun listDawratSkipsZeroCountsAndEmptyInputs() {
        assertTrue(listDawrat(emptyList()).isEmpty())
        val inputs = listOf(
            DawraSubjectSessions("الجلدية", "questions/الجلدية.json", linkedMapOf("أيار 2026" to 0))
        )
        assertTrue(listDawrat(inputs).isEmpty())
    }

    // ---------------- coverage ----------------

    @Test
    fun missingSubjectsAreTheNotDownloadedOnesOnly() {
        val all = listOf("الأذنية", "القلبية", "العصبية")
        assertEquals(listOf("القلبية", "العصبية"), missingSubjects(all, listOf("الأذنية")))
        assertTrue(missingSubjects(all, all).isEmpty())
        assertEquals(all, missingSubjects(all, emptyList()))
    }

    // ---------------- assembly ----------------

    @Test
    fun buildDawraQuizOrdersByOfficialSubjectOrderAndComputesSpans() {
        val quiz = buildDawraQuiz(
            listOf(
                "القلبية" to listOf(q("c1"), q("c2")),
                "الأذنية" to listOf(q("e1")),
                "العصبية" to listOf(q("n1"), q("n2"), q("n3"))
            )
        )
        assertEquals(listOf("الأذنية", "العصبية", "القلبية"), quiz.sections.map { it.subjectName })
        assertEquals(listOf("e1", "n1", "n2", "n3", "c1", "c2"), quiz.questions.map { it.id })
        assertEquals(QuizSectionSpan("الأذنية", 0, 1), quiz.sections[0])
        assertEquals(QuizSectionSpan("العصبية", 1, 3), quiz.sections[1])
        assertEquals(QuizSectionSpan("القلبية", 4, 2), quiz.sections[2])
        assertEquals("الأذنية", quiz.subjectAt(0))
        assertEquals("العصبية", quiz.subjectAt(3))
        assertEquals("القلبية", quiz.subjectAt(5))
        assertNull(quiz.subjectAt(6))
    }

    @Test
    fun buildDawraQuizSkipsEmptySubjects() {
        val quiz = buildDawraQuiz(
            listOf(
                "القلبية" to emptyList(),
                "الأذنية" to listOf(q("e1"))
            )
        )
        assertEquals(1, quiz.sections.size)
        assertEquals(1, quiz.questions.size)
        assertTrue(buildDawraQuiz(emptyList()).questions.isEmpty())
    }

    @Test
    fun officialOrderCoversTheTwentySevenSubjects() {
        assertEquals(27, OFFICIAL_SUBJECT_ORDER.size)
        assertEquals(27, OFFICIAL_SUBJECT_ORDER.distinct().size)
        assertEquals(0, officialSubjectRank("الأذنية"))
        assertEquals(26, officialSubjectRank("جراحة القلب"))
        assertEquals(27, officialSubjectRank("مادة غير معروفة"))
    }
}
