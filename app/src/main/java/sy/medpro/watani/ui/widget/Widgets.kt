package sy.medpro.watani.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * الكرونومتر موسوم الوحدات (ساعة/دقيقة/ثانية تحت أجزائه —
 * بملاحظة الدكتور 13:38) ويعدّ ذاتيًا داخل عملية المشغّل.
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

    internal fun buildCountdownViews(context: Context, data: WidgetData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_countdown)
        bindBackground(views, data)
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

    internal fun buildDayViews(context: Context, data: WidgetData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        bindBackground(views, data)
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

    internal fun buildLargeViews(context: Context, data: WidgetData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_large)
        bindBackground(views, data)
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

    /** Photo (resource reference — never a parceled bitmap) plus
     * the generated blend overlay. Plain mode hides both and the
     * root's burgundy gradient drawable shows instead. */
    private fun bindBackground(views: RemoteViews, data: WidgetData) {
        val index = data.landmarkIndex
        if (index == null) {
            views.setViewVisibility(R.id.widget_photo, View.GONE)
            views.setViewVisibility(R.id.widget_overlay, View.GONE)
        } else {
            views.setImageViewResource(
                R.id.widget_photo, LandmarkLibrary.all[index].drawableRes
            )
            views.setViewVisibility(R.id.widget_photo, View.VISIBLE)
            views.setImageViewBitmap(
                R.id.widget_overlay, WidgetOverlay.bitmap(data.settings.dimPercent)
            )
            views.setViewVisibility(R.id.widget_overlay, View.VISIBLE)
        }
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
     *   Chronometer with unit labels under its parts.
     * - FULL_DIGITAL: one Chronometer over the TOTAL remaining
     *   time; the days row hides (its value is inside the total)
     *   and labels hide (total hours exceed two digits).
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
                views.setViewVisibility(R.id.widget_chrono_labels, View.GONE)
                seedChrono(views, SystemClock.elapsedRealtime() + data.remainingMillis)
            }
            WidgetCountdownStyle.DAYS_AND_TIME -> {
                views.setViewVisibility(R.id.widget_days_row, View.VISIBLE)
                views.setViewVisibility(R.id.widget_chip, View.VISIBLE)
                views.setViewVisibility(R.id.widget_chrono_labels, View.VISIBLE)
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
                views.setTextViewText(textId, labelFor(action, kind))
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

    private fun labelFor(action: WidgetQuickAction, kind: Int): String = when (action) {
        WidgetQuickAction.FOCUS -> if (kind == KIND_LARGE) "ابدأ تركيز" else "تركيز"
        WidgetQuickAction.MISTAKES -> if (kind == KIND_LARGE) "بنك أخطائي" else "أخطائي"
        WidgetQuickAction.QUESTIONS -> if (kind == KIND_LARGE) "أسئلة اليوم" else "أسئلة"
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

/**
 * طبقة دمج الصورة: Bitmap صغير (32×256) يُولّد برمجيًا — صبغة
 * عنابية ثابتة 13% ثم تدرّج داكن #22050B بألفا نقاطه من
 * [WidgetSettingsMath.overlayStops] لمنزلق الطالب. يُخزّن
 * مؤقتًا لكل قيمة تعتيم (توليد واحد عند التغيّر فقط).
 */
internal object WidgetOverlay {

    private const val WIDTH = 32
    private const val HEIGHT = 256
    private const val DARK_R = 0x22
    private const val DARK_G = 0x05
    private const val DARK_B = 0x0B
    private const val TINT_R = 0x5C
    private const val TINT_G = 0x12
    private const val TINT_B = 0x20

    private var cachedDim: Int = -1
    private var cached: Bitmap? = null

    @Synchronized
    fun bitmap(dimPercent: Int): Bitmap {
        val dim = WidgetSettingsMath.clampDim(dimPercent)
        cached?.let { if (cachedDim == dim) return it }
        val (top, mid, bottom) = WidgetSettingsMath.overlayStops(dim)
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(
            Color.argb(
                (WidgetSettingsMath.TINT_ALPHA * 255).toInt(), TINT_R, TINT_G, TINT_B
            )
        )
        val paint = Paint()
        for (y in 0 until HEIGHT) {
            val t = y / (HEIGHT - 1).toFloat()
            val alpha = if (t < 0.5f) {
                top + (mid - top) * (t * 2f)
            } else {
                mid + (bottom - mid) * ((t - 0.5f) * 2f)
            }
            paint.color = Color.argb((alpha * 255).toInt(), DARK_R, DARK_G, DARK_B)
            canvas.drawRect(0f, y.toFloat(), WIDTH.toFloat(), y + 1f, paint)
        }
        cached = bmp
        cachedDim = dim
        return bmp
    }
}
