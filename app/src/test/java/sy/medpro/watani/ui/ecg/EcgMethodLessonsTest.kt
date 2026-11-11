package sy.medpro.watani.ui.ecg

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.data.EcgEnvelope

/**
 * Build 1.2.5: contract tests for the optional `methodLessons` field
 * of ecg.json (the «منهج القراءة» curriculum). The field must decode
 * fully when present and its absence must leave the rest of the
 * envelope — including the older five-step `method` — untouched.
 */
class EcgMethodLessonsTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun methodLessonsDecodeFully() {
        val json = """
            {
              "version": 2,
              "method": [{"step": 1, "title": "المعدّل", "ar": "…", "source": "AHA"}],
              "methodLessons": [
                {
                  "id": "m01",
                  "title": "الورقة والموجات",
                  "level": "أساسي",
                  "minutes": 15,
                  "sections": [
                    {"h": "شبكة الورقة", "t": "…", "points": ["25mm/s", "10mm/mV"], "source": "AHA/ACCF/HRS Part I"}
                  ],
                  "stripIds": ["s-norm-1"],
                  "checklist": ["أعرف سرعة الورقة", "أعرف المعايرة"]
                },
                {
                  "id": "m02",
                  "title": "حصار القلب الكامل",
                  "level": "متقدم",
                  "minutes": 25,
                  "sections": [],
                  "stripIds": [],
                  "checklist": []
                }
              ],
              "patterns": [],
              "strips": [],
              "leadMap": []
            }
        """.trimIndent()
        val env = moshi.adapter(EcgEnvelope::class.java).fromJson(json)!!
        val lessons = env.methodLessons!!
        assertEquals(2, lessons.size)
        val first = lessons[0]
        assertEquals("m01", first.id)
        assertEquals("الورقة والموجات", first.title)
        assertEquals("أساسي", first.level)
        assertEquals(15, first.minutes)
        assertEquals(1, first.sections!!.size)
        assertEquals("شبكة الورقة", first.sections!![0].h)
        assertEquals(listOf("25mm/s", "10mm/mV"), first.sections!![0].points)
        assertEquals("AHA/ACCF/HRS Part I", first.sections!![0].source)
        assertEquals(listOf("s-norm-1"), first.stripIds)
        assertEquals(2, first.checklist!!.size)
        // The pre-existing five-step method still decodes beside it.
        assertEquals(1, env.method!!.size)
        assertEquals(1, env.method!![0].step)
    }

    @Test
    fun methodLessonsAbsentIsNullAndHarmless() {
        val json = """
            {
              "version": 1,
              "method": [{"step": 1, "title": "المعدّل", "ar": "…", "source": "AHA"}],
              "patterns": [],
              "strips": [],
              "leadMap": []
            }
        """.trimIndent()
        val env = moshi.adapter(EcgEnvelope::class.java).fromJson(json)!!
        assertNull(env.methodLessons)
        assertTrue(env.method!!.isNotEmpty())
    }

    @Test
    fun methodLessonPartialFieldsStayNull() {
        val json = """
            {"version": 2, "methodLessons": [{"id": "m09"}], "patterns": [], "strips": []}
        """.trimIndent()
        val env = moshi.adapter(EcgEnvelope::class.java).fromJson(json)!!
        val lesson = env.methodLessons!![0]
        assertEquals("m09", lesson.id)
        assertNull(lesson.title)
        assertNull(lesson.sections)
        assertNull(lesson.checklist)
    }
}
