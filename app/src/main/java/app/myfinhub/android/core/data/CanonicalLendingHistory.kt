package app.myfinhub.android.core.data

import kotlin.math.max
import kotlinx.serialization.json.JsonObject

/** Read-only Web lending.ts history, independent of the aggregated receivable balance. */
data class CanonicalLendingHistoryRow(
    val id: String,
    val person: String,
    val date: String,
    val action: String,
    val amount: Double,
    val note: String,
    val accountId: String?,
    val origin: String,
    val runningOutstanding: Double,
)

private data class LendingHistoryMovement(
    val id: String,
    val person: String,
    val date: String,
    val action: String,
    val amount: Double,
    val note: String,
    val accountId: String?,
    val origin: String,
)

private val lendingDatePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

private fun JsonObject.finiteLendingNumber(key: String): Double =
    number(key)?.takeIf(Double::isFinite) ?: 0.0

/** Web cleanDate only accepts YYYY-MM-DD-shaped history dates; no synthetic dates. */
private fun String?.lendingHistoryDate(): String =
    this.orEmpty().trim().takeIf { lendingDatePattern.matches(it) }.orEmpty()

/**
 * Web lendingHistory: expand legacy entries and linked lending/repayment events, then calculate
 * the running per-person history from those movements. Seed outstanding is intentionally *not*
 * added to runningOutstanding: it is a separate aggregate baseline in Web lendingRows.
 *
 * Projection only: neither archived history nor an ambiguous historic ID is a mutation target.
 */
fun CanonicalFinanceDocument.canonicalLendingHistoryRows(asOf: String? = null): List<CanonicalLendingHistoryRow> {
    val raw = mutableListOf<LendingHistoryMovement>()
    seed.array("lending").mapNotNull { it as? JsonObject }.forEach { legacy ->
        val person = legacy.string("person").orEmpty().trim()
        if (person.isEmpty()) return@forEach
        legacy.array("entries").forEachIndexed { index, entry ->
            val row = entry as? JsonObject ?: return@forEachIndexed
            val date = row.string("date").lendingHistoryDate()
            if (date.isEmpty() || (!asOf.isNullOrEmpty() && date > asOf)) return@forEachIndexed
            listOf(
                Triple("lent", "lent", "Ιστορικό δανεικών"),
                Triple("repaid", "repaid", "Ιστορικό επιστροφής"),
                Triple("haircut", "forgiven", "Χάρισμα / διαγραφή οφειλής"),
            ).forEach { (field, action, note) ->
                val amount = row.finiteLendingNumber(field)
                if (amount > 0.0) raw += LendingHistoryMovement(
                    id = "legacy-$person-$date-$index-$action",
                    person = person,
                    date = date,
                    action = action,
                    amount = amount,
                    note = note,
                    accountId = null,
                    origin = "legacy",
                )
            }
        }
    }
    state.array("events").mapNotNull { it as? JsonObject }.forEach { event ->
        val person = event.string("person").orEmpty().trim()
        val date = event.string("date").lendingHistoryDate()
        val amount = event.finiteLendingNumber("amount")
        val kind = event.string("kind").orEmpty().trim()
        if (person.isEmpty() || date.isEmpty() || amount <= 0.0 ||
            (!asOf.isNullOrEmpty() && date > asOf) || kind !in setOf("lending", "repayment", "lending_writeoff")
        ) return@forEach
        // Web lendingHistory never attaches a posting account to a writeoff:
        // forgiveness changes a receivable but is not a cash transfer.
        val accountId = if (kind == "lending_writeoff") null else
            event.string("accountId").orEmpty().trim().ifEmpty {
                event.string(if (kind == "repayment") "toAccountId" else "fromAccountId")
                    .orEmpty().trim()
            }.takeIf(String::isNotEmpty)
        val note = event.string("note").orEmpty().trim().ifEmpty {
            when (kind) {
                "repayment" -> "Επιστροφή δανεικών"
                "lending_writeoff" -> "Διαγραφή απαίτησης"
                else -> "Πλήρωσα για άλλον"
            }
        }
        raw += LendingHistoryMovement(
            id = event.string("id").orEmpty().trim().ifEmpty { "event-$person-$date-${raw.size}" },
            person = person,
            date = date,
            action = when (kind) {
                "lending" -> "lent"
                "repayment" -> "repaid"
                else -> "forgiven"
            },
            amount = amount,
            note = note,
            accountId = accountId,
            origin = "event",
        )
    }
    val runningByPerson = mutableMapOf<String, Double>()
    val chronological = raw.sortedWith(compareBy<LendingHistoryMovement> { it.date }.thenBy { it.id })
    return chronological.map { entry ->
        val current = runningByPerson[entry.person] ?: 0.0
        val next = if (entry.action == "lent") current + entry.amount else max(0.0, current - entry.amount)
        runningByPerson[entry.person] = next
        CanonicalLendingHistoryRow(
            entry.id, entry.person, entry.date, entry.action, entry.amount, entry.note,
            entry.accountId, entry.origin, next,
        )
    }.sortedWith(compareByDescending<CanonicalLendingHistoryRow> { it.date }.thenByDescending { it.id })
}
