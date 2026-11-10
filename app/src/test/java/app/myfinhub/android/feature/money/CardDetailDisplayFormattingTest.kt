package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardDetailDisplayFormattingTest {
    @Test
    fun creditRateUsesGreekDecimalsAndSuppressesTrailingZero() {
        assertEquals("15%", formatGreekCreditRate("15.0"))
        assertEquals("10,95%", formatGreekCreditRate("10.95"))
        assertEquals("Μη έγκυρο επιτόκιο", formatGreekCreditRate("NaN"))
        assertEquals("Μη έγκυρο επιτόκιο", formatGreekCreditRate("Infinity"))
    }

    @Test
    fun effectiveDateIsLocalizedForDisplayWithoutChangingSourceValue() {
        val display = formatGreekCreditDate("2026-11-10")
        assertTrue(display.contains("2026"))
        assertFalse(display.contains("2026-11-10"))
        assertEquals("not-a-date", formatGreekCreditDate("not-a-date"))
    }
}
