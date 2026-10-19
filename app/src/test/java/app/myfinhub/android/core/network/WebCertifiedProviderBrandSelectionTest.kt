package app.myfinhub.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web providerBrandAssets.ts precedence; additionally reject any binding
 * that crosses its declared asset role. Never fetch untrusted image URLs.
 */
class WebCertifiedProviderBrandSelectionTest {
    private val origin="https://safe-project.supabase.co"
    private fun asset(key: String, role: String, variant: String) =
        """{"assetKey":"$key","role":"$role","variant":"$variant","mimeType":"image/png","sizeBytes":500,"url":"$origin/storage/v1/object/public/financial-provider-assets/providers/alpha/$key.png?v=2026-10-19"}"""
    private fun binding(role: String, variant: String, key: String) =
        """{"role":"$role","variant":"$variant","assetKey":"$key"}"""
    private fun provider(assets: String, bindings: String) =
        """{"providers":[{"id":"alpha","displayName":"Alpha Bank","shortName":"Alpha","providerKind":"bank","sortOrder":20,"logoAssetKey":"logo-old","wordmarkAssetKey":"wordmark-old","assets":[$assets],"bindings":[$bindings]}]}"""
    private fun parse(assets: String, bindings: String = "") =
        parseFinancialProviderCatalog(provider(assets, bindings),trustedSupabaseOrigin=origin)
            ?.single()

    @Test fun explicitThemeBindingPrecedesAllLegacyVariants() {
        val p=parse(
            listOf(asset("logo-light","logo","light"),asset("logo-other","logo","universal"),
                asset("logo-old","logo","default")).joinToString(","),
            listOf(binding("logo","light","logo-old"),
                binding("logo","universal","logo-other")).joinToString(","),
        )!!
        assertEquals("logo-old",p.certifiedBrandAsset("logo","light")?.assetKey)
        assertEquals("logo-other",p.certifiedBrandAsset("logo","dark")?.assetKey)
    }

    @Test fun oppositeToneAssetsAreNeverUsedForUnboundDarkSurfaces() {
        val p=parse(asset("light-only","logo","light"))!!
        assertNull(p.certifiedBrandAsset("logo","dark"))
        assertEquals("light-only",p.certifiedBrandAsset("logo","light")?.assetKey)
        assertNull(p.certifiedBrandAsset("logo","sepia"))
        assertNull(p.certifiedBrandAsset("other","light"))
    }

    @Test fun cardMarksFallbackToCertifiedWordmarkThenLogo() {
        val assets=listOf(
            asset("alpha-logo","logo","universal"),
            asset("alpha-wordmark","wordmark","dark"),
            asset("alpha-card","card-mark","light"),
        ).joinToString(",")
        val p=parse(assets)!!
        assertEquals("alpha-card",p.certifiedBrandAsset("card-mark","light")?.assetKey)
        assertEquals("alpha-wordmark",p.certifiedBrandAsset("card-mark","dark")?.assetKey)
        assertEquals("alpha-logo",p.certifiedBrandAsset("wordmark","light")?.assetKey)
        assertEquals("alpha-logo",p.certifiedBrandAsset("logo","dark")?.assetKey)
    }

    @Test fun crossRoleBindingsAndDuplicateKeysFailClosedAtParsing() {
        val assets=listOf(asset("alpha-logo","logo","universal"),
            asset("alpha-card","card-mark","light")).joinToString(",")
        assertNull(parse(assets,binding("card-mark","universal","alpha-logo")))
        assertNull(parse(assets,binding("wordmark","light","alpha-card")))
        assertNull(parse(asset("alpha-logo","logo","light")+","+
            asset("alpha-logo","logo","dark")))
        assertNull(parse(assets,binding("logo","universal","alpha-logo")+","+
            binding("logo","universal","alpha-logo")))
    }

    @Test fun unverifiedPreviewOrForeignHostNeverBecomesDrawableReference() {
        val payload=provider(asset("alpha-logo","logo","light"),"")
        val identity=parseFinancialProviderCatalog(payload)!!.single()
        assertTrue(identity.trustedAssets.isEmpty())
        assertNull(identity.certifiedBrandAsset("logo","light"))
        val evil=asset("alpha-logo","logo","light").replace(origin,"https://evil.example")
        assertNull(parse(evil))
        val safe=parse(asset("alpha-logo","logo","light"))!!
        val selected=safe.certifiedBrandAsset("logo","light")!!
        assertTrue(selected.url.startsWith(origin))
        assertFalse(selected.toString().contains(origin))
        assertFalse(safe.trustedAssets.toString().contains("/storage/"))
    }
}
