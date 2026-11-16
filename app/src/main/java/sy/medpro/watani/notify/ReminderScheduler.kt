package sy.medpro.watani.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * حساب موعد التذكير التالي (منطق صافٍ — يُختبر وحدويًا).
 */
object ReminderTimeMath {

    /** Next occurrence of hour:minute strictly after [nowMillis]:
     * today when still ahead, otherwise tomorrow. */
    fun nextOccurrenceMillis(nowMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val today = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate()
        var candidate = LocalDateTime.of(today, LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        if (candidate.atZone(zone).toInstant().toEpochMilli() <= nowMillis) {
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    /** Next local midnight strictly after [nowMillis]. */
    fun nextMidnightMillis(nowMillis: Long, zone: ZoneId): Long {
        val today = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate()
        return today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun todayString(nowMillis: Long, zone: ZoneId): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate().toString()

    fun localDate(nowMillis: Long, zone: ZoneId): LocalDate =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate()
}

/**
 * جدولة تذكير الدراسة بمنبّهات AlarmManager (بناء 1.2.23).
 *
 * منبّه يومي واحد بوقت الطالب يطلق [ReminderReceiver] فينشر ما هو
 * مفعّل ويعيد جدولة نفسه لليوم التالي — فيعمل والتطبيق مغلق ودون
 * إنترنت. نستخدم setAndAllowWhileIdle (يصل ضمن نافذة Doze القصيرة)
 * بدل المنبّهات الدقيقة التي تشترط إذنًا خاصًا بسياسات أندرويد
 * الحديثة. منبّه ثانٍ عند منتصف الليل يحدّث الويدجت (يوم جديد =
 * عدّاد وهدف جديدان). تُستدعى إعادة الجدولة عند: إقلاع الجهاز،
 * تحديث التطبيق، بدء التطبيق، وأي تغيير بالإعدادات.
 *
 * توسيع 1.2.30: صار لكل تنبيه منبّهه المستقل بوقته الخاص —
 * تذكير الدراسة (ACTION_FIRE)، تنبيه مراجعة الأخطاء
 * (ACTION_MISTAKES)، وسؤال اليوم الصباحي (ACTION_QUESTION) —
 * إضافة إلى منبّه تأجيل لمرة واحدة (ACTION_SNOOZE) لزر «تأجيل
 * ١٠ دقائق» بصفحة المنبّه القوي. إعادة الجدولة الكاملة لا تلمس
 * منبّه التأجيل أبدًا (رمز طلب وإجراء مختلفان).
 */
object ReminderScheduler {
    const val ACTION_FIRE = "sy.medpro.watani.action.REMINDER_FIRE"
    const val ACTION_MIDNIGHT = "sy.medpro.watani.action.MIDNIGHT_TICK"
    const val ACTION_MISTAKES = "sy.medpro.watani.action.MISTAKES_FIRE"
    const val ACTION_QUESTION = "sy.medpro.watani.action.QUESTION_FIRE"
    const val ACTION_SNOOZE = "sy.medpro.watani.action.REMINDER_SNOOZE"

    private const val REQUEST_FIRE = 7001
    private const val REQUEST_MIDNIGHT = 7002
    private const val REQUEST_MISTAKES = 7003
    private const val REQUEST_QUESTION = 7004
    private const val REQUEST_SNOOZE = 7005

    private fun fireIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleDaily(
        alarmManager: AlarmManager,
        context: Context,
        action: String,
        requestCode: Int,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        now: Long,
        zone: ZoneId
    ) {
        val pending = fireIntent(context, action, requestCode)
        if (enabled) {
            val trigger = ReminderTimeMath.nextOccurrenceMillis(now, hour, minute, zone)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarmManager.cancel(pending)
        }
    }

    /** Schedules (or cancels) every daily alarm from the current settings. */
    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val config = sy.medpro.watani.data.ReminderSettings.config(appContext)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        scheduleDaily(
            alarmManager, appContext, ACTION_FIRE, REQUEST_FIRE,
            enabled = config.studyEnabled,
            hour = config.hour, minute = config.minute, now = now, zone = zone
        )
        scheduleDaily(
            alarmManager, appContext, ACTION_MISTAKES, REQUEST_MISTAKES,
            enabled = config.mistakesEnabled,
            hour = config.mistakesHour, minute = config.mistakesMinute, now = now, zone = zone
        )
        scheduleDaily(
            alarmManager, appContext, ACTION_QUESTION, REQUEST_QUESTION,
            enabled = config.questionEnabled,
            hour = config.questionHour, minute = config.questionMinute, now = now, zone = zone
        )

        val midnightPending = fireIntent(appContext, ACTION_MIDNIGHT, REQUEST_MIDNIGHT)
        val midnight = ReminderTimeMath.nextMidnightMillis(now, zone)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, midnightPending)
    }

    /** منبّه التأجيل لمرة واحدة (زر «تأجيل ١٠ دقائق» بصفحة المنبّه القوي). */
    fun scheduleSnooze(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = sy.medpro.watani.data.SnoozeMath.triggerMillis(System.currentTimeMillis())
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, trigger,
            fireIntent(appContext, ACTION_SNOOZE, REQUEST_SNOOZE)
        )
    }
}
