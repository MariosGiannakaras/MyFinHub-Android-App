package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Non-authoritative, read-only backup preview. Structural inspection is NOT Web/server
 * schema validation and never grants permission to restore or replace finance data.
 */
data class CanonicalBackupInspection(
    val appIdentity: String,
    val schemaVersion: String,
    val updatedAt: String,
    val accounts: Int,
    val legacyTransactions: Int,
    val events: Int,
    val cards: Int,
    val budgets: Int,
    val recurring: Int,
    val scheduled: Int,
)

fun inspectCanonicalFinanceBackupJson(contents: String): CanonicalBackupInspection {
    val root = Json.parseToJsonElement(contents) as? JsonObject
        ?: throw IllegalArgumentException("JSON root is not a finance object")
    val appIdentity = (root["app"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val schema = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val seed = root["seed"] as? JsonObject
        ?: throw IllegalArgumentException("Missing seed object")
    val state = root["state"] as? JsonObject
        ?: throw IllegalArgumentException("Missing state object")
    require(appIdentity.isNotBlank() && appIdentity.length <= 100) {
        "Invalid finance document identity"
    }
    require(schema.toIntOrNull()?.let { it > 0 } == true) {
        "Invalid schema version"
    }
    val updated = (root["updatedAt"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun count(source: JsonObject, field: String): Int =
        (source[field] as? JsonArray)?.size ?: 0
    return CanonicalBackupInspection(
        appIdentity = appIdentity,
        schemaVersion = schema,
        updatedAt = updated,
        accounts = count(seed, "accounts"),
        legacyTransactions = count(seed, "transactions"),
        events = count(state, "events"),
        cards = count(state, "cards"),
        budgets = count(state, "budgets"),
        recurring = count(seed, "recurring") + count(state, "recurringCustom"),
        scheduled = count(state, "scheduled"),
    )
}
