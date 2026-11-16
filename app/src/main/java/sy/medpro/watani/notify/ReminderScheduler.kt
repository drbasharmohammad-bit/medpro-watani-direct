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
 */
object ReminderScheduler {
    const val ACTION_FIRE = "sy.medpro.watani.action.REMINDER_FIRE"
    const val ACTION_MIDNIGHT = "sy.medpro.watani.action.MIDNIGHT_TICK"

    private const val REQUEST_FIRE = 7001
    private const val REQUEST_MIDNIGHT = 7002

    private fun fireIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedules (or cancels) both alarms from the current settings. */
    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val config = sy.medpro.watani.data.ReminderSettings.config(appContext)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        val firePending = fireIntent(appContext, ACTION_FIRE, REQUEST_FIRE)
        if (config.anyEnabled) {
            val trigger = ReminderTimeMath.nextOccurrenceMillis(now, config.hour, config.minute, zone)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, firePending)
        } else {
            alarmManager.cancel(firePending)
        }

        val midnightPending = fireIntent(appContext, ACTION_MIDNIGHT, REQUEST_MIDNIGHT)
        val midnight = ReminderTimeMath.nextMidnightMillis(now, zone)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, midnightPending)
    }
}
