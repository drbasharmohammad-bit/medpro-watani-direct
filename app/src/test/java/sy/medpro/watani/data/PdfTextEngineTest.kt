package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.37 (PDF text layer): the pure half of PdfTextEngine —
 * Arabic-aware normalization and phrase search over synthetic page
 * word lists. The PDFBox extraction itself is Android-side and not
 * exercised here; what these tests pin is the matching contract the
 * reader relies on: vocalized and unvocalized text meet after
 * normalization, a hit's word range covers the whole matched phrase
 * (even mid-word edges), every occurrence is reported, and a phrase
 * split across a page break never matches.
 */
class PdfTextEngineTest {

    private fun word(text: String) = WordBox(text = text, x0 = 0f, y0 = 0f, x1 = 0.1f, y1 = 0.05f)

    private fun page(page: Int, vararg texts: String) =
        PageText(page = page, words = texts.map { word(it) })

    private fun indexOf(vararg pages: PageText) =
        BookTextIndex(pages = pages.toList(), outline = emptyList())

    // ---------- normalize ----------

    @Test
    fun normalizeStripsDiacriticsAndTatweel() {
        assertEquals("السلام", PdfTextEngine.normalize("السَّلَامُ"))
        assertEquals("السلام", PdfTextEngine.normalize("السلـــام"))
        assertEquals("قصور القلب", PdfTextEngine.normalize("قُصُورُ الْقَلْبِ"))
    }

    @Test
    fun normalizeFoldsHamzaAndFinalLetterVariants() {
        assertEquals("ااا", PdfTextEngine.normalize("آأإ"))
        assertEquals("مومن", PdfTextEngine.normalize("مؤمن"))
        assertEquals("سايل", PdfTextEngine.normalize("سائل"))
        assertEquals("فتاه", PdfTextEngine.normalize("فتاة"))
        assertEquals("علي", PdfTextEngine.normalize("على"))
    }

    @Test
    fun normalizeMapsEasternDigitsAndLowercasesLatin() {
        assertEquals("123", PdfTextEngine.normalize("١٢٣"))
        assertEquals("الجرعه 5 mg", PdfTextEngine.normalize("الجرعة ٥ MG"))
        assertEquals("ecg", PdfTextEngine.normalize("ECG"))
    }

    @Test
    fun normalizeCollapsesWhitespaceAndTrims() {
        assertEquals("قصور القلب", PdfTextEngine.normalize("  قصور   القلب  "))
        assertEquals("", PdfTextEngine.normalize("   "))
        assertEquals("", PdfTextEngine.normalize("ًٌٍ"))
    }

    // ---------- search ----------

    @Test
    fun searchFindsMultiWordPhraseAsWordRange() {
        val index = indexOf(page(0, "قصور", "القلب", "الحاد"))
        assertEquals(
            listOf(SearchHit(page = 0, wordStart = 0, wordEnd = 1)),
            PdfTextEngine.search(index, "قصور القلب")
        )
        assertEquals(
            listOf(SearchHit(page = 0, wordStart = 1, wordEnd = 2)),
            PdfTextEngine.search(index, "القلب الحاد")
        )
    }

    @Test
    fun searchIsDiacriticAndVariantInsensitive() {
        val index = indexOf(page(0, "قُصور", "القلب"))
        // Unvocalized query finds vocalized text…
        assertEquals(1, PdfTextEngine.search(index, "قصور القلب").size)
        // …and the reverse, plus ة/ه and ى/ي folding on both sides.
        val index2 = indexOf(page(3, "متلازمة", "الكلى"))
        assertEquals(
            listOf(SearchHit(page = 3, wordStart = 0, wordEnd = 1)),
            PdfTextEngine.search(index2, "متلازمه الكلي")
        )
    }

    @Test
    fun searchReportsEveryOccurrence() {
        val index = indexOf(page(0, "القلب", "سليم", "القلب", "القلب"))
        assertEquals(
            listOf(
                SearchHit(page = 0, wordStart = 0, wordEnd = 0),
                SearchHit(page = 0, wordStart = 2, wordEnd = 2),
                SearchHit(page = 0, wordStart = 3, wordEnd = 3)
            ),
            PdfTextEngine.search(index, "القلب")
        )
    }

    @Test
    fun searchExpandsMidWordMatchToWholeWords() {
        // The phrase begins inside the word «بقصور»: the hit must
        // cover that whole word, not a partial range.
        val index = indexOf(page(0, "أهلا", "بقصور", "القلب"))
        assertEquals(
            listOf(SearchHit(page = 0, wordStart = 1, wordEnd = 2)),
            PdfTextEngine.search(index, "قصور القلب")
        )
    }

    @Test
    fun searchNeverCrossesPageBoundaries() {
        val index = indexOf(
            page(0, "نهاية", "قصور"),
            page(1, "القلب", "بداية")
        )
        assertTrue(PdfTextEngine.search(index, "قصور القلب").isEmpty())
        // Each half still finds its own page, with the page's number.
        assertEquals(
            listOf(SearchHit(page = 0, wordStart = 1, wordEnd = 1)),
            PdfTextEngine.search(index, "قصور")
        )
        assertEquals(
            listOf(SearchHit(page = 1, wordStart = 0, wordEnd = 0)),
            PdfTextEngine.search(index, "القلب")
        )
    }

    @Test
    fun searchEmptyOrAbsentQueryYieldsNoHits() {
        val index = indexOf(page(0, "قصور", "القلب"))
        assertTrue(PdfTextEngine.search(index, "").isEmpty())
        assertTrue(PdfTextEngine.search(index, "   ").isEmpty())
        assertTrue(PdfTextEngine.search(index, "ًٌ").isEmpty())
        assertTrue(PdfTextEngine.search(index, "الربو").isEmpty())
        assertTrue(PdfTextEngine.search(indexOf(), "القلب").isEmpty())
    }

    @Test
    fun searchMatchesEasternDigitQueryAgainstWesternText() {
        val index = indexOf(page(0, "الجرعة", "5", "ملغ"))
        assertEquals(
            listOf(SearchHit(page = 0, wordStart = 1, wordEnd = 1)),
            PdfTextEngine.search(index, "٥")
        )
    }
}
