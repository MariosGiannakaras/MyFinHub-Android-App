package app.myfinhub.android.app

import app.myfinhub.android.core.network.FinancialProviderRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Web getFinancialProviderSnapshot is authoritative only for an exact providerId. */
class CanonicalProviderCatalogDisplayTest {
    private val providers=listOf(
        FinancialProviderRecord(
            "custom-wallet","Νέος Οικονομικός Πάροχος","Νέος","wallet","GR",
            12,null,null,
        ),
        FinancialProviderRecord(
            "alpha","Alpha Bank","Alpha","bank","GR",
            20,"alpha-logo-white-on-blue","alpha-wordmark-color",
        ),
    )

    @Test fun exactCustomProviderLabelWinsOverHardcodedLegacyInstitution() {
        assertEquals("Νέος Οικονομικός Πάροχος",canonicalCatalogProviderName(
            "custom-wallet","Piraeus",providers,
        ))
        assertEquals("Alpha Bank",canonicalCatalogProviderName(
            " alpha ","Revolut",providers,
        ))
    }

    @Test fun unknownIdDoesNotFalselyMatchKnownBankText() {
        assertEquals("Revolut",canonicalCatalogProviderName(
            "not-an-approved-bank","Revolut",providers,
        ))
        assertEquals("Τρέχουσα ετικέτα",canonicalCatalogProviderName(
            null,"Τρέχουσα ετικέτα",providers,
        ))
        assertNull(canonicalCatalogProviderName("",null,providers))
        assertNull(canonicalCatalogProviderName("custom-wallet",null,emptyList()))
    }
}
