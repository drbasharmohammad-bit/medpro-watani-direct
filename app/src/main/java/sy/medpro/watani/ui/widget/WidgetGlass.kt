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

internal object WidgetGlass {

    // Glass tint colours from the approved mockup recipe.
    private const val BURG_R = 0x5E; private const val BURG_G = 0x12; private const val BURG_B = 0x24
    private const val DARK_R = 0x2E; private const val DARK_G = 0x08; private const val DARK_B = 0x12
    private const val IVORY_R = 0xFA; private const val IVORY_G = 0xF6; private const val IVORY_B = 0xF0

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

    private fun compose(
        context: Context,
        data: WidgetData,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ): Bitmap? {
        val settings = data.settings
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radiusPx, radiusPx, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        // ---- base layer: landmark photo or plain burgundy ----
        val index = data.landmarkIndex
        if (index != null) {
            val source = sy.medpro.watani.ui.ImageDecode.decodeResourceDownsampled(
                context.resources, LandmarkLibrary.all[index].drawableRes, widthPx, heightPx
            ) ?: return null
            val crop = WidgetShapeMath.centerCropRect(
                source.width, source.height, widthPx.toFloat() / heightPx.toFloat()
            )
            val srcRect = android.graphics.Rect(
                crop[0].toInt(), crop[1].toInt(),
                (crop[0] + crop[2]).toInt(), (crop[1] + crop[3]).toInt()
            )
            // Blur of what is behind (the mockup's GaussianBlur):
            // render the photo into a small buffer and scale it
            // back up — a real pixel blur of the background layer.
            val smallW = (widthPx / 8).coerceAtLeast(8)
            val smallH = (heightPx / 8).coerceAtLeast(8)
            val small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
            val smallCanvas = Canvas(small)
            smallCanvas.drawBitmap(
                source, srcRect,
                RectF(0f, 0f, smallW.toFloat(), smallH.toFloat()), paint
            )
            canvas.drawBitmap(
                small, android.graphics.Rect(0, 0, smallW, smallH),
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), paint
            )
            // Approved dim grade (legibility under the glass).
            val (top, mid, bottom) = WidgetSettingsMath.overlayStops(settings.dimPercent)
            val dimPaint = Paint()
            for (y in 0 until heightPx) {
                val t = y / (heightPx - 1).coerceAtLeast(1).toFloat()
                val a = if (t < 0.5f) top + (mid - top) * (t * 2f) else mid + (bottom - mid) * ((t - 0.5f) * 2f)
                dimPaint.color = Color.argb((a * 0.75f * 255).toInt(), 0x22, 0x05, 0x0B)
                canvas.drawRect(0f, y.toFloat(), widthPx.toFloat(), y + 1f, dimPaint)
            }
        } else {
            // Plain mode: a burgundy vertical gradient base (the
            // glass still applies — tint, sheen and edge).
            val basePaint = Paint()
            for (y in 0 until heightPx) {
                val t = y / (heightPx - 1).coerceAtLeast(1).toFloat()
                basePaint.color = Color.rgb(
                    (DARK_R + (BURG_R - DARK_R) * t).toInt(),
                    (DARK_G + (BURG_G - DARK_G) * t).toInt(),
                    (DARK_B + (BURG_B - DARK_B) * t).toInt()
                )
                canvas.drawRect(0f, y.toFloat(), widthPx.toFloat(), y + 1f, basePaint)
            }
        }

        // ---- glass tint (style × student alpha, floored) ----
        val tintAlpha = WidgetGlassMath.effectiveTintAlpha(settings.glassStyle, settings.glassAlphaPercent)
        val (tr, tg, tb) = when (settings.glassStyle) {
            WidgetGlassStyle.DARK -> Triple(BURG_R, BURG_G, BURG_B)
            WidgetGlassStyle.LIGHT -> Triple(IVORY_R, IVORY_G, IVORY_B)
            WidgetGlassStyle.CLEAR -> Triple(DARK_R, DARK_G, DARK_B)
        }
        canvas.drawColor(Color.argb((tintAlpha * 255).toInt(), tr, tg, tb))

        // ---- top sheen: white 34 → 0 over the upper half ----
        val sheenPaint = Paint()
        val sheenEnd = (heightPx * 0.5f).toInt().coerceAtLeast(1)
        for (y in 0 until sheenEnd) {
            val t = y / sheenEnd.toFloat()
            sheenPaint.color = Color.argb(((1f - t) * 34).toInt(), 255, 255, 255)
            canvas.drawRect(0f, y.toFloat(), widthPx.toFloat(), y + 1f, sheenPaint)
        }

        // ---- edge light: warm hairline + top highlight line ----
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (radiusPx * 0.09f).coerceIn(2f, 6f)
            color = when (settings.glassStyle) {
                WidgetGlassStyle.LIGHT -> Color.argb(150, 255, 255, 255)
                else -> Color.argb(115, 255, 238, 205)
            }
        }
        val inset = edgePaint.strokeWidth
        canvas.drawRoundRect(
            RectF(inset, inset, widthPx - inset, heightPx - inset),
            radiusPx, radiusPx, edgePaint
        )
        val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = edgePaint.strokeWidth
            color = when (settings.glassStyle) {
                WidgetGlassStyle.LIGHT -> Color.argb(90, 255, 255, 255)
                WidgetGlassStyle.CLEAR -> Color.argb(70, 255, 255, 255)
                else -> Color.argb(52, 255, 255, 255)
            }
        }
        canvas.drawLine(radiusPx, inset * 2, widthPx - radiusPx, inset * 2, hiPaint)
        return out
    }

    /** The goal ring (mockups): gold arc over a faint track.
     * Drawn as a bitmap — RemoteViews has no canvas views. */
    fun ringBitmap(sizePx: Int, percent: Int, light: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = sizePx * 0.11f
        val rect = RectF(stroke / 2, stroke / 2, sizePx - stroke / 2, sizePx - stroke / 2)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = if (light) Color.argb(60, 94, 18, 36) else Color.argb(46, 255, 255, 255)
        }
        canvas.drawArc(rect, 0f, 360f, false, track)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = 0xFFC2A059.toInt()
        }
        val sweep = 360f * percent.coerceIn(0, 100) / 100f
        if (sweep > 0f) canvas.drawArc(rect, -90f, sweep, false, arc)
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
        val r = heightPx * 0.32f
        val gap = widthPx.toFloat() / n
        dots.forEachIndexed { i, on ->
            paint.color = when {
                on -> 0xFFC2A059.toInt()
                light -> Color.argb(70, 94, 18, 36)
                else -> Color.argb(60, 255, 255, 255)
            }
            canvas.drawCircle(gap * (i + 0.5f), heightPx / 2f, r, paint)
        }
        return bmp
    }
}
