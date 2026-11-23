package sy.medpro.watani.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «حسب السنوات» لمواد الجامعات (23 Nov 2026): الدوال النقية للطبقة —
 * تحويل مسار الشاردة إلى مسار ملف سنواتها، ملخّصات السنوات واختيار
 * أسئلة سنة كاملة (قائمة مسطّحة واحدة، بلا مواضيع داخل السنوات —
 * تصحيح الدكتور 23 Nov 2026), وحكم اكتمال الزوج الموحّد
 * (كل-أو-لا-شيء) — وتحليل Moshi لملف سنوات بالبنية المنشورة
 * المجمّدة، بنفس محوّل الإنتاج (KotlinJsonAdapterFactory كما في
 * WataniRepository).
 */
class YearsLogicTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    /** ملف سنوات تجريبي بالبنية المجمّدة حرفيًا (مثال المواصفة):
     * أسئلة كل سنة مباشرة تحتها، بلا أي مستوى مواضيع. */
    private val yearsFixtureJson = """
        {"version":"damascus-years-2026-11-23","subject_id":"أذنية",
         "subject_name":"أذنية","university":"damascus",
         "university_name":"جامعة دمشق","count":5,
         "years":[
           {"year":2026,"name":"دمشق 2026","count":3,
            "questions":[
              {"id":"Y26-1","text":"سؤال أول","options":["أ","ب"],"correct":"أ"},
              {"id":"Y26-2","text":"سؤال ثانٍ","options":["أ","ب"],"correct":"ب"},
              {"id":"Y26-3","text":"سؤال ثالث","options":["أ","ب"],"correct":"أ"}
            ]},
           {"year":2025,"name":"دمشق 2025",
            "questions":[
              {"id":"Y25-1","text":"سؤال رابع","options":["أ","ب"],"correct":"ب"},
              {"id":"Y25-2","text":"سؤال خامس","options":["أ","ب"],"correct":"أ"}
            ]}
         ]}
    """.trimIndent()

    private fun parseYears(): YearsEnvelope =
        moshi.adapter(YearsEnvelope::class.java).fromJson(yearsFixtureJson)!!

    // ---------------------------------------------------------------
    // yearsPathFor
    // ---------------------------------------------------------------

    @Test
    fun yearsPathForUniversitySubject() {
        assertEquals(
            "dawrat_jami3at/damascus_years/أذنية.json",
            yearsPathFor("dawrat_jami3at/damascus/أذنية.json")
        )
        assertEquals(
            "dawrat_jami3at/aleppo_years/القلبية.json",
            yearsPathFor("dawrat_jami3at/aleppo/القلبية.json")
        )
    }

    @Test
    fun yearsPathForRejectsOtherFamilies() {
        assertNull(yearsPathFor("questions/أذنية.json"))
        assertNull(yearsPathFor("questions_en/أذنية.json"))
        assertNull(yearsPathFor("uworld/أذنية.json"))
        assertNull(yearsPathFor("dawrat/أذنية.json"))
        assertNull(yearsPathFor("library.json"))
    }

    @Test
    fun yearsPathForRejectsYearsFilesAndMalformedPaths() {
        // No double conversion of a years file itself.
        assertNull(yearsPathFor("dawrat_jami3at/damascus_years/أذنية.json"))
        // No university folder / deeper nesting / non-JSON.
        assertNull(yearsPathFor("dawrat_jami3at/أذنية.json"))
        assertNull(yearsPathFor("dawrat_jami3at/damascus/sub/أذنية.json"))
        assertNull(yearsPathFor("dawrat_jami3at/damascus/أذنية.txt"))
        assertNull(yearsPathFor("dawrat_jami3at//أذنية.json"))
    }

    // ---------------------------------------------------------------
    // Moshi parse + pure selectors
    // ---------------------------------------------------------------

    @Test
    fun moshiParsesFrozenYearsSchema() {
        val envelope = parseYears()
        assertEquals("damascus-years-2026-11-23", envelope.version)
        assertEquals("أذنية", envelope.subjectName)
        assertEquals("damascus", envelope.university)
        assertEquals(5, envelope.count)
        assertEquals(2, envelope.years?.size)
        assertEquals(2026, envelope.years?.get(0)?.year)
        // Question records sit directly under the year (flat list,
        // no topics level) and are the shard QuestionDto shape.
        val first = envelope.years?.get(0)?.questions?.get(0)
        assertNotNull(first)
        assertEquals("Y26-1", first?.id)
        assertEquals(listOf("أ", "ب"), first?.options)
    }

    @Test
    fun yearSummariesUsePublishedCountOrQuestionListSize() {
        val summaries = yearSummaries(parseYears())
        assertEquals(2, summaries.size)
        // 2026 carries a published count (3) — used as-is.
        assertEquals(YearSummary(2026, "دمشق 2026", 3), summaries[0])
        // 2025 has no count field — its direct question list (2).
        assertEquals(YearSummary(2025, "دمشق 2025", 2), summaries[1])
        assertTrue(yearSummaries(null).isEmpty())
    }

    @Test
    fun yearQuestionsSelectionIsOneFlatList() {
        val envelope = parseYears()
        // The whole year is one flat list — exactly what the year
        // quiz (openYearQuiz) receives, in file order.
        assertEquals(
            listOf("Y26-1", "Y26-2", "Y26-3"),
            questionsForYear(envelope, 2026).map { it.id }
        )
        assertEquals(
            listOf("Y25-1", "Y25-2"),
            questionsForYear(envelope, 2025).map { it.id }
        )
        // Unknown year / missing envelope -> empty, never another
        // year's questions.
        assertTrue(questionsForYear(envelope, 2024).isEmpty())
        assertTrue(questionsForYear(null, 2026).isEmpty())
    }

    // ---------------------------------------------------------------
    // isSectionPairUpToDate — the all-or-nothing verdict
    // ---------------------------------------------------------------

    private val subjectPath = "dawrat_jami3at/damascus/أذنية.json"
    private val yearsPath = "dawrat_jami3at/damascus_years/أذنية.json"

    private fun entity(path: String, hash: String = "h1", count: Int = 10) =
        DownloadedSectionEntity(
            sectionPath = path, version = "1", hash = hash,
            count = count, downloadedAt = 0L
        )

    private fun entry(hash: String? = "h1", count: Int? = 10) =
        ManifestFileEntry(hash = hash, size = 1000L, count = count, version = "1")

    private fun manifestWithTwin() = mapOf(
        subjectPath to entry(),
        yearsPath to entry(hash = "h2")
    )

    @Test
    fun pairCompleteWhenBothFilesPresentAndCurrent() {
        val downloaded = listOf(
            entity(subjectPath, hash = "h1"),
            entity(yearsPath, hash = "h2")
        )
        assertTrue(isSectionPairUpToDate(subjectPath, manifestWithTwin(), downloaded))
    }

    @Test
    fun pairIncompleteWithOnlyOneFileEitherWay() {
        val files = manifestWithTwin()
        // Subject alone: NOT downloaded (the doctor's all-or-nothing rule).
        assertFalse(
            isSectionPairUpToDate(subjectPath, files, listOf(entity(subjectPath, hash = "h1")))
        )
        // Years alone: NOT downloaded either.
        assertFalse(
            isSectionPairUpToDate(subjectPath, files, listOf(entity(yearsPath, hash = "h2")))
        )
        assertFalse(isSectionPairUpToDate(subjectPath, files, emptyList()))
    }

    @Test
    fun pairIncompleteWhenEitherFileIsStale() {
        val files = manifestWithTwin()
        val staleSubject = listOf(
            entity(subjectPath, hash = "old"),
            entity(yearsPath, hash = "h2")
        )
        assertFalse(isSectionPairUpToDate(subjectPath, files, staleSubject))
        val staleYears = listOf(
            entity(subjectPath, hash = "h1"),
            entity(yearsPath, hash = "old")
        )
        assertFalse(isSectionPairUpToDate(subjectPath, files, staleYears))
    }

    @Test
    fun twinlessPathsKeepTheSingleFileVerdict() {
        // No years twin in the manifest: subject alone decides,
        // exactly the pre-existing behaviour.
        val files = mapOf(subjectPath to entry())
        assertTrue(
            isSectionPairUpToDate(subjectPath, files, listOf(entity(subjectPath, hash = "h1")))
        )
        assertFalse(isSectionPairUpToDate(subjectPath, files, emptyList()))
        // Other families never gain a twin.
        val wataniPath = "questions/أذنية.json"
        val wataniFiles = mapOf(wataniPath to entry())
        assertTrue(
            isSectionPairUpToDate(wataniPath, wataniFiles, listOf(entity(wataniPath, hash = "h1")))
        )
        assertFalse(isSectionPairUpToDate(wataniPath, wataniFiles, emptyList()))
    }

    @Test
    fun missingManifestEntryFallsBackToPresence() {
        // Mirror of the subject screen's no-entry verdict: presence
        // of BOTH files is still required when the twin is known
        // only through the subject's own entry… and with no entries
        // at all the twin is undiscoverable, so presence of the
        // subject file alone is the verdict (offline fallback).
        val noEntries = emptyMap<String, ManifestFileEntry>()
        assertTrue(
            isSectionPairUpToDate(subjectPath, noEntries, listOf(entity(subjectPath)))
        )
        assertFalse(isSectionPairUpToDate(subjectPath, noEntries, emptyList()))
    }
}
