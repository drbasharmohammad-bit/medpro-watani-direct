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
import sy.medpro.watani.data.QuestionDto
import sy.medpro.watani.data.ShareLinks
import java.io.File
import java.io.FileOutputStream

/**
 * Share-card renderer (spec template, 1080x1350):
 * - warm ivory background #FBF7F2 with very faint gold geometric
 *   decoration in the corners (arcs + dots, low alpha),
 * - full-width dark-maroon header band: "Med Pro — وطني" in bold white,
 *   a tasteful gold medical plus, and the subject as a gold pill with
 *   maroon text,
 * - question in dark text (Thmanyah Sans; Cairo fallback), ~44px at
 *   1.35 line spacing, laid out ONLY with StaticLayout (real Arabic
 *   shaping + RTL — strings are never reversed by hand),
 * - each option in a white rounded card with a light maroon border and
 *   a maroon circular badge carrying the Arabic label,
 * - NO hint of the correct answer anywhere,
 * - gold gradient footer band (~150px): "الجواب والشرح الكامل بالتطبيق"
 *   in bold maroon + "@medprowatain" smaller.
 *
 * Long questions: font sizes step down (question 44->36->30, options
 * 40->34->30) until everything fits; if it still overflows at the
 * smallest size, the question text is truncated with "…" (documented
 * behaviour) rather than letting content spill over the footer.
 */
object ShareCardRenderer {
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

    /** Thmanyah Sans first (regular + bold OTF in res/font); when the OTF
     * cannot be loaded on a device, fall back to the bundled Cairo. */
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

    private fun layout(text: String, paint: TextPaint, width: Int, lineSpacing: Float = 1.35f): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        }
        return builder.build()
    }

    fun render(context: Context, question: QuestionDto, subjectLabel: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (regular, bold) = fonts(context)
        val contentWidth = WIDTH - (PADDING * 2).toInt()

        // ---- background + faint gold corner decoration ----
        canvas.drawColor(IVORY)
        drawDecoration(canvas)

        // ---- header band ----
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MAROON }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEADER_H, headerPaint)
        // gold plus (medical cross), left side of the band
        val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        val plusCx = 108f
        val plusCy = 84f
        canvas.drawRoundRect(RectF(plusCx - 13f, plusCy - 42f, plusCx + 13f, plusCy + 42f), 8f, 8f, plusPaint)
        canvas.drawRoundRect(RectF(plusCx - 42f, plusCy - 13f, plusCx + 42f, plusCy + 13f), 8f, 8f, plusPaint)
        // brand (right-aligned, RTL start)
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
        // subject pill: gold capsule, maroon text, under the brand
        val pillPaint = textPaint(bold, 34f, MAROON_DARK, bold = true)
        val pillText = subjectLabel.ifBlank { "بنك الأسئلة" }
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

        // ---- body: pick the largest size step that fits ----
        val options = question.options.orEmpty()
        val labels = options.indices.map { ShareLinks.optionLabel(question, it) }
        val bodyTop = HEADER_H + 44f
        val bodyBottom = HEIGHT - FOOTER_H - 36f
        val available = (bodyBottom - bodyTop).toInt()

        val steps = listOf(44f to 40f, 36f to 34f, 30f to 30f)
        var questionText = question.text?.trim().orEmpty()
        var chosen: Triple<StaticLayout, List<StaticLayout>, Pair<Float, Float>>? = null
        for ((qSize, oSize) in steps) {
            val qLayout = layout(questionText, textPaint(regular, qSize, TEXT_DARK), contentWidth)
            val oLayouts = options.map { layout(it, textPaint(regular, oSize, TEXT_DARK), optionTextWidth(oSize)) }
            if (totalBodyHeight(qLayout, oLayouts) <= available) {
                chosen = Triple(qLayout, oLayouts, qSize to oSize)
                break
            }
        }
        if (chosen == null) {
            // Still overflowing at the smallest step: truncate the
            // question with "…" until it fits (documented behaviour).
            val (qSize, oSize) = steps.last()
            val oLayouts = options.map { layout(it, textPaint(regular, oSize, TEXT_DARK), optionTextWidth(oSize)) }
            var truncated = questionText
            var qLayout = layout(truncated, textPaint(regular, qSize, TEXT_DARK), contentWidth)
            while (totalBodyHeight(qLayout, oLayouts) > available && truncated.length > 24) {
                truncated = truncated.dropLast(16).trimEnd() + "…"
                qLayout = layout(truncated, textPaint(regular, qSize, TEXT_DARK), contentWidth)
            }
            chosen = Triple(qLayout, oLayouts, qSize to oSize)
        }
        val (qLayout, oLayouts, sizes) = chosen
        val (_, optionSize) = sizes

        var y = bodyTop
        canvas.save()
        canvas.translate(PADDING, y)
        qLayout.draw(canvas)
        canvas.restore()
        y += qLayout.height + 36f

        // ---- option cards ----
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MAROON; alpha = 46; style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MAROON }
        options.forEachIndexed { i, _ ->
            val oLayout = oLayouts[i]
            val cardH = oLayout.height + 40f
            val rect = RectF(PADDING, y, WIDTH - PADDING, y + cardH)
            canvas.drawRoundRect(rect, 28f, 28f, cardPaint)
            canvas.drawRoundRect(rect, 28f, 28f, borderPaint)
            // badge circle at the RTL start (right side)
            val badgeCx = WIDTH - PADDING - 52f
            val badgeCy = y + cardH / 2f
            canvas.drawCircle(badgeCx, badgeCy, 31f, badgePaint)
            val badgeLayout = layout(
                labels[i],
                textPaint(bold, optionSize * 0.92f, Color.WHITE, bold = true),
                64,
                lineSpacing = 1.0f
            )
            canvas.save()
            canvas.translate(badgeCx - 32f, badgeCy - badgeLayout.height / 2f)
            badgeLayout.draw(canvas)
            canvas.restore()
            // option text, right-aligned block left of the badge
            canvas.save()
            canvas.translate(PADDING + 28f, y + 20f)
            oLayout.draw(canvas)
            canvas.restore()
            y += cardH + 20f
        }

        // ---- footer band: gold gradient ----
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, HEIGHT - FOOTER_H, WIDTH.toFloat(), HEIGHT.toFloat(),
                GOLD, GOLD_DARK, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, HEIGHT - FOOTER_H, WIDTH.toFloat(), HEIGHT.toFloat(), footerPaint)
        val footerTitle = layout(
            "الجواب والشرح الكامل بالتطبيق",
            textPaint(bold, 46f, MAROON_DARK, bold = true),
            contentWidth,
            lineSpacing = 1.05f
        )
        canvas.save()
        canvas.translate(PADDING, HEIGHT - FOOTER_H + 22f)
        footerTitle.draw(canvas)
        canvas.restore()
        val footerHandle = layout(
            "@medprowatain",
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

    private fun optionTextWidth(optionSize: Float): Int {
        // card width minus: card side padding (28+28), badge zone (~118)
        return (WIDTH - (PADDING * 2) - 56f - 118f).toInt()
    }

    private fun totalBodyHeight(qLayout: StaticLayout, oLayouts: List<StaticLayout>): Int {
        var h = qLayout.height + 36
        oLayouts.forEach { h += it.height + 40 + 20 }
        return h
    }

    /** Very faint gold geometry: concentric arcs + dot grids in corners. */
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

    /** Saves the card PNG into cacheDir/share_images and returns the file. */
    fun saveToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "share_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "question_card_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }
}
