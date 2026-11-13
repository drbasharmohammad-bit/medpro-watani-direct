package sy.medpro.watani.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import sy.medpro.watani.R
import sy.medpro.watani.data.SupportMessage
import sy.medpro.watani.data.SupportMessages
import java.io.File
import java.io.FileOutputStream

/**
 * Support-tip share card (build 1.2.11, the doctor's order — share the
 * home support message as a beautiful image). Same visual identity and
 * template geometry as the approved question share card
 * ([ShareCardRenderer], 1080x1350): warm ivory background with faint
 * gold corner decoration, full-width dark-maroon header band
 * "Med Pro — وطني" with the gold medical plus, the tip's category as a
 * gold pill, the message text in Thmanyah (Cairo fallback) laid out
 * ONLY with StaticLayout (real Arabic shaping + RTL), and the gold
 * gradient footer carrying "t.me/medprowatain". The text is vertically
 * centred in the body area; font steps down until it fits, and — as a
 * last resort — truncates with "…" rather than spilling over the
 * footer (same documented behaviour as the question card).
 */
object TipCardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private val IVORY = Color.rgb(0xFB, 0xF7, 0xF2)
    private val MAROON = Color.rgb(0x7A, 0x1F, 0x2B)
    private val MAROON_DARK = Color.rgb(0x52, 0x13, 0x1C)
    private val GOLD = Color.rgb(0xD4, 0xAF, 0x37)
    private val GOLD_DARK = Color.rgb(0xA6, 0x7C, 0x1E)
    private val TEXT_DARK = Color.rgb(0x2C, 0x15, 0x18)

    private const val PADDING = 64f
    private const val HEADER_H = 196f
    private const val FOOTER_H = 150f

    private fun loadTypeface(context: Context, resId: Int): Typeface? =
        try { ResourcesCompat.getFont(context, resId) } catch (_: Exception) { null }

    private fun fonts(context: Context): Pair<Typeface, Typeface> {
        val regular = loadTypeface(context, R.font.thmanyahsans_regular)
            ?: loadTypeface(context, R.font.cairo)
            ?: Typeface.SANS_SERIF
        val bold = loadTypeface(context, R.font.thmanyahsans_bold)
            ?: loadTypeface(context, R.font.cairo)
            ?: Typeface.SANS_SERIF
        return regular to bold
    }

    private fun textPaint(typeface: Typeface, size: Float, color: Int, bold: Boolean = false): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = if (bold) Typeface.create(typeface, Typeface.BOLD) else typeface
            textSize = size
            this.color = color
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int, lineSpacing: Float = 1.5f): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        }
        return builder.build()
    }

    fun render(context: Context, message: SupportMessage): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (regular, bold) = fonts(context)
        val contentWidth = WIDTH - (PADDING * 2).toInt()

        // ---- background + faint gold corner decoration ----
        canvas.drawColor(IVORY)
        drawDecoration(canvas)

        // ---- header band (identical to the question card) ----
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MAROON }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEADER_H, headerPaint)
        val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        val plusCx = 108f
        val plusCy = 84f
        canvas.drawRoundRect(RectF(plusCx - 13f, plusCy - 42f, plusCx + 13f, plusCy + 42f), 8f, 8f, plusPaint)
        canvas.drawRoundRect(RectF(plusCx - 42f, plusCy - 13f, plusCx + 42f, plusCy + 13f), 8f, 8f, plusPaint)
        val brandLayout = layout(
            "Med Pro — وطني",
            textPaint(bold, 56f, Color.WHITE, bold = true),
            (WIDTH - 190 - PADDING.toInt()),
            lineSpacing = 1.1f
        )
        canvas.save()
        canvas.translate(190f, 34f)
        brandLayout.draw(canvas)
        canvas.restore()
        // category pill: gold capsule, maroon text, under the brand
        val pillPaint = textPaint(bold, 34f, MAROON_DARK, bold = true)
        val pillText = SupportMessages.categoryTitle(message.categoryId).ifBlank { "رسائل دعم وطمأنينة" }
        val pillTextWidth = pillPaint.measureText(pillText)
        val pillW = pillTextWidth + 56f
        val pillTop = 122f
        val pillLeft = WIDTH - PADDING - pillW
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        canvas.drawRoundRect(RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + 52f), 26f, 26f, pillBg)
        val pillLayout = layout(pillText, pillPaint, pillW.toInt() - 16, lineSpacing = 1.0f)
        canvas.save()
        canvas.translate(pillLeft + 8f, pillTop + 7f)
        pillLayout.draw(canvas)
        canvas.restore()

        // ---- body: largest size step that fits, vertically centred ----
        val bodyTop = HEADER_H + 56f
        val bodyBottom = HEIGHT - FOOTER_H - 56f
        val available = (bodyBottom - bodyTop).toInt()
        val dividerH = 26f // gold divider above the text + its spacing
        val steps = listOf(52f, 46f, 40f, 34f)
        var text = message.text.trim()
        var chosen: StaticLayout? = null
        for (size in steps) {
            val l = layout(text, textPaint(bold, size, TEXT_DARK, bold = true), contentWidth)
            if (l.height + dividerH <= available) { chosen = l; break }
        }
        if (chosen == null) {
            val size = steps.last()
            var truncated = text
            var l = layout(truncated, textPaint(bold, size, TEXT_DARK, bold = true), contentWidth)
            while (l.height + dividerH > available && truncated.length > 24) {
                truncated = truncated.dropLast(16).trimEnd() + "…"
                l = layout(truncated, textPaint(bold, size, TEXT_DARK, bold = true), contentWidth)
            }
            chosen = l
        }
        val textLayout = checkNotNull(chosen)
        val blockH = dividerH + textLayout.height
        var y = bodyTop + (available - blockH) / 2f
        // gold divider: short rounded bar at the RTL start (right side)
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        canvas.drawRoundRect(
            RectF(WIDTH - PADDING - 120f, y, WIDTH - PADDING, y + 7f),
            3.5f, 3.5f, dividerPaint
        )
        y += dividerH
        canvas.save()
        canvas.translate(PADDING, y)
        textLayout.draw(canvas)
        canvas.restore()

        // ---- footer band: gold gradient (same as the question card) ----
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, HEIGHT - FOOTER_H, WIDTH.toFloat(), HEIGHT.toFloat(),
                GOLD, GOLD_DARK, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, HEIGHT - FOOTER_H, WIDTH.toFloat(), HEIGHT.toFloat(), footerPaint)
        val footerTitle = layout(
            "المزيد من رسائل الدعم والتحفيز بالتطبيق",
            textPaint(bold, 46f, MAROON_DARK, bold = true),
            contentWidth,
            lineSpacing = 1.05f
        )
        canvas.save()
        canvas.translate(PADDING, HEIGHT - FOOTER_H + 22f)
        footerTitle.draw(canvas)
        canvas.restore()
        val footerHandle = layout(
            "t.me/medprowatain",
            textPaint(regular, 33f, MAROON_DARK),
            contentWidth,
            lineSpacing = 1.0f
        )
        canvas.save()
        canvas.translate(PADDING, HEIGHT - FOOTER_H + 22f + footerTitle.height + 8f)
        footerHandle.draw(canvas)
        canvas.restore()

        return bitmap
    }

    /** Very faint gold geometry: identical to the question card. */
    private fun drawDecoration(canvas: Canvas) {
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD; alpha = 20; style = Paint.Style.STROKE; strokeWidth = 3f
        }
        for (r in listOf(90f, 150f, 210f)) {
            canvas.drawArc(RectF(-r, HEIGHT - FOOTER_H - r, r, HEIGHT - FOOTER_H + r), 270f, 90f, false, arcPaint)
            canvas.drawArc(RectF(WIDTH - r, -r, WIDTH + r, r), 90f, 90f, false, arcPaint)
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; alpha = 26 }
        for (row in 0 until 3) {
            for (col in 0 until 5) {
                canvas.drawCircle(56f + col * 34f, 250f + row * 34f, 5f, dotPaint)
                canvas.drawCircle(WIDTH - 56f - col * 34f, HEIGHT - FOOTER_H - 120f + row * 34f, 5f, dotPaint)
            }
        }
    }

    /**
     * Saves the tip card PNG into cacheDir/share_images and returns the
     * file. Same F-15 discipline as the question card: one FIXED file
     * name, overwritten on every share; legacy timestamped tip cards
     * are swept so the folder stays bounded.
     */
    fun saveToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "share_images")
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("tip_card_") && f.name != "tip_card.png") {
                try { f.delete() } catch (_: Exception) { }
            }
        }
        val file = File(dir, "tip_card.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }
}
