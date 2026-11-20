package sy.medpro.watani.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Build 1.2.32 pure-logic pins for the standalone dawra files
 * (dawrat_sessions/): the path mapping, the unified-record ->
 * QuestionDto conversion (a faithful port of the publishing
 * pipeline's own rendering, so a question reads identically in the
 * full-dawra sitting and in its per-subject session), the
 * file-based assembly and coverage inputs, the three-source dawra
 * list merge, and the download count verification branch.
 */
class DawraSessionFileLogicTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun record(
        id: String = "NAT-TST-0001",
        subject: String = "الأذنية",
        session: String = "كانون 2025",
        question: String = "نص السؤال",
        options: List<String> = listOf("خ1", "خ2", "خ3"),
        correctIndex: Int? = 1,
        correctLabel: String? = "ب",
        explanation: DawraSessionExplanationDto? = null,
        sources: List<String>? = null,
        confidence: String? = null
    ) = DawraSessionQuestionDto(
        id = id, subject = subject, session = session, question = question,
        options = options, correctIndex = correctIndex, correctLabel = correctLabel,
        explanation = explanation, sources = sources, confidence = confidence
    )

    // ---------------- paths ----------------

    @Test
    fun sessionPathRoundTrips() {
        assertEquals("dawrat_sessions/كانون 2025.json", dawraSessionPath("كانون 2025"))
        assertEquals("كانون 2025", dawraSessionNameOfPath("dawrat_sessions/كانون 2025.json"))
        assertEquals("May 2026", dawraSessionNameOfPath("dawrat_sessions/May 2026.json"))
    }

    @Test
    fun sessionNameRejectsNonUnitPaths() {
        assertNull(dawraSessionNameOfPath("dawrat_sessions.json"))
        assertNull(dawraSessionNameOfPath("questions/الأذنية.json"))
        assertNull(dawraSessionNameOfPath("dawrat_sessions/"))
        assertNull(dawraSessionNameOfPath("dawrat_sessions/.json"))
    }

    // ---------------- conversion ----------------

    @Test
    fun conversionCarriesKeyBothWaysLikeTheShards() {
        val dto = record().toQuestionDto()
        assertEquals("NAT-TST-0001", dto.id)
        assertEquals("نص السؤال", dto.text)
        assertEquals(listOf("خ1", "خ2", "خ3"), dto.options)
        assertEquals(listOf("أ", "ب", "ج"), dto.optionLabels)
        assertEquals("خ2", dto.correct)
        assertEquals(listOf(1), dto.correctIndices)
        assertEquals("كانون 2025", dto.session)
        // The grading core resolves the same index the shards yield.
        assertEquals(listOf(1), getCorrectIndices(dto, dto.options))
    }

    @Test
    fun mergedExplanationMatchesThePipelineRendering() {
        val rec = record(
            explanation = DawraSessionExplanationDto(
                whyCorrect = "لأن السبب قائم.",
                whyWrong = linkedMapOf("ج" to "خطأ ج.", "أ" to "خطأ أ."),
                summary = "ملخص الفكرة."
            ),
            sources = listOf("مصدر أول", "مصدر ثانٍ"),
            confidence = "high"
        )
        val expected = "الإجابة الصحيحة (ب): لأن السبب قائم.\n" +
            "لماذا الخيارات الأخرى خاطئة:\nأ: خطأ أ.\nج: خطأ ج.\n" +
            "المصادر: مصدر أول؛ مصدر ثانٍ\n" +
            "الثقة: high"
        assertEquals(expected, mergedDawraExplanation(rec))
        // The summary is NOT inside the merged string — it is the
        // separate «الزبدة» field, exactly as the shards carry it.
        assertEquals("الخلاصة: ملخص الفكرة.", dawraZubda(rec))
        assertEquals("الخلاصة: ملخص الفكرة.", rec.toQuestionDto().zubda)
    }

    @Test
    fun recordWithoutExplanationYieldsNoCardAndNoZubda() {
        val rec = record(explanation = DawraSessionExplanationDto(null, null, null))
        assertEquals("", mergedDawraExplanation(rec))
        assertNull(dawraZubda(rec))
        assertEquals("", rec.toQuestionDto().explanation)
        assertNull(rec.toQuestionDto().zubda)
        val bare = record(explanation = null)
        assertEquals("", mergedDawraExplanation(bare))
    }

    // ---------------- assembly + inputs ----------------

    @Test
    fun assemblyGroupsBySubjectInOfficialOrder() {
        val records = listOf(
            record(id = "1", subject = "القلبية"),
            record(id = "2", subject = "الأذنية"),
            record(id = "3", subject = "القلبية"),
            record(id = "4", subject = "الأذنية")
        )
        val quiz = buildDawraQuizFromRecords(records)
        assertEquals(4, quiz.questions.size)
        assertEquals(listOf("2", "4", "1", "3"), quiz.questions.map { it.id })
        assertEquals(2, quiz.sections.size)
        assertEquals(QuizSectionSpan("الأذنية", 0, 2), quiz.sections[0])
        assertEquals(QuizSectionSpan("القلبية", 2, 2), quiz.sections[1])
        assertEquals("القلبية", quiz.subjectAt(3))
    }

    @Test
    fun sessionInputsCountPerSubjectUnderThePathName() {
        val path = "dawrat_sessions/كانون 2025.json"
        val inputs = dawraSessionInputs(
            listOf(
                record(subject = "الأذنية"), record(subject = "الأذنية"),
                record(subject = "القلبية")
            ),
            path
        )
        assertEquals(2, inputs.size)
        val bySubject = inputs.associateBy { it.subjectName }
        assertEquals(mapOf("كانون 2025" to 2), bySubject["الأذنية"]?.sessionCounts)
        assertEquals(mapOf("كانون 2025" to 1), bySubject["القلبية"]?.sessionCounts)
        assertTrue(inputs.all { it.path == path })
    }

    // ---------------- list merge + resolve ----------------

    @Test
    fun manifestCountsSkipTheIndexAndOtherFamilies() {
        val files = mapOf(
            "dawrat_sessions/كانون 2025.json" to ManifestFileEntry("h", 10L, 240, "1.0"),
            "dawrat_sessions.json" to ManifestFileEntry("h", 10L, 8513, "1.0"),
            "questions/الأذنية.json" to ManifestFileEntry("h", 10L, 300, "1.0")
        )
        assertEquals(mapOf("كانون 2025" to 240), manifestDawraCounts(files))
    }

    @Test
    fun mergePrefersFileThenShardsThenManifest() {
        val shards = listOf(
            DawraInfo("كانون 2025", 200, 20),
            DawraInfo("تشرين 2024", 241, 27)
        )
        val files = listOf(DawraInfo("كانون 2025", 240, 24))
        val manifest = mapOf("كانون 2025" to 240, "تشرين 2024" to 241, "أيار 2024" to 240)
        val merged = mergeDawraInfos(shards, files, manifest)
        assertEquals(listOf("كانون 2025", "تشرين 2024", "أيار 2024"), merged.map { it.name })
        // File totals win for كانون 2025 and are flagged standalone.
        assertEquals(240, merged[0].totalQuestions)
        assertTrue(merged[0].standaloneFile)
        assertTrue(merged[0].inManifest)
        // Shard totals stay for تشرين 2024.
        assertEquals(241, merged[1].totalQuestions)
        assertFalse(merged[1].standaloneFile)
        assertTrue(merged[1].inManifest)
        // Manifest-only dawra listed with its full count, nothing local.
        assertEquals(240, merged[2].totalQuestions)
        assertEquals(0, merged[2].subjectsPresent)
        assertTrue(merged[2].inManifest)
    }

    @Test
    fun resolveInputsUsesTheFileWhenDownloaded() {
        val shards = listOf(
            DawraSubjectSessions("الأذنية", "questions/الأذنية.json", mapOf("كانون 2025" to 5))
        )
        val fileInputs = listOf(
            DawraSubjectSessions("الأذنية", "dawrat_sessions/كانون 2025.json", mapOf("كانون 2025" to 7))
        )
        assertEquals(fileInputs, resolveDawraInputs(shards, fileInputs, "كانون 2025"))
        assertEquals(shards, resolveDawraInputs(shards, fileInputs, "تشرين 2024"))
        assertEquals(shards, resolveDawraInputs(shards, emptyList(), "كانون 2025"))
    }

    // ---------------- download verification ----------------

    @Test
    fun verifyCountParsesABareDawraArray() {
        val json = """
            [
              {"id":"NAT-1","subject":"الأذنية","session":"كانون 2025","question":"س",
               "options":["أ","ب"],"correct_index":0,"correct_label":"أ",
               "explanation":{"why_correct":"ص","why_wrong":{"ب":"خ"},"summary":"م"},
               "sources":["كتاب"],"confidence":"high"},
              {"id":"NAT-2","subject":"القلبية","session":"كانون 2025","question":"س2",
               "options":["أ","ب"],"correct_index":1,"correct_label":"ب",
               "explanation":null,"sources":null,"confidence":null}
            ]
        """.trimIndent()
        assertEquals(
            2,
            SectionVerification.verifySectionCount("dawrat_sessions/كانون 2025.json", json, moshi)
        )
    }

    @Test
    fun verifyCountRejectsAnEmptyDawraArray() {
        try {
            SectionVerification.verifySectionCount("dawrat_sessions/كانون 2025.json", "[]", moshi)
            fail("an empty dawra file must not verify")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("بلا أسئلة"))
        }
    }

    @Test
    fun dawraFilesHashWithSha256TruncatedLikeTheManifest() {
        // dawrat_sessions/ is NOT in the manifest's blake2b-8 set:
        // its entries are sha256[:16] (build_dawrat_sessions.py).
        val bytes = "abc".toByteArray(Charsets.UTF_8)
        assertEquals(
            "ba7816bf8f01cfea",
            SectionVerification.manifestHashHex("dawrat_sessions/كانون 2025.json", bytes)
        )
    }

    // ---------------- restricted classification ----------------

    @Test
    fun dawraPathsAreRestricted() {
        assertTrue(WorkerActivationApi.isRestrictedPath("dawrat_sessions/كانون 2025.json"))
        assertTrue(WorkerActivationApi.isRestrictedPath("dawrat_sessions.json"))
        assertFalse(WorkerActivationApi.isRestrictedPath("dawrat_sessionsx/fake.json"))
    }
}
