package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlin.math.max

/**
 * Web reports.ts creditCardSnapshots + creditPortfolioSnapshot.
 * Archived cards retain debt but contribute no active credit limit.
 * No card secrets, backend requests or profile mutations.
 */
data class CanonicalReportCreditCardRow(
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
    val cards: List<CanonicalReportCreditCardRow>,
)

fun CanonicalFinanceDocument.canonicalReportCreditPortfolio(
    asOf: String,
): CanonicalReportCreditPortfolio {
    val defaultLimit = settingsObject().number("creditLimit")?.takeIf {
        it.isFinite() && it >= 0.0
    } ?: 0.0
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    // Web allCards sorts by creation timestamp before the stable debt/name sort;
    // older records without the active field remain active (active !== false).
    val rawCards = state.array("cards").mapNotNull { it as? JsonObject }
    val cards = canonicalCards().asSequence().filter { it.kind == "credit" }
        .sortedWith(Comparator { a,b ->
            val chronology=a.createdAt.orEmpty().compareTo(b.createdAt.orEmpty())
            if(chronology!=0) chronology else collator.compare(a.id,b.id)
        })
        .map { card ->
            val raw = rawCards.firstOrNull { it.string("id") == card.id }
            val active = raw?.bool("active") != false
            val explicitLimit = if(raw?.get("creditLimit") == JsonNull) 0.0
                else card.creditLimit?.takeIf { it.isFinite() && it >= 0.0 }
            val debt = creditDebtForCardAt(card.id, asOf)
            val limit = if (!active) 0.0 else explicitLimit ?: defaultLimit
            CanonicalReportCreditCardRow(
                id=card.id, nickname=card.nickname, bankId=card.bankId,
                active=active, debt=debt, limit=limit,
                available=max(0.0,limit-debt),
                usage=if(limit>0) debt/limit else 0.0,
            )
        }
        .sortedWith(Comparator { a,b ->
            val debt=b.debt.compareTo(a.debt)
            if(debt!=0) debt else collator.compare(a.nickname,b.nickname)
        }).toList()
    val activeCards = cards.count { it.active }
    val outstanding = cards.sumOf { it.debt }
    val activeLimit = cards.filter { it.active }.sumOf { it.limit }
    return CanonicalReportCreditPortfolio(
        activeCards=activeCards,totalCards=cards.size,
        debt=outstanding,limit=activeLimit,
        available=max(0.0,activeLimit-outstanding),
        usage=if(activeLimit>0)outstanding/activeLimit else 0.0,
        cards=cards,
    )
}
