package sy.medpro.watani.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer 2 — the reader's text-engine fallback policy (build
 * 1.2.52), written RED first.
 *
 * The text layer's primary engine is now PDFium itself (the
 * engine that renders), with the PDFBox engine kept as the
 * fallback. The policy pinned here: fall back whenever the
 * PDFium pass produced nothing usable — no index at all, or
 * an index without a single word (a scanned book honestly
 * lands in the same place: the fallback also finds no words,
 * and the book simply has no text layer). An index WITH words
 * never falls back: PDFium's geometry is the rendering's own,
 * and re-extracting with a second engine could only drift it.
 */
class PdfiumTextEnginePolicyTest {

    private fun indexWithWords(wordCount: Int): BookTextIndex =
        BookTextIndex(
            pages = listOf(
                PageText(
                    page = 0,
                    words = (0 until wordCount).map {
                        WordBox("w$it", 0f, 0f, 0.1f, 0.1f)
                    }
                )
            ),
            outline = emptyList()
        )

    @Test
    fun nullIndexFallsBack() {
        assertTrue(PdfiumTextEngine.shouldFallbackToPdfBox(null))
    }

    @Test
    fun wordlessIndexFallsBack() {
        assertTrue(PdfiumTextEngine.shouldFallbackToPdfBox(indexWithWords(0)))
        assertTrue(
            PdfiumTextEngine.shouldFallbackToPdfBox(
                BookTextIndex(pages = emptyList(), outline = emptyList())
            )
        )
    }

    @Test
    fun indexWithWordsNeverFallsBack() {
        assertFalse(PdfiumTextEngine.shouldFallbackToPdfBox(indexWithWords(1)))
        assertFalse(PdfiumTextEngine.shouldFallbackToPdfBox(indexWithWords(500)))
    }
}
