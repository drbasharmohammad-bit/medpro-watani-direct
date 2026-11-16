package sy.medpro.watani.data

import android.content.Context

/**
 * إعدادات التذكير اليومي (بناء 1.2.23) — مخزن جهازي بسيط.
 *
 * التذكير معطّل افتراضيًا: لا يُفعَّل إلا بيد الطالب من شاشة
 * «الإشعارات والتذكير»، وعندها فقط يُطلب إذن الإشعارات. الوقت واحد
 * للتنبيهين (تذكير الدراسة وتنبيه مراجعة الأخطاء) ولكلٍّ منهما مفتاح
 * تشغيل مستقل.
 */
data class ReminderConfig(
    val studyEnabled: Boolean = false,
    val mistakesEnabled: Boolean = false,
    val hour: Int = 19,
    val minute: Int = 0
) {
    val anyEnabled: Boolean get() = studyEnabled || mistakesEnabled
}

object ReminderSettings {
    private const val PREFS_NAME = "watani_reminder_settings"
    private const val KEY_STUDY_ENABLED = "study_enabled"
    private const val KEY_MISTAKES_ENABLED = "mistakes_enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"

    fun config(context: Context): ReminderConfig {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ReminderConfig(
            studyEnabled = prefs.getBoolean(KEY_STUDY_ENABLED, false),
            mistakesEnabled = prefs.getBoolean(KEY_MISTAKES_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, 19).coerceIn(0, 23),
            minute = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59)
        )
    }

    fun setStudyEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_STUDY_ENABLED, enabled).apply()
    }

    fun setMistakesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MISTAKES_ENABLED, enabled).apply()
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }
}
