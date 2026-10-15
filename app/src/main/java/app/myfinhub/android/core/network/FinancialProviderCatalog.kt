package app.myfinhub.android.core.network

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Safe owner-only provider identity metadata from Web GET /api/account-metadata.
 * Remote URL-bearing artwork is intentionally NOT loaded or rendered until host allowlisting
 * and actual asset-bound image loading have been reviewed; never persist external URLs.
 */
data class FinancialProviderRecord(
    val id: String,
    val displayName: String,
    val shortName: String,
    val providerKind: String,
    val countryCode: String?,
    val sortOrder: Int,
    val logoAssetKey: String?,
    val wordmarkAssetKey: String?,
)

private val providerIdRegex = Regex("^[a-z][a-z0-9-]{0,63}$")
private val providerAssetKeyRegex = Regex("^[a-z][a-z0-9-]{0,95}$")
private val providerKinds = setOf("bank","fintech","wallet","payment")

/** Web financialProviderClient parseProvider, limited to explicit non-URL identity metadata. */
internal fun parseFinancialProviderCatalog(body: String, json: Json = Json): List<FinancialProviderRecord>? {
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
        FinancialProviderRecord(id,displayName,shortName,kind,country,sortOrder,logo,wordmark)
    }
    return parsed.distinctBy(FinancialProviderRecord::id)
        .sortedWith(compareBy<FinancialProviderRecord> { it.sortOrder }
            .thenBy { it.displayName.lowercase(Locale.forLanguageTag("el-GR")) })
        .takeIf { it.isNotEmpty() }
}
