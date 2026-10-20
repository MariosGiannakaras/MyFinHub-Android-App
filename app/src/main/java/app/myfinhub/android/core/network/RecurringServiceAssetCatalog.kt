package app.myfinhub.android.core.network

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject

/** Web /api/account-metadata?resource=recurring-service-assets: no image bytes or secrets. */
data class RecurringServiceAssetRecord(
    val assetKey: String,
    val recurringId: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val updatedAt: String,
) {
    /** URLs and private finance identities must not leak through diagnostics. */
    override fun toString() = "RecurringServiceAssetRecord(assetKey=<redacted>, url=<redacted>)"
}

private val recurringAssetKeyRegex = Regex("^[a-z][a-z0-9-]{0,95}$")
private val recurringAssetSuffix = Regex("^service-asset-[a-f0-9]{24}\\.(?:png|jpg|webp|svg)$")
private val recurringMimeTypes = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "webp" to "image/webp",
    "svg" to "image/svg+xml",
)

/**
 * Exact Supabase public storage path only, not just any HTTPS URL.
 * Rejects cross-host/userinfo/ports, percent-encoded traversal, fragments,
 * arbitrary query keys and filename/path mismatch. Public URLs stay volatile.
 */
internal fun trustedRecurringServiceAssetUrl(
    raw: String,
    trustedSupabaseOrigin: String,
): String? = runCatching {
    val base = URI(trustedSupabaseOrigin.trimEnd('/'))
    val url = URI(raw)
    val basePort = if (base.port < 0) 443 else base.port
    val urlPort = if (url.port < 0) 443 else url.port
    if (base.scheme != "https" || url.scheme != "https" ||
        base.host == null || url.host == null ||
        !base.host.equals(url.host, ignoreCase = true) ||
        basePort != urlPort ||
        base.rawUserInfo != null || url.rawUserInfo != null ||
        url.rawFragment != null) return@runCatching null
    val prefix = "/storage/v1/object/public/recurring-service-assets/services/"
    val path = url.rawPath ?: return@runCatching null
    if (!path.startsWith(prefix)) return@runCatching null
    val file = path.removePrefix(prefix)
    if (!recurringAssetSuffix.matches(file)) return@runCatching null
    val query = url.rawQuery
    if (query != null && !query.matches(Regex("v=[A-Za-z0-9%._:-]{1,120}"))) {
        return@runCatching null
    }
    url.toASCIIString()
}.getOrNull()

/**
 * Validated, scoped owner+AAL2 metadata list. Empty arrays are genuine no-asset
 * states; malformed or foreign-provider records fail closed rather than
 * displaying attacker-controlled service logos.
 */
internal fun parseRecurringServiceAssetCatalog(
    body: String,
    trustedSupabaseOrigin: String,
    json: Json = Json,
): List<RecurringServiceAssetRecord>? {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return null
    val raw = root["assets"] as? JsonArray ?: return null
    if (raw.size > 300) return null
    val result = mutableListOf<RecurringServiceAssetRecord>()
    val keys = mutableSetOf<String>()
    for (element in raw) {
        val o = element as? JsonObject ?: return null
        fun str(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
        val assetKey = str("assetKey")?.takeIf(recurringAssetKeyRegex::matches) ?: return null
        if (!keys.add(assetKey)) return null
        val recurringId = str("recurringId")?.takeIf {
            it.isNotBlank() && it.length <= 200
        } ?: return null
        // Web retains the user's ORIGINAL file name, independent of the
        // generated service-asset-<uuid> storage basename.
        val fileName = str("fileName")?.takeIf {
            it.isNotBlank() && it.length <= 255 &&
                '/' !in it && '\\' !in it && it.none(Char::isISOControl)
        } ?: return null
        val rawUrl = str("url") ?: return null
        val url = trustedRecurringServiceAssetUrl(rawUrl,trustedSupabaseOrigin) ?: return null
        val urlFile = URI(url).rawPath.substringAfterLast('/')
        val suffix = urlFile.substringAfterLast('.')
        if (urlFile.substringBeforeLast('.') != assetKey) return null
        val mime = str("mimeType")?.takeIf { it == recurringMimeTypes[suffix] }
            ?: return null
        val size = (o["sizeBytes"] as? JsonPrimitive)?.longOrNull
            ?.takeIf { it in 1..2_097_152 } ?: return null
        val updatedAt = str("updatedAt")?.takeIf {
            it.length in 1..80
        } ?: return null
        result += RecurringServiceAssetRecord(
            assetKey,recurringId,url,fileName,mime,size,updatedAt,
        )
    }
    return result
}
