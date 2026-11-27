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

    // Only structural templates match the owner screenshot: all dates, digits,
    // amounts, balances, account and merchant names are synthetic.
    private val realShapeDebit = """
        ALERT-ΑΓΟΡΑ ΜΕ ΚΑΡΤΑ
        18/10/26 08:17
        ΛΟΓ/ΜΟΣ:TEST ACCOUNT
        ΠΟΣΟ=-13 EUR (ΧΡ)
        ΛΟΓ.ΥΠΟΛ=111.22
        ΔΙΑΘ.ΥΠΟΛ=100.50
        SAMPLE OWNER SHOP CITY
    """.trimIndent()
    private val realShapeCredit = """
        ALERT-ΠΙΣΤΩΤΙΚΗ ΚΑΡΤΑ
        ΑΓΟΡΑ 18/10/2026 21:17
        ΚΑΡΤΑ: 1234***9876
        ΠΟΣΟ= 17.43 EUR
        ΑΠΟ: SAMPLE_TEST SHOP STREET
        ΧΩΡΑ: ΕΛΛΑΔΑ
    """.trimIndent()

    @Test fun parsesPiraeusObservedDebitShapeWithoutCardAndIgnoresBalances() {
        val alert = requireNotNull(BankAlertParser.parse(realShapeDebit))
        assertEquals(BankAlertParser.Kind.PURCHASE, alert.kind)
        assertEquals(1300L, alert.cents)
        assertEquals("2026-10-18", alert.date.toString())
        assertEquals("08:17", alert.time.toString())
        assertEquals("", alert.lastFour)
        assertEquals("TEST ACCOUNT", alert.accountHint)
        assertEquals("SAMPLE OWNER SHOP CITY", alert.merchant)
        assertEquals(330L, BankAlertParser.parse(realShapeDebit.replace("-13 EUR", "-3.30 EUR"))?.cents)
    }

    @Test fun parsesPiraeusObservedCreditShapeWithoutRetainingCardPrefix() {
        val alert = requireNotNull(BankAlertParser.parse(realShapeCredit))
        assertEquals(BankAlertParser.Kind.CREDIT_PURCHASE, alert.kind)
        assertEquals(1743L, alert.cents)
        assertEquals("9876", alert.lastFour)
        assertEquals("SAMPLE_TEST SHOP STREET", alert.merchant)
        assertEquals("GR", alert.country)
    }

    @Test fun automaticCaptureOnlyAcceptsVerifiedTransactionLayouts() {
        assertEquals(1300L, BankAlertParser.parseIncomingPiraeus(realShapeDebit)?.cents)
        assertEquals(1743L, BankAlertParser.parseIncomingPiraeus(realShapeCredit)?.cents)
        assertEquals(330L, BankAlertParser.parseIncomingPiraeus(realShapeDebit
            .replace("-13 EUR (ΧΡ)", "−3.30 EUR (XΡ)").replace("\n", "\r\n\r\n"))?.cents)
        assertNull(BankAlertParser.parseIncomingPiraeus(purchase))
        assertNull(BankAlertParser.parseIncomingPiraeus(realShapeCredit + "\nOTP 123456"))
        assertNull(BankAlertParser.parseIncomingPiraeus(realShapeDebit + "\n" + realShapeCredit))
    }

    @Test fun rejectsModifiedObservedShapesRatherThanGuessing() {
        listOf(realShapeDebit.replace("-13 EUR", "13 EUR"), realShapeDebit.replace("(ΧΡ)", "(ΠΡ)"),
            realShapeDebit + "\nOTP 123456", realShapeDebit + "\n" + realShapeDebit,
            realShapeDebit.replace("111.22", "invalid"), realShapeCredit.replace("1234***9876", "1234567890987654"),
            realShapeCredit.replace("ΑΓΟΡΑ", "ΑΝΑΛΗΨΗ"), realShapeCredit.replace("17.43 EUR", "17.43 USD"),
            realShapeCredit + "\nΑΚΥΡΩΘΗΚΕ").forEachIndexed { index, message ->
            assertNull("malformed structured shape $index", BankAlertParser.parse(message))
        }
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
