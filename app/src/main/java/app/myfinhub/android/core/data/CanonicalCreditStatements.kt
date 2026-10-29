package app.myfinhub.android.core.data

import kotlin.math.max
import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Read-only Web creditStatementViews / recommendedPayableStatement semantics. */
data class CanonicalCreditStatementView(
    val id: String,
    val cardId: String,
    val openDate: String,
    val closeDate: String,
    val dueDate: String,
    val boundaryRule: String,
    val purchaseIds: List<String>,
    val paymentIds: List<String>,
    val purchaseTotal: Double,
    val paymentTotal: Double,
    val remaining: Double,
    val status: String,
)

/**
 * Source identity is counted from every physical canonical statement row, including
 * other cards and malformed rows omitted by the read-only view projection.
 * A locally unique selected-card view cannot authorize a shared financial ID.
 */
internal fun CanonicalFinanceDocument.canonicalStatementSourceIdCounts(): Map<String, Int> =
    state.array("creditStatements")
        .mapNotNull { (it as? JsonObject)?.string("id") }
        .groupingBy { it }.eachCount()

/**
 * The Web statement domain includes mixed-funding `expense` events as credit
 * purchases for their credit-funded portion only (not the entire expense).
 * A plain debit expense never becomes a statement charge.
 */
internal fun canonicalStatementPurchaseAmount(event: JsonObject): Double = when (event.string("kind")) {
    "card_purchase" -> event.number("amount") ?: 0.0
    "expense" -> if (event["funding"] is JsonArray) max(0.0, -(event.number("creditDelta") ?: 0.0)) else 0.0
    else -> 0.0
}

/**
 * Mirrors Web creditStatements.ts roundMoney:
 * Math.round((value + Number.EPSILON) * 100) / 100.
 * Kotlin round() uses ties-to-even, unlike JavaScript Math.round().
 * This is a read-only historical projection; it does not loosen write validation.
 */
private fun roundCreditMoney(value: Double): Double =
    floor((value + Math.ulp(1.0)) * 100.0 + 0.5) / 100.0

/**
 * Statement linkage is authoritative from the canonical event JSON. The existing
 * canonicalEvents parser owns money/event semantics; this projection adds only
 * statementId linkage without creating a duplicate event model or finance ledger.
 */
fun CanonicalFinanceDocument.creditStatementViews(
    cardId: String,
    asOf: String,
): List<CanonicalCreditStatementView> {
    // Preserve the single canonical event parser for money/kind semantics, but match Web's
    // creditStatementEvents ordering: date, createdAt, ID. The JSON array is not a timeline.
    // Match each parsed canonical event with its *physical* JSON source row.
    // Historical imports can repeat event IDs. An id->JSON map would silently
    // substitute the last duplicate's statement, amount and createdAt for every
    // earlier row. Web creditStatementEvents operates directly on the array.
    val rawEvents = state.array("events")
        .mapNotNull { it as? JsonObject }
        .filter { it.string("id") != null }
    val events = canonicalEvents().zip(rawEvents)
        .filter { (event, raw) ->
            event.date <= asOf &&
                (event.kind == "card_payment" || canonicalStatementPurchaseAmount(raw) > 0.005) &&
                raw.string("statementId") != null
        }.sortedWith(
            compareBy<Pair<CanonicalEvent, JsonObject>> { it.first.date }
                .thenBy { it.second.string("createdAt").orEmpty() }
                .thenBy { it.first.id },
        ).groupBy { (_, raw) -> raw.string("statementId").orEmpty() }

    return state.array("creditStatements").mapNotNull { raw ->
        val record = raw as? JsonObject ?: return@mapNotNull null
        val id = record.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val linkedCard = record.string("cardId") ?: return@mapNotNull null
        if (cardId != linkedCard) return@mapNotNull null
        val openDate = record.string("openDate") ?: return@mapNotNull null
        val closeDate = record.string("closeDate") ?: return@mapNotNull null
        val dueDate = record.string("dueDate") ?: return@mapNotNull null
        val associated = events[id].orEmpty()
        val purchases = associated.filter { (_, raw) ->
            canonicalStatementPurchaseAmount(raw) > 0.005
        }
        val payments = associated.filter { (event, _) -> event.kind == "card_payment" }
        val purchaseTotal = roundCreditMoney(purchases.sumOf { (_, raw) ->
            canonicalStatementPurchaseAmount(raw)
        })
        val paymentTotal = roundCreditMoney(payments.sumOf { (event, _) -> event.amount })
        val remaining = roundCreditMoney(max(0.0, purchaseTotal - paymentTotal))
        val status = when {
            remaining <= 0.005 -> "paid"
            asOf < closeDate -> "open"
            asOf >= dueDate -> "due"
            else -> "closed"
        }
        CanonicalCreditStatementView(
            id = id, cardId = linkedCard, openDate = openDate, closeDate = closeDate,
            dueDate = dueDate, boundaryRule = record.string("boundaryRule").orEmpty(),
            purchaseIds = purchases.map { it.first.id }, paymentIds = payments.map { it.first.id },
            purchaseTotal = purchaseTotal, paymentTotal = paymentTotal,
            remaining = remaining, status = status,
        )
    }.sortedWith(compareByDescending<CanonicalCreditStatementView> { it.closeDate }.thenByDescending { it.id })
}

/** Web priority: due > closed > open; paid statements are not payment candidates. */
fun CanonicalFinanceDocument.recommendedPayableStatement(
    cardId: String,
    asOf: String,
): CanonicalCreditStatementView? {
    val priority = mapOf("due" to 0, "closed" to 1, "open" to 2, "paid" to 3)
    return creditStatementViews(cardId, asOf)
        .filter { it.remaining > 0.005 }
        .minWithOrNull(compareBy<CanonicalCreditStatementView> {
            priority[it.status] ?: 3
        }.thenBy { it.dueDate }.thenBy { it.closeDate })
}

/** Web calls these events out rather than fabricating statement assignment. */
fun CanonicalFinanceDocument.unlinkedCreditStatementEventIds(cardId: String): List<String> =
    state.array("events").mapNotNull { raw ->
        val e = raw as? JsonObject ?: return@mapNotNull null
        if (e.string("cardId") != cardId ||
            (e.string("kind") != "card_payment" && canonicalStatementPurchaseAmount(e) <= 0.005) ||
            !e.string("statementId").isNullOrEmpty()) return@mapNotNull null
        e.string("id")
    }
