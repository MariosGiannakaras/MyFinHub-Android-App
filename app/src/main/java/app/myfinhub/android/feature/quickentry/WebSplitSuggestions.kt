package app.myfinhub.android.feature.quickentry

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Web domain.ts suggestSplitParts parsing, without generated/random transaction IDs. */
data class WebSplitSuggestion(
    val label: String,
    val category: String,
    val amountText: String,
    val kind: String,
)

private val webSplitMoneyTokens = Regex("""(\d{1,5}(?:[.,]\d{1,2})?)\s*€""")
private val webSplitTrailingAmounts = Regex("""\s*[:=]?\s*\d[\d.,]*(?:\s*€)?[\s\S]*$""")

fun canonicalWebSplitSuggestions(
    note: String,
    fallbackCategory: String = "Άλλο",
): List<WebSplitSuggestion> = note.trim().split('\n').mapNotNull { raw ->
    val line=raw.trim()
    if (line.isEmpty()) return@mapNotNull null
    val amountTokens=webSplitMoneyTokens.findAll(line)
        .mapNotNull { hit -> hit.groupValues[1].replace(',','.').toDoubleOrNull() }
        .filter { it > 0.0 && it.isFinite() }.toList()
    if (amountTokens.isEmpty()) return@mapNotNull null
    val amount=amountTokens.sum()
    val kind=with(line.uppercase(Locale.forLanguageTag("el-GR"))) {
        when {
            contains("ΕΠΙΣΤΡΟΦ") -> "refund"
            contains("ΜΙΣΘ") || contains("ΕΠΙΔΟΜ") || contains("ΔΩΡΟ") -> "income"
            contains("ΑΠΟΤΑΜΙΕΥΣ") || contains("PAY&SAVE") -> "saving"
            contains("ΔΙΟΡΘΩΣ") || contains("ΔΙΌΡΘΩΣ") -> "reconciliation"
            contains("ΜΕΤΑΦΟΡ") || contains("ΑΝΑΛΗΨ") -> "transfer"
            else -> "expense"
        }
    }
    val stripped=line.replace(webSplitTrailingAmounts,"").trim()
    WebSplitSuggestion(
        label=stripped.ifBlank { line.take(40) },
        category=fallbackCategory,
        amountText=BigDecimal.valueOf(amount).setScale(2,RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString(),
        kind=kind,
    )
}

/**
 * The Android split writer currently represents expense parts only.
 * Web's other predicted semantics MUST NOT be silently downcast to expense.
 * Suggestions are opt-in, cannot replace user-edited parts, and are applied
 * only when all expense lines sum exactly to the declared money amount.
 */
fun QuickEntryUiState.safeWebExpenseSplitSuggestions(): List<QuickEntrySplitPartDraft> {
    if (kind != QuickEntryKind.SPLIT ||
        splitParts.any {
            it.amountText.isNotBlank() || it.label.isNotBlank() ||
                it.subcategory.isNotBlank() || it.category != category
        }
    ) return emptyList()
    if (expenseCategories.none { it.name == category }) return emptyList()
    val suggestions=canonicalWebSplitSuggestions(note,category)
    if (suggestions.size !in 2..12 || suggestions.any { it.kind != "expense" }) return emptyList()
    val totalCents=amountText.toCurrencyCentsOrNull() ?: return emptyList()
    val partsCents=suggestions.map { it.amountText.toCurrencyCentsOrNull() ?: return emptyList() }
    if (partsCents.any { it <= 0 } || partsCents.sum() != totalCents) return emptyList()
    return suggestions.mapIndexed { index, row ->
        QuickEntrySplitPartDraft(
            id="part-" + (index + 1),
            label=row.label,
            category=row.category,
            amountText=row.amountText,
        )
    }
}
