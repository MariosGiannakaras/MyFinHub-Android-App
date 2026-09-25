package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Safe structural validation for the known Android-read canonical fields. Unknown fields are ignored. */
data class CanonicalIntegrityIssue(val code: String)

object CanonicalDataIntegrity {
    private const val MAX_ABSOLUTE_MONEY = 90_000_000_000_000.0
    private val revisionRegex = Regex("^(0|[1-9]\\d*)$")

    fun validateEnvelope(envelope: CanonicalFinanceEnvelope): CanonicalIntegrityIssue? {
        if (!revisionRegex.matches(envelope.revision)) return CanonicalIntegrityIssue("INVALID_REVISION")
        return validateDocument(envelope.document)
    }

    fun validateDocument(document: CanonicalFinanceDocument): CanonicalIntegrityIssue? {
        val state = document.raw["state"] as? JsonObject
            ?: return CanonicalIntegrityIssue("MISSING_STATE_OBJECT")
        val seedElement = document.raw["seed"]
        val seed = when (seedElement) {
            null -> JsonObject(emptyMap())
            is JsonObject -> seedElement
            else -> return CanonicalIntegrityIssue("INVALID_SEED_OBJECT")
        }

        validateIdentityCollection(seed, "accounts", requireId = true)?.let { return it }
        validateIdentityCollection(seed, "transactions", requireId = true)?.let { return it }
        validateIdentityCollection(seed, "recurring", requireId = true)?.let { return it }
        validateIdentityCollection(state, "events", requireId = true)?.let { return it }
        validateIdentityCollection(state, "scheduled", requireId = true)?.let { return it }
        validateIdentityCollection(state, "cards", requireId = true)?.let { return it }
        validateIdentityCollection(state, "customTransactions", requireId = true)?.let { return it }
        validateIdentityCollection(state, "budgets", requireId = false)?.let { return it }

        validateDateCollection(seed, "transactions", "date")?.let { return it }
        validateOptionalDateCollection(seed, "recurring", "firstExpectedDate")?.let { return it }
        validateDateCollection(state, "events", "date")?.let { return it }
        validateDateCollection(state, "scheduled", "dueDate")?.let { return it }
        validateDateCollection(state, "customTransactions", "date")?.let { return it }
        validateMonthCollection(state, "budgets", "month")?.let { return it }

        validateMoneyCollection(seed, "transactions", setOf("amount"))?.let { return it }
        validateMoneyCollection(seed, "recurring", setOf("amount"))?.let { return it }
        validateMoneyCollection(state, "scheduled", setOf("amount"))?.let { return it }
        validateMoneyCollection(state, "customTransactions", setOf("amount"))?.let { return it }
        validateMoneyCollection(state, "cards", setOf("creditLimit"), nullable = setOf("creditLimit"))?.let { return it }
        validateMoneyCollection(state, "budgets", setOf("amount"))?.let { return it }
        validateEventMoney(state)?.let { return it }
        validateSnapshotBalances(seed)?.let { return it }

        return null
    }

    private fun validateIdentityCollection(
        owner: JsonObject,
        name: String,
        requireId: Boolean,
    ): CanonicalIntegrityIssue? {
        val raw = owner[name] ?: return null
        val array = raw as? JsonArray ?: return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_ARRAY")
        val seen = mutableSetOf<String>()
        array.forEach { element ->
            val item = element as? JsonObject
                ?: return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_ITEM")
            val id = (item["id"] as? JsonPrimitive)?.contentOrNull?.trim()
            if (requireId && id.isNullOrBlank()) return CanonicalIntegrityIssue("MISSING_${name.uppercase()}_ID")
            if (!id.isNullOrBlank() && !seen.add(id)) return CanonicalIntegrityIssue("DUPLICATE_${name.uppercase()}_ID")
        }
        return null
    }

    private fun validateDateCollection(owner: JsonObject, name: String, field: String): CanonicalIntegrityIssue? =
        validateOptionalDateCollection(owner, name, field, requireValue = false)

    private fun validateOptionalDateCollection(
        owner: JsonObject,
        name: String,
        field: String,
        requireValue: Boolean = false,
    ): CanonicalIntegrityIssue? {
        val raw = owner[name] ?: return null
        val array = raw as? JsonArray ?: return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_ARRAY")
        array.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val value = (item[field] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (value.isBlank()) {
                if (requireValue) return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_${field.uppercase()}")
                return@forEach
            }
            if (runCatching { LocalDate.parse(value.take(10)) }.isFailure) {
                return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_${field.uppercase()}")
            }
        }
        return null
    }

    private fun validateMonthCollection(owner: JsonObject, name: String, field: String): CanonicalIntegrityIssue? {
        val raw = owner[name] ?: return null
        val array = raw as? JsonArray ?: return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_ARRAY")
        array.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val value = (item[field] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (value.isNotBlank() && runCatching { YearMonth.parse(value) }.isFailure) {
                return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_${field.uppercase()}")
            }
        }
        return null
    }

    private fun validateMoneyCollection(
        owner: JsonObject,
        name: String,
        fields: Set<String>,
        nullable: Set<String> = emptySet(),
    ): CanonicalIntegrityIssue? {
        val raw = owner[name] ?: return null
        val array = raw as? JsonArray ?: return CanonicalIntegrityIssue("INVALID_${name.uppercase()}_ARRAY")
        array.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            fields.forEach { field ->
                validateMoney(item[field], "${name.uppercase()}_${field.uppercase()}", field in nullable)?.let { return it }
            }
        }
        return null
    }

    private fun validateEventMoney(state: JsonObject): CanonicalIntegrityIssue? {
        val raw = state["events"] ?: return null
        val events = raw as? JsonArray ?: return CanonicalIntegrityIssue("INVALID_EVENTS_ARRAY")
        events.forEach { element ->
            val event = element as? JsonObject ?: return@forEach
            setOf("amount", "savingAmount", "receivableDelta", "creditDelta").forEach { field ->
                if (field == "amount" || event.containsKey(field)) {
                    validateMoney(event[field], "EVENTS_${field.uppercase()}")?.let { return it }
                }
            }
            listOf("legs", "parts").forEach { childName ->
                val child = event[childName] ?: return@forEach
                val items = child as? JsonArray ?: return CanonicalIntegrityIssue("INVALID_EVENTS_${childName.uppercase()}")
                items.forEach { rawChild ->
                    val childObject = rawChild as? JsonObject ?: return CanonicalIntegrityIssue("INVALID_EVENTS_${childName.uppercase()}_ITEM")
                    validateMoney(childObject["amount"], "EVENTS_${childName.uppercase()}_AMOUNT")?.let { return it }
                }
            }
        }
        return null
    }

    private fun validateSnapshotBalances(seed: JsonObject): CanonicalIntegrityIssue? {
        val raw = seed["snapshots"] ?: return null
        val snapshots = raw as? JsonArray ?: return CanonicalIntegrityIssue("INVALID_SNAPSHOTS_ARRAY")
        snapshots.forEach { element ->
            val snapshot = element as? JsonObject ?: return@forEach
            val balances = snapshot["balances"] as? JsonObject ?: return@forEach
            balances.values.forEach { value ->
                validateMoney(value, "SNAPSHOT_BALANCE")?.let { return it }
            }
        }
        return null
    }

    private fun validateMoney(
        element: kotlinx.serialization.json.JsonElement?,
        code: String,
        nullable: Boolean = false,
    ): CanonicalIntegrityIssue? {
        if (element == null || element is JsonNull) {
            return if (nullable) null else CanonicalIntegrityIssue("INVALID_$code")
        }
        val value = (element as? JsonPrimitive)?.doubleOrNull
            ?: return CanonicalIntegrityIssue("INVALID_$code")
        return if (!value.isFinite() || kotlin.math.abs(value) > MAX_ABSOLUTE_MONEY) {
            CanonicalIntegrityIssue("INVALID_$code")
        } else {
            null
        }
    }
}
