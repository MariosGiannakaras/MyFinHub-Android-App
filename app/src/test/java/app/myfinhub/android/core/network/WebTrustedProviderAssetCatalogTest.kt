package app.myfinhub.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Canonical server public-bucket artwork, no arbitrary Web remote requests. */
class WebTrustedProviderAssetCatalogTest {
    private val origin = "https://assets.example.supabase.co"
    private fun payload(url: String) = """
      {"providers":[{"id":"approved","displayName":"Εγκεκριμένη","shortName":"Τράπεζα",
        "providerKind":"bank","sortOrder":1,"countryCode":"GR",
        "logoAssetKey":"trusted-logo","wordmarkAssetKey":null,
        "assets":[{"assetKey":"trusted-logo","role":"logo","variant":"dark",
          "url":"$url","mimeType":"image/png","sizeBytes":2048}],
        "bindings":[{"role":"logo","variant":"dark","assetKey":"trusted-logo"}]
      }]}
    """.trimIndent()
    private val valid="$origin/storage/v1/object/public/financial-provider-assets/providers/approved/dark.png?v=2026-10-19T08%3A00%3A00Z"

    @Test fun certifiedSameOriginProviderAssetAndBindingParsedOnlyForCorrectOwnerOrigin() {
        val parsed=parseFinancialProviderCatalog(payload(valid),
            trustedSupabaseOrigin=origin)!!.single()
        assertEquals("trusted-logo",parsed.trustedAssets.single().assetKey)
        assertEquals("dark",parsed.trustedBindings.single().variant)
        assertEquals(valid,parsed.trustedAssets.single().url)
        assertTrue(parsed.toString().contains("dark.png").not())
        // Identity-only parsing never silently enables external image loading.
        assertTrue(parseFinancialProviderCatalog(payload(valid))!!.single().trustedAssets.isEmpty())
    }
    @Test fun untrustedHostsAndForeignProviderDirectoriesFailClosed() {
        for (bad in listOf(
            "https://evil.invalid/logo.png",
            "https://assets.example.supabase.co.evil.invalid/storage/v1/object/public/financial-provider-assets/providers/approved/dark.png",
            "$origin/storage/v1/object/public/financial-provider-assets/providers/other/dark.png",
            "$origin/storage/v1/object/public/financial-provider-assets/providers/approved/../other/dark.png",
            "$origin/storage/v1/object/public/financial-provider-assets/providers/approved/dark.png?redirect=https://evil.invalid",
            "http://assets.example.supabase.co/storage/v1/object/public/financial-provider-assets/providers/approved/dark.png",
        )) {
            assertNull(parseFinancialProviderCatalog(payload(bad),
                trustedSupabaseOrigin=origin))
        }
    }
    @Test fun onlyCanonicalVersionQueriesAndVerifiedPathsAccepted() {
        assertNull(trustedProviderAssetUrl(
            "$origin/storage/v1/object/public/another-bucket/providers/approved/mark.png",
            "approved",origin))
        assertEquals(valid,trustedProviderAssetUrl(valid,"approved",origin))
    }
}
