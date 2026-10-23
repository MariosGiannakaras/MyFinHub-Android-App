package app.myfinhub.android.core.data

import java.time.YearMonth
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Web MonthlyBudget scope=subcategory. This is metadata only: no ledger posting,
 * no future commitment mutation and no change to historical category identities.
 */
data class UpsertCanonicalSubcategoryBudget(
    val budgetId: String,
    val month: String,
    val category: String,
    val subcategory: String,
    val amount: Double,
    val alertThreshold: Int,
    val carryover: Boolean,
    val expected: JsonObject?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Αποθήκευση ορίου υποκατηγορίας"

    private fun sameScope(row: JsonObject): Boolean =
        row.string("scope") == "subcategory" && row.string("month") == month &&
            webBudgetCategoryKey(row.string("category").orEmpty()) ==
                webBudgetCategoryKey(category) &&
            webBudgetCategoryKey(row.string("subcategory").orEmpty()) ==
                webBudgetCategoryKey(subcategory)

    private fun matches(document: CanonicalFinanceDocument): List<JsonObject> =
        document.state.array("budgets").filterIsInstance<JsonObject>().filter(::sameScope)

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val same = matches(document).singleOrNull() ?: return false
        val threshold = same.number("alertThreshold") ?: return false
        val normalized = if (threshold <= 1.0) (threshold * 100).roundToInt()
            else threshold.roundToInt()
        return same.string("id") == budgetId &&
            runCatching { moneyToCents(same.number("amount") ?: 0.0) == moneyToCents(amount) }
                .getOrDefault(false) &&
            normalized == alertThreshold && (same.bool("carryover") == true) == carryover
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(runCatching { YearMonth.parse(month).toString() == month }.getOrDefault(false)) {
            "Μη έγκυρος μήνας budget."
        }
        require(budgetId.isNotBlank() && category.isNotBlank() && subcategory.isNotBlank() &&
            category == category.trim() && subcategory == subcategory.trim()) {
            "Επίλεξε υπάρχουσα κατηγορία και υποκατηγορία."
        }
        require(moneyToCents(amount) > 0L && alertThreshold in 50..99) {
            "Το όριο πρέπει να είναι θετικό με προειδοποίηση 50–99%."
        }
        val rows = document.state.array("budgets")
        val existing = matches(document)
        require(existing.size <= 1) { "Ιστορικά διπλό όριο υποκατηγορίας: μόνο προβολή." }
        if (satisfiedBy(document)) return document
        val previous = existing.singleOrNull()
        require(previous == expected &&
            (previous == null || previous.string("id") == budgetId)) {
            "Το όριο άλλαξε στον server. Έλεγξε τη νεότερη έκδοση."
        }
        require(rows.filterIsInstance<JsonObject>()
            .none { it.string("id") == budgetId && it != previous }) {
            "Το ID χρησιμοποιείται από διαφορετικό όριο."
        }
        val row = JsonObject((previous ?: JsonObject(emptyMap())) + mapOf(
            "id" to JsonPrimitive(budgetId),
            "month" to JsonPrimitive(month),
            "scope" to JsonPrimitive("subcategory"),
            "category" to JsonPrimitive(category),
            "subcategory" to JsonPrimitive(subcategory),
            "amount" to JsonPrimitive(centsToMoney(moneyToCents(amount))),
            "alertThreshold" to JsonPrimitive(alertThreshold / 100.0),
            "carryover" to JsonPrimitive(carryover),
            "createdAt" to JsonPrimitive(previous?.string("createdAt") ?: nowIso),
            "updatedAt" to JsonPrimitive(nowIso),
        ))
        val updated = if (previous == null) rows + row else rows.map {
            if (it == previous) row else it
        }
        return document.withMutableState(
            document.state.updated("budgets", JsonArray(updated)), nowIso,
        )
    }
}

/** Owner-confirmed removal of a single source row, never a parent category or transaction. */
data class DeleteCanonicalSubcategoryBudget(
    val budgetId: String,
    val month: String,
    val category: String,
    val subcategory: String,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Διαγραφή ορίου υποκατηγορίας"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        budgetId.isNotBlank() && document.state.array("budgets")
            .none { (it as? JsonObject)?.string("id") == budgetId }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(budgetId.isNotBlank() && expected.string("id") == budgetId &&
            expected.string("scope") == "subcategory" &&
            expected.string("month") == month &&
            expected.string("category") == category &&
            expected.string("subcategory") == subcategory) {
            "Μη έγκυρη ταυτότητα ορίου υποκατηγορίας."
        }
        val rows = document.state.array("budgets")
        val matches = rows.filterIsInstance<JsonObject>().filter { it.string("id") == budgetId }
        require(matches.size <= 1) { "Διπλό ID ορίου. Απαιτείται έλεγχος." }
        val actual = matches.singleOrNull() ?: return document
        require(actual == expected) { "Το όριο άλλαξε σε άλλη συσκευή. Έλεγξε την εγγραφή." }
        return document.withMutableState(
            document.state.updated("budgets", JsonArray(rows.filterNot { it == actual })),
            nowIso,
        )
    }
}
