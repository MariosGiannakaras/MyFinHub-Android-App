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
    val eventRecords = state.array("events").mapNotNull { it as? JsonObject }
        .mapNotNull { raw -> raw.string("id")?.let { it to raw } }.toMap()
    val eventMetadata = eventRecords.mapValues { (_, raw) ->
        raw.string("statementId") to raw.string("createdAt").orEmpty()
    }
    val events = canonicalEvents().filter { event ->
        event.date <= asOf &&
            (event.kind == "card_payment" ||
                (eventRecords[event.id]?.let(::canonicalStatementPurchaseAmount) ?: 0.0) > 0.005) &&
            eventMetadata[event.id]?.first != null
    }.sortedWith(
        compareBy<CanonicalEvent> { it.date }
            .thenBy { eventMetadata[it.id]?.second.orEmpty() }
            .thenBy(CanonicalEvent::id),
    ).groupBy { event -> eventMetadata[event.id]?.first.orEmpty() }

    return state.array("creditStatements").mapNotNull { raw ->
        val record = raw as? JsonObject ?: return@mapNotNull null
        val id = record.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val linkedCard = record.string("cardId") ?: return@mapNotNull null
        if (cardId != linkedCard) return@mapNotNull null
        val openDate = record.string("openDate") ?: return@mapNotNull null
        val closeDate = record.string("closeDate") ?: return@mapNotNull null
        val dueDate = record.string("dueDate") ?: return@mapNotNull null
        val associated = events[id].orEmpty()
        val purchases = associated.filter {
            (eventRecords[it.id]?.let(::canonicalStatementPurchaseAmount) ?: 0.0) > 0.005
        }
        val payments = associated.filter { it.kind == "card_payment" }
        val purchaseTotal = roundCreditMoney(purchases.sumOf {
            eventRecords[it.id]?.let(::canonicalStatementPurchaseAmount) ?: 0.0
        })
        val paymentTotal = roundCreditMoney(payments.sumOf { it.amount })
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
            purchaseIds = purchases.map { it.id }, paymentIds = payments.map { it.id },
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
