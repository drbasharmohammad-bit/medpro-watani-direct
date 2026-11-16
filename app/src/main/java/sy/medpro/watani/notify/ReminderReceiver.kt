package sy.medpro.watani.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sy.medpro.watani.data.DailyChallengeSource
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.MistakesBank
import sy.medpro.watani.data.ReminderSettings
import sy.medpro.watani.ui.widget.Widgets

/**
 * مستقبِل منبّهات الدراسة (بناء 1.2.23).
 *
 * عند منبّه الوقت اليومي: ينشر تذكير الدراسة إن كان مفعّلًا
 * (بأرقام الهدف اليومي الحالية)، وينشر تنبيه مراجعة الأخطاء إن
 * كان مفعّلًا ويوجد مستحقّات فعلًا في «بنك أخطائي»، ثم يعيد
 * جدولة الغد. عند منبّه منتصف الليل: يحدّث الويدجت لليوم الجديد
 * ويعيد الجدولة. كل العمل محلي وقصير — لا شبكة هنا أبدًا.
 *
 * توسيع 1.2.30: لكل تنبيه منبّهه ووقته المستقل الآن — تذكير
 * الدراسة (يُنشر بسطح المنبّه القوي بملء الشاشة إن اختاره الطالب
 * وكان إذنه متاحًا)، تنبيه الأخطاء بوقته، وسؤال اليوم الصباحي
 * بوقته. منبّه التأجيل يعيد نشر تذكير الدراسة مرة واحدة. ومنتصف
 * الليل صار يحدّث بطاقة العدّاد الدائمة أيضًا مع الويدجت.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        when (intent?.action) {
            ReminderScheduler.ACTION_FIRE -> {
                val config = ReminderSettings.config(appContext)
                if (config.studyEnabled) {
                    postStudyReminderNow(appContext)
                }
                ReminderScheduler.rescheduleAll(appContext)
            }
            ReminderScheduler.ACTION_MISTAKES -> {
                val config = ReminderSettings.config(appContext)
                if (config.mistakesEnabled) {
                    val due = MistakesBank.stats(appContext).dueToday
                    if (due > 0) StudyNotifications.postMistakesDue(appContext, due)
                }
                ReminderScheduler.rescheduleAll(appContext)
            }
            ReminderScheduler.ACTION_QUESTION -> {
                val config = ReminderSettings.config(appContext)
                if (config.questionEnabled) {
                    val pick = DailyChallengeSource.pickToday(appContext)
                    if (pick != null) {
                        val (subject, question) = pick
                        val text = question.text?.takeIf { it.isNotBlank() }
                        if (text != null) StudyNotifications.postDailyQuestion(appContext, subject, text)
                    }
                }
                ReminderScheduler.rescheduleAll(appContext)
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                // تأجيل المنبّه القوي: إعادة نشر تذكير الدراسة مرة
                // واحدة بنفس سطحه المختار، بلا مساس بالجدولة اليومية.
                val config = ReminderSettings.config(appContext)
                if (config.studyEnabled) {
                    postStudyReminderNow(appContext)
                }
            }
            ReminderScheduler.ACTION_MIDNIGHT -> {
                Widgets.updateAll(appContext)
                StudyNotifications.refreshCountdownCard(appContext)
                ReminderScheduler.rescheduleAll(appContext)
            }
        }
    }

    private fun postStudyReminderNow(appContext: Context) {
        val snapshot = DailyGoalEngine.snapshot(appContext)
        StudyNotifications.postStudyReminderSmart(
            appContext,
            goal = snapshot.goal,
            doneToday = snapshot.doneToday,
            hasBank = snapshot.hasBank
        )
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
                StudyNotifications.refreshCountdownCard(context.applicationContext)
            }
        }
    }
}
