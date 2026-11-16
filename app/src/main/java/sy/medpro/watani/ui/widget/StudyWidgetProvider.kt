package sy.medpro.watani.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import sy.medpro.watani.MainActivity
import sy.medpro.watani.R
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.FocusTimerEngine
import sy.medpro.watani.data.OfflineStudyVault

/**
 * ويدجت الشاشة الرئيسية (بناء 1.2.23): عدّاد أيام الامتحان، هدف
 * اليوم وتقدّمه، ودقائق تركيز اليوم — بهوية التطبيق (عنابي/عاجي).
 *
 * البيانات من المخازن المحلية نفسها التي تقرأها الرئيسية (الهدف
 * اليومي + عدّاد الامتحان + محرك التركيز) فلا رقم مختلف بين
 * الويدجت والتطبيق. التحديث: دوري (updatePeriodMillis) + عند نشاط
 * التطبيق + منبّه منتصف الليل. النقرة تفتح التطبيق.
 */
class StudyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {

        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context.applicationContext)
                val component = ComponentName(context.applicationContext, StudyWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return
                val views = buildViews(context.applicationContext)
                ids.forEach { manager.updateAppWidget(it, views) }
            } catch (_: Exception) {
                // A widget refresh must never crash a caller (boot,
                // midnight alarm, activity start).
            }
        }

        fun buildViews(context: Context): RemoteViews {
            val appContext = context.applicationContext
            val views = RemoteViews(appContext.packageName, R.layout.study_widget)

            val target = DailyGoalEngine.examTargetOrDefault(appContext)
            val daysLeft = OfflineStudyVault.daysUntilExam(target)
            val snapshot = DailyGoalEngine.snapshot(appContext)

            FocusTimerEngine.init(appContext)
            val focusMinutes = FocusTimerEngine.state.value.minutesToday

            views.setTextViewText(R.id.widget_days, daysLeft.toString())
            views.setTextViewText(
                R.id.widget_goal_text,
                when {
                    !snapshot.hasBank -> "نزّل بنوك الأسئلة ليُحسب هدفك اليومي"
                    snapshot.goal <= 0 -> "أنهيت الأسئلة المتاحة — أحسنت"
                    else -> "هدف اليوم: ${snapshot.doneToday}/${snapshot.goal} سؤالًا"
                }
            )
            val progress = if (snapshot.goal > 0) {
                (snapshot.doneToday * 100 / snapshot.goal).coerceIn(0, 100)
            } else if (snapshot.hasBank) 100 else 0
            views.setProgressBar(R.id.widget_goal_progress, 100, progress, false)
            views.setTextViewText(R.id.widget_focus_text, "تركيز اليوم: $focusMinutes دقيقة")

            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = PendingIntent.getActivity(
                appContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
