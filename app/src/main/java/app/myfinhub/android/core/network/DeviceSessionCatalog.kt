package app.myfinhub.android.core.network

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Web deviceSessionsHandler.ts owner+AAL2 DTO; read-only, never an auth credential. */
data class DeviceSessionRecord(
    val sessionId: String,
    val platform: String,
    val label: String,
    val appVersion: String?,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val current: Boolean,
)

private val deviceSessionId = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE,
)

internal fun parseDeviceSessions(body: String, json: Json = Json): List<DeviceSessionRecord>? {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val rows = root["devices"] as? JsonArray ?: return null
    val count = (root["count"] as? JsonPrimitive)?.intOrNull ?: return null
    if (count != rows.size || count < 0 || count > 1000) return null
    return rows.map { item ->
        val row = item as? JsonObject ?: return null
        fun text(key: String, limit: Int): String? =
            (row[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf {
                it.isNotBlank() && it.length <= limit &&
                    it.none { c -> c.code < 32 || c.code == 127 }
            }
        val id = text("sessionId", 36)?.takeIf(deviceSessionId::matches) ?: return null
        val platform = text("platform", 80) ?: return null
        val label = text("label", 160) ?: return null
        val version = text("appVersion", 80)
        val first = text("firstSeenAt", 50) ?: return null
        val last = text("lastSeenAt", 50) ?: return null
        if (runCatching { Instant.parse(first) }.isFailure ||
            runCatching { Instant.parse(last) }.isFailure) return null
        val current = (row["current"] as? JsonPrimitive)?.booleanOrNull ?: return null
        DeviceSessionRecord(id, platform, label, version, first, last, current)
    }.takeIf { parsed -> parsed.distinctBy(DeviceSessionRecord::sessionId).size == parsed.size }
}
