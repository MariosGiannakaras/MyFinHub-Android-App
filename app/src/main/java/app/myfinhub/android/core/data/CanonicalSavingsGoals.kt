package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlin.math.max
import kotlinx.serialization.json.JsonObject

/** Pure Web savingsGoals.ts projection: goals share eligible savings-account assets.
 * Each goal reports progress against the same backing balance, never invent allocated funds.
 */
data class CanonicalSavingsGoalRow(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val targetDate: String?,
    val backingBalance: Double,
    val progressPercent: Double,
    val deadlinePast: Boolean,
)

fun CanonicalFinanceDocument.canonicalSavingsGoals(asOf: LocalDate): List<CanonicalSavingsGoalRow> {
    val eligible = canonicalAccounts().filter(CanonicalAccount::isSavingsAccount)
    val balances = accountBalances(asOf.toString())
    val backing = eligible.sumOf { account -> max(0.0, balances[account.id] ?: 0.0) }
    return state.array("savingsGoals").mapNotNull { raw ->
        val goal = raw as? JsonObject ?: return@mapNotNull null
        val id = goal.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        // Web savingsGoals.ts renders historical rows with invalid/nonpositive
        // targetAmount and reports 0% progress, rather than silently deleting
        // an imported goal from the non-posting projection.
        val amount = goal.number("targetAmount") ?: 0.0
        val rawDate = goal.string("targetDate")
        val date = rawDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val percent = if (!amount.isFinite() || amount <= 0.0) 0.0
            else (backing / amount * 100.0).coerceIn(0.0,100.0)
        CanonicalSavingsGoalRow(
            id = id,
            name = goal.string("name").orEmpty().ifBlank { "Στόχος αποταμίευσης" },
            // Keep the original imported deadline visible even if its ISO
            // value is malformed. Web renders the saved targetDate as supplied;
            // only a *valid* date participates in deadlinePast comparisons.
            targetAmount = amount, targetDate = rawDate,
            backingBalance = backing, progressPercent = percent,
            deadlinePast = date?.isBefore(asOf) == true,
        )
    }
}
