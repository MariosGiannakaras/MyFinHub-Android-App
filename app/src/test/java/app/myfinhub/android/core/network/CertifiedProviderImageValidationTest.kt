package app.myfinhub.android.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertifiedProviderImageValidationTest {
    private fun asset(
        mime: String,
        url: String="https://approved.supabase.co/storage/v1/object/public/financial-provider-assets/providers/bank/x.png",
        size: Long?=null,
    )=TrustedProviderAssetReference("x","logo","light",url,mime,size)

    @Test fun contentSignatureMustMatchDeclaredAndReturnedMimeType() {
        val png=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1,2,3)
        val jpeg=byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0x12,0x03)
        val webp="RIFFxxxxWEBPVP8 ".toByteArray()
        assertTrue(certifiedProviderImageBytesValid(asset("image/png"),"image/png",png))
        assertTrue(certifiedProviderImageBytesValid(asset("image/jpeg"),"image/jpeg",jpeg))
        assertTrue(certifiedProviderImageBytesValid(asset("image/webp"),"image/webp",webp))
        assertFalse(certifiedProviderImageBytesValid(asset("image/png"),"image/jpeg",png))
        assertFalse(certifiedProviderImageBytesValid(asset("image/jpeg"),"image/jpeg",png))
        assertFalse(certifiedProviderImageBytesValid(asset("image/webp"),"image/webp",jpeg))
        assertFalse(certifiedProviderImageBytesValid(asset("image/svg+xml"),"image/svg+xml",png))
        assertFalse(certifiedProviderImageBytesValid(asset("image/png"),null,png))
    }

    @Test fun sizeBoundariesAndResponseMimeTypeAreStrict() {
        val png=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
        assertFalse(certifiedProviderImageBytesValid(asset("image/png"),"image/png",byteArrayOf()))
        assertFalse(certifiedProviderImageBytesValid(asset("image/png"),"image/png",ByteArray(2_097_153)))
        assertFalse(certifiedProviderImageBytesValid(asset("image/png",size=2_097_153),"image/png",png))
        assertTrue(certifiedProviderImageBytesValid(asset("image/png",size=8),"image/png; charset=binary",png))
        assertFalse(certifiedProviderImageBytesValid(asset("image/png",size=-1),"image/png",png))
    }

    @Test fun configuredProviderOriginMustMatchAndEncodedTraversalRejected() {
        val host="https://approved.supabase.co"
        val url=asset("image/png").url
        assertTrue(trustedProviderAssetUrl(url,"bank",host) != null)
        assertTrue(trustedProviderAssetUrl(url.replace("approved.supabase.co","evil.example"),"bank",host) == null)
        assertTrue(trustedProviderAssetUrl(url.replace("/bank/","/other/"),"bank",host) == null)
        assertTrue(trustedProviderAssetUrl(url.replace("/x.png","/%2e%2e/x.png"),"bank",host) == null)
        assertTrue(trustedProviderAssetUrl(url.replace("https://","http://"),"bank",host) == null)
    }
}
