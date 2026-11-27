package app.myfinhub.android.feature.bankalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankAlertParserTest {
    // Synthetic specimens, NOT bank-issued sample messages.
    private val purchase = "Πειραιώς; Αγορά; κάρτα ****1234; 26/11/2026 14:35; 4,50 EUR; Έμπορος: TEST COFFEE; Χώρα: GR"
    private val withdrawal = "PIRAEUS; WITHDRAWAL; card XX5678; 2026-11-26 09:15; EUR 1.234,50; ATM: TEST ATM; COUNTRY: GR"

    @Test fun parsesExactCentsAndMaskedDebitPurchase() {
        val alert = requireNotNull(BankAlertParser.parse(purchase))
        assertEquals(BankAlertParser.Kind.PURCHASE, alert.kind)
        assertEquals(450L, alert.cents)
        assertEquals("2026-11-26", alert.date.toString())
        assertEquals("14:35", alert.time.toString())
        assertEquals("1234", alert.lastFour)
        assertEquals("TEST COFFEE", alert.merchant)
        assertEquals("GR", alert.country)
    }

    @Test fun parsesWithdrawalAndGreekThousandsWithoutGuessing() {
        val alert = requireNotNull(BankAlertParser.parse(withdrawal))
        assertEquals(BankAlertParser.Kind.WITHDRAWAL, alert.kind)
        assertEquals(123450L, alert.cents)
        assertEquals("TEST ATM", alert.merchant)
        assertEquals("5678", alert.lastFour)
    }

    @Test fun rejectsMalformedAmbiguousSensitiveOrUnsupportedContent() {
        val invalid = listOf(
            purchase + "; 10,00 EUR", purchase.replace("4,50 EUR", "-4,50 EUR"),
            purchase.replace("EUR", "USD"), purchase.replace("26/11/2026", "31/11/2026"),
            purchase.replace("****1234", "1234567890123456"), purchase + "; OTP",
            purchase + "; https://example.invalid", purchase + "; ΑΠΟΡΡΙΦΘΗΚΕ",
            purchase.replace("****1234", ""), purchase.replace("Πειραιώς", "Other Bank"),
            purchase + "; Ανάληψη", purchase + "; 25/11/2026", purchase + "; 14:36",
            purchase.replace("4,50 EUR", "0,00 EUR"), purchase.replace("4,50 EUR", "1.02.300,50 EUR"),
            purchase + "; " + "x".repeat(1500), purchase + "; *****5678", purchase + "\u202e",
            purchase + "; ΠΙΣΤΩΤΙΚΗ", purchase + "; REFUND",
        )
        invalid.forEachIndexed { index, text ->
            assertNull("invalid synthetic case $index", BankAlertParser.parse(text))
        }
    }
}
