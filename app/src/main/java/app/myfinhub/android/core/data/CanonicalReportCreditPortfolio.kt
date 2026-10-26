package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    // Web creditLimitForCard also evaluates Number(settings.creditLimit).
    // Reuse the exact same legacy-number conversion as per-card limits:
    // padded numeric strings and booleans are valid, null/empty means zero.
    val fallbackLimit = webCreditLimitOrNull(settingsObject())
        ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    val idCollator = Collator.getInstance(Locale.US)
    // Web creditCardSnapshots first reads allCards(): sorted by createdAt,
    // then id. Its subsequent debt/nickname sort is stable, so equal debt
    // and nickname retain that original chronology, not persisted array order.
    // Preserve source-row identity rather than looking up by ID: historical
    // duplicate card IDs must not inherit another row's explicit creditLimit.
    // Web Number(null) and Number("") are zero, unlike absent (undefined).
    val sourceRows = state.array("cards").filterIsInstance<JsonObject>()
        .filter { it.string("id") != null }
    val cards = canonicalCards().zip(sourceRows)
        .filter { (card, _) -> card.kind == "credit" }
        .sortedWith(Comparator { left, right ->
            val byCreation = left.first.createdAt.orEmpty()
                .compareTo(right.first.createdAt.orEmpty())
            if (byCreation != 0) byCreation else idCollator.compare(left.first.id, right.first.id)
        })
        .map { (card, raw) ->
            val debt = creditDebtForCardAt(card.id, asOf)
            val limit = if (!card.active) 0.0 else
                webCreditLimitOrNull(raw)?.takeIf { it.isFinite() && it >= 0.0 }
                    ?: fallbackLimit
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

/**
 * JS Number(card.creditLimit): absent/invalid values use shared fallback,
 * while explicitly null, empty or whitespace-only values mean zero.
 * This is read-only and retains each persisted card row (including duplicate IDs).
 */
private fun webCreditLimitOrNull(card: JsonObject): Double? = when (val raw = card["creditLimit"]) {
    null -> null
    JsonNull -> 0.0
    is JsonPrimitive -> when {
        !raw.isString && raw.content == "true" -> 1.0
        !raw.isString && raw.content == "false" -> 0.0
        raw.content.isBlank() -> 0.0
        else -> raw.content.trim().toDoubleOrNull()
    }
    else -> null
}
