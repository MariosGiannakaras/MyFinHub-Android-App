package app.myfinhub.android.core.data

import kotlin.math.max
import kotlin.math.round
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

private fun roundCreditMoney(value: Double): Double = round(value * 100.0) / 100.0

/**
 * Statement linkage is authoritative from the canonical event JSON. The existing
 * canonicalEvents parser owns money/event semantics; this projection adds only
 * statementId linkage without creating a duplicate event model or finance ledger.
 */
fun CanonicalFinanceDocument.creditStatementViews(
    cardId: String,
    asOf: String,
): List<CanonicalCreditStatementView> {
    val linkedStatementIds = state.array("events")
        .mapNotNull { raw ->
            val event = raw as? JsonObject ?: return@mapNotNull null
            val id = event.string("id") ?: return@mapNotNull null
            val linkedId = event.string("statementId") ?: return@mapNotNull null
            id to linkedId
        }
        .toMap()
    val events = canonicalEvents().filter { event ->
        event.date <= asOf && event.kind in setOf("card_purchase", "card_payment") &&
            linkedStatementIds.containsKey(event.id)
    }.groupBy { event -> linkedStatementIds[event.id].orEmpty() }

    return state.array("creditStatements").mapNotNull { raw ->
        val record = raw as? JsonObject ?: return@mapNotNull null
        val id = record.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val linkedCard = record.string("cardId") ?: return@mapNotNull null
        if (cardId != linkedCard) return@mapNotNull null
        val openDate = record.string("openDate") ?: return@mapNotNull null
        val closeDate = record.string("closeDate") ?: return@mapNotNull null
        val dueDate = record.string("dueDate") ?: return@mapNotNull null
        val associated = events[id].orEmpty()
        val purchases = associated.filter { it.kind == "card_purchase" }
        val payments = associated.filter { it.kind == "card_payment" }
        val purchaseTotal = roundCreditMoney(purchases.sumOf { it.amount })
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
            e.string("kind") !in setOf("card_purchase", "card_payment") ||
            e.string("statementId") != null) return@mapNotNull null
        e.string("id")
    }
