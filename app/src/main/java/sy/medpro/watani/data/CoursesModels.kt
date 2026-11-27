package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Build 1.2.53 — library courses (the doctor's «مرونة» order,
 * 27 Nov 2026). A course is PURE SERVER DATA: the catalog lives
 * at `courses.json` on the content host, and adding a course
 * later is lesson files + one catalog entry — never an app
 * update (the mechanism is documented in
 * watani-content/courses-README.md).
 *
 * A lesson is one file — a video or a PDF — at [CourseLesson.path]
 * on the content host (a path relative to the /v1/ origin, or an
 * absolute URL). Lessons are downloaded and opened through the
 * EXISTING library machinery ([CourseLogic.lessonItem] converts a
 * lesson to the [LibraryItem] the download/open/player/reader
 * stack already consumes), so a downloaded course works fully
 * offline, with the unified PDF reader and the existing video
 * player — no new players.
 */
@JsonClass(generateAdapter = true)
data class CourseCatalog(
    @Json(name = "courses") val courses: List<Course>?
)

@JsonClass(generateAdapter = true)
data class Course(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "coverUrl") val coverUrl: String? = null,
    /** "free" (every signed-in student) or "activated". */
    @Json(name = "access") val access: String? = null,
    @Json(name = "lessons") val lessons: List<CourseLesson>? = null
)

@JsonClass(generateAdapter = true)
data class CourseLesson(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String? = null,
    /** "video" or "pdf" — the kind comes from this field, never
     * from guessing the file extension. */
    @Json(name = "type") val type: String? = null,
    @Json(name = "path") val path: String? = null,
    @Json(name = "size") val size: Long? = null
)

object CourseLogic {

    const val ACCESS_FREE = "free"
    const val ACCESS_ACTIVATED = "activated"

    /** Content origin the relative lesson paths resolve against. */
    const val CONTENT_ORIGIN = "https://watani-content.pages.dev/v1/"

    /**
     * Access decision: "free" is open to every signed-in student;
     * "activated" — and ANY unknown value, failing closed — needs
     * a live activation.
     */
    fun canAccess(course: Course, isActivated: Boolean): Boolean =
        if (course.access?.trim()?.lowercase() == ACCESS_FREE) true else isActivated

    fun isPdfLesson(lesson: CourseLesson): Boolean =
        lesson.type?.trim()?.lowercase() == "pdf"

    /** Absolute URL of a lesson file (relative paths resolve
     * against the content origin; absolute URLs pass through). */
    fun lessonUrl(lesson: CourseLesson): String? {
        val path = lesson.path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            CONTENT_ORIGIN + path.trimStart('/')
        }
    }

    /**
     * The [LibraryItem] the existing library download/open stack
     * consumes for this lesson. The id is namespaced by course so
     * two courses can carry the same file without colliding in
     * the on-disk library store.
     */
    fun lessonItem(course: Course, lesson: CourseLesson): LibraryItem = LibraryItem(
        id = "course_${course.id.orEmpty()}_${lesson.id.orEmpty()}",
        title = lesson.title,
        fileUrl = lessonUrl(lesson),
        size = lesson.size,
        hash = null,
        description = null
    )

    fun lessonItems(course: Course): List<LibraryItem> =
        course.lessons.orEmpty().map { lessonItem(course, it) }

    /** True only when the course has lessons and EVERY one of
     * them reports downloaded (atomic-course semantics: a partial
     * course is not «downloaded»). */
    fun isCourseDownloaded(course: Course, isDownloaded: (LibraryItem) -> Boolean): Boolean {
        val items = lessonItems(course)
        return items.isNotEmpty() && items.all(isDownloaded)
    }
}
