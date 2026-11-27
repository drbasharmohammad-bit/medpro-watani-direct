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

/**
 * نمط الزجاج (بناء 1.2.51 — عائلة الويدجت الزجاجية v4 المعتمدة):
 * عنابي داكن (الافتراضي كما بالنماذج) · عاجي فاتح (نص عنابي) ·
 * شفاف (صبغة خفيفة على الخلفية مباشرة). النمط واحد لكل العائلة.
 */
enum class WidgetGlassStyle { DARK, LIGHT, CLEAR }

/** نمط الزجاج. */
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
    val countdownStyle: WidgetCountdownStyle = WidgetCountdownStyle.DAYS_AND_TIME,
    val quickActions: List<WidgetQuickAction> = WidgetQuickAction.entries,
    /** نمط الزجاج v4 (1.2.51) — الافتراضي عنابي داكن كما بالنماذج. */
    val glassStyle: WidgetGlassStyle = WidgetGlassStyle.DARK,
    /** قوّة صبغة الزجاج ٪ — بحدّ أدنى يحفظ المقروئية (انظر
     * [WidgetGlassMath.GLASS_ALPHA_MIN]). الافتراضي 65 كما بنموذج
     * شاشة الإعدادات المعتمد. */
    val glassAlphaPercent: Int = WidgetGlassMath.DEFAULT_GLASS_ALPHA,
    /** إظهار ويدجت «زوادة اليوم» ومحتواه (1.2.51). */
    val showTip: Boolean = true,
    /** حجم المعاينة المختار بشاشة الإعدادات (للمعاينة فقط — حجم
     * الويدجت الحقيقي يقرره الطالب عند التثبيت على الشاشة). */
    val previewSize: WidgetPreviewSize = WidgetPreviewSize.S4X2
)

/** أحجام معاينة شاشة الإعدادات (رقائق النموذج المعتمد). */
@Suppress("MagicNumber") // aspect ratios ARE the size tokens of the approved settings mockup
enum class WidgetPreviewSize(val label: String, val aspect: Float) {
    S2X1("2×1", 2.0f),
    S2X2("2×2", 1.0f),
    S4X1("4×1", 4.0f),
    S4X2("4×2", 2.0f),
    S4X4("4×4", 1.0f)
}

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
 * هندسة زاوية الويدجت — منطق نقي (إصلاح 1.2.27 لبلاغ الدكتور:
 * زاوية الصورة خارجة عن قوس الزاوية).
 *
 * مسار الزاوية واحد لكل الطبقات: نصف قطر [CORNER_RADIUS_DP] ‏(24dp)
 * — وهو الرقم نفسه بـdrawables ‏(widget_bg/widget_photo_frame).
 * منذ 1.2.27 تُقصّ الصورة والتراكب فعليًا بهذا المسار على مستوى
 * الـBitmap قبل الإرسال للمشغّل، فلا يبقى أي بكسل خارج القوس
 * مهما كان المشغّل. لا خط إطار حول الويدجت (حُذف بأمر الدكتور
 * 16 نوفمبر 18:32) — الصورة تنتهي عند القوس وحسب.
 */
object WidgetShapeMath {

    /** نصف قطر زاوية الويدجت بالـdp — يطابق drawables الزاوية. */
    const val CORNER_RADIUS_DP = 24

    /** نصف القطر بالبكسل لمقاس فعلي بالـdp عند كثافة الشاشة. */
    fun radiusPx(widthDp: Int, heightDp: Int, density: Float): Float {
        val shortest = minOf(widthDp, heightDp).coerceAtLeast(1)
        // A round-rect radius can never exceed half the shortest side.
        return minOf(CORNER_RADIUS_DP * density, shortest * density / 2f)
    }

    /** مقاس الـBitmap بالبكسل لمقاس ويدجت بالـdp (بسقف أمان للذاكرة
     * وحدّ الـBinder): تُحفَظ النسبة ويُقصّ الأطول عند الحاجة،
     * وبسقف إجمالي للبكسلات أيضًا (إصلاح زجاج 1.2.52): ويدجتات
     * كبيرة بكثافة عالية كانت تتجاوز ما تمرّره خدمة الويدجتات
     * ببعض المشغّلات فيسقط التحديث بصمت ويبقى الويدجت قديمًا
     * أو فارغًا — السقف يبقي كل وجه ضمن ما يُسلَّم فعلًا. */
    fun bitmapSizePx(widthDp: Int, heightDp: Int, density: Float): Pair<Int, Int> {
        var w = (widthDp.coerceAtLeast(1) * density).toInt().coerceAtLeast(1)
        var h = (heightDp.coerceAtLeast(1) * density).toInt().coerceAtLeast(1)
        if (w > MAX_BITMAP_SIDE_PX || h > MAX_BITMAP_SIDE_PX) {
            val sideScale = MAX_BITMAP_SIDE_PX.toFloat() / maxOf(w, h)
            w = (w * sideScale).toInt().coerceAtLeast(1)
            h = (h * sideScale).toInt().coerceAtLeast(1)
        }
        val pixels = w.toLong() * h.toLong()
        if (pixels > MAX_BITMAP_PIXELS) {
            val pixelScale = kotlin.math.sqrt(MAX_BITMAP_PIXELS.toDouble() / pixels.toDouble())
            w = (w * pixelScale).toInt().coerceAtLeast(1)
            h = (h * pixelScale).toInt().coerceAtLeast(1)
        }
        return w to h
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
    @Suppress("MagicNumber") // kind ids are the Widgets.KIND_* contract values
    fun fallbackSizeDp(kind: Int): Pair<Int, Int> = when (kind) {
        0 -> FallbackSizes.SMALL_SQUARE   // KIND_COUNTDOWN — widget_countdown_info.xml
        1 -> FallbackSizes.WIDE           // KIND_DAY — widget_day_info.xml
        2 -> FallbackSizes.LARGE          // KIND_LARGE — widget_day_large_info.xml
        3 -> FallbackSizes.WIDE           // KIND_COUNTDOWN_WIDE (design 1, 4×2)
        4 -> FallbackSizes.HERO           // KIND_HERO (design 2, 4×4)
        5 -> FallbackSizes.SMALL_SQUARE   // KIND_GOAL (design 3, 2×2)
        6 -> FallbackSizes.SMALL_SQUARE   // KIND_STREAK (design 4, 2×2)
        7 -> FallbackSizes.STRIP          // KIND_CONTINUE (design 5, 4×1)
        8 -> FallbackSizes.WIDE           // KIND_TIP (design 6, 4×2)
        9 -> FallbackSizes.STRIP          // KIND_ACTIONS (design 7, 4×1)
        10 -> FallbackSizes.SMALL_SQUARE  // KIND_FOCUS (design 8, 2×2)
        else -> FallbackSizes.MINI        // KIND_MINI (design 9, 2×1)
    }

    /** Default dp sizes per widget kind (mirror the provider
     * info XMLs) — the literals ARE the size tokens. */
    @Suppress("MagicNumber")
    private object FallbackSizes {
        val SMALL_SQUARE: Pair<Int, Int> = 110 to 110
        val WIDE: Pair<Int, Int> = 250 to 140
        val LARGE: Pair<Int, Int> = 250 to 180
        val HERO: Pair<Int, Int> = 250 to 260
        val STRIP: Pair<Int, Int> = 250 to 70
        val MINI: Pair<Int, Int> = 170 to 80
    }

    const val MAX_BITMAP_SIDE_PX = 1200

    /** سقف إجمالي لبكسلات أي وجه ويدجت مخبوز (انظر bitmapSizePx). */
    const val MAX_BITMAP_PIXELS = 1_200_000
}

// ---------------------------------------------------------------------
// Glass math (build 1.2.51, widget glass family v4) — pure.
// ---------------------------------------------------------------------

object WidgetGlassMath {

    /**
     * ⛔ الحدّ الأدنى الموثّق لقوّة صبغة الزجاج: 45٪.
     *
     * السبب (درس مراجعات Glassify المسجّل بتقرير التصميم:
     * «الشفافية الزائدة تقتل المقروئية على الخلفيات المزدحمة»):
     * تحت 45٪ تهبط صبغة العنابي/العاجي فوق أفتح صور المعالم
     * (سماء تدمر/بصرى نهارًا) بتباين النص العاجي/العنابي دون حدّ
     * القراءة المريحة على شاشة رئيسية تُقرأ بلمحة. المنزلق إذن
     * يعمل 45..100 فقط، وأي قيمة مخزّنة أدنى تُرفع للحدّ عند
     * القراءة — لا توجد حالة يصير فيها الويدجت غير مقروء.
     */
    const val GLASS_ALPHA_MIN: Int = 45
    const val GLASS_ALPHA_MAX: Int = 100
    const val DEFAULT_GLASS_ALPHA: Int = 65
    private const val PERCENT_BASE: Float = 100f
    /** كسر قوّة الصبغة بالنمط الشفاف.
     * إصلاح زجاج 1.2.52: كان 0.35 فتهبط الصبغة الفعلية عند حدّ
     * المنزلق الأدنى إلى ~0.16 — نص عاجي فوق سماء نهارية بلا
     * أي حماية تقريبًا (أحد أسباب حكم «الزجاج فاشل» على الجهاز).
     * رُفع الكسر وثُبّتت أرضية مطلقة أدناه فلا توجد حالة منزلق
     * يصير فيها النمط الشفاف شفافًا فعلًا. */
    private const val CLEAR_TINT_FRACTION: Float = 0.55f
    /** أرضية ألفا الصبغة المطلقة للنمط الشفاف (0..1). */
    private const val CLEAR_TINT_FLOOR: Float = 0.30f

    fun clampGlassAlpha(value: Int): Int =
        value.coerceIn(GLASS_ALPHA_MIN, GLASS_ALPHA_MAX)

    /** ألفا الصبغة الفعلية 0..1 من نسبة المنزلق المقصوصة. */
    fun tintAlpha(glassAlphaPercent: Int): Float =
        clampGlassAlpha(glassAlphaPercent) / PERCENT_BASE

    /** للنمط الشفاف تُخفَّف الصبغة لكسر من قوّتها — الزجاج على
     * الخلفية مباشرة كما بنموذج «شفاف» — لكن بأرضية مطلقة
     * (CLEAR_TINT_FLOOR) فوق طبقة التعتيم التحتية، فالمقروئية
     * محميّة عند كل قيمة منزلق. */
    fun effectiveTintAlpha(style: WidgetGlassStyle, glassAlphaPercent: Int): Float =
        when (style) {
            WidgetGlassStyle.CLEAR -> maxOf(
                tintAlpha(glassAlphaPercent) * CLEAR_TINT_FRACTION, CLEAR_TINT_FLOOR
            )
            else -> tintAlpha(glassAlphaPercent)
        }
}

/**
 * اشتقاق مقاس الويدجت الفعلي بالـdp من خيارات AppWidgetOptions
 * — منطق نقي (إصلاح زجاج 1.2.52).
 *
 * العطل المُثبت بـ1.2.51: القراءة كانت من OPTION_APPWIDGET_MIN_*
 * فقط، وهي عند أغلب المشغّلات **الحدّ الأدنى** لمقاس الويدجت
 * لا مقاسه الحالي؛ فالوجه الزجاجي (تغبيش + صبغة + حافة + قصّ
 * زوايا) كان يُخبز بمقاس أصغر من الخلية الفعلية ثم يمدّده
 * المشغّل — ضبابية وقصّ خاطئ وحواف مشوّهة.
 *
 * دلالة الخيارات الموثّقة من أندرويد: بالوضع العمودي العرض
 * الحالي هو MIN_WIDTH والارتفاع الحالي هو MAX_HEIGHT، وبالأفقي
 * ينعكس الدوران (MAX_WIDTH × MIN_HEIGHT). مشغّلات لا تملأ
 * النطاق (MIN == MAX) لا تتأثر بالقاعدة.
 */
/** نطاق المقاس الذي يبلّغه المشغّل لخلية الويدجت (خيارات
 * AppWidgetManager الأربعة) — يُمرَّر كوحدة واحدة حتى لا تتضخم
 * قائمة وسائط اشتقاق المقاس مع كل بُعد جديد. */
data class WidgetSizeBounds(
    val minWidth: Int,
    val minHeight: Int,
    val maxWidth: Int,
    val maxHeight: Int
)

object WidgetSizeMath {

    /** المقاس الذي يُخبز عليه وجه الويدجت: من النطاق المُبلَّغ
     * حسب الاتجاه، وسقوطًا للمقاس الافتراضي عند غياب أي بُعد. */
    fun deriveSizeDp(
        bounds: WidgetSizeBounds,
        isPortrait: Boolean,
        fallback: Pair<Int, Int>
    ): Pair<Int, Int> {
        val width = if (isPortrait) {
            firstPositive(bounds.minWidth, fallback.first)
        } else {
            firstPositive(bounds.maxWidth, bounds.minWidth, fallback.first)
        }
        val height = if (isPortrait) {
            firstPositive(bounds.maxHeight, bounds.minHeight, fallback.second)
        } else {
            firstPositive(bounds.minHeight, fallback.second)
        }
        return width to height
    }

    private fun firstPositive(vararg values: Int): Int =
        values.firstOrNull { it > 0 } ?: 1
}

/** سلسلة الأيام ونقاط الأسبوع — منطق نقي من خريطة النشاط اليومي
 * (مفاتيح yyyy-MM-dd كما يخزّنها OfflineStudyVault). */
object WidgetStreakMath {

    /** فهرس أقدم يوم بنافذة الأسبوع (اليوم + 6 أيام سابقة). */
    private const val WEEK_LAST_INDEX: Int = 6

    /** أيام متتالية منتهية اليوم (أو أمس إن لم يبدأ اليوم بعد —
     * سلسلة الأمس لا تنكسر قبل أن يدرس الطالب اليوم). */
    fun streakDays(activity: Map<String, Int>, today: java.time.LocalDate): Int {
        var cursor = if ((activity[today.toString()] ?: 0) > 0) today else today.minusDays(1)
        var streak = 0
        while ((activity[cursor.toString()] ?: 0) > 0) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** نشاط آخر 7 أيام منتهية اليوم، من الأقدم للأحدث (نقاط
     * الأسبوع السبع بالنماذج). */
    fun weekDots(activity: Map<String, Int>, today: java.time.LocalDate): List<Boolean> =
        (WEEK_LAST_INDEX downTo 0).map { back ->
            (activity[today.minusDays(back.toLong()).toString()] ?: 0) > 0
        }
}

/**
 * آخر موضع دراسة («تابع من حيث توقفت» للويدجت): يُحفظ عند فتح
 * أي مادة من البنك (المسار والاسم)، ويقرؤه الويدجت ليعرض الموضع
 * ويفتحه بلمسته. جهازي كإعدادات الويدجت.
 */
object LastStudyStore {

    private const val PREFS = "watani_widget_last_study"
    private const val KEY_PATH = "path"
    private const val KEY_NAME = "name"

    fun save(context: Context, path: String, subjectName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PATH, path)
            .putString(KEY_NAME, subjectName)
            .apply()
    }

    /** (المسار، اسم المادة) أو null إن لم يدرس الطالب بعد. */
    fun load(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PATH, null)?.takeIf { it.isNotBlank() }
        val name = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
        return if (path != null && name != null) path to name else null
    }
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
    private const val KEY_STYLE = "countdown_style"
    private const val KEY_ACTIONS = "quick_actions"
    private const val KEY_GLASS_STYLE = "glass_style"
    private const val KEY_GLASS_ALPHA = "glass_alpha_percent"
    private const val KEY_SHOW_TIP = "show_tip"
    private const val KEY_PREVIEW_SIZE = "preview_size"

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
            countdownStyle = runCatching {
                WidgetCountdownStyle.valueOf(prefs.getString(KEY_STYLE, defaults.countdownStyle.name)!!)
            }.getOrDefault(defaults.countdownStyle),
            quickActions = WidgetSettingsMath.decodeQuickActions(
                prefs.getString(KEY_ACTIONS, null)
            ),
            glassStyle = runCatching {
                WidgetGlassStyle.valueOf(prefs.getString(KEY_GLASS_STYLE, defaults.glassStyle.name)!!)
            }.getOrDefault(defaults.glassStyle),
            glassAlphaPercent = WidgetGlassMath.clampGlassAlpha(
                prefs.getInt(KEY_GLASS_ALPHA, defaults.glassAlphaPercent)
            ),
            showTip = prefs.getBoolean(KEY_SHOW_TIP, defaults.showTip),
            previewSize = runCatching {
                WidgetPreviewSize.valueOf(prefs.getString(KEY_PREVIEW_SIZE, defaults.previewSize.name)!!)
            }.getOrDefault(defaults.previewSize)
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
            .putString(KEY_STYLE, settings.countdownStyle.name)
            .putString(KEY_ACTIONS, WidgetSettingsMath.encodeQuickActions(settings.quickActions))
            .putString(KEY_GLASS_STYLE, settings.glassStyle.name)
            .putInt(KEY_GLASS_ALPHA, WidgetGlassMath.clampGlassAlpha(settings.glassAlphaPercent))
            .putBoolean(KEY_SHOW_TIP, settings.showTip)
            .putString(KEY_PREVIEW_SIZE, settings.previewSize.name)
            .apply()
    }
}
