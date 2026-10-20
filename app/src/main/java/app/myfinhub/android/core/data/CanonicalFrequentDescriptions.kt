package app.myfinhub.android.core.data

/**
 * Web domain.ts frequentDescriptions for effective historical legacy entries.
 * No guesswork from new events and no activity or finance mutation.
 */
data class CanonicalFrequentDescription(
    val label: String,
    val count: Int,
    val category: String,
    val accountId: String?,
    val lastAmount: Double,
    val lastDate: String,
)

fun CanonicalFinanceDocument.canonicalWebFrequentDescriptions(
    type: String = "expense",
    limit: Int = 12,
): List<CanonicalFrequentDescription> {
    data class Acc(
        val label: String,
        var count: Int,
        var category: String,
        var accountId: String?,
        var lastAmount: Double,
        var lastDate: String,
    )
    val grouped = linkedMapOf<String, Acc>()
    val colonAmount = Regex(""":\s*[-+]?\d.*$""")
    effectiveLegacyTransactions().forEach { tx ->
        if (tx.type != type) return@forEach
        val label = tx.note.trim()
            .lineSequence().firstOrNull().orEmpty()
            .replace(colonAmount, "")
            .trim()
            .take(42)
        if (label.length < 2) return@forEach
        val existing = grouped[label]
        if (existing == null) {
            grouped[label] = Acc(label, 1, tx.category.orEmpty(),
                tx.accountId, tx.amount, tx.date)
        } else {
            existing.count++
            if (tx.date >= existing.lastDate) {
                existing.lastDate = tx.date
                existing.lastAmount = tx.amount
                existing.category = tx.category?.takeIf { it.isNotBlank() }
                    ?: existing.category
                existing.accountId = tx.accountId?.takeIf { it.isNotBlank() }
                    ?: existing.accountId
            }
        }
    }
    return grouped.values.sortedByDescending { it.count }
        .take(limit.coerceAtLeast(0))
        .map { row ->
            CanonicalFrequentDescription(
                row.label, row.count, row.category, row.accountId,
                row.lastAmount, row.lastDate,
            )
        }
}
