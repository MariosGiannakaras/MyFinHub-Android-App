package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalCardLastFourDisplayTest {
    @Test fun exactAsciiFourDigitsKeepTheirExistingMaskedPresentation() {
        assertEquals("0123", canonicalCardDisplayLastFour("0123"))
        assertEquals("•••• 0123", canonicalCardMaskedLastFour("0123"))
    }

    @Test fun malformedImportedSuffixesNeverRevealPanOrPartialSecret() {
        listOf<String?>(
            null, "", "123", "12345", "4242424242424242",
            "•••• 4242", " 4242", "4242 ", "42-42", "12A4", "１２３４",
        ).forEach { untrusted ->
            assertNull(canonicalCardDisplayLastFour(untrusted))
            assertNull(canonicalCardMaskedLastFour(untrusted))
        }
    }
}
