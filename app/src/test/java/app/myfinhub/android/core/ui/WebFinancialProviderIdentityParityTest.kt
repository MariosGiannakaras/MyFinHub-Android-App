package app.myfinhub.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Current Web financialProviders.ts all eight identities and explicit-id priority. */
class WebFinancialProviderIdentityParityTest {
    @Test fun everyWebProviderMatchesFromKnownIdAndCommonAliases() {
        val labels = listOf(
            "Πειραιώς" to FinancialProvider.PIRAEUS,
            "Alpha Bank" to FinancialProvider.ALPHA,
            "Εθνική Τράπεζα" to FinancialProvider.NATIONAL,
            "Eurobank" to FinancialProvider.EUROBANK,
            "Revolut" to FinancialProvider.REVOLUT,
            "Viva.com" to FinancialProvider.VIVA,
            "Magenta Pay" to FinancialProvider.PAYZY,
            "PayPal" to FinancialProvider.PAYPAL,
        )
        assertEquals(8, FinancialProvider.entries.size)
        labels.forEach { (label, expected) ->
            assertEquals(expected.key, financialProviderId(label))
            assertEquals(expected, financialProvider(expected.key, ""))
        }
        assertEquals("piraeus", financialProviderId("Τράπεζα Πειραιώς"))
    }

    @Test fun explicitProviderIdOverridesNameAndUnknownIdMustNotGuessOtherBank() {
        assertEquals("alpha",accountFinancialProviderId(" alpha ","Eurobank","acc","Revolut"))
        assertEquals("custom-provider",accountFinancialProviderId("custom-provider","Piraeus","acc","Piraeus"))
        assertEquals(FinancialProvider.ALPHA,financialProvider("account-piraeus","Piraeus","alpha"))
        assertNull(financialProvider("Piraeus","Piraeus","custom-provider"))
        assertNull(financialProvider("non-bank-wallet","Unknown"))
    }

    @Test fun realAccountNamesPreserveSemanticsWithoutDuplicatedProviderName() {
        assertEquals("Αποταμιευτικός",
            financialAccountDisplayName("acc-savings","Εθνική Τράπεζα","savings","national"))
        assertEquals("Λογαριασμός",
            financialAccountDisplayName("acc-main","Eurobank","bank","eurobank"))
        assertEquals("Ταμιευτήριο",
            financialAccountDisplayName("acc-main","Ταμιευτήριο","bank","piraeus"))
        assertEquals("Μετρητά",
            financialAccountDisplayName("cash","Μετρητά","cash","alpha"))
        assertEquals("Custom Wallet",
            financialAccountDisplayName("custom","Custom Wallet","bank","unlisted-provider"))
    }
}
