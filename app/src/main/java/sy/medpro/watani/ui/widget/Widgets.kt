package sy.medpro.watani.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import sy.medpro.watani.MainActivity
import sy.medpro.watani.R
import sy.medpro.watani.data.CountdownMath
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.DayGoalItem
import sy.medpro.watani.data.DayGoalsStore
import sy.medpro.watani.data.ExamDateText
import sy.medpro.watani.data.FocusTimerEngine
import sy.medpro.watani.data.WidgetBackgroundMath
import sy.medpro.watani.data.WidgetCountdownStyle
import sy.medpro.watani.data.WidgetQuickAction
import sy.medpro.watani.data.WidgetSettings
import sy.medpro.watani.data.WidgetSettingsMath
import sy.medpro.watani.data.WidgetShapeMath
import sy.medpro.watani.data.WidgetSettingsStore
import java.time.LocalDate

/**
 * عائلة ويدجتات الشاشة الرئيسية v3 (بناء 1.2.25 — تصميم اعتمده
 * الدكتور من نماذج بصرية 16 نوفمبر 13:42 بعد رفضه 1.2.24 على
 * جهازه).
 *
 * فوق صورة معلم سوري (تدوير يومي حتمي أو اختيار الطالب) مدموجة
 * بطبقة الدمج المعتمدة: صبغة عنابية 13% + تدرّج داكن تتحدد
 * ألفاه من منزلق تعتيم الطالب (عند 70% يطابق النموذج حرفيًا).
 * الصورة مورد drawable يُضبط بمرجعه — لا تُرسَل Bitmap للصورة
 * أبدًا، فكلفة التحديث على الـBinder تبقى تافهة مهما كبرت
 * المكتبة (~30 معلمًا).
 *
 * الأحجام بمحتوى مختلف فعلًا: صغير = عدّاد فقط · متوسط =
 * عدّاد + تاريخ + تقدم أسئلة اليوم + زرّان سريعان · كبير =
 * كل ذلك + أهداف اليوم + تركيز اليوم/الأسبوع + 4 أزرار.
 * الكرونومتر رقم زمني وحده (HH:MM:SS) بلا وسوم وحدات —
 * الدكتور أمر بحذف كلمات «ساعة/دقيقة/ثانية» نهائيًا بعد
 * تجربة جهازه (16 نوفمبر 14:43، كلمته الأخيرة تنسخ اعتماد
 * الوسم 13:38) — ويعدّ ذاتيًا داخل عملية المشغّل.
 *
 * ⛔ الأزرار السريعة (شرط الدكتور الصريح): كل زر يفتح شاشته
 * المقصودة داخل التطبيق مباشرة عبر [WidgetRouter] — تركيز ←
 * شاشة التركيز، أخطائي ← بنك أخطائي، أسئلة ← بنك الأسئلة،
 * المكتبة ← المكتبة. لكل زر قصديّة مستقلة (فعل + requestCode
 * فريدان لكل زر ولكل حجم) فلا تندمج النوايا أبدًا. جسم
 * الويدجت وحده يفتح الرئيسية.
 *
 * انحرافان قسريان عن النموذج (حدود RemoteViews): خط النصوص
 * خط النظام (لا خطوط مخصصة في RemoteViews)، وذهبي الأرقام
 * مسطّح #E9C767 (لا تدرّج لون على نصّيّات RemoteViews).
 */

internal data class WidgetData(
    val remainingMillis: Long,
    val days: Long,
    val withinDayMillis: Long,
    /** Exam date in Levantine Arabic («الثلاثاء 22 كانون الأول 2026»). */
    val examDateText: String,
    /** Questions answered today (the vault's daily counter). */
    val doneToday: Int,
    /** The student's own daily question target; null = not set. */
    val questionTarget: Int?,
    /** The student's free-text goals for today. */
    val items: List<DayGoalItem>,
    val focusMinutes: Int,
    val focusSessions: Int,
    val focusWeekMinutes: Int,
    val settings: WidgetSettings,
    /** Chosen landmark index for today, or null in plain mode. */
    val landmarkIndex: Int?,
    val landmarkName: String?
)

object Widgets {

    private val PROVIDERS: List<Class<*>> = listOf(
        CountdownWidgetProvider::class.java,
        DayWidgetProvider::class.java,
        DayWidgetLargeProvider::class.java
    )

    /** Refreshes every placed widget of the family. Called on app
     * activity, at the midnight tick, after boot, after any in-app
     * goal edit, and after every widget-settings change — a widget
     * refresh must never crash a caller, so everything is guarded. */
    fun updateAll(context: Context) {
        try {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val data = loadData(appContext)
            PROVIDERS.forEach { providerClass ->
                val component = ComponentName(appContext, providerClass)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return@forEach
                val kind = when (providerClass) {
                    CountdownWidgetProvider::class.java -> KIND_COUNTDOWN
                    DayWidgetProvider::class.java -> KIND_DAY
                    else -> KIND_LARGE
                }
                // Per-instance views (1.2.27): the background
                // bitmaps are clipped at the instance's real size,
                // so each placed widget gets its own build.
                ids.forEach { id ->
                    val views = buildViewsForKind(
                        appContext, data, kind, widgetSizeDp(manager, id, kind)
                    )
                    manager.updateAppWidget(id, views)
                }
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
            appWidgetIds.forEach { id ->
                val views = buildViewsForKind(
                    appContext, data, layoutKind, widgetSizeDp(appWidgetManager, id, layoutKind)
                )
                appWidgetManager.updateAppWidget(id, views)
            }
        } catch (_: Exception) {
            // A system-driven widget update must never crash the
            // host process; the next scheduled refresh retries.
        }
    }

    private fun buildViewsForKind(
        context: Context,
        data: WidgetData,
        kind: Int,
        sizeDp: Pair<Int, Int>
    ): RemoteViews = when (kind) {
        KIND_COUNTDOWN -> buildCountdownViews(context, data, sizeDp)
        KIND_DAY -> buildDayViews(context, data, sizeDp)
        else -> buildLargeViews(context, data, sizeDp)
    }

    /** The placed instance's real size in dp from its widget
     * options; the provider-info minimums when the host reports
     * nothing usable (1.2.27 — the background clip is built at
     * exactly this size). */
    private fun widgetSizeDp(
        manager: AppWidgetManager,
        appWidgetId: Int,
        kind: Int
    ): Pair<Int, Int> {
        val options: Bundle? = try {
            manager.getAppWidgetOptions(appWidgetId)
        } catch (_: Exception) {
            null
        }
        val fallback = WidgetShapeMath.fallbackSizeDp(kind)
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        return (if (width > 0) width else fallback.first) to
            (if (height > 0) height else fallback.second)
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
        val settings = WidgetSettingsStore.settings(context)
        val landmarkIndex = WidgetBackgroundMath.pickIndex(
            settings, LocalDate.now().toEpochDay(), LandmarkLibrary.all.size
        )
        return WidgetData(
            remainingMillis = remaining,
            days = parts.days,
            withinDayMillis = parts.withinDayMillis,
            examDateText = ExamDateText.formatMillis(target),
            doneToday = snapshot.doneToday,
            questionTarget = snapshot.manualGoal,
            items = DayGoalsStore.todayItems(context),
            focusMinutes = focus.minutesToday,
            focusSessions = focus.sessionsToday,
            focusWeekMinutes = focus.minutesWeek,
            settings = settings,
            landmarkIndex = landmarkIndex,
            landmarkName = landmarkIndex?.let { LandmarkLibrary.all[it].name }
        )
    }

    // ------------------------- builders -------------------------

    internal fun buildCountdownViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int> = WidgetShapeMath.fallbackSizeDp(KIND_COUNTDOWN)
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_countdown)
        bindBackground(views, context, data, sizeDp)
        if (data.settings.showCountdown) {
            views.setViewVisibility(R.id.widget_days_row, View.VISIBLE)
            bindChronometer(views, data)
        } else {
            views.setViewVisibility(R.id.widget_days_row, View.GONE)
            views.setViewVisibility(R.id.widget_chip, View.GONE)
            views.setViewVisibility(R.id.widget_expired, View.GONE)
        }
        bindLandmarkLine(views, data, force = !data.settings.showCountdown)
        bindOpenApp(context, views, R.id.widget_root)
        return views
    }

    internal fun buildDayViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int> = WidgetShapeMath.fallbackSizeDp(KIND_DAY)
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        bindBackground(views, context, data, sizeDp)
        if (data.settings.showCountdown) {
            views.setViewVisibility(R.id.widget_countdown_block, View.VISIBLE)
            bindChronometer(views, data)
            views.setTextViewText(R.id.widget_date, data.examDateText)
        } else {
            views.setViewVisibility(R.id.widget_countdown_block, View.GONE)
        }
        bindLandmarkLine(views, data, force = false)
        bindQuestionProgress(views, data)
        bindButtons(
            context, views, data, KIND_DAY,
            listOf(
                Triple(R.id.widget_btn_1, R.id.widget_btn_1_icon, R.id.widget_btn_1_text),
                Triple(R.id.widget_btn_2, R.id.widget_btn_2_icon, R.id.widget_btn_2_text)
            )
        )
        bindOpenApp(context, views, R.id.widget_root)
        return views
    }

    internal fun buildLargeViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int> = WidgetShapeMath.fallbackSizeDp(KIND_LARGE)
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_large)
        bindBackground(views, context, data, sizeDp)
        if (data.settings.showCountdown) {
            views.setViewVisibility(R.id.widget_countdown_block, View.VISIBLE)
            bindChronometer(views, data)
            views.setTextViewText(R.id.widget_date, data.examDateText)
        } else {
            views.setViewVisibility(R.id.widget_countdown_block, View.GONE)
            views.setViewVisibility(R.id.widget_expired, View.GONE)
        }
        bindQuestionProgress(views, data)
        bindGoalLines(
            views, data.items,
            listOf(R.id.widget_goal_line_1, R.id.widget_goal_line_2, R.id.widget_goal_line_3)
        )
        views.setViewVisibility(
            R.id.widget_goals_panel,
            if (data.settings.showGoals) View.VISIBLE else View.GONE
        )
        views.setTextViewText(
            R.id.widget_focus_text,
            "تركيز اليوم: ${data.focusMinutes} دقيقة · ${sessionsText(data.focusSessions)} — الأسبوع: ${data.focusWeekMinutes} دقيقة"
        )
        views.setViewVisibility(
            R.id.widget_focus_row,
            if (data.settings.showFocus) View.VISIBLE else View.GONE
        )
        bindLandmarkLine(views, data, force = false)
        bindButtons(
            context, views, data, KIND_LARGE,
            listOf(
                Triple(R.id.widget_btn_1, R.id.widget_btn_1_icon, R.id.widget_btn_1_text),
                Triple(R.id.widget_btn_2, R.id.widget_btn_2_icon, R.id.widget_btn_2_text),
                Triple(R.id.widget_btn_3, R.id.widget_btn_3_icon, R.id.widget_btn_3_text),
                Triple(R.id.widget_btn_4, R.id.widget_btn_4_icon, R.id.widget_btn_4_text)
            )
        )
        bindOpenApp(context, views, R.id.widget_root)
        return views
    }

    // ------------------------- bindings -------------------------

    /** Photo plus the generated blend overlay. Plain mode hides
     * both and the root's burgundy gradient drawable shows instead.
     *
     * 1.2.27 (doctor's device report — photo corner outside the
     * gold frame's arc): BOTH layers are now sent as bitmaps
     * pre-clipped in code to the frame's own round-rect path
     * (radius [WidgetShapeMath.CORNER_RADIUS_DP] at the instance's
     * real size), so no pixel can land outside the arc on any
     * launcher. Before, the photo relied on clipToOutline in the
     * host process (not applied to the ImageView content on the
     * doctor's launcher) and the overlay had no clipping at all.
     * Any bitmap failure falls back to the pre-1.2.27 resource
     * path rather than breaking the widget. */
    private fun bindBackground(
        views: RemoteViews,
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ) {
        val index = data.landmarkIndex
        if (index == null) {
            views.setViewVisibility(R.id.widget_photo, View.GONE)
            views.setViewVisibility(R.id.widget_overlay, View.GONE)
            return
        }
        val density = context.resources.displayMetrics.density
        val (widthPx, heightPx) = WidgetShapeMath.bitmapSizePx(sizeDp.first, sizeDp.second, density)
        val radiusPx = WidgetShapeMath.radiusPx(sizeDp.first, sizeDp.second, density)
        val photo = WidgetPhotos.rounded(
            context, LandmarkLibrary.all[index].drawableRes, widthPx, heightPx, radiusPx
        )
        if (photo != null) {
            views.setImageViewBitmap(R.id.widget_photo, photo)
        } else {
            views.setImageViewResource(
                R.id.widget_photo, LandmarkLibrary.all[index].drawableRes
            )
        }
        views.setViewVisibility(R.id.widget_photo, View.VISIBLE)
        views.setImageViewBitmap(
            R.id.widget_overlay,
            WidgetOverlay.bitmap(data.settings.dimPercent, widthPx, heightPx, radiusPx)
        )
        views.setViewVisibility(R.id.widget_overlay, View.VISIBLE)
    }

    private fun bindLandmarkLine(views: RemoteViews, data: WidgetData, force: Boolean) {
        val name = data.landmarkName
        if (name != null && (force || data.settings.showLandmarkName)) {
            views.setTextViewText(R.id.widget_landmark, name)
            views.setViewVisibility(R.id.widget_landmark, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_landmark, View.GONE)
        }
    }

    /**
     * The live countdown chip. Styles (student's choice):
     * - DAYS_AND_TIME: within-day remainder on a count-down
     *   Chronometer (bare HH:MM:SS — no unit labels, by the
     *   doctor's device ruling 16 Nov 14:43).
     * - FULL_DIGITAL: one Chronometer over the TOTAL remaining
     *   time; the days row hides (its value is inside the total).
     * - DAYS_ONLY: no chip at all.
     * Past the exam moment the chip hides and the arrival line
     * shows instead (a count-down Chronometer past its base
     * would render negative garbage).
     */
    private fun bindChronometer(views: RemoteViews, data: WidgetData) {
        if (data.remainingMillis <= 0L) {
            views.setViewVisibility(R.id.widget_days_row, View.GONE)
            views.setViewVisibility(R.id.widget_chip, View.GONE)
            views.setViewVisibility(R.id.widget_expired, View.VISIBLE)
            return
        }
        views.setViewVisibility(R.id.widget_expired, View.GONE)
        views.setTextViewText(R.id.widget_days, data.days.toString())
        when (data.settings.countdownStyle) {
            WidgetCountdownStyle.DAYS_ONLY -> {
                views.setViewVisibility(R.id.widget_days_row, View.VISIBLE)
                views.setViewVisibility(R.id.widget_chip, View.GONE)
            }
            WidgetCountdownStyle.FULL_DIGITAL -> {
                views.setViewVisibility(R.id.widget_days_row, View.GONE)
                views.setViewVisibility(R.id.widget_chip, View.VISIBLE)
                seedChrono(views, SystemClock.elapsedRealtime() + data.remainingMillis)
            }
            WidgetCountdownStyle.DAYS_AND_TIME -> {
                views.setViewVisibility(R.id.widget_days_row, View.VISIBLE)
                views.setViewVisibility(R.id.widget_chip, View.VISIBLE)
                seedChrono(
                    views,
                    CountdownMath.chronometerBase(
                        SystemClock.elapsedRealtime(), data.withinDayMillis
                    )
                )
            }
        }
    }

    private fun seedChrono(views: RemoteViews, base: Long) {
        views.setChronometer(R.id.widget_chrono, base, "%s", true)
        views.setChronometerCountDown(R.id.widget_chrono, true)
        views.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
    }

    /** Today's question progress against the student's OWN target.
     * No target set → an invitation to set one, never an imposed
     * auto number (doctor's standing ruling). */
    private fun bindQuestionProgress(views: RemoteViews, data: WidgetData) {
        views.setViewVisibility(
            R.id.widget_questions_block,
            if (data.settings.showQuestions) View.VISIBLE else View.GONE
        )
        if (!data.settings.showQuestions) return
        val target = data.questionTarget
        if (target == null) {
            views.setTextViewText(R.id.widget_goal_text, data.doneToday.toString())
            views.setTextViewText(R.id.widget_goal_caption, "اضبط هدفك اليومي من التطبيق")
            views.setProgressBar(R.id.widget_goal_progress, 100, 0, false)
        } else {
            views.setTextViewText(
                R.id.widget_goal_text, "${data.doneToday} من $target"
            )
            views.setTextViewText(R.id.widget_goal_caption, "هدف اليوم: $target سؤالًا")
            views.setProgressBar(
                R.id.widget_goal_progress, 100,
                CountdownMath.progressPercent(data.doneToday, target), false
            )
        }
    }

    /** Free-text goals on the large widget's dark panel: done
     * items get a gold check, pending ones a hollow circle;
     * unused lines hide. An empty day shows one invitation line. */
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
                views.setTextColor(viewId, if (item.done) 0xFFE9C767.toInt() else 0xFFFAF4E6.toInt())
                views.setViewVisibility(viewId, View.VISIBLE)
            }
        }
    }

    /**
     * Quick-action buttons: each visible slot binds the student's
     * chosen action (in his order) with its OWN PendingIntent —
     * a unique intent action string and request code per
     * (action × widget kind), so intents can never merge and a
     * tap always lands on that button's intended screen.
     */
    private fun bindButtons(
        context: Context,
        views: RemoteViews,
        data: WidgetData,
        kind: Int,
        slots: List<Triple<Int, Int, Int>>
    ) {
        val actions = if (data.settings.showButtons) data.settings.quickActions else emptyList()
        views.setViewVisibility(
            R.id.widget_buttons_row,
            if (actions.isEmpty()) View.GONE else View.VISIBLE
        )
        if (actions.isEmpty()) return
        slots.forEachIndexed { index, (boxId, iconId, textId) ->
            val action = actions.getOrNull(index)
            if (action == null) {
                views.setViewVisibility(boxId, View.GONE)
            } else {
                views.setViewVisibility(boxId, View.VISIBLE)
                views.setImageViewResource(iconId, iconFor(action, kind))
                views.setTextViewText(textId, labelFor(action))
                views.setOnClickPendingIntent(boxId, routePendingIntent(context, action, kind))
            }
        }
    }

    private fun iconFor(action: WidgetQuickAction, kind: Int): Int = when (action) {
        WidgetQuickAction.FOCUS -> if (kind == KIND_LARGE) R.drawable.ic_w_play else R.drawable.ic_w_clock
        WidgetQuickAction.MISTAKES -> R.drawable.ic_w_alert
        WidgetQuickAction.QUESTIONS -> R.drawable.ic_w_question
        WidgetQuickAction.LIBRARY -> R.drawable.ic_w_book
    }

    /**
     * Short labels on every size — the long large-widget forms
     * («بنك أخطائي», «أسئلة اليوم», «ابدأ تركيز») clipped on the
     * doctor's real device (MIUI squeezed the buttons until the
     * text cut off, 16 Nov 14:43). Short forms + singleLine in
     * the layouts guarantee no button text can ever clip; the
     * routing destination is unchanged.
     */
    private fun labelFor(action: WidgetQuickAction): String = when (action) {
        WidgetQuickAction.FOCUS -> "تركيز"
        WidgetQuickAction.MISTAKES -> "أخطائي"
        WidgetQuickAction.QUESTIONS -> "أسئلة"
        WidgetQuickAction.LIBRARY -> "المكتبة"
    }

    /** One independent PendingIntent per (action × kind): the
     * unique action string is what keeps Android from merging
     * intents that differ only by extras. */
    private fun routePendingIntent(
        context: Context,
        action: WidgetQuickAction,
        kind: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = "sy.medpro.watani.widget.OPEN_${action.name}_K$kind"
            putExtra(WidgetRouter.EXTRA_ROUTE, action.route)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val requestCode = 100 + kind * 10 + action.ordinal
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

    private fun sessionsText(count: Int): String = when (count) {
        1 -> "جلسة واحدة"
        2 -> "جلستان"
        else -> "$count جلسة"
    }
}

/** The one corner path every widget layer follows (1.2.27):
 * a round-rect of the frame's own radius over the full bitmap. */
private fun widgetClipPath(widthPx: Int, heightPx: Int, radiusPx: Float): Path =
    Path().apply {
        addRoundRect(
            RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
            radiusPx, radiusPx, Path.Direction.CW
        )
    }

/**
 * صورة المعلم مقصوصة فعليًا على مسار الإطار (1.2.27): تُفكّ من
 * المورد (مربّعات 1200×1200)، تُقصّ مركزيًا لنسبة مقاس الويدجت
 * الحقيقي، وتُرسم عبر مسار دائري نصف قطره نصف قطر الإطار نفسه —
 * فلا يصل المشغّل أي بكسل خارج القوس. تُخزّن مؤقتًا لكل
 * (مورد، مقاس). عند أي فشل تُرجع null ويعود الربط لمسار المورد.
 */
internal object WidgetPhotos {

    private data class Key(val resId: Int, val widthPx: Int, val heightPx: Int)

    private var cachedKey: Key? = null
    private var cached: Bitmap? = null

    @Synchronized
    fun rounded(
        context: Context,
        resId: Int,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ): Bitmap? {
        val key = Key(resId, widthPx, heightPx)
        cached?.let { if (cachedKey == key) return it }
        val result = try {
            val source = BitmapFactory.decodeResource(context.resources, resId)
                ?: return null
            val crop = WidgetShapeMath.centerCropRect(
                source.width, source.height, widthPx.toFloat() / heightPx.toFloat()
            )
            val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.clipPath(widgetClipPath(widthPx, heightPx, radiusPx))
            val srcRect = android.graphics.Rect(
                crop[0].toInt(), crop[1].toInt(),
                (crop[0] + crop[2]).toInt(), (crop[1] + crop[3]).toInt()
            )
            val dstRect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            canvas.drawBitmap(source, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
            out
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        if (result != null) {
            cached = result
            cachedKey = key
        }
        return result
    }
}

/**
 * طبقة دمج الصورة: Bitmap يُولّد برمجيًا بمقاس الويدجت الحقيقي —
 * صبغة عنابية ثابتة 13% ثم تدرّج داكن #22050B بألفا نقاطه من
 * [WidgetSettingsMath.overlayStops] لمنزلق الطالب، وكلّه مرسوم
 * عبر مسار قصّ الإطار الدائري (1.2.27 — قبلها كانت الطبقة بلا
 * أي قصّ فتخرج زواياها المربّعة عن قوس الإطار). يُخزّن مؤقتًا
 * لكل (تعتيم، مقاس).
 */
internal object WidgetOverlay {

    private const val DARK_R = 0x22
    private const val DARK_G = 0x05
    private const val DARK_B = 0x0B
    private const val TINT_R = 0x5C
    private const val TINT_G = 0x12
    private const val TINT_B = 0x20

    private data class Key(val dim: Int, val widthPx: Int, val heightPx: Int)

    private var cachedKey: Key? = null
    private var cached: Bitmap? = null

    @Synchronized
    fun bitmap(dimPercent: Int, widthPx: Int, heightPx: Int, radiusPx: Float): Bitmap {
        val dim = WidgetSettingsMath.clampDim(dimPercent)
        val key = Key(dim, widthPx, heightPx)
        cached?.let { if (cachedKey == key) return it }
        val (top, mid, bottom) = WidgetSettingsMath.overlayStops(dim)
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.clipPath(widgetClipPath(widthPx, heightPx, radiusPx))
        canvas.drawColor(
            Color.argb(
                (WidgetSettingsMath.TINT_ALPHA * 255).toInt(), TINT_R, TINT_G, TINT_B
            )
        )
        val paint = Paint()
        for (y in 0 until heightPx) {
            val t = y / (heightPx - 1).coerceAtLeast(1).toFloat()
            val alpha = if (t < 0.5f) {
                top + (mid - top) * (t * 2f)
            } else {
                mid + (bottom - mid) * ((t - 0.5f) * 2f)
            }
            paint.color = Color.argb((alpha * 255).toInt(), DARK_R, DARK_G, DARK_B)
            canvas.drawRect(0f, y.toFloat(), widthPx.toFloat(), y + 1f, paint)
        }
        cached = bmp
        cachedKey = key
        return bmp
    }
}
