package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeHistoricalMoneyLabelTest {
    @Test fun nonfiniteHistoricalDataNeverAppearsAsAValidEuroAmount() {
        for (amount in listOf(Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY)) {
            assertEquals("Μη έγκυρο ποσό", safeHistoricalMoneyLabel(amount))
        }
    }

    @Test fun legitimateCreditDebitAndZeroStillUseGreekMoneyFormatting() {
        val amount = safeHistoricalMoneyLabel(1234.56)
        val debit = safeHistoricalMoneyLabel(-45.50)
        val zero = safeHistoricalMoneyLabel(0.0)
        assertTrue(amount.contains("1.234,56"))
        assertTrue(debit.contains("-45,50"))
        assertTrue(zero.contains("0,00"))
        assertFalse(amount.contains("NaN") || amount.contains("∞"))
    }
}
