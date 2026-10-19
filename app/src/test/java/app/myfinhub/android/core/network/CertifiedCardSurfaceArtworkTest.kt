package app.myfinhub.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Strict Web card-mark theme = card gradient, never Android light/dark theme. */
class CertifiedCardSurfaceArtworkTest {
    private fun asset(key: String,variant: String,role: String="card-mark")=
        TrustedProviderAssetReference(
            assetKey=key,role=role,variant=variant,
            url="https://approved.supabase.co/storage/v1/object/public/financial-provider-assets/providers/mfh-bank/$key.png",
            mimeType="image/png",sizeBytes=25,
        )
    private val provider=FinancialProviderRecord(
        id="mfh-bank",displayName="Bank",shortName="B",providerKind="bank",
        countryCode="GR",sortOrder=1,
        logoAssetKey="logo",wordmarkAssetKey=null,
        trustedAssets=listOf(
            asset("light-mark","light"),
            asset("dark-mark","dark"),
            asset("logo","universal","logo"),
        ),
        trustedBindings=listOf(
            TrustedProviderAssetBinding("card-mark","light","light-mark"),
            TrustedProviderAssetBinding("card-mark","dark","dark-mark"),
        ),
    )
    @Test fun darkCardUsesDarkCertifiedBrandEvenInLightApplicationTheme() {
        assertEquals("dark-mark",certifiedCardMarkForSurface(
            listOf(provider),"mfh-bank",0.10f,
        )?.assetKey)
    }
    @Test fun lightCardUsesLightCertifiedBrandEvenInDarkApplicationTheme() {
        assertEquals("light-mark",certifiedCardMarkForSurface(
            listOf(provider),"mfh-bank",0.9f,
        )?.assetKey)
    }
    @Test fun unknownProviderCannotBorrowExistingBankMarkByNickname() {
        assertNull(certifiedCardMarkForSurface(listOf(provider),"nickname-piraeus",0.1f))
        assertNull(certifiedCardMarkForSurface(emptyList(),"mfh-bank",0.1f))
    }
    @Test fun nonfiniteOrOutOfRangeColorsFailClosedAndNoMismatchedRoles() {
        assertNull(certifiedCardMarkForSurface(listOf(provider),"mfh-bank",Float.NaN))
        assertNull(certifiedCardMarkForSurface(listOf(provider),"mfh-bank",-0.1f))
        assertNull(certifiedCardMarkForSurface(listOf(provider),"mfh-bank",1.2f))
        val logoOnly=provider.copy(trustedAssets=listOf(asset("logo","universal","logo")),
            trustedBindings=emptyList())
        // The Web card-mark role may explicitly fall back to an owner-certified logo.
        assertEquals("logo",certifiedCardMarkForSurface(
            listOf(logoOnly),"mfh-bank",0.6f,
        )?.assetKey)
    }
}
