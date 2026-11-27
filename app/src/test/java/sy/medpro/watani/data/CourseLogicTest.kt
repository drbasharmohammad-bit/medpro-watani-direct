package sy.medpro.watani.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.53 — library courses: the catalog is pure server data
 * (courses.json on the content host); adding a course later is
 * files + one catalog entry, never an app update. These tests pin
 * the catalog contract and the lesson → LibraryItem conversion the
 * existing download/open machinery consumes.
 */
class CourseLogicTest {

    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(CourseCatalog::class.java)

    private val sampleJson = """
        {"courses":[
          {"id":"trial-basics","title":"أساسيات","description":"وصف","coverUrl":null,
           "access":"activated",
           "lessons":[
             {"id":"l1","title":"الدرس الأول","type":"pdf","path":"books/a.pdf","size":100},
             {"id":"l2","title":"الدرس الثاني","type":"video","path":"https://example.com/v.mp4","size":200}
           ]}
        ]}
    """.trimIndent()

    @Test
    fun catalogParses() {
        val catalog = adapter.fromJson(sampleJson)
        assertEquals(1, catalog?.courses?.size)
        val course = catalog!!.courses!![0]
        assertEquals("trial-basics", course.id)
        assertEquals("activated", course.access)
        assertEquals(2, course.lessons?.size)
    }

    @Test
    fun lessonItemResolvesRelativePathAgainstContentOrigin() {
        val course = adapter.fromJson(sampleJson)!!.courses!![0]
        val item = CourseLogic.lessonItem(course, course.lessons!![0])
        assertEquals("https://watani-content.pages.dev/v1/books/a.pdf", item.fileUrl)
        assertEquals("course_trial-basics_l1", item.id)
        assertEquals(100L, item.size)
        assertEquals("الدرس الأول", item.title)
    }

    @Test
    fun lessonItemKeepsAbsoluteUrl() {
        val course = adapter.fromJson(sampleJson)!!.courses!![0]
        val item = CourseLogic.lessonItem(course, course.lessons!![1])
        assertEquals("https://example.com/v.mp4", item.fileUrl)
        assertEquals("course_trial-basics_l2", item.id)
    }

    @Test
    fun accessMatrix() {
        val free = Course(id = "f", access = "free")
        val paid = Course(id = "p", access = "activated")
        val unknown = Course(id = "u", access = "whatever")
        assertTrue(CourseLogic.canAccess(free, isActivated = false))
        assertTrue(CourseLogic.canAccess(free, isActivated = true))
        assertFalse(CourseLogic.canAccess(paid, isActivated = false))
        assertTrue(CourseLogic.canAccess(paid, isActivated = true))
        // An unknown access value fails closed to activated-only,
        // never silently free.
        assertFalse(CourseLogic.canAccess(unknown, isActivated = false))
    }

    @Test
    fun courseDownloadedOnlyWhenEveryLessonIs() {
        val course = adapter.fromJson(sampleJson)!!.courses!![0]
        assertFalse(CourseLogic.isCourseDownloaded(course) { false })
        assertFalse(CourseLogic.isCourseDownloaded(course) { it.id?.endsWith("l1") == true })
        assertTrue(CourseLogic.isCourseDownloaded(course) { true })
        // A course with no lessons is never "downloaded".
        assertFalse(CourseLogic.isCourseDownloaded(Course(id = "e", lessons = emptyList())) { true })
    }

    @Test
    fun lessonKindFollowsTypeFieldNotExtension() {
        val course = adapter.fromJson(sampleJson)!!.courses!![0]
        assertTrue(CourseLogic.isPdfLesson(course.lessons!![0]))
        assertFalse(CourseLogic.isPdfLesson(course.lessons!![1]))
    }
}
