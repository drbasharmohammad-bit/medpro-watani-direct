package sy.medpro.watani.data

import android.content.Context

/**
 * إعدادات التذكير اليومي (بناء 1.2.23) — مخزن جهازي بسيط.
 *
 * التذكير معطّل افتراضيًا: لا يُفعَّل إلا بيد الطالب من شاشة
 * «الإشعارات والتذكير»، وعندها فقط يُطلب إذن الإشعارات. الوقت واحد
 * للتنبيهين (تذكير الدراسة وتنبيه مراجعة الأخطاء) ولكلٍّ منهما مفتاح
 * تشغيل مستقل.
 *
 * توسيع 1.2.30 (ميزات شاشة القفل): مفاتيح وأوقات مستقلة لبطاقة
 * العدّاد الدائمة، وسؤال اليوم الصباحي، وشريط التركيز، والمنبّه
 * القوي بملء الشاشة — وكلها معطّلة افتراضيًا أيضًا. تنبيه مراجعة
 * الأخطاء صار بوقته الخاص؛ عند غياب مفتاح وقته (ترقية من بناء
 * أقدم) يُقرأ الوقت المشترك القديم نفسه، فلا يتغيّر سلوك أي
 * مستخدم حالي حتى يغيّره بنفسه.
 */
data class ReminderConfig(
    val studyEnabled: Boolean = false,
    val mistakesEnabled: Boolean = false,
    val hour: Int = 19,
    val minute: Int = 0,
    /** بطاقة العدّاد الدائمة على شاشة القفل (1.2.30). */
    val countdownCardEnabled: Boolean = false,
    /** سؤال اليوم صباحًا على شاشة القفل (1.2.30) ووقته المستقل. */
    val questionEnabled: Boolean = false,
    val questionHour: Int = 8,
    val questionMinute: Int = 0,
    /** وقت تنبيه مراجعة الأخطاء المستقل (1.2.30). */
    val mistakesHour: Int = 19,
    val mistakesMinute: Int = 0,
    /** شريط جلسة التركيز الحي على شاشة القفل (1.2.30). */
    val focusBarEnabled: Boolean = false,
    /** منبّه الدراسة القوي بملء الشاشة (1.2.30). */
    val strongAlarmEnabled: Boolean = false
) {
    val anyEnabled: Boolean get() = studyEnabled || mistakesEnabled
}

object ReminderSettings {
    private const val PREFS_NAME = "watani_reminder_settings"
    private const val KEY_STUDY_ENABLED = "study_enabled"
    private const val KEY_MISTAKES_ENABLED = "mistakes_enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_COUNTDOWN_ENABLED = "countdown_card_enabled"
    private const val KEY_QUESTION_ENABLED = "question_enabled"
    private const val KEY_QUESTION_HOUR = "question_hour"
    private const val KEY_QUESTION_MINUTE = "question_minute"
    private const val KEY_MISTAKES_HOUR = "mistakes_hour"
    private const val KEY_MISTAKES_MINUTE = "mistakes_minute"
    private const val KEY_FOCUS_BAR_ENABLED = "focus_bar_enabled"
    private const val KEY_STRONG_ALARM_ENABLED = "strong_alarm_enabled"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun config(context: Context): ReminderConfig {
        val prefs = prefs(context)
        val hour = prefs.getInt(KEY_HOUR, 19).coerceIn(0, 23)
        val minute = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59)
        return ReminderConfig(
            studyEnabled = prefs.getBoolean(KEY_STUDY_ENABLED, false),
            mistakesEnabled = prefs.getBoolean(KEY_MISTAKES_ENABLED, false),
            hour = hour,
            minute = minute,
            countdownCardEnabled = prefs.getBoolean(KEY_COUNTDOWN_ENABLED, false),
            questionEnabled = prefs.getBoolean(KEY_QUESTION_ENABLED, false),
            questionHour = prefs.getInt(KEY_QUESTION_HOUR, 8).coerceIn(0, 23),
            questionMinute = prefs.getInt(KEY_QUESTION_MINUTE, 0).coerceIn(0, 59),
            // وقت الأخطاء المستقل يُورث الوقت المشترك عند غيابه —
            // سلوك ما قبل 1.2.30 يبقى كما هو تمامًا بعد الترقية.
            mistakesHour = if (prefs.contains(KEY_MISTAKES_HOUR))
                prefs.getInt(KEY_MISTAKES_HOUR, hour).coerceIn(0, 23) else hour,
            mistakesMinute = if (prefs.contains(KEY_MISTAKES_MINUTE))
                prefs.getInt(KEY_MISTAKES_MINUTE, minute).coerceIn(0, 59) else minute,
            focusBarEnabled = prefs.getBoolean(KEY_FOCUS_BAR_ENABLED, false),
            strongAlarmEnabled = prefs.getBoolean(KEY_STRONG_ALARM_ENABLED, false)
        )
    }

    fun setStudyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_STUDY_ENABLED, enabled).apply()
    }

    fun setMistakesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MISTAKES_ENABLED, enabled).apply()
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun setCountdownCardEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COUNTDOWN_ENABLED, enabled).apply()
    }

    fun setQuestionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUESTION_ENABLED, enabled).apply()
    }

    fun setQuestionTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_QUESTION_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_QUESTION_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun setMistakesTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_MISTAKES_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MISTAKES_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun setFocusBarEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOCUS_BAR_ENABLED, enabled).apply()
    }

    fun setStrongAlarmEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_STRONG_ALARM_ENABLED, enabled).apply()
    }
}
