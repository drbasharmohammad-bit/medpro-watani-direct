package sy.medpro.watani.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import sy.medpro.watani.data.WidgetGlassMath
import sy.medpro.watani.data.WidgetGlassStyle
import sy.medpro.watani.data.WidgetSettingsMath
import sy.medpro.watani.data.WidgetShapeMath

/**
 * الثوب الزجاجي v4 (بناء 1.2.51 — النماذج المعتمدة حرفيًا من
 * `widget-glass-v4`): تغبيش لما خلف الويدجت + صبغة + إضاءة حافة.
 *
 * وصفة النموذج (build_mockups.py ‏glass()): خلفية مُغبّشة
 * (GaussianBlur 26) ← صبغة (عنابي #5E1224 بألفا 118/255 للنمط
 * الداكن · عاجي #FAF6F0 بألفا 128/255 للفاتح · عنابي داكن
 * #2E0812 بألفا 52/255 للشفاف) ← لمعة علوية بيضاء خفيفة ← حافة
 * مضيئة (255,238,205) وخط إضاءة أعلى.
 *
 * التكييف مع أندرويد (موثّق بصراحة): الـ«خلف» الذي يغبّشه
 * الويدجت هو طبقة خلفيته نفسها — صورة المعلم التي يضعها
 * الويدجت (أو التدرّج العنابي السادة) — لأن RemoteViews لا تصل
 * لخلفية المشغّل نفسها. التغبيش هنا حقيقي على تلك الطبقة
 * (تصغير/تكبير تراكمي = blur فعلي بالبكسلات)، والصبغة بقوّة
 * منزلق الطالب لكن بحدّها الأدنى الموثّق (45٪) من
 * [WidgetGlassMath]، وفوقها تبقى طبقة التعتيم المعتمدة لحماية
 * التباين. ألوان النصوص بكل نمط من [GlassPalette].
 */

/** ألوان نصوص/عناصر كل نمط زجاجي (ARGB) — من النماذج حرفيًا. */
data class GlassPalette(
    val heading: Int,
    val primary: Int,
    val secondary: Int,
    val soft: Int,
    val timeAccent: Int,
    val onGold: Int
) {
    companion object {
        fun forStyle(style: WidgetGlassStyle): GlassPalette = when (style) {
            WidgetGlassStyle.LIGHT -> GlassPalette(
                heading = 0xFF5E1224.toInt(),   // عناوين عنابية على العاجي
                primary = 0xFF5E1224.toInt(),   // الأرقام/النص الأساسي عنابي
                secondary = 0xFF8A6D2F.toInt(), // ذهبي داكن مقروء على الفاتح
                soft = 0xFF97755F.toInt(),
                timeAccent = 0xFF5E1224.toInt(),
                onGold = 0xFF5E1224.toInt()
            )
            else -> GlassPalette( // DARK + CLEAR: نص عاجي/ذهبي
                heading = 0xFFC2A059.toInt(),   // GOLD — عناوين النماذج
                primary = 0xFFFAF6F0.toInt(),   // IVORY — الأرقام الكبيرة
                secondary = 0xFFDED2C0.toInt(), // IVORY_DIM
                soft = 0xFFC6BAA8.toInt(),      // IVORY_SOFT
                timeAccent = 0xFFE8CD96.toInt(), // GOLD_LT — الكرونومتر
                onGold = 0xFF2E0812.toInt()
            )
        }
    }
}

/** ثوابت وصفة الزجاج الرقمية — كل رقم بالوصفة له اسم هنا. */
@Suppress("MagicNumber") // the ARGB literals ARE the glass design tokens (repo pattern: LibraryDesign)
private object GlassRecipe {
    // Glass tint channel colours (mockup recipe).
    const val BURG_R = 0x5E; const val BURG_G = 0x12; const val BURG_B = 0x24
    const val DARK_R = 0x2E; const val DARK_G = 0x08; const val DARK_B = 0x12
    const val IVORY_R = 0xFA; const val IVORY_G = 0xF6; const val IVORY_B = 0xF0
    // The approved dim grade colour (#22050B) and its strength
    // under the glass (the pre-v4 overlay runs at full strength).
    const val DIM_R = 0x22; const val DIM_G = 0x05; const val DIM_B = 0x0B
    const val DIM_STRENGTH = 0.75f
    const val ALPHA_MAX = 255
    // Blur: render the background at 1/8 size and scale back up.
    const val BLUR_DOWNSAMPLE = 8
    const val MIN_BLUR_BUFFER = 8
    // Top sheen: white alpha 34 → 0 over the upper half.
    const val SHEEN_MAX_ALPHA = 34
    const val SHEEN_HEIGHT_FRACTION = 0.5f
    const val GRADIENT_MIDPOINT = 0.5f
    // Edge light (ARGB): warm hairline + top highlight per style.
    val EDGE_ON_LIGHT: Int = 0x96FFFFFF.toInt()
    val EDGE_ON_DARK: Int = 0x73FFEECC.toInt()
    val HIGHLIGHT_ON_LIGHT: Int = 0x5AFFFFFF.toInt()
    val HIGHLIGHT_ON_CLEAR: Int = 0x46FFFFFF.toInt()
    val HIGHLIGHT_ON_DARK: Int = 0x34FFFFFF.toInt()
    const val EDGE_STROKE_FRACTION = 0.09f
    const val EDGE_STROKE_MIN = 2f
    const val EDGE_STROKE_MAX = 6f
    const val HIGHLIGHT_INSET_FACTOR = 2f
    // Goal ring + week dots geometry.
    const val FULL_CIRCLE_DEGREES = 360f
    const val ARC_START_DEGREES = -90f
    const val RING_STROKE_FRACTION = 0.11f
    const val PERCENT_BASE = 100
    val TRACK_ON_LIGHT: Int = 0x3C5E1224.toInt()
    val TRACK_ON_DARK: Int = 0x2EFFFFFF.toInt()
    val GOLD: Int = 0xFFC2A059.toInt()
    val DOT_OFF_LIGHT: Int = 0x465E1224.toInt()
    val DOT_OFF_DARK: Int = 0x3CFFFFFF.toInt()
    const val DOT_RADIUS_FRACTION = 0.32f
    const val DOT_CENTER_OFFSET = 0.5f
    const val WHITE_RGB: Int = 0x00FFFFFF
    /** موضع ارتفاع مستطيل القصّ بمصفوفة centerCropRect ‏[x, y, w, h]. */
    const val CROP_HEIGHT_INDEX: Int = 3
    /** إزاحة قناة الألفا داخل لون ARGB. */
    const val ALPHA_SHIFT: Int = 24
}

internal object WidgetGlass {

    private data class Key(
        val landmark: Int, val style: WidgetGlassStyle, val alpha: Int,
        val dim: Int, val widthPx: Int, val heightPx: Int
    )

    private var cachedKey: Key? = null
    private var cached: Bitmap? = null

    /**
     * The composed glass background at the widget's real size:
     * (photo | plain gradient) → blur → dim grade → glass tint →
     * top sheen → bottom grade → edge light, all clipped to the
     * one corner path. Any failure returns null and the caller
     * falls back to the pre-v4 photo/overlay path.
     */
    @Synchronized
    fun background(
        context: Context,
        data: WidgetData,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ): Bitmap? {
        val settings = data.settings
        val key = Key(
            data.landmarkIndex ?: -1, settings.glassStyle,
            WidgetGlassMath.clampGlassAlpha(settings.glassAlphaPercent),
            settings.dimPercent, widthPx, heightPx
        )
        cached?.let { if (cachedKey == key) return it }
        val result = try {
            compose(context, data, widthPx, heightPx, radiusPx)
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

    /** Orchestrates the recipe steps over one clipped canvas. */
    private fun compose(
        context: Context,
        data: WidgetData,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ): Bitmap? {
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val clip = Path().apply {
            addRoundRect(
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
                radiusPx, radiusPx, Path.Direction.CW
            )
        }
        canvas.clipPath(clip)
        val baseDrawn = if (data.landmarkIndex != null) {
            drawPhotoBase(context, data, canvas, widthPx, heightPx)
        } else {
            drawGradientBase(canvas, widthPx, heightPx)
            true
        }
        if (!baseDrawn) return null
        drawTint(canvas, data)
        drawSheen(canvas, widthPx, heightPx)
        drawEdge(canvas, data, widthPx, heightPx, radiusPx)
        return out
    }

    /** Photo base: center-cropped landmark, blurred by rendering
     * into a small buffer and scaling back up (a real pixel blur
     * of the background layer, standing in for the mockup's
     * GaussianBlur 26), then the approved dim grade at reduced
     * strength under the glass. False = photo undecodable. */
    private fun drawPhotoBase(
        context: Context,
        data: WidgetData,
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int
    ): Boolean {
        val index = data.landmarkIndex
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val source = index?.let {
            sy.medpro.watani.ui.ImageDecode.decodeResourceDownsampled(
                context.resources, LandmarkLibrary.all[it].drawableRes, widthPx, heightPx
            )
        }
        if (index == null || source == null) return false
        val crop = WidgetShapeMath.centerCropRect(
            source.width, source.height, widthPx.toFloat() / heightPx.toFloat()
        )
        val srcRect = android.graphics.Rect(
            crop[0].toInt(), crop[1].toInt(),
            (crop[0] + crop[2]).toInt(), (crop[1] + crop[GlassRecipe.CROP_HEIGHT_INDEX]).toInt()
        )
        val smallW = (widthPx / GlassRecipe.BLUR_DOWNSAMPLE).coerceAtLeast(GlassRecipe.MIN_BLUR_BUFFER)
        val smallH = (heightPx / GlassRecipe.BLUR_DOWNSAMPLE).coerceAtLeast(GlassRecipe.MIN_BLUR_BUFFER)
        val small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(
            source, srcRect, RectF(0f, 0f, smallW.toFloat(), smallH.toFloat()), paint
        )
        canvas.drawBitmap(
            small, android.graphics.Rect(0, 0, smallW, smallH),
            RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), paint
        )
        drawDimGrade(canvas, data, widthPx, heightPx)
        return true
    }

    /** The approved dim grade (legibility), at 75% strength —
     * the glass tint above it carries the rest of the recipe. */
    private fun drawDimGrade(canvas: Canvas, data: WidgetData, widthPx: Int, heightPx: Int) {
        val (top, mid, bottom) = WidgetSettingsMath.overlayStops(data.settings.dimPercent)
        val dimPaint = Paint()
        for (y in 0 until heightPx) {
            val t = y / (heightPx - 1).coerceAtLeast(1).toFloat()
            val a = if (t < GlassRecipe.GRADIENT_MIDPOINT) {
                top + (mid - top) * (t * 2f)
            } else {
                mid + (bottom - mid) * ((t - GlassRecipe.GRADIENT_MIDPOINT) * 2f)
            }
            dimPaint.color = Color.argb(
                (a * GlassRecipe.DIM_STRENGTH * GlassRecipe.ALPHA_MAX).toInt(),
                GlassRecipe.DIM_R, GlassRecipe.DIM_G, GlassRecipe.DIM_B
            )
            canvas.drawRect(0f, y.toFloat(), widthPx.toFloat(), y + 1f, dimPaint)
        }
    }

    /** Plain mode base: a burgundy vertical gradient (the glass
     * still applies above it — tint, sheen and edge). */
    private fun drawGradientBase(canvas: Canvas, widthPx: Int, heightPx: Int) {
        val basePaint = Paint()
        for (y in 0 until heightPx) {
            val t = y / (heightPx - 1).coerceAtLeast(1).toFloat()
            basePaint.color = Color.rgb(
                (GlassRecipe.DARK_R + (GlassRecipe.BURG_R - GlassRecipe.DARK_R) * t).toInt(),
                (GlassRecipe.DARK_G + (GlassRecipe.BURG_G - GlassRecipe.DARK_G) * t).toInt(),
                (GlassRecipe.DARK_B + (GlassRecipe.BURG_B - GlassRecipe.DARK_B) * t).toInt()
            )
            canvas.drawRect(0f, y.toFloat(), widthPx.toFloat(), y + 1f, basePaint)
        }
    }

    /** Glass tint: the style colour at the student's floored
     * slider alpha. */
    private fun drawTint(canvas: Canvas, data: WidgetData) {
        val settings = data.settings
        val tintAlpha = WidgetGlassMath.effectiveTintAlpha(
            settings.glassStyle, settings.glassAlphaPercent
        )
        val (tr, tg, tb) = when (settings.glassStyle) {
            WidgetGlassStyle.DARK -> Triple(GlassRecipe.BURG_R, GlassRecipe.BURG_G, GlassRecipe.BURG_B)
            WidgetGlassStyle.LIGHT -> Triple(GlassRecipe.IVORY_R, GlassRecipe.IVORY_G, GlassRecipe.IVORY_B)
            WidgetGlassStyle.CLEAR -> Triple(GlassRecipe.DARK_R, GlassRecipe.DARK_G, GlassRecipe.DARK_B)
        }
        canvas.drawColor(Color.argb((tintAlpha * GlassRecipe.ALPHA_MAX).toInt(), tr, tg, tb))
    }

    /** Top sheen: white 34 → 0 over the upper half. */
    private fun drawSheen(canvas: Canvas, widthPx: Int, heightPx: Int) {
        val sheenPaint = Paint()
        val sheenEnd = (heightPx * GlassRecipe.SHEEN_HEIGHT_FRACTION).toInt().coerceAtLeast(1)
        for (y in 0 until sheenEnd) {
            val t = y / sheenEnd.toFloat()
            val alpha = ((1f - t) * GlassRecipe.SHEEN_MAX_ALPHA).toInt()
            sheenPaint.color = (alpha shl GlassRecipe.ALPHA_SHIFT) or GlassRecipe.WHITE_RGB
            canvas.drawRect(0f, y.toFloat(), widthPx.toFloat(), y + 1f, sheenPaint)
        }
    }

    /** Edge light: warm hairline + top highlight line. */
    private fun drawEdge(
        canvas: Canvas,
        data: WidgetData,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ) {
        val style = data.settings.glassStyle
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = (radiusPx * GlassRecipe.EDGE_STROKE_FRACTION)
                .coerceIn(GlassRecipe.EDGE_STROKE_MIN, GlassRecipe.EDGE_STROKE_MAX)
            color = if (style == WidgetGlassStyle.LIGHT) {
                GlassRecipe.EDGE_ON_LIGHT
            } else {
                GlassRecipe.EDGE_ON_DARK
            }
        }
        val inset = edgePaint.strokeWidth
        canvas.drawRoundRect(
            RectF(inset, inset, widthPx - inset, heightPx - inset),
            radiusPx, radiusPx, edgePaint
        )
        val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = edgePaint.strokeWidth
            color = when (style) {
                WidgetGlassStyle.LIGHT -> GlassRecipe.HIGHLIGHT_ON_LIGHT
                WidgetGlassStyle.CLEAR -> GlassRecipe.HIGHLIGHT_ON_CLEAR
                else -> GlassRecipe.HIGHLIGHT_ON_DARK
            }
        }
        val hiY = inset * GlassRecipe.HIGHLIGHT_INSET_FACTOR
        canvas.drawLine(radiusPx, hiY, widthPx - radiusPx, hiY, hiPaint)
    }

    /** The goal ring (mockups): gold arc over a faint track.
     * Drawn as a bitmap — RemoteViews has no canvas views. */
    fun ringBitmap(sizePx: Int, percent: Int, light: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = sizePx * GlassRecipe.RING_STROKE_FRACTION
        val rect = RectF(stroke / 2, stroke / 2, sizePx - stroke / 2, sizePx - stroke / 2)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = if (light) GlassRecipe.TRACK_ON_LIGHT else GlassRecipe.TRACK_ON_DARK
        }
        canvas.drawArc(rect, 0f, GlassRecipe.FULL_CIRCLE_DEGREES, false, track)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = GlassRecipe.GOLD
        }
        val sweep = GlassRecipe.FULL_CIRCLE_DEGREES *
            percent.coerceIn(0, GlassRecipe.PERCENT_BASE) / GlassRecipe.PERCENT_BASE
        if (sweep > 0f) {
            canvas.drawArc(rect, GlassRecipe.ARC_START_DEGREES, sweep, false, arc)
        }
        return bmp
    }

    /** The week's seven dots (mockups): gold = studied, faint =
     * not yet. One bitmap, oldest → newest left-to-right in LTR
     * drawing order; the widget layouts are RTL so the row reads
     * today-first from the right, matching the mockups. */
    fun dotsBitmap(widthPx: Int, heightPx: Int, dots: List<Boolean>, light: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val n = dots.size.coerceAtLeast(1)
        val r = heightPx * GlassRecipe.DOT_RADIUS_FRACTION
        val gap = widthPx.toFloat() / n
        dots.forEachIndexed { i, on ->
            paint.color = when {
                on -> GlassRecipe.GOLD
                light -> GlassRecipe.DOT_OFF_LIGHT
                else -> GlassRecipe.DOT_OFF_DARK
            }
            canvas.drawCircle(gap * (i + GlassRecipe.DOT_CENTER_OFFSET), heightPx / 2f, r, paint)
        }
        return bmp
    }
}
