package sy.medpro.watani.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.MistakesBank
import sy.medpro.watani.data.ReminderSettings
import sy.medpro.watani.ui.widget.Widgets

/**
 * مستقبِل منبّهات الدراسة (بناء 1.2.23).
 *
 * عند منبّه الوقت اليومي: ينشر تذكير الدراسة إن كان مفعّلًا (بأرقام
 * الهدف اليومي الحالية)، وينشر تنبيه مراجعة الأخطاء إن كان مفعّلًا
 * ويوجد مستحقّات فعلًا في «بنك أخطائي»، ثم يعيد جدولة الغد. عند
 * منبّه منتصف الليل: يحدّث الويدجت لليوم الجديد ويعيد الجدولة.
 * كل العمل محلي وقصير — لا شبكة هنا أبدًا.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        when (intent?.action) {
            ReminderScheduler.ACTION_FIRE -> {
                val config = ReminderSettings.config(appContext)
                if (config.studyEnabled) {
                    val snapshot = DailyGoalEngine.snapshot(appContext)
                    StudyNotifications.postStudyReminder(
                        appContext,
                        goal = snapshot.goal,
                        doneToday = snapshot.doneToday,
                        hasBank = snapshot.hasBank
                    )
                }
                if (config.mistakesEnabled) {
                    val due = MistakesBank.stats(appContext).dueToday
                    if (due > 0) StudyNotifications.postMistakesDue(appContext, due)
                }
                ReminderScheduler.rescheduleAll(appContext)
            }
            ReminderScheduler.ACTION_MIDNIGHT -> {
                Widgets.updateAll(appContext)
                ReminderScheduler.rescheduleAll(appContext)
            }
        }
    }
}

/**
 * إعادة الجدولة بعد إقلاع الجهاز أو تحديث التطبيق — المنبّهات لا
 * تصمد وحدها عبر إعادة التشغيل، فهذا المستقبِل يعيد زرعها من
 * الإعدادات المحفوظة، ويحدّث الويدجت أيضًا.
 */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ReminderScheduler.rescheduleAll(context.applicationContext)
                Widgets.updateAll(context.applicationContext)
            }
        }
    }
}
