package sy.medpro.watani.ui.firstaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the First Aid reference Markdown parser (build
 * 1.2.36, merge phase 5). The contract is the phase-4 decision spec
 * (firstaid-markdown-decision.md): the measured subset of the 154
 * published referenceMd texts — headings (H5+ folds to H4), inline
 * bold/italic/code, nested lists, pipe tables WITH or WITHOUT a
 * separator row (first row is the header either way), the single
 * literal «اشرحها ببساطة» quote shape (843 boxes), dividers, and the
 * Step-1 in-text figure markers [شكل: F1 — …]. A Python port of this
 * parser was run over all 154 published packs during the build: every
 * non-empty source line is represented (0 real missing lines; the 839
 * figure lines map 1:1 to Figure blocks).
 */
class FirstAidMarkdownParserTest {

    private fun texts(blocks: List<FaMdBlock>): List<String> = blocks.map { block ->
        when (block) {
            is FaMdBlock.Heading -> block.spans.joinToString("") { it.text }
            is FaMdBlock.Paragraph -> block.spans.joinToString("") { it.text }
            is FaMdBlock.Quote -> block.spans.joinToString("") { it.text }
            is FaMdBlock.SimpleBox ->
                (block.title + block.body).joinToString("") { it.text }
            is FaMdBlock.ListBlock ->
                block.items.joinToString("") { item -> item.spans.joinToString("") { it.text } }
            is FaMdBlock.Table ->
                (block.header + block.rows.flatten())
                    .joinToString("") { cell -> cell.joinToString("") { it.text } }
            is FaMdBlock.Figure -> "${block.mediaId} ${block.caption}"
            FaMdBlock.Divider -> "---"
        }
    }

    @Test
    fun headingsFoldAtH4() {
        val blocks = FirstAidMarkdownParser.parse("# أ\n\n## ب\n\n##### ج")
        val headings = blocks.filterIsInstance<FaMdBlock.Heading>()
        assertEquals(listOf(1, 2, 4), headings.map { it.level })
    }

    @Test
    fun inlineBoldItalicCode() {
        val spans = FirstAidMarkdownParser.parseInline("نص **عريض** و*مائل* و_مائل2_ و`كود`")
        assertTrue(spans.any { it.bold && it.text == "عريض" })
        assertTrue(spans.any { it.italic && it.text == "مائل" })
        assertTrue(spans.any { it.italic && it.text == "مائل2" })
        assertTrue(spans.any { it.code && it.text == "كود" })
    }

    @Test
    fun tableWithSeparatorRow() {
        val md = "| أ | ب |\n|---|---|\n| 1 | 2 |"
        val table = FirstAidMarkdownParser.parse(md).filterIsInstance<FaMdBlock.Table>().single()
        assertEquals(2, table.header.size)
        assertEquals(1, table.rows.size)
        assertEquals("1", table.rows[0][0].joinToString("") { it.text })
    }

    @Test
    fun tableWithoutSeparatorRowKeepsFirstRowAsHeader() {
        // The 6 no-separator files of the corpus (FA-ENDO-01 shape):
        // consecutive pipe lines, first line IS the header.
        val md = "| النمط | العلامة |\n| STEMI | ارتفاع ST |\n| NSTEMI | دون ارتفاع |"
        val table = FirstAidMarkdownParser.parse(md).filterIsInstance<FaMdBlock.Table>().single()
        assertEquals("النمط", table.header[0].joinToString("") { it.text })
        assertEquals(2, table.rows.size)
    }

    @Test
    fun simpleBoxShapeIsDetectedAndSplit() {
        val md = "> **اشرحها ببساطة (الداء التاجي):** دهون تتراكم ببطء داخل جدار الشريان."
        val box = FirstAidMarkdownParser.parse(md).filterIsInstance<FaMdBlock.SimpleBox>().single()
        assertTrue(box.title.joinToString("") { it.text }.startsWith("اشرحها ببساطة"))
        assertTrue(box.body.joinToString("") { it.text }.contains("دهون تتراكم"))
    }

    @Test
    fun plainQuoteIsNotASimpleBox() {
        val blocks = FirstAidMarkdownParser.parse("> ملاحظة عادية")
        assertEquals(1, blocks.filterIsInstance<FaMdBlock.Quote>().size)
        assertEquals(0, blocks.filterIsInstance<FaMdBlock.SimpleBox>().size)
    }

    @Test
    fun nestedListLevels() {
        val md = "- أول\n  - فرعي\n- ثانٍ\n1. مرقّم"
        val list = FirstAidMarkdownParser.parse(md).filterIsInstance<FaMdBlock.ListBlock>().single()
        assertEquals(listOf(0, 1, 0, 0), list.items.map { it.level })
        assertTrue(list.items.last().ordered)
        assertEquals(1, list.items.last().number)
    }

    @Test
    fun figureMarkerBecomesFigureBlock() {
        val md = "نص قبل.\n\n[شكل: F3 — شوكة تنسّخ الـDNA]\n\nنص بعد."
        val blocks = FirstAidMarkdownParser.parse(md)
        val figure = blocks.filterIsInstance<FaMdBlock.Figure>().single()
        assertEquals("F3", figure.mediaId)
        assertEquals("شوكة تنسّخ الـDNA", figure.caption)
    }

    @Test
    fun everyLineOfARepresentativeReferenceIsRepresented() {
        val md = """
            # FA2-CARD-01 — داء الشرايين التاجية

            - **القسم:** الطب الباطني
            - **صفحات الكتاب:** 17–18

            ---

            ## الدرس 1

            يبقى الداء **السبب الرئيسي** للمراضة، و`ملف المصدر` مذكور.

            > **اشرحها ببساطة:** دهون تتراكم ببطء.

            | النمط | العلامة |
            | STEMI | ارتفاع ST |
        """.trimIndent()
        val blocks = FirstAidMarkdownParser.parse(md)
        val rendered = texts(blocks).joinToString("\n")
        for (line in md.lines().map { it.trim() }.filter { it.isNotEmpty() && it != "---" }) {
            // Audit classification (21 Nov 2026, CODE-001): this test
            // was the full suite's ONLY failure, and the failure was
            // in the PROBE, not the parser. The old probe trimmed
            // marker characters only from the line's edges — a list
            // line starting with "- " kept its inline "**" markers,
            // so the probe searched the rendered text for literal
            // markdown syntax the parser correctly consumes. The
            // line's TEXT was always fully represented (list-item
            // spans carry «القسم:» + «الطب الباطني»). The probe now
            // strips block markers at the head and inline markers
            // everywhere before probing — the contract under test is
            // unchanged: every line's text must appear in the blocks.
            val probe = line
                .replace(Regex("^\\s*([#>|\\-]\\s*)+"), "")
                .replace("*", "")
                .replace("`", "")
                .trim()
            if (line.startsWith("|")) {
                for (cell in line.trim('|').split('|').map { it.trim() }) {
                    if (cell.isNotEmpty()) assertTrue("missing cell: $cell", rendered.contains(cell))
                }
            } else if (probe.isNotEmpty()) {
                assertTrue("missing line: $line", rendered.contains(probe.take(12)))
            }
        }
    }
}
