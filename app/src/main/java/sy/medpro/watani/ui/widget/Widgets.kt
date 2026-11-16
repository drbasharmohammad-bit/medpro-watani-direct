package sy.medpro.watani.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import sy.medpro.watani.MainActivity
import sy.medpro.watani.R
import sy.medpro.watani.data.CountdownMath
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.DayGoalItem
import sy.medpro.watani.data.DayGoalsStore
import sy.medpro.watani.data.FocusTimerEngine

/**
 * عائلة ويدجتات الشاشة الرئيسية (بناء 1.2.24 — إعادة تصميم كاملة
 * بأمر الدكتور بعد تجربته ويدجت 1.2.23 الأحادي على جهازه ورفضه
 * مستواه).
 *
 * ثلاثة أحجام يختار بينها الطالب من قائمة ويدجتات النظام:
 * - صغير (CountdownWidgetProvider): العدّاد فقط.
 * - متوسط (DayWidgetProvider): العدّاد + تقدم أسئلة اليوم + أهداف
 *   اليوم النصية.
 * - كبير (DayWidgetLargeProvider): كل ما سبق + دقائق تركيز اليوم،
 *   وقابل لتكبير الحجم.
 *
 * العدّاد الحي: الأيام الكاملة نصًّا، وباقي اليوم (ساعات/دقائق/
 * ثوانٍ) يعرضه Chronometer بعدّ تنازلي يعدّ ذاتيًا داخل عملية
 * المشغّل (اللانشر) — التطبيق لا يستيقظ كل ثانية فلا استنزاف
 * بطارية؛ قاعدة الكرونومتر تُعاد زراعتها مع كل تحديث ويدجت
 * (دوري/منتصف الليل/نشاط التطبيق/تعديل الأهداف).
 *
 * الأهداف المعروضة هي ما ضبطه الطالب بنفسه فقط: هدفه الرقمي من
 * الأسئلة وأهدافه النصية لليوم — لا رقم تلقائي مفروض أبدًا.
 * أي تعديل داخل التطبيق يستدعي [updateAll] فينعكس على الويدجت
 * مباشرة.
 */

internal data class WidgetData(
    val remainingMillis: Long,
    val days: Long,
    val withinDayMillis: Long,
    /** Questions answered today (the vault's daily counter). */
    val doneToday: Int,
    /** The student's own daily question target; null = not set. */
    val questionTarget: Int?,
    /** The student's free-text goals for today. */
    val items: List<DayGoalItem>,
    val focusMinutes: Int,
    val focusSessions: Int
)

object Widgets {

    private val PROVIDERS: List<Class<*>> = listOf(
        CountdownWidgetProvider::class.java,
        DayWidgetProvider::class.java,
        DayWidgetLargeProvider::class.java
    )

    /** Refreshes every placed widget of the family. Called on app
     * activity, at the midnight tick, after boot, and immediately
     * after any in-app goal edit — a widget refresh must never
     * crash a caller, so everything is guarded. */
    fun updateAll(context: Context) {
        try {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val data = loadData(appContext)
            PROVIDERS.forEach { providerClass ->
                val component = ComponentName(appContext, providerClass)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return@forEach
                val views = when (providerClass) {
                    CountdownWidgetProvider::class.java -> buildCountdownViews(appContext, data)
                    DayWidgetProvider::class.java -> buildDayViews(appContext, data)
                    else -> buildLargeViews(appContext, data)
                }
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        } catch (_: Exception) {
            // Guarded: boot / midnight / edit callbacks must survive.
        }
    }

    /** Single-provider refresh used by each provider's onUpdate. */
    internal fun updateProvider(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        layoutKind: Int
    ) {
        try {
            val appContext = context.applicationContext
            val data = loadData(appContext)
            val views = when (layoutKind) {
                KIND_COUNTDOWN -> buildCountdownViews(appContext, data)
                KIND_DAY -> buildDayViews(appContext, data)
                else -> buildLargeViews(appContext, data)
            }
            appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
        } catch (_: Exception) {
            // A system-driven widget update must never crash the
            // host process; the next scheduled refresh retries.
        }
    }

    internal const val KIND_COUNTDOWN = 0
    internal const val KIND_DAY = 1
    internal const val KIND_LARGE = 2

    internal fun loadData(context: Context): WidgetData {
        val target = DailyGoalEngine.examTargetOrDefault(context)
        val remaining = target - System.currentTimeMillis()
        val parts = CountdownMath.split(remaining)
        val snapshot = DailyGoalEngine.snapshot(context)
        FocusTimerEngine.init(context)
        val focus = FocusTimerEngine.state.value
        return WidgetData(
            remainingMillis = remaining,
            days = parts.days,
            withinDayMillis = parts.withinDayMillis,
            doneToday = snapshot.doneToday,
            questionTarget = snapshot.manualGoal,
            items = DayGoalsStore.todayItems(context),
            focusMinutes = focus.minutesToday,
            focusSessions = focus.sessionsToday
        )
    }

    // ------------------------- builders -------------------------

    internal fun buildCountdownViews(context: Context, data: WidgetData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_countdown)
        bindCountdown(views, data, R.id.widget_chrono, R.id.widget_expired)
        views.setTextViewText(R.id.widget_days, data.days.toString())
        bindOpenApp(context, views, R.id.widget_root)
        return views
    }

    internal fun buildDayViews(context: Context, data: WidgetData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        bindCountdown(views, data, R.id.widget_chrono, R.id.widget_expired)
        views.setTextViewText(R.id.widget_days, data.days.toString())
        bindQuestionProgress(views, data)
        bindGoalLines(
            views, data.items,
            listOf(R.id.widget_goal_line_1, R.id.widget_goal_line_2, R.id.widget_goal_line_3)
        )
        bindOpenApp(context, views, R.id.widget_root)
        return views
    }

    internal fun buildLargeViews(context: Context, data: WidgetData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_large)
        bindCountdown(views, data, R.id.widget_chrono, R.id.widget_expired)
        views.setTextViewText(R.id.widget_days, data.days.toString())
        bindQuestionProgress(views, data)
        bindGoalLines(
            views, data.items,
            listOf(
                R.id.widget_goal_line_1, R.id.widget_goal_line_2, R.id.widget_goal_line_3,
                R.id.widget_goal_line_4, R.id.widget_goal_line_5
            )
        )
        views.setTextViewText(
            R.id.widget_focus_text,
            "تركيز اليوم: ${data.focusMinutes} دقيقة · ${data.focusSessions} جلسة"
        )
        bindOpenApp(context, views, R.id.widget_root)
        return views
    }

    // ------------------------- bindings -------------------------

    /**
     * The live countdown: whole days are a separate text view; the
     * within-day remainder ticks on a count-down Chronometer whose
     * base is re-seeded at every widget update. Once the exam
     * moment passes, the Chronometer hides and the arrival line
     * shows instead (a count-down Chronometer past its base would
     * render negative garbage).
     */
    private fun bindCountdown(views: RemoteViews, data: WidgetData, chronoId: Int, expiredId: Int) {
        if (data.remainingMillis > 0L) {
            val base = CountdownMath.chronometerBase(
                SystemClock.elapsedRealtime(), data.withinDayMillis
            )
            views.setChronometer(chronoId, base, "%s", true)
            views.setChronometerCountDown(chronoId, true)
            views.setViewVisibility(chronoId, View.VISIBLE)
            views.setViewVisibility(expiredId, View.GONE)
        } else {
            views.setViewVisibility(chronoId, View.GONE)
            views.setViewVisibility(expiredId, View.VISIBLE)
        }
    }

    /** Today's question progress against the student's OWN target.
     * No target set → an invitation to set one, never an imposed
     * auto number (doctor's 1.2.24 ruling). */
    private fun bindQuestionProgress(views: RemoteViews, data: WidgetData) {
        val target = data.questionTarget
        if (target == null) {
            views.setTextViewText(
                R.id.widget_goal_text,
                "أسئلة اليوم: ${data.doneToday} — اضبط هدفك من التطبيق"
            )
            views.setProgressBar(R.id.widget_goal_progress, 100, 0, false)
        } else {
            views.setTextViewText(
                R.id.widget_goal_text,
                "أسئلة اليوم: ${data.doneToday} من $target"
            )
            views.setProgressBar(
                R.id.widget_goal_progress, 100,
                CountdownMath.progressPercent(data.doneToday, target), false
            )
        }
    }

    /** Free-text goals as fixed lines: done items get a gold check,
     * pending ones a hollow circle; unused lines hide. An empty day
     * shows one invitation line instead of a blank gap. */
    private fun bindGoalLines(views: RemoteViews, items: List<DayGoalItem>, lineIds: List<Int>) {
        if (items.isEmpty()) {
            views.setTextViewText(R.id.widget_goal_line_1, "لا أهداف مكتوبة لليوم — أضفها من التطبيق")
            views.setViewVisibility(R.id.widget_goal_line_1, View.VISIBLE)
            lineIds.drop(1).forEach { views.setViewVisibility(it, View.GONE) }
            return
        }
        lineIds.forEachIndexed { index, viewId ->
            val item = items.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(viewId, View.GONE)
            } else {
                views.setTextViewText(viewId, (if (item.done) "✓ " else "○ ") + item.text)
                views.setTextColor(viewId, if (item.done) 0xFFB08D57.toInt() else 0xFFFFFFFF.toInt())
                views.setViewVisibility(viewId, View.VISIBLE)
            }
        }
    }

    private fun bindOpenApp(context: Context, views: RemoteViews, rootId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(rootId, pending)
    }
}
