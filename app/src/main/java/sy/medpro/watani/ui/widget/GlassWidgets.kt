package sy.medpro.watani.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import sy.medpro.watani.MainActivity
import sy.medpro.watani.R
import sy.medpro.watani.data.WidgetGlassStyle
import sy.medpro.watani.data.WidgetQuickAction
import sy.medpro.watani.data.WidgetTipMath

/** أدوار ألوان لوحة الزجاج على عناصر التخطيط (مجموعة قيمية —
 * نمط الريبو لتفادي LongParameterList). */
internal data class PaletteRoles(
    val headings: List<Int>,
    val primary: List<Int>,
    val secondary: List<Int>,
    val accent: List<Int>
)

/**
 * بنّاؤو تصاميم عائلة الزجاج v4 التسعة (1.2.51) — فُصلوا عن
 * [Widgets] حتى لا يتضخم (LargeClass)، ويعيدون استخدام ربطاته
 * الداخلية القائمة (الخلفية الزجاجية، الكرونومتر، الأزرار).
 */
internal object GlassWidgetBuilders {

    // PendingIntent request codes for the glass family's routes.
    private const val REQ_HERO_CONTINUE: Int = 71
    private const val REQ_CONTINUE: Int = 73
    private const val REQ_TIP_EMPTY: Int = 74
    private const val REQ_TIP: Int = 75
    private const val REQ_FOCUS_START: Int = 76
    // Ring/dot geometry (dp) and shared bases from the mockups.
    private const val RING_HERO_DP: Int = 64
    private const val RING_GOAL_DP: Int = 56
    private const val DOTS_HEIGHT_DP: Int = 18
    private const val PERCENT_BASE: Int = 100
    private const val MINUTES_PER_HOUR: Int = 60

    // ── عائلة الزجاج v4 (1.2.51): البنّاؤون التسعة ─────────────

    /** لوحة ألوان النمط الزجاجي على عناصر التخطيط: نصوص النمط
     * الفاتح عنابية، والداكن/الشفاف عاجية/ذهبية — كما بالنماذج. */
    private fun applyPalette(views: RemoteViews, data: WidgetData, roles: PaletteRoles) {
        val palette = GlassPalette.forStyle(data.settings.glassStyle)
        roles.headings.forEach { views.setTextColor(it, palette.heading) }
        roles.primary.forEach { views.setTextColor(it, palette.primary) }
        roles.secondary.forEach { views.setTextColor(it, palette.secondary) }
        roles.accent.forEach { views.setTextColor(it, palette.timeAccent) }
    }


    /** تصميم 1: العدّاد التنازلي 4×2. */
    internal fun buildCountdownWideViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_countdown)
        Widgets.bindBackground(views, context, data, sizeDp)
        Widgets.bindChronometer(views, data)
        applyPalette(
            views, data, PaletteRoles(
            headings = listOf(R.id.widget_glass_title),
            primary = listOf(R.id.widget_days, R.id.widget_expired),
            secondary = listOf(R.id.widget_days_caption, R.id.widget_chip_caption),
            accent = listOf(R.id.widget_chrono)
        ))
        return views
    }

    /** تصميم 9: عدّاد مصغّر 2×1. */
    internal fun buildMiniViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_mini)
        Widgets.bindBackground(views, context, data, sizeDp)
        Widgets.bindChronometer(views, data)
        applyPalette(
            views, data, PaletteRoles(
            headings = emptyList(),
            primary = listOf(R.id.widget_days, R.id.widget_expired),
            secondary = listOf(R.id.widget_days_caption, R.id.widget_chip_caption),
            accent = listOf(R.id.widget_chrono)
        ))
        return views
    }

    /** تصميم 2: الجاهزية البطل 4×4 — عدّاد + حلقة الهدف + السلسلة
     * + شريط «تابع من حيث توقفت» الذهبي. */
    internal fun buildHeroViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_hero)
        Widgets.bindBackground(views, context, data, sizeDp)
        Widgets.bindChronometer(views, data)
        val target = data.questionTarget ?: 0
        val percent = if (target > 0) {
            (data.doneToday * PERCENT_BASE / target).coerceIn(0, PERCENT_BASE)
        } else {
            0
        }
        val ringPx = (RING_HERO_DP * densityOf(context)).toInt()
        views.setImageViewBitmap(
            R.id.widget_ring,
            WidgetGlass.ringBitmap(ringPx, percent, data.settings.glassStyle == WidgetGlassStyle.LIGHT)
        )
        views.setTextViewText(R.id.widget_ring_text, "${data.doneToday}")
        views.setTextViewText(
            R.id.widget_goal_text,
            if (target > 0) "من $target سؤالًا" else "لم يُضبط هدف بعد"
        )
        views.setTextViewText(R.id.widget_streak_number, "${data.streakDays}")
        views.setTextViewText(
            R.id.widget_continue_text,
            if (data.lastSubjectName != null) {
                "تابع من حيث توقفت — ${data.lastSubjectName}"
            } else {
                "تابع من حيث توقفت"
            }
        )
        views.setOnClickPendingIntent(
            R.id.widget_continue_strip,
            routePendingIntent(context, WidgetRouter.ROUTE_CONTINUE, REQ_HERO_CONTINUE)
        )
        applyPalette(
            views, data, PaletteRoles(
            headings = listOf(R.id.widget_glass_title),
            primary = listOf(
                R.id.widget_days, R.id.widget_expired, R.id.widget_ring_text, R.id.widget_goal_caption
            ),
            secondary = listOf(R.id.widget_days_caption, R.id.widget_goal_text, R.id.widget_streak_caption),
            accent = listOf(R.id.widget_chrono, R.id.widget_streak_number)
        ))
        return views
    }

    /** تصميم 3: هدف اليوم 2×2 — حلقة + «من X سؤالًا» + النص الحر. */
    internal fun buildGoalViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_goal)
        Widgets.bindBackground(views, context, data, sizeDp)
        val target = data.questionTarget ?: 0
        val percent = if (target > 0) {
            (data.doneToday * PERCENT_BASE / target).coerceIn(0, PERCENT_BASE)
        } else {
            0
        }
        val ringPx = (RING_GOAL_DP * densityOf(context)).toInt()
        views.setImageViewBitmap(
            R.id.widget_ring,
            WidgetGlass.ringBitmap(ringPx, percent, data.settings.glassStyle == WidgetGlassStyle.LIGHT)
        )
        views.setTextViewText(R.id.widget_ring_text, "${data.doneToday}")
        views.setTextViewText(
            R.id.widget_goal_text,
            if (target > 0) "من $target سؤالًا" else "اضبط هدفك من التطبيق"
        )
        val free = data.items.firstOrNull { it.text.isNotBlank() }?.text
        if (free != null && data.settings.showGoals) {
            views.setTextViewText(R.id.widget_goal_freetext, free)
            views.setViewVisibility(R.id.widget_goal_freetext, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_goal_freetext, View.GONE)
        }
        applyPalette(
            views, data, PaletteRoles(
            headings = listOf(R.id.widget_glass_title),
            primary = listOf(R.id.widget_goal_text, R.id.widget_ring_text),
            secondary = listOf(R.id.widget_goal_freetext),
            accent = emptyList()
        ))
        return views
    }

    /** تصميم 4: سلسلة الأيام 2×2 — الرقم + نقاط الأسبوع. */
    internal fun buildStreakViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_streak)
        Widgets.bindBackground(views, context, data, sizeDp)
        views.setTextViewText(R.id.widget_streak_number, "${data.streakDays}")
        val density = densityOf(context)
        views.setImageViewBitmap(
            R.id.widget_dots,
            WidgetGlass.dotsBitmap(
                (sizeDp.first * density).toInt().coerceAtLeast(1),
                (DOTS_HEIGHT_DP * density).toInt(),
                data.weekDots,
                data.settings.glassStyle == WidgetGlassStyle.LIGHT
            )
        )
        applyPalette(
            views, data, PaletteRoles(
            headings = listOf(R.id.widget_glass_title),
            primary = listOf(R.id.widget_streak_number),
            secondary = listOf(R.id.widget_streak_caption),
            accent = emptyList()
        ))
        return views
    }

    /** تصميم 5: تابع من حيث توقفت 4×1 — آخر مادة + حبة «تابع». */
    internal fun buildContinueViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_continue)
        Widgets.bindBackground(views, context, data, sizeDp)
        views.setTextViewText(
            R.id.widget_continue_subject,
            data.lastSubjectName ?: "ابدأ أول مادة من التطبيق"
        )
        val intent = routePendingIntent(context, WidgetRouter.ROUTE_CONTINUE, REQ_CONTINUE)
        views.setOnClickPendingIntent(R.id.widget_root, intent)
        views.setOnClickPendingIntent(R.id.widget_continue_pill, intent)
        applyPalette(
            views, data, PaletteRoles(
            headings = emptyList(),
            primary = listOf(R.id.widget_continue_title),
            secondary = listOf(R.id.widget_continue_subject),
            accent = emptyList()
        ))
        return views
    }

    /** تصميم 6: زوادة اليوم 4×2 — نصيحة ثابتة اليوم من الحزم
     * المنزّلة؛ غيابها = حالة فارغة أنيقة بلا خطأ. */
    internal fun buildTipViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_tip)
        Widgets.bindBackground(views, context, data, sizeDp)
        val tip = data.tipOfDay
        if (tip == null) {
            views.setViewVisibility(R.id.widget_tip_text, View.GONE)
            views.setViewVisibility(R.id.widget_tip_author, View.GONE)
            views.setViewVisibility(R.id.widget_tip_empty, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                routePendingIntent(context, WidgetRouter.ROUTE_TIPS, REQ_TIP_EMPTY)
            )
        } else {
            views.setViewVisibility(R.id.widget_tip_empty, View.GONE)
            views.setViewVisibility(R.id.widget_tip_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_tip_text, tip.text)
            val line = WidgetTipMath.authorLine(tip)
            if (line != null) {
                views.setViewVisibility(R.id.widget_tip_author, View.VISIBLE)
                views.setTextViewText(R.id.widget_tip_author, line)
            } else {
                views.setViewVisibility(R.id.widget_tip_author, View.GONE)
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                routePendingIntent(context, WidgetRouter.tipsRoute(tip.subject), REQ_TIP)
            )
        }
        applyPalette(
            views, data, PaletteRoles(
            headings = listOf(R.id.widget_glass_title),
            primary = listOf(R.id.widget_tip_text),
            secondary = listOf(R.id.widget_tip_empty, R.id.widget_tip_author),
            accent = emptyList()
        ))
        return views
    }

    /** تصميم 7: أزرار سريعة 4×1 — نفس ربط الأزرار القائم. */
    internal fun buildActionsViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_actions)
        Widgets.bindBackground(views, context, data, sizeDp)
        Widgets.bindButtons(
            context, views, data, Widgets.KIND_ACTIONS,
            listOf(
                Triple(R.id.widget_btn_1, R.id.widget_btn_1_icon, R.id.widget_btn_1_text),
                Triple(R.id.widget_btn_2, R.id.widget_btn_2_icon, R.id.widget_btn_2_text),
                Triple(R.id.widget_btn_3, R.id.widget_btn_3_icon, R.id.widget_btn_3_text),
                Triple(R.id.widget_btn_4, R.id.widget_btn_4_icon, R.id.widget_btn_4_text)
            )
        )
        applyPalette(
            views, data, PaletteRoles(
            headings = emptyList(),
            primary = listOf(
                R.id.widget_btn_1_text, R.id.widget_btn_2_text,
                R.id.widget_btn_3_text, R.id.widget_btn_4_text
            ),
            secondary = emptyList(),
            accent = emptyList()
        ))
        return views
    }

    /** تصميم 8: تركيز اليوم 2×2 — الوقت الكبير + «ابدأ جلسة». */
    internal fun buildFocusViews(
        context: Context,
        data: WidgetData,
        sizeDp: Pair<Int, Int>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_focus)
        Widgets.bindBackground(views, context, data, sizeDp)
        val m = data.focusMinutes
        views.setTextViewText(
            R.id.widget_focus_big,
            "${m / MINUTES_PER_HOUR}:${(m % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
        )
        views.setOnClickPendingIntent(
            R.id.widget_focus_start,
            routePendingIntent(context, WidgetQuickAction.FOCUS.route, REQ_FOCUS_START)
        )
        applyPalette(
            views, data, PaletteRoles(
            headings = listOf(R.id.widget_glass_title),
            primary = listOf(R.id.widget_focus_big),
            secondary = emptyList(),
            accent = emptyList()
        ))
        return views
    }
}

/** PendingIntent عام لأي قيمة توجيه (زوادة/تابع/تركيز...) —
 * بنمط التوجيه القائم نفسه: ختم النونس الخاص بالتثبيت وفعل
 * مميز لكل طلب حتى لا تتصادم النوايا المعلّقة. */
private fun routePendingIntent(context: Context, route: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        this.action = "sy.medpro.watani.widget.GLASS_$requestCode"
        putExtra(WidgetRouter.EXTRA_ROUTE, route)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    WidgetRouter.stampRoute(intent, context)
    return PendingIntent.getActivity(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun densityOf(context: Context): Float =
    context.resources.displayMetrics.density
