package app.myfinhub.android.core.data

import kotlin.math.max
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Desktop-equivalent outstanding balance for one lending person. */
internal fun CanonicalFinanceDocument.lendingOutstandingForPerson(person: String): Double {
    val target = person.trim()
    if (target.isBlank()) return 0.0

    var outstanding = seed.entryArray("lending")
        .mapNotNull { it as? JsonObject }
        .filter { it.entryString("person")?.trim() == target }
        .sumOf { it.entryNumber("outstanding") ?: 0.0 }

    state.entryArray("events")
        .mapNotNull { it as? JsonObject }
        .filter { it.entryString("person")?.trim() == target }
        .forEach { event -> outstanding += event.entryNumber("receivableDelta") ?: 0.0 }

    return outstanding
}

/**
 * Desktop-equivalent card debt including pre-cardId legacy credit events assigned to the oldest
 * credit-card identity. New Android writes always carry cardId, but historical shared data may not.
 */
internal fun CanonicalFinanceDocument.creditDebtForCardAt(cardId: String, asOf: String): Double {
    val target = cardId.trim()
    if (target.isBlank()) return 0.0
    val legacyOwnerId = legacyCreditOwnerId()

    var debt = 0.0
    state.entryArray("events").mapNotNull { it as? JsonObject }.forEach { event ->
        val kind = event.entryString("kind").orEmpty()
        if (kind != "card_purchase" && kind != "card_payment") return@forEach
        if (event.entryString("date").orEmpty() > asOf) return@forEach

        val explicitCardId = event.entryString("cardId")?.trim().orEmpty()
        val belongsToTarget = if (explicitCardId.isNotBlank()) {
            explicitCardId == target
        } else {
            legacyOwnerId == target
        }
        if (!belongsToTarget) return@forEach

        val amount = event.entryNumber("amount") ?: 0.0
        debt += if (kind == "card_purchase") amount else -amount
    }
    return max(0.0, debt)
}

private fun CanonicalFinanceDocument.legacyCreditOwnerId(): String? {
    val identities = buildList {
        state.entryArray("cards").mapNotNull { it as? JsonObject }.forEach { card ->
            if (card.entryString("kind") != "credit") return@forEach
            val id = card.entryString("id")?.trim().orEmpty()
            if (id.isNotBlank()) add(id to card.entryString("createdAt").orEmpty())
        }
        state.entryArray("deletedCards").mapNotNull { it as? JsonObject }.forEach { card ->
            if (card.entryString("kind") != "credit") return@forEach
            val id = card.entryString("id")?.trim().orEmpty()
            if (id.isNotBlank()) add(id to card.entryString("createdAt").orEmpty())
        }
    }
    return identities.minWithOrNull(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })?.first
}

private fun JsonObject.entryArray(name: String): JsonArray =
    (this[name] as? JsonArray) ?: JsonArray(emptyList())

private fun JsonObject.entryString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.entryNumber(name: String): Double? =
    (this[name] as? JsonPrimitive)?.doubleOrNull
