package sy.medpro.watani

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import sy.medpro.watani.data.CasesEnvelope
import sy.medpro.watani.data.SubjectEnvelope

class WataniDownloadParserTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testCasesEnvelopeParsing() {
        val sampleJson = """
            {
              "version": "1.0",
              "count": 436,
              "cases": [
                {
                  "id": "case_1",
                  "title": "احتشاء عضلة قلبية",
                  "specialty": "الباطنة القلبية",
                  "presentation": "ألم صدري",
                  "history": [],
                  "examinations": [],
                  "tests": [],
                  "diagnosisOptions": ["احتشاء", "ذبح صدرية"],
                  "correctDiagnosis": 0,
                  "explanation": "شرح تفصيلي",
                  "learningPoints": ["نقطة 1"],
                  "xpReward": 50
                }
              ]
            }
        """.trimIndent()

        val envelope = moshi.adapter(CasesEnvelope::class.java).fromJson(sampleJson)
        assertNotNull(envelope)
        assertEquals(436, envelope?.count)
        assertEquals(1, envelope?.cases?.size)
        assertEquals("احتشاء عضلة قلبية", envelope?.cases?.get(0)?.title)
    }

    @Test
    fun testSubjectEnvelopeParsing() {
        val sampleJson = """
            {
              "version": "1.0",
              "subject_id": "cardio",
              "subject_name": "الباطنة القلبية",
              "count": 120,
              "topics": [
                {
                  "id": "t1",
                  "name": "قصور القلب",
                  "questions": [
                    {
                      "id": "q1",
                      "text": "ما تدبير قصور القلب الأيسر الحاد؟",
                      "options": ["أكسجين ومدرات", "سوائل", "إستركنين", "بلا صلة"],
                      "correct": 0,
                      "explanation": "المدرات تخفف الحمل."
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val envelope = moshi.adapter(SubjectEnvelope::class.java).fromJson(sampleJson)
        assertNotNull(envelope)
        assertEquals(120, envelope?.count)
        assertEquals(1, envelope?.topics?.size)
        assertEquals(1, envelope?.topics?.get(0)?.questions?.size)
        assertEquals("قصور القلب", envelope?.topics?.get(0)?.name)
    }
}
