package sy.medpro.watani.data

import android.content.Context

/**
 * إعدادات ويدجت الشاشة الرئيسية v3 (بناء 1.2.25 — تصميم اعتمده
 * الدكتور من النماذج البصرية 16 نوفمبر 13:42).
 *
 * الطالب يخصّص ويدجته من شاشة «إعدادات الويدجت» داخل التطبيق:
 * نمط الخلفية (تدوير معالم سورية يوميًا / معلم ثابت يختاره /
 * عنابي سادة)، تعتيم الخلفية، الوحدات الظاهرة، نمط العدّاد،
 * والأزرار السريعة الظاهرة. كل تغيير يُحفظ فورًا ويتبعه بثّ
 * تحديث لعائلة الويدجتات (Widgets.updateAll) فينعكس مباشرة.
 *
 * الإعدادات جهازية (ليست لكل حساب): الويدجت يعيش على شاشة
 * الجهاز نفسه، فمخزن واحد بلا ربط حسابي.
 */

/** نمط خلفية الويدجت. */
enum class WidgetBgMode { ROTATE, FIXED, PLAIN }

/** نمط عرض العدّ التنازلي. */
enum class WidgetCountdownStyle {
    /** أيام كاملة + كرونومتر باقي اليوم (الافتراضي، كما بالنماذج). */
    DAYS_AND_TIME,

    /** أيام كاملة فقط بلا كرونومتر. */
    DAYS_ONLY,

    /** عدّاد رقمي واحد بإجمالي الوقت المتبقي (ساعات كلية). */
    FULL_DIGITAL
}

/**
 * زر سريع على الويدجت. [route] هي قيمة الـextra التي يحملها
 * PendingIntent الزر إلى MainActivity — وكل زر يفتح شاشته
 * المقصودة داخل التطبيق مباشرة (شرط الدكتور الصريح 13:42).
 */
enum class WidgetQuickAction(val route: String) {
    FOCUS("focus"),
    MISTAKES("mistakes"),
    QUESTIONS("questions"),
    LIBRARY("library");

    companion object {
        fun fromRoute(route: String?): WidgetQuickAction? =
            entries.firstOrNull { it.route == route }
    }
}

data class WidgetSettings(
    val bgMode: WidgetBgMode = WidgetBgMode.ROTATE,
    val fixedLandmarkIndex: Int = 0,
    /** تعتيم الخلفية 0..100 — الافتراضي 70 كما بنموذج الإعدادات. */
    val dimPercent: Int = WidgetSettingsMath.DEFAULT_DIM,
    val showCountdown: Boolean = true,
    val showQuestions: Boolean = true,
    val showGoals: Boolean = true,
    val showFocus: Boolean = true,
    val showButtons: Boolean = true,
    val showLandmarkName: Boolean = false,
    val countdownStyle: WidgetCountdownStyle = WidgetCountdownStyle.DAYS_AND_TIME,
    val quickActions: List<WidgetQuickAction> = WidgetQuickAction.entries
)

// ---------------------------------------------------------------------
// Pure logic (no Android imports — unit tested).
// ---------------------------------------------------------------------

object WidgetSettingsMath {

    const val DEFAULT_DIM: Int = 70
    const val MAX_QUICK_ACTIONS: Int = 4

    /** القيمة التي عندها يطابق التدرّج نموذج التصميم المعتمد حرفيًا. */
    private const val REFERENCE_DIM: Int = 70

    // نقاط ألفا التدرّج المعتمدة عند التعتيم المرجعي (DESIGN_NOTES).
    private const val STOP_TOP = 0.65f
    private const val STOP_MID = 0.55f
    private const val STOP_BOTTOM = 0.92f

    /** صبغة عنابية موحّدة فوق الصورة (13%) — ثابتة لا يحرّكها المنزلق. */
    const val TINT_ALPHA: Float = 0.13f

    fun clampDim(value: Int): Int = value.coerceIn(0, 100)

    fun clampLandmarkIndex(index: Int, count: Int): Int =
        if (count <= 0) 0 else index.coerceIn(0, count - 1)

    /**
     * ألفا نقاط التدرّج الثلاث (أعلى/وسط/أسفل) لمنزلق تعتيم معيّن:
     * عند القيمة المرجعية (70) تعود قيم النموذج المعتمد حرفيًا،
     * ودونها/فوقها تتدرّج خطيًا وتُقصّ عند 1.
     */
    fun overlayStops(dimPercent: Int): Triple<Float, Float, Float> {
        val scale = clampDim(dimPercent).toFloat() / REFERENCE_DIM.toFloat()
        fun stop(base: Float): Float = (base * scale).coerceIn(0f, 1f)
        return Triple(stop(STOP_TOP), stop(STOP_MID), stop(STOP_BOTTOM))
    }

    /**
     * تبديل زر سريع بقائمة الظاهرة: إزالة إن كان حاضرًا، وإضافة
     * بآخر القائمة إن غاب وبقي متّسع (السقف 4). قائمة بلا تكرار
     * أبدًا، والترتيب هو ترتيب عرض الأزرار على الويدجت.
     */
    fun toggleQuickAction(
        current: List<WidgetQuickAction>,
        action: WidgetQuickAction
    ): List<WidgetQuickAction> {
        val clean = current.distinct()
        return when {
            action in clean -> clean - action
            clean.size >= MAX_QUICK_ACTIONS -> clean
            else -> clean + action
        }
    }

    /** ترميز القائمة لخزن التفضيلات (أسماء الثوابت مفصولة بفواصل). */
    fun encodeQuickActions(actions: List<WidgetQuickAction>): String =
        actions.distinct().joinToString(",") { it.name }

    /** فكّ الترميز متسامح: الأسماء المجهولة تُسقط والترتيب يُحفظ. */
    fun decodeQuickActions(raw: String?): List<WidgetQuickAction> {
        if (raw.isNullOrBlank()) return WidgetQuickAction.entries
        val parsed = raw.split(",").mapNotNull { token ->
            WidgetQuickAction.entries.firstOrNull { it.name == token.trim() }
        }.distinct()
        return parsed
    }
}

/**
 * اختيار صورة الخلفية — منطق نقي.
 * التدوير حتمي بتاريخ اليوم: نفس الصورة على كل الأجهزة في اليوم
 * نفسه، وتتبدّل في اليوم التالي (تحديث منتصف الليل القائم يعيد
 * رسم الويدجتات).
 */
object WidgetBackgroundMath {

    /** فهرس صورة اليوم: epochDay modulo عدد المعالم (دائمًا ضمن النطاق). */
    fun indexForEpochDay(epochDay: Long, count: Int): Int =
        if (count <= 0) 0 else Math.floorMod(epochDay, count.toLong()).toInt()

    /**
     * الفهرس الفعلي المعروض: سادة ← null (لا صورة)، ثابت ← اختيار
     * الطالب مقصوصًا للنطاق، تدوير ← صورة اليوم.
     */
    fun pickIndex(settings: WidgetSettings, epochDay: Long, count: Int): Int? {
        if (count <= 0) return null
        return when (settings.bgMode) {
            WidgetBgMode.PLAIN -> null
            WidgetBgMode.FIXED -> WidgetSettingsMath.clampLandmarkIndex(settings.fixedLandmarkIndex, count)
            WidgetBgMode.ROTATE -> indexForEpochDay(epochDay, count)
        }
    }
}

/**
 * هندسة إطار الويدجت — منطق نقي (إصلاح 1.2.27 لبلاغ الدكتور:
 * زاوية الصورة خارجة عن قوس الإطار الذهبي).
 *
 * مسار الزاوية واحد لكل الطبقات: نصف قطر [CORNER_RADIUS_DP] ‏(24dp)
 * — وهو الرقم نفسه بـdrawables ‏(widget_bg/widget_frame/
 * widget_photo_frame). منذ 1.2.27 تُقصّ الصورة والتراكب فعليًا
 * بهذا المسار على مستوى الـBitmap قبل الإرسال للمشغّل، فلا يبقى
 * أي بكسل خارج القوس مهما كان المشغّل.
 */
object WidgetShapeMath {

    /** نصف قطر زاوية الويدجت بالـdp — يطابق drawables الإطار. */
    const val CORNER_RADIUS_DP = 24

    /** نصف القطر بالبكسل لمقاس فعلي بالـdp عند كثافة الشاشة. */
    fun radiusPx(widthDp: Int, heightDp: Int, density: Float): Float {
        val shortest = minOf(widthDp, heightDp).coerceAtLeast(1)
        // A round-rect radius can never exceed half the shortest side.
        return minOf(CORNER_RADIUS_DP * density, shortest * density / 2f)
    }

    /** مقاس الـBitmap بالبكسل لمقاس ويدجت بالـdp (بسقف أمان للذاكرة
     * وحدّ الـBinder): تُحفَظ النسبة ويُقصّ الأطول عند الحاجة. */
    fun bitmapSizePx(widthDp: Int, heightDp: Int, density: Float): Pair<Int, Int> {
        val w = (widthDp.coerceAtLeast(1) * density).toInt().coerceAtLeast(1)
        val h = (heightDp.coerceAtLeast(1) * density).toInt().coerceAtLeast(1)
        if (w <= MAX_BITMAP_SIDE_PX && h <= MAX_BITMAP_SIDE_PX) return w to h
        val scale = MAX_BITMAP_SIDE_PX.toFloat() / maxOf(w, h)
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    /** مستطيل القصّ المركزي (centerCrop): أكبر مستطيل بنسبة
     * [targetAspect] داخل مصدر [srcW]×[srcH]، متمركزًا — يُرجع
     * (left, top, width, height) بوحدات المصدر. */
    fun centerCropRect(srcW: Int, srcH: Int, targetAspect: Float): FloatArray {
        val safeSrcW = srcW.coerceAtLeast(1).toFloat()
        val safeSrcH = srcH.coerceAtLeast(1).toFloat()
        val srcAspect = safeSrcW / safeSrcH
        return if (srcAspect > targetAspect) {
            val w = safeSrcH * targetAspect
            floatArrayOf((safeSrcW - w) / 2f, 0f, w, safeSrcH)
        } else {
            val h = safeSrcW / targetAspect
            floatArrayOf(0f, (safeSrcH - h) / 2f, safeSrcW, h)
        }
    }

    /** المقاس الافتراضي بالـdp لكل حجم عند غياب AppWidgetOptions
     * (يطابق minWidth/minHeight بملفات تعريف الويدجتات). */
    fun fallbackSizeDp(kind: Int): Pair<Int, Int> = when (kind) {
        0 -> 110 to 110   // KIND_COUNTDOWN — widget_countdown_info.xml
        1 -> 250 to 140   // KIND_DAY — widget_day_info.xml
        else -> 250 to 180 // KIND_LARGE — widget_day_large_info.xml
    }

    const val MAX_BITMAP_SIDE_PX = 1200
}

// ---------------------------------------------------------------------
// Store — device-level SharedPreferences.
// ---------------------------------------------------------------------

object WidgetSettingsStore {

    private const val PREFS = "watani_widget_settings_v3"
    private const val KEY_BG_MODE = "bg_mode"
    private const val KEY_FIXED_INDEX = "fixed_landmark_index"
    private const val KEY_DIM = "dim_percent"
    private const val KEY_SHOW_COUNTDOWN = "show_countdown"
    private const val KEY_SHOW_QUESTIONS = "show_questions"
    private const val KEY_SHOW_GOALS = "show_goals"
    private const val KEY_SHOW_FOCUS = "show_focus"
    private const val KEY_SHOW_BUTTONS = "show_buttons"
    private const val KEY_SHOW_LANDMARK = "show_landmark_name"
    private const val KEY_STYLE = "countdown_style"
    private const val KEY_ACTIONS = "quick_actions"

    fun settings(context: Context): WidgetSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = WidgetSettings()
        return WidgetSettings(
            bgMode = runCatching {
                WidgetBgMode.valueOf(prefs.getString(KEY_BG_MODE, defaults.bgMode.name)!!)
            }.getOrDefault(defaults.bgMode),
            fixedLandmarkIndex = prefs.getInt(KEY_FIXED_INDEX, defaults.fixedLandmarkIndex),
            dimPercent = WidgetSettingsMath.clampDim(prefs.getInt(KEY_DIM, defaults.dimPercent)),
            showCountdown = prefs.getBoolean(KEY_SHOW_COUNTDOWN, defaults.showCountdown),
            showQuestions = prefs.getBoolean(KEY_SHOW_QUESTIONS, defaults.showQuestions),
            showGoals = prefs.getBoolean(KEY_SHOW_GOALS, defaults.showGoals),
            showFocus = prefs.getBoolean(KEY_SHOW_FOCUS, defaults.showFocus),
            showButtons = prefs.getBoolean(KEY_SHOW_BUTTONS, defaults.showButtons),
            showLandmarkName = prefs.getBoolean(KEY_SHOW_LANDMARK, defaults.showLandmarkName),
            countdownStyle = runCatching {
                WidgetCountdownStyle.valueOf(prefs.getString(KEY_STYLE, defaults.countdownStyle.name)!!)
            }.getOrDefault(defaults.countdownStyle),
            quickActions = WidgetSettingsMath.decodeQuickActions(
                prefs.getString(KEY_ACTIONS, null)
            )
        )
    }

    fun save(context: Context, settings: WidgetSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BG_MODE, settings.bgMode.name)
            .putInt(KEY_FIXED_INDEX, settings.fixedLandmarkIndex)
            .putInt(KEY_DIM, WidgetSettingsMath.clampDim(settings.dimPercent))
            .putBoolean(KEY_SHOW_COUNTDOWN, settings.showCountdown)
            .putBoolean(KEY_SHOW_QUESTIONS, settings.showQuestions)
            .putBoolean(KEY_SHOW_GOALS, settings.showGoals)
            .putBoolean(KEY_SHOW_FOCUS, settings.showFocus)
            .putBoolean(KEY_SHOW_BUTTONS, settings.showButtons)
            .putBoolean(KEY_SHOW_LANDMARK, settings.showLandmarkName)
            .putString(KEY_STYLE, settings.countdownStyle.name)
            .putString(KEY_ACTIONS, WidgetSettingsMath.encodeQuickActions(settings.quickActions))
            .apply()
    }
}
