package sy.medpro.watani.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
                // 1.2.31 crash hardening: pickToday sweeps, decrypts
                // and parses the downloaded banks (DailyChallengeSource
                // .loadPool) — seconds of IO on a big install. Running
                // it synchronously here put that whole sweep on the
                // broadcast MAIN thread (ANR / broadcast timeout, and
                // the process could be killed mid-post). The work now
                // runs async on IO under goAsync; rescheduling is in
                // the finally so tomorrow's alarm is planted even if
                // today's pick fails.
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val config = ReminderSettings.config(appContext)
                        if (config.questionEnabled) {
                            val pick = DailyChallengeSource.pickToday(appContext)
                            if (pick != null) {
                                val (subject, question) = pick
                                val text = question.text?.takeIf { it.isNotBlank() }
                                if (text != null) {
                                    StudyNotifications.postDailyQuestion(appContext, subject, text)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // A failed pick loses today's question only —
                        // never the process, never tomorrow's alarm.
                    } finally {
                        try {
                            ReminderScheduler.rescheduleAll(appContext)
                        } catch (_: Exception) {
                        }
                        pending.finish()
                    }
                }
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
                // 1.2.31: widget rendering decodes/draws the photo
                // backgrounds per placed instance — real work that
                // does not belong on the broadcast main thread
                // either. Same goAsync + IO treatment.
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        Widgets.updateAll(appContext)
                        StudyNotifications.refreshCountdownCard(appContext)
                    } catch (_: Exception) {
                    } finally {
                        try {
                            ReminderScheduler.rescheduleAll(appContext)
                        } catch (_: Exception) {
                        }
                        pending.finish()
                    }
                }
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
                // 1.2.31: the widget render is the heavy part here
                // too (boot broadcasts have the tightest budget) —
                // async on IO under goAsync, like the midnight path.
                val appContext = context.applicationContext
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        ReminderScheduler.rescheduleAll(appContext)
                        Widgets.updateAll(appContext)
                        StudyNotifications.refreshCountdownCard(appContext)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
