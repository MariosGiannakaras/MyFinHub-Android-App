package app.myfinhub.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner-only Web recurring service asset transport and URL host safety. */
class WebRecurringServiceAssetCatalogTest {
    private val host="https://demo.supabase.co"
    private val key="service-asset-"+"abcdef012345abcdef012345"
    private val base="$host/storage/v1/object/public/recurring-service-assets/services/$key.png"
    private fun response(url: String = base, mime: String = "image/png", file: String = "$key.png") = """
        {"assets":[{"assetKey":"cover-1","recurringId":"internet","url":"$url",
         "fileName":"$file","mimeType":"$mime","sizeBytes":12345,
         "updatedAt":"2026-10-19T09:00:00Z"}]}
    """.trimIndent()

    @Test fun validCanonicalPublicAssetAndEmptyResponseRoundtrip() {
        val valid=parseRecurringServiceAssetCatalog(response(),host)!!
        assertEquals(1,valid.size)
        assertEquals("internet",valid.single().recurringId)
        assertEquals(base,valid.single().url)
        assertTrue(!valid.single().toString().contains(host))
        assertEquals(emptyList<RecurringServiceAssetRecord>(),
            parseRecurringServiceAssetCatalog("""{"assets":[]}""",host))
    }

    @Test fun foreignHostsAndUntrustedPathsCannotImpersonateServiceArt() {
        val evil=listOf(
            "https://evil.example/storage/v1/object/public/recurring-service-assets/services/$key.png",
            "https://demo.supabase.co.evil.example/storage/v1/object/public/recurring-service-assets/services/$key.png",
            "http://demo.supabase.co/storage/v1/object/public/recurring-service-assets/services/$key.png",
            "https://demo.supabase.co:8443/storage/v1/object/public/recurring-service-assets/services/$key.png",
            "https://user@demo.supabase.co/storage/v1/object/public/recurring-service-assets/services/$key.png",
            "$base#fragment",
            "$base?download=1",
            "$host/storage/v1/object/public/recurring-service-assets/services/../$key.png",
            "$host/storage/v1/object/public/financial-provider-assets/services/$key.png",
        )
        evil.forEach { bad -> assertNull(bad,parseRecurringServiceAssetCatalog(response(bad),host)) }
    }

    @Test fun malformedPayloadWrongMimeSizeAndFilenameFailClosed() {
        assertNull(parseRecurringServiceAssetCatalog("""{}""",host))
        assertNull(parseRecurringServiceAssetCatalog(
            response(mime="image/svg+xml"),host))
        assertNull(parseRecurringServiceAssetCatalog(
            response(file="bad.png"),host))
        assertNull(parseRecurringServiceAssetCatalog(
            response().replace("12345","2500000"),host))
        assertNull(parseRecurringServiceAssetCatalog(
            response().replace("\"cover-1\"","\"../evil\""),host))
    }
}
