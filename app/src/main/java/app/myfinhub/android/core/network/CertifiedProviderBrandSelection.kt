package app.myfinhub.android.core.network

import java.text.Collator
import java.util.Locale

/**
 * Web providerBrandAssets.ts resolution order, but only over origin-verified
 * owner asset references. No arbitrary logoUrl / wordmarkUrl fallback and
 * no download side effects; a missing certified image is a neutral fallback.
 */
internal fun FinancialProviderRecord.certifiedBrandAsset(
    role: String,
    tone: String,
): TrustedProviderAssetReference? {
    if (role !in setOf("logo", "wordmark", "card-mark") ||
        tone !in setOf("light", "dark")
    ) return null
    val opposite = if (tone == "light") "dark" else "light"
    fun rank(asset: TrustedProviderAssetReference): Int? = when {
        asset.variant == tone -> 0
        asset.variant.startsWith("$tone-") -> 1
        asset.variant == "universal" -> 2
        asset.variant == "default" -> 3
        asset.variant == opposite || asset.variant.startsWith("$opposite-") -> null
        else -> 4
    }
    fun bound(requestedRole: String): TrustedProviderAssetReference? =
        sequenceOf(tone, "universal").mapNotNull { variant ->
            trustedBindings.firstOrNull {
                it.role == requestedRole && it.variant == variant
            }?.let { binding ->
                trustedAssets.firstOrNull {
                    it.assetKey == binding.assetKey && it.role == requestedRole
                }
            }
        }.firstOrNull()
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    fun legacy(requestedRole: String): TrustedProviderAssetReference? =
        trustedAssets.filter { it.role == requestedRole && rank(it) != null }
            .sortedWith(Comparator { left, right ->
                val priority = rank(left)!!.compareTo(rank(right)!!)
                if (priority != 0) priority
                else {
                    val variant = collator.compare(left.variant, right.variant)
                    if (variant != 0) variant else collator.compare(left.assetKey, right.assetKey)
                }
            }).firstOrNull()

    fun byKey(requestedRole: String, key: String?): TrustedProviderAssetReference? =
        trustedAssets.firstOrNull { it.role == requestedRole && it.assetKey == key }

    fun logo(): TrustedProviderAssetReference? =
        bound("logo") ?: legacy("logo") ?: byKey("logo", logoAssetKey)
    fun wordmark(): TrustedProviderAssetReference? =
        bound("wordmark") ?: legacy("wordmark") ?:
            byKey("wordmark", wordmarkAssetKey) ?: logo()
    return when (role) {
        "logo" -> logo()
        "wordmark" -> wordmark()
        else -> bound("card-mark") ?: legacy("card-mark") ?: wordmark()
    }
}
