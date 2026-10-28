package app.myfinhub.android.feature.money

import app.myfinhub.android.core.network.CardSecretUpdate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web/develop v1.7 server/cardVaultCrypto.ts NEW-WRITE contract vectors. */
class CardVaultWriteContractTest {
    private val october2026 = YearMonth.of(2026, 10)

    @Test fun panMustContainExactlySixteenNumericDigitsAfterFormatting() {
        assertEquals("4242424242424242", CardVaultWriteContract.normalizePan("4242 4242-4242 4242"))
        assertNull(CardVaultWriteContract.normalizePan("123456789012345"))
        assertNull(CardVaultWriteContract.normalizePan("12345678901234567"))
        assertNull(CardVaultWriteContract.normalizePan(""))
    }

    @Test fun cvvMustBeExactlyThreeAsciiDigits() {
        assertEquals("123", CardVaultWriteContract.normalizeCvv(" 123 "))
        assertNull(CardVaultWriteContract.normalizeCvv("1234"))
        assertNull(CardVaultWriteContract.normalizeCvv("12"))
        assertNull(CardVaultWriteContract.normalizeCvv("12a"))
        assertNull(CardVaultWriteContract.normalizeCvv("１２３"))
    }

    @Test fun expiryChecksActualUtcMonthAndNormalizesFourDigitYears() {
        assertEquals("10/26", CardVaultWriteContract.normalizeExpiry("10/26", october2026))
        assertEquals("10/26", CardVaultWriteContract.normalizeExpiry("10/2026", october2026))
        assertEquals("11/27", CardVaultWriteContract.normalizeExpiry("11-2027", october2026))
        assertEquals("12/99", CardVaultWriteContract.normalizeExpiry("12/2099", october2026))
        assertNull(CardVaultWriteContract.normalizeExpiry("09/26", october2026))
        assertNull(CardVaultWriteContract.normalizeExpiry("01/2020", october2026))
        assertNull(CardVaultWriteContract.normalizeExpiry("13/27", october2026))
        assertNull(CardVaultWriteContract.normalizeExpiry("00/27", october2026))
        assertNull(CardVaultWriteContract.normalizeExpiry("11/2100", october2026))
        assertNull(CardVaultWriteContract.normalizeExpiry("11/0027", october2026))
    }

    @Test fun migrationPartialWritesStillRequireStrictContract() {
        assertTrue(CardVaultWriteContract.isValidNewWrite(CardSecretUpdate(cvv = "123"), october2026))
        assertTrue(CardVaultWriteContract.isValidNewWrite(CardSecretUpdate(
            pan = "4242424242424242", expiry = "11/2027", cvv = "123",
        ), october2026))
        assertFalse(CardVaultWriteContract.isValidNewWrite(CardSecretUpdate(cvv = "1234"), october2026))
        assertFalse(CardVaultWriteContract.isValidNewWrite(CardSecretUpdate(expiry = "01/20"), october2026))
        assertFalse(CardVaultWriteContract.isValidNewWrite(CardSecretUpdate(
            pan = "4242424242424242", cvv = "1234",
        ), october2026))
        assertFalse(CardVaultWriteContract.isValidNewWrite(CardSecretUpdate(), october2026))
    }
}
