package sy.medpro.watani.data

import android.content.Context

/** آخر قسم فتحه الطالب (قسم مواضيع أو كويز موضوع) لاستئنافه من الرئيسية. */
data class ResumeEntry(
    val path: String,
    val title: String,
    val tab: String,
    val isTopicQuiz: Boolean,
    val timestamp: Long
)

/**
 * مخزن «أكمل من وين وقّفت»: يُسجَّل عند كل فتح قسم/كويز من
 * MainViewModel (openSubject / openTopicQuiz) بـSharedPreferences
 * ("watani_resume")، وتقرؤه الرئيسية لعرض زر الاستئناف ويعيد
 * MainViewModel.resumeLastSection فتحه بنفس الدوال.
 */
object ResumeStore {
    private const val PREFS_NAME = "watani_resume"
    private const val KEY_PATH = "path"
    private const val KEY_TITLE = "title"
    private const val KEY_TAB = "tab"
    private const val KEY_IS_TOPIC_QUIZ = "is_topic_quiz"
    private const val KEY_TIMESTAMP = "timestamp"

    fun record(context: Context, path: String, title: String, tab: String, isTopicQuiz: Boolean) {
        if (path.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PATH, path)
            .putString(KEY_TITLE, title)
            .putString(KEY_TAB, tab)
            .putBoolean(KEY_IS_TOPIC_QUIZ, isTopicQuiz)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): ResumeEntry? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PATH, null)
        if (path.isNullOrBlank()) return null
        return ResumeEntry(
            path = path,
            title = prefs.getString(KEY_TITLE, "") ?: "",
            tab = prefs.getString(KEY_TAB, "") ?: "",
            isTopicQuiz = prefs.getBoolean(KEY_IS_TOPIC_QUIZ, false),
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        )
    }

    fun hasResume(context: Context): Boolean = load(context) != null
}
