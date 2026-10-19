package app.myfinhub.android.core.network

/** Web card-mark role resolution depends on the color of the card itself.
 * Unknown or mismatched bank IDs never borrow another bank's identity.
 */
internal fun certifiedCardMarkForSurface(
    catalog: List<FinancialProviderRecord>,
    bankId: String,
    surfaceLuminance: Float,
): TrustedProviderAssetReference? {
    if (surfaceLuminance.isNaN() || surfaceLuminance !in 0f..1f) return null
    val provider=catalog.firstOrNull { it.id == bankId } ?: return null
    return provider.certifiedBrandAsset(
        "card-mark",
        if (surfaceLuminance < 0.5f) "dark" else "light",
    )
}
