package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer 2 — word selection for the yellow text highlighter
 * (1.2.50). The pre-1.2.50 drag matched words by strict rect
 * overlap with zero tolerance and zero feedback: a drag that
 * grazed a word's edge — the natural way a finger selects —
 * matched nothing and the student saw the highlighter "not
 * working". [HighlightSelection] pins the replacement: the
 * selection rect is expanded by a small page-fraction
 * tolerance, a word whose centre falls inside the raw
 * selection also counts, and distant words never match.
 * Coordinates are page fractions, y from the top — the
 * [WordBox] space.
 */
class HighlightSelectionTest {

    private val line1Word1 = WordBox("أهلا", 0.10f, 0.10f, 0.20f, 0.13f)
    private val line1Word2 = WordBox("بالعالم", 0.22f, 0.10f, 0.32f, 0.13f)
    private val farWord = WordBox("بعيدة", 0.60f, 0.50f, 0.70f, 0.53f)
    private val words = listOf(line1Word1, line1Word2, farWord)

    @Test
    fun dragAcrossALineSelectsItsWords() {
        val matched = HighlightSelection.selectWords(
            words, left = 0.09f, top = 0.09f, right = 0.33f, bottom = 0.14f
        )
        assertEquals(listOf(line1Word1, line1Word2), matched)
    }

    @Test
    fun grazeWithinToleranceStillSelects() {
        // The drag ends 0.003 of the page width before the
        // second word starts — inside the 0.004 tolerance, so
        // the word the finger clearly meant is selected.
        val matched = HighlightSelection.selectWords(
            words, left = 0.09f, top = 0.10f, right = 0.217f, bottom = 0.13f
        )
        assertEquals(listOf(line1Word1, line1Word2), matched)
    }

    @Test
    fun grazeBeyondToleranceSelectsNothingExtra() {
        // A 0.01 gap is a deliberate stop before the word.
        val matched = HighlightSelection.selectWords(
            words, left = 0.09f, top = 0.10f, right = 0.21f, bottom = 0.13f
        )
        assertEquals(listOf(line1Word1), matched)
    }

    @Test
    fun wordCentreInsideSelectionSelects() {
        // A thin drag through the middle of the second word
        // only: its centre is inside, so it is selected even
        // though the drag never reaches its edges.
        val matched = HighlightSelection.selectWords(
            words, left = 0.26f, top = 0.112f, right = 0.28f, bottom = 0.118f
        )
        assertEquals(listOf(line1Word2), matched)
    }

    @Test
    fun emptyAreaSelectsNothing() {
        val matched = HighlightSelection.selectWords(
            words, left = 0.40f, top = 0.80f, right = 0.50f, bottom = 0.85f
        )
        assertTrue(matched.isEmpty())
    }
}
