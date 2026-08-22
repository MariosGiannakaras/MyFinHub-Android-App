package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lossless canonical finance document.
 *
 * Android projections read from [raw] but writes retain every unknown field supplied by the shared
 * MyFinHub schema. The server remains the canonical source of truth.
 */
data class CanonicalFinanceDocument(
    val raw: JsonObject,
) {
    val schemaVersion: Int?
        get() = raw["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    val updatedAt: String
        get() = raw["updatedAt"]?.jsonPrimitive?.contentOrNull.orEmpty()

    val state: JsonObject
        get() = raw["state"]?.jsonObject ?: JsonObject(emptyMap())

    val seed: JsonObject
        get() = raw["seed"]?.jsonObject ?: JsonObject(emptyMap())

    fun accounts(): List<AccountProjection> {
        val accountDefinitions = seed.array("accounts")
        val snapshots = seed.array("snapshots")
        val latestBalances = snapshots.lastOrNull()?.jsonObject?.get("balances") as? JsonObject

        return accountDefinitions.mapNotNull { element ->
            val account = element as? JsonObject ?: return@mapNotNull null
            val id = account.string("id") ?: return@mapNotNull null
            AccountProjection(
                id = id,
                name = account.string("name") ?: id,
                kind = account.string("kind").orEmpty(),
                excludeFromAvailable = account.bool("excludeFromAvailable") ?: false,
                snapshotBalance = latestBalances?.get(id)?.jsonPrimitive?.doubleOrNull,
            )
        }
    }

    fun financeEvents(): List<FinanceEventProjection> = state.array("events").mapNotNull { element ->
        val event = element as? JsonObject ?: return@mapNotNull null
        FinanceEventProjection(
            id = event.string("id") ?: return@mapNotNull null,
            date = event.string("date").orEmpty(),
            kind = event.string("kind").orEmpty(),
            amount = event.number("amount") ?: 0.0,
            note = event.string("note").orEmpty(),
            category = event.string("category"),
            accountId = event.string("accountId"),
            fromAccountId = event.string("fromAccountId"),
            toAccountId = event.string("toAccountId"),
            cardId = event.string("cardId"),
        )
    }

    fun scheduled(): List<ScheduledProjection> = state.array("scheduled").mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        ScheduledProjection(
            id = item.string("id") ?: return@mapNotNull null,
            dueDate = item.string("dueDate").orEmpty(),
            kind = item.string("kind").orEmpty(),
            amount = item.number("amount") ?: 0.0,
            note = item.string("note").orEmpty(),
            status = item.string("status").orEmpty(),
        )
    }

    fun cards(): List<CardProjection> = state.array("cards").mapNotNull { element ->
        val card = element as? JsonObject ?: return@mapNotNull null
        CardProjection(
            id = card.string("id") ?: return@mapNotNull null,
            bankId = card.string("bankId").orEmpty(),
            nickname = card.string("nickname").orEmpty(),
            kind = card.string("kind").orEmpty(),
            network = card.string("network").orEmpty(),
            last4 = card.string("last4"),
            creditLimit = card.number("creditLimit"),
            active = card.bool("active") ?: false,
            vaultRef = card.string("vaultRef"),
        )
    }

    private fun JsonObject.array(name: String): JsonArray =
        (this[name] as? JsonArray) ?: JsonArray(emptyList())

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.number(name: String): Double? =
        this[name]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.bool(name: String): Boolean? =
        this[name]?.jsonPrimitive?.booleanOrNull
}

data class CanonicalFinanceEnvelope(
    val document: CanonicalFinanceDocument,
    val revision: String,
    val lastSavedAt: String,
)

data class CanonicalWriteReceipt(
    val revision: String,
    val lastSavedAt: String,
)

data class AccountProjection(
    val id: String,
    val name: String,
    val kind: String,
    val excludeFromAvailable: Boolean,
    val snapshotBalance: Double?,
)

data class FinanceEventProjection(
    val id: String,
    val date: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val category: String?,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val cardId: String?,
)

data class ScheduledProjection(
    val id: String,
    val dueDate: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val status: String,
)

data class CardProjection(
    val id: String,
    val bankId: String,
    val nickname: String,
    val kind: String,
    val network: String,
    val last4: String?,
    val creditLimit: Double?,
    val active: Boolean,
    val vaultRef: String?,
)
