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
        val amount = goal.number("targetAmount")
            ?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
        val rawDate = goal.string("targetDate")
        val date = rawDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val percent = (backing / amount * 100.0).coerceIn(0.0,100.0)
        CanonicalSavingsGoalRow(
            id = id,
            name = goal.string("name").orEmpty().ifBlank { "Στόχος αποταμίευσης" },
            targetAmount = amount, targetDate = date?.toString(),
            backingBalance = backing, progressPercent = percent,
            deadlinePast = date?.isBefore(asOf) == true,
        )
    }
}
