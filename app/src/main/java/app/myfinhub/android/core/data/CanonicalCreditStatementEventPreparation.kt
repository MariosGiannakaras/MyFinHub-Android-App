package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Port of Web creditStatements.ts prepareCreditStatementEvent. */
data class PreparedCanonicalCreditStatementEvent(
    val event: JsonObject,
    val statements: List<kotlinx.serialization.json.JsonElement>,
)

/**
 * Preparation is pure, and happens when appending to the canonical event collection.
 * A valid explicit statement link is preserved; invalid/cross-card links are removed.
 * An unlinked credit purchase creates (or reuses) a deterministic billing cycle.
 * An unlinked payment stays unlinked, never silently spending money on a guessed cycle.
 */
fun CanonicalFinanceDocument.prepareCanonicalCreditStatementEvent(
    event: JsonObject,
    nowIso: String,
): PreparedCanonicalCreditStatementEvent {
    val statements = state.array("creditStatements")
    val kind = event.string("kind")
    val isCreditPurchase = canonicalStatementPurchaseAmount(event) > 0.005
    val cardId = event.string("cardId")?.takeIf(String::isNotBlank)
    // JS if(event.statementId) treats whitespace-only IDs as explicit links.
    // Never generate a new billing cycle when such a malformed link fails lookup.
    val explicitId = event.string("statementId")?.takeIf(String::isNotEmpty)
    if (explicitId != null) {
        // Web prepareCreditStatementEvent uses Array.find(id), not any(id, card).
        // Ambiguous historical IDs are resolved by their first physical record;
        // a later duplicate owned by this card must not authorize a cross-card link.
        val first = statements.firstOrNull { (it as? JsonObject)?.string("id") == explicitId }
            as? JsonObject
        val valid = (isCreditPurchase || kind == "card_payment") &&
            cardId != null && first?.string("cardId") == cardId
        return PreparedCanonicalCreditStatementEvent(
            if (valid) event else JsonObject(event - "statementId"), statements
        )
    }
    if (!isCreditPurchase || cardId == null) {
        return PreparedCanonicalCreditStatementEvent(event, statements)
    }
    val card = canonicalCards().firstOrNull { it.id == cardId && it.kind == "credit" }
        ?: return PreparedCanonicalCreditStatementEvent(event, statements)
    val configuration = card.statementConfiguration()
        ?: return PreparedCanonicalCreditStatementEvent(event, statements)
    val date = LocalDate.parse(event.string("date")
        ?: throw IllegalArgumentException("Card purchase must have a valid date."))
    val close = statementCloseDateForPurchase(
        date, configuration.closingDay, configuration.boundaryRule)
    val id = canonicalCreditStatementId(cardId, close)
    val current = statements.filterIsInstance<JsonObject>().firstOrNull { it.string("id") == id }
    // Duplicate IDs owned by a different card indicate corrupted canonical data.
    // Never attribute a new purchase to another card's statement.
    if (current != null && current.string("cardId") != cardId) {
        return PreparedCanonicalCreditStatementEvent(event, statements)
    }
    val record = current ?: JsonObject(mapOf(
        "id" to JsonPrimitive(id),
        "cardId" to JsonPrimitive(cardId),
        "openDate" to JsonPrimitive(statementOpenDateForClose(
            close, configuration.closingDay, configuration.boundaryRule).toString()),
        "closeDate" to JsonPrimitive(close.toString()),
        "dueDate" to JsonPrimitive(statementDueDateForClose(
            close, configuration.dueDay).toString()),
        "boundaryRule" to JsonPrimitive(configuration.boundaryRule),
        "createdAt" to JsonPrimitive(nowIso),
        "updatedAt" to JsonPrimitive(nowIso),
    ))
    return PreparedCanonicalCreditStatementEvent(
        event.updated("statementId", JsonPrimitive(id)),
        if (current == null) statements + record else statements,
    )
}
