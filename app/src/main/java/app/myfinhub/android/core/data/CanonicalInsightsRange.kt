package app.myfinhub.android.core.data

import kotlin.math.max
import kotlinx.serialization.json.JsonObject

/**
 * Read-only date-range analytics using the same legacy/review/event semantics as monthlyFlow and
 * categoryTotals. Inclusive ISO date bounds keep equivalent-period Insights comparisons honest.
 */
fun CanonicalFinanceDocument.flowBetween(
    startInclusive: String,
    endInclusive: String,
): CanonicalMonthlyFlow {
    if (endInclusive < startInclusive) return CanonicalMonthlyFlow(0.0, 0.0, 0.0, 0.0)

    var aggregate = FlowImpact()
    effectiveLegacyTransactions()
        .filter { it.date in startInclusive..endInclusive }
        .forEach { aggregate += legacyFlowImpact(it) }
    canonicalEvents()
        .filter { it.date in startInclusive..endInclusive }
        .forEach { aggregate += eventFlowImpact(it) }

    return CanonicalMonthlyFlow(
        income = aggregate.income,
        expense = max(0.0, aggregate.expense),
        saving = aggregate.saving,
        refunds = aggregate.refund,
    )
}

/** Exact signed expense contribution: refunds reduce the category total; IDs open full detail. */
data class CategoryContribution(val transactionId: String, val category: String, val amount: Double)

fun CanonicalFinanceDocument.categoryTotalsBetween(
    startInclusive: String,
    endInclusive: String,
): Map<String, Double> = categoryContributionsBetween(startInclusive, endInclusive)
    .groupBy(CategoryContribution::category)
    .mapValues { (_, rows) -> max(0.0, rows.sumOf(CategoryContribution::amount)) }
    .filterValues { it > 0.005 }

fun CanonicalFinanceDocument.categoryContributionsBetween(
    startInclusive: String,
    endInclusive: String,
): List<CategoryContribution> {
    if (endInclusive < startInclusive) return emptyList()

    val contributions = mutableListOf<CategoryContribution>()
    fun add(transactionId: String, category: String?, amount: Double) {
        val key = category?.takeIf(String::isNotBlank) ?: "Άλλο"
        contributions += CategoryContribution(transactionId, key, amount)
    }

    effectiveLegacyTransactions()
        .filter { it.date in startInclusive..endInclusive }
        .forEach { tx ->
            val decision = insightsReviewDecision(tx.id)
            if (
                decision?.string("status") == "confirmed" &&
                decision.string("semanticKind") == "split" &&
                decision.array("parts").isNotEmpty()
            ) {
                decision.array("parts").forEach { rawPart ->
                    val part = rawPart as? JsonObject ?: return@forEach
                    val amount = part.number("amount") ?: return@forEach
                    when (part.string("kind") ?: "expense") {
                        "expense" -> add(tx.id, part.string("category"), amount)
                        "refund" -> add(tx.id, part.string("category"), -amount)
                    }
                }
            } else {
                val impact = legacyFlowImpact(tx)
                if (impact.expense != 0.0) {
                    add(tx.id, decision?.string("category") ?: tx.category, impact.expense)
                }
            }
        }

    canonicalEvents()
        .filter { it.date in startInclusive..endInclusive }
        .forEach { event ->
            if (event.kind == "split") {
                event.parts.forEach { part -> add(event.id, part.category, part.amount) }
            } else {
                val impact = eventFlowImpact(event)
                if (impact.expense != 0.0) add(event.id, event.category, impact.expense)
            }
        }

    return contributions
}

private fun CanonicalFinanceDocument.insightsReviewDecision(transactionId: String): JsonObject? =
    state.obj("reviewDecisions")[transactionId] as? JsonObject

