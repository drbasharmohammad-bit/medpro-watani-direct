package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.51 — one digit system for the whole exam-countdown
 * card (doctor's device photo: a Latin «27» cell among Eastern
 * cells). Every countdown surface value goes through
 * [CountdownDigits]; these pins keep any Latin digit from leaking
 * back through any path (tile, units, date line).
 */
class CountdownDigitsTest {

    private fun assertAllEastern(text: String) {
        assertTrue("Latin digit leaked in: $text", text.none { it in '0'..'9' })
    }

    @Test
    fun unitConvertsEveryDigit() {
        assertEquals("٢٧", CountdownDigits.unit(27))
        assertEquals("٠", CountdownDigits.unit(0))
        assertEquals("١٢٣", CountdownDigits.unit(123))
        assertAllEastern(CountdownDigits.unit(9876543210L))
    }

    @Test
    fun unitPadsWithEasternZero() {
        assertEquals("٠٥", CountdownDigits.unit(5, pad = 2))
        assertEquals("٠٠", CountdownDigits.unit(0, pad = 2))
        assertEquals("٥٩", CountdownDigits.unit(59, pad = 2))
        // Longer than the pad: never truncated.
        assertEquals("١٢٣", CountdownDigits.unit(123, pad = 2))
    }

    @Test
    fun easternConvertsInsideMixedText() {
        assertEquals("٢٢ كانون الأول ٢٠٢٦", CountdownDigits.eastern("22 كانون الأول 2026"))
        assertEquals("لا أرقام هنا", CountdownDigits.eastern("لا أرقام هنا"))
        assertAllEastern(CountdownDigits.eastern("2026-11-24 08:30"))
    }
}
