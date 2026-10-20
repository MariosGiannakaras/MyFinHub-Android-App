package app.myfinhub.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner+AAL2 financial-provider metadata from the existing Web API, no remote URL rendering. */
class WebFinancialProviderCatalogTest {
    @Test fun parsesAndSortsDynamicKnownAndOwnerAddedProvidersByCanonicalOrder() {
        val decoded=parseFinancialProviderCatalog("""
        {"providers":[
          {"id":"custom-bank","displayName":"Νέα Τράπεζα","shortName":"Νέα",
           "providerKind":"bank","countryCode":"GR","sortOrder":21,
           "logoAssetKey":"custom-mark","wordmarkAssetKey":null,
           "logoUrl":"https://untrusted.invalid/logo.png"},
          {"id":"alpha","displayName":"Alpha Bank","shortName":"Alpha",
           "providerKind":"bank","countryCode":"GR","sortOrder":20,
           "logoAssetKey":"alpha-logo-white-on-blue","wordmarkAssetKey":"alpha-wordmark-color"}
        ]}
        """.trimIndent())!!
        assertEquals(listOf("alpha","custom-bank"),decoded.map { it.id })
        assertEquals("Νέα Τράπεζα",decoded[1].displayName)
        assertEquals("custom-mark",decoded[1].logoAssetKey)
        assertEquals("GR",decoded[1].countryCode)
        // Image URLs do not enter the sensitive Android metadata model.
        assertTrue(decoded[1].toString().contains("Νέα Τράπεζα"))
        assertTrue(decoded[1].toString().contains("logoUrl").not())
    }

    @Test fun GreekAccentsUseWebLocaleComparisonInsideEqualSortOrder() {
        val decoded = parseFinancialProviderCatalog("""
          {"providers":[
            {"id":"zeta","displayName":"Ζήτα","shortName":"Z",
             "providerKind":"bank","sortOrder":1},
            {"id":"alpha-greek","displayName":"Άλφα","shortName":"A",
             "providerKind":"bank","sortOrder":1},
            {"id":"first","displayName":"Ωμέγα","shortName":"O",
             "providerKind":"bank","sortOrder":0}
          ]}
        """.trimIndent())!!
        assertEquals(listOf("first","alpha-greek","zeta"),decoded.map { it.id })
    }

    @Test fun themeAssetBindingPrefersExplicitRoleAndFallsBackOnlyToUniversal() {
        val item=FinancialProviderRecord(
            id="bank",displayName="Test bank",shortName="Test",
            providerKind="bank",countryCode="GR",sortOrder=1,
            logoAssetKey=null,wordmarkAssetKey=null,
            trustedAssets=listOf(
                TrustedProviderAssetReference("bank-logo-light","logo","light",
                    "https://safe.invalid/logo-light.png","image/png",100),
                TrustedProviderAssetReference("bank-logo-universal","logo","universal",
                    "https://safe.invalid/logo.png","image/png",120),
                TrustedProviderAssetReference("bank-mark-dark","card-mark","dark",
                    "https://safe.invalid/card-dark.png","image/png",130),
            ),
            trustedBindings=listOf(
                TrustedProviderAssetBinding("logo","light","bank-logo-light"),
                TrustedProviderAssetBinding("logo","universal","bank-logo-universal"),
                TrustedProviderAssetBinding("card-mark","dark","bank-mark-dark"),
            ),
        )
        assertEquals("bank-logo-light",item.boundAssetForTheme("logo",false)?.assetKey)
        assertEquals("bank-logo-universal",item.boundAssetForTheme("logo",true)?.assetKey)
        assertEquals("bank-mark-dark",item.boundAssetForTheme("card-mark",true)?.assetKey)
        assertNull(item.boundAssetForTheme("card-mark",false))
        assertNull(item.boundAssetForTheme("wordmark",false))
        assertNull(item.boundAssetForTheme("arbitrary",true))
    }

    @Test fun crossRoleAssetIdentityMustNotBeUsedEvenInInconsistentCatalog() {
        val item=FinancialProviderRecord(
            id="bank",displayName="Bank",shortName="B",providerKind="bank",
            countryCode=null,sortOrder=0,logoAssetKey=null,wordmarkAssetKey=null,
            trustedAssets=listOf(
                TrustedProviderAssetReference("logo","logo","universal",
                    "https://safe.invalid/logo.png","image/png",100),
            ),
            trustedBindings=listOf(
                TrustedProviderAssetBinding("card-mark","universal","logo"),
            ),
        )
        assertNull(item.boundAssetForTheme("card-mark",true))
    }

    @Test fun malformedCatalogAndUnknownKindsFailClosedWithoutInventedProviders() {
        assertNull(parseFinancialProviderCatalog("{}"))
        assertNull(parseFinancialProviderCatalog("""{"providers":[]}"""))
        assertNull(parseFinancialProviderCatalog("""
            {"providers":[{"id":"fake","displayName":"Fake","shortName":"F",
            "providerKind":"untrusted","sortOrder":1}]}
        """.trimIndent()))
    }

    @Test fun individualInvalidRowsCannotImpersonateOrReplaceApprovedProviders() {
        val decoded=parseFinancialProviderCatalog("""
          {"providers":[
            {"id":"ok","displayName":"Approved","shortName":"A",
             "providerKind":"wallet","countryCode":"US","sortOrder":1},
            {"id":"not valid","displayName":"Poison","shortName":"P",
             "providerKind":"bank","sortOrder":2},
            {"id":"ok","displayName":"Duplicate","shortName":"D",
             "providerKind":"bank","sortOrder":3},
            {"id":"evil","displayName":"Unsafe","shortName":"E",
             "providerKind":"bank","sortOrder":4,"logoAssetKey":"../../bad"}
          ]}
        """.trimIndent())!!
        assertEquals(1,decoded.size)
        assertEquals("Approved",decoded[0].displayName)
    }
}
