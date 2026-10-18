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
