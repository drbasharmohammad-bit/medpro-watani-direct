package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily
import java.io.File

// =====================================================================
// Custom Compose Markdown renderer for the First Aid references
// (merge phase 5) — built EXACTLY to the phase-4 decision spec
// (firstaid-markdown-decision.md), over the measured subset of the
// 154 published referenceMd texts:
//   headings #..##### (H5+ folds to H4), **bold**, *italic* / _italic_,
//   unordered + ordered lists (2-space nesting), pipe tables up to 9
//   columns INCLUDING the 6 files whose tables have NO separator row
//   (first row = header either way), `inline code`, --- dividers, and
//   blockquotes — which in this corpus are ONLY the 843 «اشرحها
//   ببساطة» boxes, one literal shape: a quote paragraph starting with
//   **اشرحها ببساطة (…):**. No links, no images, no code blocks, no
//   HTML exist in the corpus; the parser has no branches for them.
// Step-1 references carry in-text figure markers as their own
// paragraph: [شكل: F1 — caption…]; the parser emits a Figure block
// and the unit player resolves the id to a downloaded local file.
// The parser is pure Kotlin (no Compose/Android) so JVM tests pin it.
// =====================================================================

/** One inline span of reference text. */
data class FaInlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false
)

/** One block of a parsed reference document. */
sealed interface FaMdBlock {
    data class Heading(val level: Int, val spans: List<FaInlineSpan>) : FaMdBlock
    data class Paragraph(val spans: List<FaInlineSpan>) : FaMdBlock
    data class ListBlock(val items: List<FaMdListItem>) : FaMdBlock
    data class Table(val header: List<List<FaInlineSpan>>, val rows: List<List<List<FaInlineSpan>>>) : FaMdBlock

    /** A «اشرحها ببساطة» box: [title] is the bold lead (e.g.
     * «اشرحها ببساطة (…):»), [body] the explanation after it. */
    data class SimpleBox(val title: List<FaInlineSpan>, val body: List<FaInlineSpan>) : FaMdBlock
    data class Quote(val spans: List<FaInlineSpan>) : FaMdBlock
    data class Figure(val mediaId: String, val caption: String) : FaMdBlock
    data object Divider : FaMdBlock
}

data class FaMdListItem(
    val level: Int,
    val ordered: Boolean,
    val number: Int?,
    val spans: List<FaInlineSpan>
)

object FirstAidMarkdownParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val LIST_ITEM = Regex("^(\\s*)([-*]|\\d+[.)])\\s+(.*)$")
    private val SEPARATOR_CELL = Regex("^:?-{1,}:?$")
    private val FIGURE = Regex("^\\[شكل:\\s*(\\S+)\\s*[—-]\\s*(.*)\\]$")

    /** Parses the inline subset: `code`, **bold**, *italic*, _italic_. */
    fun parseInline(text: String): List<FaInlineSpan> {
        val out = mutableListOf<FaInlineSpan>()
        val buf = StringBuilder()
        fun flush(bold: Boolean = false, italic: Boolean = false, code: Boolean = false) {
            if (buf.isNotEmpty()) {
                out += FaInlineSpan(buf.toString(), bold, italic, code)
                buf.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush()
                        out += FaInlineSpan(text.substring(i + 1, end), code = true)
                        i = end + 1
                    } else { buf.append(text[i]); i++ }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 1) {
                        flush()
                        out += FaInlineSpan(text.substring(i + 2, end), bold = true)
                        i = end + 2
                    } else { buf.append(text[i]); i++ }
                }
                text[i] == '*' || text[i] == '_' -> {
                    val mark = text[i]
                    val end = text.indexOf(mark, i + 1)
                    if (end > i + 1) {
                        flush()
                        out += FaInlineSpan(text.substring(i + 1, end), italic = true)
                        i = end + 1
                    } else { buf.append(text[i]); i++ }
                }
                else -> { buf.append(text[i]); i++ }
            }
        }
        flush()
        return out
    }

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim() }

    private fun isSeparatorRow(cells: List<String>): Boolean =
        cells.isNotEmpty() && cells.all { SEPARATOR_CELL.matches(it) }

    private fun isSimpleBoxText(merged: String): Boolean =
        merged.startsWith("**اشرحها ببساطة")

    /** Splits a simple-box quote into its bold title and body spans:
     * the leading **…** run is the title, the rest the body. */
    private fun simpleBox(merged: String): FaMdBlock.SimpleBox {
        val end = merged.indexOf("**", 2)
        return if (end > 2) {
            FaMdBlock.SimpleBox(
                title = listOf(FaInlineSpan(merged.substring(2, end), bold = true)),
                body = parseInline(merged.substring(end + 2).trim())
            )
        } else {
            FaMdBlock.SimpleBox(title = parseInline(merged), body = emptyList())
        }
    }

    fun parse(markdown: String): List<FaMdBlock> {
        val blocks = mutableListOf<FaMdBlock>()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            when {
                line.isEmpty() -> i++

                line == "---" || line == "***" -> { blocks += FaMdBlock.Divider; i++ }

                HEADING.matches(line) -> {
                    val m = HEADING.matchEntire(line)!!
                    val level = minOf(m.groupValues[1].length, 4)
                    blocks += FaMdBlock.Heading(level, parseInline(m.groupValues[2].trim()))
                    i++
                }

                line.startsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableLines += lines[i].trim()
                        i++
                    }
                    val allRows = tableLines.map { splitRow(it) }
                    val bodyRows = allRows.drop(1).filterNot { isSeparatorRow(it) }
                    blocks += FaMdBlock.Table(
                        header = allRows.first().map { parseInline(it) },
                        rows = bodyRows.map { row -> row.map { parseInline(it) } }
                    )
                }

                line.startsWith(">") -> {
                    val parts = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        parts += lines[i].trim().removePrefix(">").trim()
                        i++
                    }
                    val merged = parts.joinToString(" ").trim()
                    blocks += if (isSimpleBoxText(merged)) simpleBox(merged)
                    else FaMdBlock.Quote(parseInline(merged))
                }

                LIST_ITEM.matches(raw) || LIST_ITEM.matches(line) -> {
                    val items = mutableListOf<FaMdListItem>()
                    while (i < lines.size) {
                        val candidate = lines[i]
                        val m = LIST_ITEM.matchEntire(candidate) ?: LIST_ITEM.matchEntire(candidate.trim())
                        if (m == null) break
                        val indent = m.groupValues[1].length
                        val marker = m.groupValues[2]
                        val ordered = marker.first().isDigit()
                        items += FaMdListItem(
                            level = indent / 2,
                            ordered = ordered,
                            number = if (ordered) marker.trim('.', ')').toIntOrNull() else null,
                            spans = parseInline(m.groupValues[3].trim())
                        )
                        i++
                    }
                    blocks += FaMdBlock.ListBlock(items)
                }

                else -> {
                    // Paragraph: consecutive non-blank, non-special lines.
                    val parts = mutableListOf<String>()
                    while (i < lines.size) {
                        val l = lines[i].trim()
                        if (l.isEmpty() || l == "---" || l.startsWith("#") ||
                            l.startsWith("|") || l.startsWith(">") ||
                            LIST_ITEM.matches(lines[i]) || LIST_ITEM.matches(l)
                        ) break
                        parts += l
                        i++
                    }
                    val merged = parts.joinToString(" ").trim()
                    val fig = FIGURE.matchEntire(merged)
                    if (fig != null) {
                        blocks += FaMdBlock.Figure(fig.groupValues[1], fig.groupValues[2].trim())
                    } else if (merged.isNotEmpty()) {
                        blocks += FaMdBlock.Paragraph(parseInline(merged))
                    }
                }
            }
        }
        return blocks
    }
}

// ---------------------------------------------------------------------
// Compose renderer (05-reference model): lesson cards with Kufi
// headings, the gold «اشرحها ببساطة» box, maroon-header RTL tables.
// ---------------------------------------------------------------------

@Composable
private fun List<FaInlineSpan>.toAnnotated(
    boldColor: Color? = null,
    baseFamily: FontFamily = NotoSansArabicFontFamily
): AnnotatedString = buildAnnotatedString {
    for (span in this@toAnnotated) {
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else baseFamily,
            color = if (span.bold && boldColor != null) boldColor else Color.Unspecified,
            background = if (span.code) faGoldTint() else Color.Unspecified
        )
        withStyle(style) { append(span.text) }
    }
}

@Composable
fun FirstAidReferenceView(
    markdown: String,
    modifier: Modifier = Modifier,
    figureFile: (String) -> File? = { null },
    figureCaption: (String) -> String? = { null }
) {
    val blocks = remember(markdown) { FirstAidMarkdownParser.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        for (block in blocks) {
            when (block) {
                is FaMdBlock.Heading -> FaMdHeading(block)
                is FaMdBlock.Paragraph -> Text(
                    block.spans.toAnnotated(boldColor = faAccentText()),
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 13.5.sp, lineHeight = 24.sp, color = faInk()
                )
                is FaMdBlock.ListBlock -> FaMdList(block)
                is FaMdBlock.Table -> FaMdTable(block.header, block.rows)
                is FaMdBlock.SimpleBox -> FaSimpleBox(block)
                is FaMdBlock.Quote -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(faChipCodeBg())
                        .padding(12.dp)
                ) {
                    Text(
                        block.spans.toAnnotated(),
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 13.sp, lineHeight = 23.sp, color = faInk()
                    )
                }
                is FaMdBlock.Figure -> FaFigure(block, figureFile, figureCaption)
                FaMdBlock.Divider -> HorizontalDivider(color = faLine())
            }
        }
    }
}

@Composable
private fun FaMdHeading(block: FaMdBlock.Heading) {
    val (size, color) = when (block.level) {
        1 -> 19.5.sp to faHeading()
        2 -> 16.5.sp to faHeading()
        3 -> 14.5.sp to FaColors.Maroon
        else -> 13.5.sp to FaColors.Maroon
    }
    Text(
        block.spans.toAnnotated(baseFamily = NotoKufiFontFamily),
        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
        fontSize = size, lineHeight = size * 1.5f, color = color,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun FaMdList(block: FaMdBlock.ListBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (item in block.items) {
            Row(modifier = Modifier.padding(start = (item.level * 18).dp)) {
                Text(
                    if (item.ordered) "${item.number ?: ""}. " else "•  ",
                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp, color = faAccentText()
                )
                Text(
                    item.spans.toAnnotated(boldColor = faAccentText()),
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 13.5.sp, lineHeight = 23.sp, color = faInk()
                )
            }
        }
    }
}

/** The «اشرحها ببساطة» box of the 05-reference model: warm gold
 * gradient card, white icon tile, gold-deep title, warm-brown body. */
@Composable
private fun FaSimpleBox(block: FaMdBlock.SimpleBox) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFFFFFDF6), Color(0xFFF8EFDA))
                )
            )
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Lightbulb, contentDescription = null,
                tint = faGoldText(), modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                block.title.toAnnotated(baseFamily = NotoKufiFontFamily),
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.8.sp, color = faGoldDeepText()
            )
            if (block.body.isNotEmpty()) {
                Text(
                    block.body.toAnnotated(),
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.3.sp, lineHeight = 21.5.sp, color = Color(0xFF5C4A33),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** RTL table (05-reference model): maroon header row, zebra body rows,
 * first column bold maroon. ≤4 columns flex by weight; wider tables
 * scroll horizontally with a per-column minimum width. */
@Composable
fun FaMdTable(
    header: List<List<FaInlineSpan>>,
    rows: List<List<List<FaInlineSpan>>>,
    modifier: Modifier = Modifier
) {
    val colCount = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return
    val wide = colCount > 4
    val table: @Composable () -> Unit = {
        Column(
            modifier = if (wide) Modifier.width((colCount * 150).dp) else Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp))
                    .background(FaColors.Maroon)
            ) {
                for (c in 0 until colCount) {
                    val cell = header.getOrNull(c)
                    Text(
                        cell?.toAnnotated() ?: AnnotatedString(""),
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 11.6.sp, color = Color(0xFFFFF8EC),
                        modifier = (if (wide) Modifier.width(150.dp) else Modifier.weight(1f))
                            .padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }
            }
            rows.forEachIndexed { r, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (r % 2 == 1) Color(0xFFFBF7F0) else Color.Transparent)
                ) {
                    for (c in 0 until colCount) {
                        val cell = row.getOrNull(c)
                        Text(
                            cell?.toAnnotated() ?: AnnotatedString(""),
                            fontFamily = NotoSansArabicFontFamily,
                            fontWeight = if (c == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp, lineHeight = 17.sp,
                            color = if (c == 0) FaColors.Maroon else faInk(),
                            modifier = (if (wide) Modifier.width(150.dp) else Modifier.weight(1f))
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
                if (r < rows.lastIndex) HorizontalDivider(color = Color(0xFFF1E8DC), thickness = 1.dp)
            }
        }
    }
    FaCard(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(12.dp)) {
            if (wide) {
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) { table() }
            } else {
                table()
            }
        }
    }
}

/** A Step-1 figure at its in-text marker: the downloaded image when
 * present, otherwise a captioned placeholder (never a broken box). */
@Composable
private fun FaFigure(
    block: FaMdBlock.Figure,
    figureFile: (String) -> File?,
    figureCaption: (String) -> String?
) {
    val file = figureFile(block.mediaId)
    val bitmap = remember(file?.absolutePath, file?.length()) {
        file?.takeIf { it.exists() && it.length() > 0 }?.let {
            // Decode at display size (1.2.48, item 6): the figure
            // renders at slot width, so the decode subsamples to
            // that width instead of holding the full source bitmap.
            sy.medpro.watani.ui.ImageDecode.decodeFileDownsampled(
                it.absolutePath, sy.medpro.watani.ui.ImageDecode.FIGURE_TARGET_WIDTH_PX
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = block.caption,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(faChipCodeBg())
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "شكل ${block.mediaId} — يُنزَّل مع الوحدة ويظهر هنا عند توفره",
                    fontFamily = NotoSansArabicFontFamily, fontSize = 12.sp, color = faMuted()
                )
            }
        }
        val caption = figureCaption(block.mediaId) ?: block.caption
        if (caption.isNotBlank()) {
            Text(
                caption, fontFamily = NotoSansArabicFontFamily,
                fontSize = 11.5.sp, lineHeight = 18.sp, color = faMuted()
            )
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}
