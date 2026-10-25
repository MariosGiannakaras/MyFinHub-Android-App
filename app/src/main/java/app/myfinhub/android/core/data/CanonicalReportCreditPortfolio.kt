package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale

/** Web reports.ts creditCardSnapshots and creditPortfolioSnapshot, read-only. */
data class CanonicalReportCreditCard(
    val id: String,
    val nickname: String,
    val bankId: String,
    val active: Boolean,
    val debt: Double,
    val limit: Double,
    val available: Double,
    val usage: Double,
)
data class CanonicalReportCreditPortfolio(
    val activeCards: Int,
    val totalCards: Int,
    val debt: Double,
    val limit: Double,
    val available: Double,
    val usage: Double,
    val cards: List<CanonicalReportCreditCard>,
)

/**
 * Debt continues to belong to its archived card identity. A closed card has
 * zero available line and still contributes its outstanding balance to total
 * liabilities; only active cards contribute an available credit limit.
 *
 * Never overwrite FinanceData or infer a bank from the card nickname.
 */
fun CanonicalFinanceDocument.canonicalReportCreditPortfolio(
    asOf: String,
): CanonicalReportCreditPortfolio {
    val fallbackLimit = settingsObject().number("creditLimit")
        ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    val idCollator = Collator.getInstance(Locale.US)
    // Web creditCardSnapshots first reads allCards(): sorted by createdAt,
    // then id. Its subsequent debt/nickname sort is stable, so equal debt
    // and nickname retain that original chronology, not persisted array order.
    val cards = canonicalCards()
        .filter { it.kind == "credit" }
        .sortedWith(Comparator { left, right ->
            val byCreation = left.createdAt.orEmpty().compareTo(right.createdAt.orEmpty())
            if (byCreation != 0) byCreation else idCollator.compare(left.id, right.id)
        })
        .map { card ->
            val debt = creditDebtForCardAt(card.id, asOf)
            val limit = if (!card.active) 0.0 else
                card.creditLimit?.takeIf { it.isFinite() && it >= 0.0 } ?: fallbackLimit
            CanonicalReportCreditCard(
                id = card.id,
                nickname = card.nickname,
                bankId = card.bankId,
                active = card.active,
                debt = debt,
                limit = limit,
                available = (limit - debt).coerceAtLeast(0.0),
                usage = if (limit > 0.0) debt / limit else 0.0,
            )
        }.sortedWith(Comparator { a, b ->
            val byDebt = b.debt.compareTo(a.debt)
            if (byDebt != 0) byDebt else collator.compare(a.nickname, b.nickname)
        })
    val active = cards.filter { it.active }
    val debt = cards.sumOf { it.debt }
    val limit = active.sumOf { it.limit }
    return CanonicalReportCreditPortfolio(
        activeCards = active.size,
        totalCards = cards.size,
        debt = debt,
        limit = limit,
        available = (limit - debt).coerceAtLeast(0.0),
        usage = if (limit > 0.0) debt / limit else 0.0,
        cards = cards,
    )
}
