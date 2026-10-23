package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Delete only a selected, owner-confirmed overall-budget metadata row.
 * Finance events, category limits, recurring obligations and previous months are untouched.
 */
data class DeleteCanonicalOverallBudget(
    val budgetId: String,
    val month: String,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Διαγραφή συνολικού προϋπολογισμού"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        budgetId.isNotBlank() &&
            document.state.array("budgets").none {
                (it as? JsonObject)?.string("id") == budgetId
            }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(budgetId.isNotBlank() && month == expected.string("month") &&
            expected.string("id") == budgetId && expected.string("scope") == "overall" &&
            runCatching { java.time.YearMonth.parse(month).toString() == month }.getOrDefault(false)
        ) { "Η ταυτότητα του συνολικού ορίου δεν είναι έγκυρη." }
        val rows = document.state.array("budgets")
        val idMatches = rows.filterIsInstance<JsonObject>()
            .filter { it.string("id") == budgetId }
        require(idMatches.size <= 1) {
            "Το όριο έχει διπλό αναγνωριστικό. Η διαγραφή αποκλείστηκε."
        }
        val current = idMatches.singleOrNull() ?: return document
        require(current == expected) {
            "Το όριο τροποποιήθηκε σε άλλη συσκευή. Έλεγξε τα ενημερωμένα δεδομένα."
        }
        require(rows.filterIsInstance<JsonObject>()
            .count { it.string("scope") == "overall" && it.string("month") == month } == 1
        ) { "Υπάρχουν πολλαπλά συνολικά όρια για τον μήνα." }
        return document.withMutableState(
            document.state.updated("budgets", JsonArray(rows.filterNot { it == current })),
            nowIso,
        )
    }
}
