package app.myfinhub.android.core.network

import java.net.URI
import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject

/** Safe owner-only provider identity metadata from Web GET /api/account-metadata.
 * Remote URL-bearing artwork is intentionally NOT loaded or rendered until host allowlisting
 * and actual asset-bound image loading have been reviewed; never persist external URLs.
 */
/** Untrusted provider URLs are never resolved as images without canonical origin verification.
 * toString redacts the public path to avoid unnecessary debug/diagnostic persistence.
 */
data class TrustedProviderAssetReference(
    val assetKey: String,
    val role: String,
    val variant: String,
    val url: String,
    val mimeType: String,
    val sizeBytes: Long?,
) {
    override fun toString(): String =
        "TrustedProviderAssetReference(assetKey=$assetKey, role=$role, variant=$variant, url=<redacted>)"
}

data class TrustedProviderAssetBinding(
    val role: String,
    val variant: String,
    val assetKey: String,
)

data class FinancialProviderRecord(
    val id: String,
    val displayName: String,
    val shortName: String,
    val providerKind: String,
    val countryCode: String?,
    val sortOrder: Int,
    val logoAssetKey: String?,
    val wordmarkAssetKey: String?,
    val trustedAssets: List<TrustedProviderAssetReference> = emptyList(),
    val trustedBindings: List<TrustedProviderAssetBinding> = emptyList(),
)

private val providerIdRegex = Regex("^[a-z][a-z0-9-]{0,63}$")
private val providerAssetKeyRegex = Regex("^[a-z][a-z0-9-]{0,95}$")
private val providerKinds = setOf("bank","fintech","wallet","payment")
private val providerAssetRoles = setOf("logo","wordmark","card-mark")
private val providerAssetVariants = setOf("universal","light","dark")
private val providerAssetMimeTypes =
    setOf("image/png","image/jpeg","image/webp","image/svg+xml")
private val providerAssetVariantRegex=Regex("^[a-z][a-z0-9-]{0,63}$")

/**
 * Only the configured Supabase HTTPS origin and its public provider asset bucket
 * may supply brand artwork. No arbitrary https domains, redirects, userinfo,
 * encoded traversal, foreign provider directories or opaque query parameters.
 */
internal fun trustedProviderAssetUrl(
    raw: String,
    providerId: String,
    configuredSupabaseOrigin: String,
): String? = runCatching {
    val base = URI(configuredSupabaseOrigin.removeSuffix("/"))
    val url = URI(raw)
    if (base.scheme != "https" || url.scheme != "https" ||
        base.host == null || url.host == null ||
        !base.host.equals(url.host, ignoreCase=true) ||
        (base.port.takeIf { it != -1 } ?: 443) != (url.port.takeIf { it != -1 } ?: 443) ||
        url.rawUserInfo != null || url.rawFragment != null ||
        base.rawUserInfo != null) return@runCatching null
    val prefix="/storage/v1/object/public/financial-provider-assets/providers/$providerId/"
    val path=url.rawPath ?: return@runCatching null
    if (!path.startsWith(prefix)) return@runCatching null
    val remainder=path.removePrefix(prefix)
    if (!remainder.matches(Regex("[A-Za-z0-9._/-]+")) ||
        remainder.split('/').any { it.isEmpty() || it=="." || it==".." }) return@runCatching null
    val query=url.rawQuery
    if (query != null && !query.matches(Regex("v=[A-Za-z0-9%._:-]{1,120}"))) return@runCatching null
    url.toASCIIString()
}.getOrNull()


/** Web financialProviderClient parseProvider, limited to explicit non-URL identity metadata. */
internal fun parseFinancialProviderCatalog(
    body: String,
    json: Json = Json,
    trustedSupabaseOrigin: String? = null,
): List<FinancialProviderRecord>? {
    val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val providers = payload["providers"] as? JsonArray ?: return null
    if (providers.isEmpty()) return null
    val parsed = providers.mapNotNull { raw ->
        val row = raw as? JsonObject ?: return@mapNotNull null
        fun value(name: String): String? = (row[name] as? JsonPrimitive)?.contentOrNull
        val id = value("id")?.trim()?.takeIf(providerIdRegex::matches) ?: return@mapNotNull null
        val displayName = value("displayName")?.trim()?.takeIf {
            it.isNotEmpty() && it.length <= 120
        } ?: return@mapNotNull null
        val shortName = value("shortName")?.trim()?.takeIf {
            it.isNotEmpty() && it.length <= 80
        } ?: return@mapNotNull null
        val kind = value("providerKind")?.takeIf(providerKinds::contains) ?: return@mapNotNull null
        val sortOrder = (row["sortOrder"] as? JsonPrimitive)?.intOrNull
            ?: return@mapNotNull null
        val country = value("countryCode")?.trim()?.takeIf(String::isNotBlank)
        if (country != null && !country.matches(Regex("^[A-Z]{2}$"))) return@mapNotNull null
        fun assetKey(key: String): String? {
            val value = value(key)?.trim()?.takeIf(String::isNotEmpty) ?: return null
            return value.takeIf(providerAssetKeyRegex::matches)
        }
        val logo = assetKey("logoAssetKey")
        val wordmark = assetKey("wordmarkAssetKey")
        if (value("logoAssetKey")?.isNotBlank() == true && logo == null) return@mapNotNull null
        if (value("wordmarkAssetKey")?.isNotBlank() == true && wordmark == null) return@mapNotNull null
        // Owner identity metadata remains safe even in preview/unit parsing without
        // an origin. Production requests supply BuildConfig's trusted Supabase URL.
        val trustedAssets = mutableListOf<TrustedProviderAssetReference>()
        val trustedBindings = mutableListOf<TrustedProviderAssetBinding>()
        if (trustedSupabaseOrigin != null) {
            val rawAssets = row["assets"] as? JsonArray
                ?: if (row["assets"] == null) JsonArray(emptyList()) else return@mapNotNull null
            if (rawAssets.size > 200) return@mapNotNull null
            for (rawAsset in rawAssets) {
                val asset = rawAsset as? JsonObject ?: return@mapNotNull null
                fun a(name: String) = (asset[name] as? JsonPrimitive)?.contentOrNull
                val key = a("assetKey")?.takeIf(providerAssetKeyRegex::matches)
                    ?: return@mapNotNull null
                val role = a("role")?.takeIf(providerAssetRoles::contains)
                    ?: return@mapNotNull null
                val variant = a("variant")?.takeIf(providerAssetVariantRegex::matches)
                    ?: return@mapNotNull null
                val mimeType = a("mimeType")?.takeIf(providerAssetMimeTypes::contains)
                    ?: return@mapNotNull null
                val rawUrl=a("url") ?: return@mapNotNull null
                val url=trustedProviderAssetUrl(rawUrl,id,trustedSupabaseOrigin)
                    ?: return@mapNotNull null
                val sizeBytes=(asset["sizeBytes"] as? JsonPrimitive)?.longOrNull
                if (asset["sizeBytes"] != null &&
                    asset["sizeBytes"] !is kotlinx.serialization.json.JsonNull &&
                    (sizeBytes == null || sizeBytes < 0 || sizeBytes > 2_097_152))
                    return@mapNotNull null
                trustedAssets += TrustedProviderAssetReference(key,role,variant,url,mimeType,sizeBytes)
            }
            val assetKeys=trustedAssets.map { it.assetKey }.toSet()
            val rawBindings = row["bindings"] as? JsonArray
                ?: if (row["bindings"] == null) JsonArray(emptyList()) else return@mapNotNull null
            if (rawBindings.size > 200) return@mapNotNull null
            for (rawBinding in rawBindings) {
                val binding=rawBinding as? JsonObject ?: return@mapNotNull null
                fun b(name: String)=(binding[name] as? JsonPrimitive)?.contentOrNull
                val role=b("role")?.takeIf(providerAssetRoles::contains)
                    ?: return@mapNotNull null
                val variant=b("variant")?.takeIf(providerAssetVariants::contains)
                    ?: return@mapNotNull null
                val assetKey=b("assetKey")?.takeIf(assetKeys::contains)
                    ?: return@mapNotNull null
                trustedBindings += TrustedProviderAssetBinding(role,variant,assetKey)
            }
        }
        FinancialProviderRecord(
            id,displayName,shortName,kind,country,sortOrder,logo,wordmark,
            trustedAssets,trustedBindings,
        )
    }
    // The Web's localeCompare('el') applies within equal sortOrder groups.
    // Lowercase Unicode ordinal comparison misorders accented Greek providers.
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    return parsed.distinctBy(FinancialProviderRecord::id)
        .sortedWith(Comparator { left, right ->
            val priority = left.sortOrder.compareTo(right.sortOrder)
            if (priority != 0) priority
            else collator.compare(left.displayName, right.displayName)
        })
        .takeIf { it.isNotEmpty() }
}
