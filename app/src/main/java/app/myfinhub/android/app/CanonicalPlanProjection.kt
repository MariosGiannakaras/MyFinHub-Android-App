package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalAccount
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.availableMoney
import app.myfinhub.android.core.data.bool
import app.myfinhub.android.core.data.canonicalAccounts
import app.myfinhub.android.core.data.canonicalScheduled
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.core.data.number
import app.myfinhub.android.core.data.overallBudget
import app.myfinhub.android.core.data.string
import app.myfinhub.android.core.ui.financialAccountDisplayName
import app.myfinhub.android.feature.plan.BudgetDraft
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.plan.PlannedUrgency
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonObject

/**
 * Builds the production Plan projection from canonical finance data.
 *
 * The forecast is intentionally explainable: current available money + expected income − pending
 * obligations ± the net available-money impact of scheduled transfers. Recurring obligations are
 * included once per next occurrence and collapse against an equivalent scheduled occurrence only
 * when title, amount, flow and due date all match.
 */
internal fun projectCanonicalPlanState(
    document: CanonicalFinanceDocument,
    today: LocalDate,
    previous: PlanUiState?,
): PlanUiState {
    val month = YearMonth.from(today)
    val canonicalBudget = document.overallBudget(month.toString())
    val horizonDays = previous?.forecastHorizonDays?.takeIf { it > 0 } ?: 30
    val horizonEnd = today.plusDays(horizonDays.toLong())
    val items = canonicalPlannedItems(document, today)
    val forecastItems = items.filter { item ->
        item.dueDateIso.toLocalDateOrNull()?.let { due -> !due.isAfter(horizonEnd) } == true
    }
    val forecastObligations = forecastItems
        .filter { it.flow == PlannedFlow.OBLIGATION }
        .sumOf(PlannedItem::amount)
    val forecastIncome = forecastItems
        .filter { it.flow == PlannedFlow.INCOME }
        .sumOf(PlannedItem::amount)
    val accountsById = document.canonicalAccounts().associateBy(CanonicalAccount::id)
    val transferImpact = document.canonicalScheduled()
        .filter { item ->
            item.status == "pending" &&
                item.kind == "transfer" &&
                item.dueDate.toLocalDateOrNull()?.let { due -> !due.isAfter(horizonEnd) } == true
        }
        .sumOf { item ->
            scheduledTransferAvailableImpact(
                amount = item.amount,
                fromAccountId = item.fromAccountId,
                toAccountId = item.toAccountId,
                accountsById = accountsById,
            )
        }
    val startBalance = document.availableMoney(today.toString())
    val monthlyFlow = document.monthlyFlow(month.toString())

    return PlanUiState(
        items = items,
        budget = previous?.budget ?: BudgetDraft(
            monthlyLimitText = canonicalBudget?.amount?.toPlainPlanMoney() ?: "",
            alertThresholdText = (canonicalBudget?.alertThreshold ?: 80).toString(),
        ),
        forecastHorizonDays = horizonDays,
        forecastStartBalance = startBalance,
        forecastExpectedIncome = forecastIncome,
        forecastObligations = forecastObligations,
        forecastTransferImpact = transferImpact,
        forecastEndBalance = startBalance + forecastIncome - forecastObligations + transferImpact,
        forecastEndDateLabel = horizonEnd.format(
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("el-GR")),
        ),
        budgetSpent = monthlyFlow.expense,
        budgetMonthLabel = month.atDay(1).format(
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("el-GR")),
        ),
        message = previous?.message,
    )
}

internal fun canonicalPlannedItems(
    document: CanonicalFinanceDocument,
    today: LocalDate,
): List<PlannedItem> {
    val accounts = document.canonicalAccounts()
    val accountNames = accounts.associate { account ->
        account.id to financialAccountDisplayName(account.id, account.name, account.kind)
    }
    val scheduled = document.canonicalScheduled()
        .filter { it.status == "pending" }
        .map { item ->
            val dueDate = item.dueDate.toLocalDateOrNull()
            PlannedItem(
                id = item.id,
                title = item.note.ifBlank { scheduledPlanKindLabel(item.kind) },
                dueLabel = dueDate?.let { formatPlanDueDate(it, today) } ?: item.dueDate.ifBlank { "Χωρίς ημερομηνία" },
                dueDateIso = dueDate?.toString().orEmpty(),
                amount = item.amount,
                kind = PlannedKind.SCHEDULED,
                flow = when (item.kind) {
                    "income" -> PlannedFlow.INCOME
                    "transfer" -> PlannedFlow.TRANSFER
                    else -> PlannedFlow.OBLIGATION
                },
                category = item.category.orEmpty(),
                accountLabel = when (item.kind) {
                    "transfer" -> transferRouteLabel(item.fromAccountId, item.toAccountId, accountNames)
                    else -> item.accountId?.let(accountNames::get).orEmpty()
                },
                urgency = urgencyFor(dueDate, today),
            )
        }

    val scheduledIdentities = scheduled.mapNotNull(::plannedIdentity).toSet()
    val recurring = document.seed.array("recurring").mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        if (item.bool("active") == false || item.string("status") in setOf("paused", "stopped")) return@mapNotNull null
        val id = item.string("id") ?: return@mapNotNull null
        val title = item.string("name") ?: id
        val amount = item.number("amount") ?: 0.0
        val dueDate = nextRecurringDate(
            firstExpectedDate = item.string("firstExpectedDate"),
            day = item.number("day")?.toInt(),
            today = today,
        )
        PlannedItem(
            id = id,
            title = title,
            dueLabel = dueDate?.let { formatPlanDueDate(it, today) } ?: "Επαναλαμβανόμενο",
            dueDateIso = dueDate?.toString().orEmpty(),
            amount = amount,
            kind = PlannedKind.RECURRING,
            flow = PlannedFlow.OBLIGATION,
            category = item.string("category").orEmpty(),
            accountLabel = item.string("accountId")?.let(accountNames::get).orEmpty(),
            note = item.string("note").orEmpty(),
            urgency = urgencyFor(dueDate, today),
        )
    }.filterNot { item -> plannedIdentity(item)?.let(scheduledIdentities::contains) == true }

    return (scheduled + recurring)
        .sortedWith(
            compareBy<PlannedItem> { it.dueDateIso.ifBlank { "9999-12-31" } }
                .thenBy { it.title.lowercase(Locale.forLanguageTag("el-GR")) }
                .thenBy { it.id },
        )
        .take(20)
}

private fun plannedIdentity(item: PlannedItem): String? {
    if (item.dueDateIso.isBlank()) return null
    val normalizedTitle = item.title.trim().lowercase(Locale.forLanguageTag("el-GR"))
    val cents = (item.amount * 100.0).roundToLong()
    return "${item.flow}|$normalizedTitle|$cents|${item.dueDateIso}"
}

private fun nextRecurringDate(
    firstExpectedDate: String?,
    day: Int?,
    today: LocalDate,
): LocalDate? {
    val first = firstExpectedDate.toLocalDateOrNull()
    if (first != null && !first.isBefore(today)) return first
    val targetDay = day ?: first?.dayOfMonth ?: return null
    if (targetDay !in 1..31) return null

    var month = YearMonth.from(today)
    var candidate = month.atDay(min(targetDay, month.lengthOfMonth()))
    if (candidate.isBefore(today)) {
        month = month.plusMonths(1)
        candidate = month.atDay(min(targetDay, month.lengthOfMonth()))
    }
    return candidate
}

private fun urgencyFor(dueDate: LocalDate?, today: LocalDate): PlannedUrgency = when {
    dueDate == null -> PlannedUrgency.LATER
    dueDate.isBefore(today) -> PlannedUrgency.OVERDUE
    !dueDate.isAfter(today.plusDays(7)) -> PlannedUrgency.THIS_WEEK
    else -> PlannedUrgency.LATER
}

private fun scheduledTransferAvailableImpact(
    amount: Double,
    fromAccountId: String?,
    toAccountId: String?,
    accountsById: Map<String, CanonicalAccount>,
): Double {
    val from = fromAccountId?.let(accountsById::get) ?: return 0.0
    val to = toAccountId?.let(accountsById::get) ?: return 0.0
    val fromAvailable = from.kind != "credit" && !from.excludeFromAvailable
    val toAvailable = to.kind != "credit" && !to.excludeFromAvailable
    return when {
        fromAvailable && !toAvailable -> -amount
        !fromAvailable && toAvailable -> amount
        else -> 0.0
    }
}

private fun transferRouteLabel(
    fromAccountId: String?,
    toAccountId: String?,
    accountNames: Map<String, String>,
): String {
    val from = fromAccountId?.let(accountNames::get)
    val to = toAccountId?.let(accountNames::get)
    return when {
        from != null && to != null -> "Από $from → Προς $to"
        from != null -> "Από $from"
        to != null -> "Προς $to"
        else -> ""
    }
}

private fun formatPlanDueDate(date: LocalDate, today: LocalDate): String {
    val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("el-GR")))
}

private fun scheduledPlanKindLabel(kind: String): String = when (kind) {
    "expense" -> "Προγραμματισμένο έξοδο"
    "income" -> "Προγραμματισμένο έσοδο"
    "transfer" -> "Προγραμματισμένη μεταφορά"
    else -> "Προγραμματισμένη κίνηση"
}

private fun String?.toLocalDateOrNull(): LocalDate? = this
    ?.takeIf(String::isNotBlank)
    ?.let { raw -> runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() }

private fun Double.toPlainPlanMoney(): String = if (this % 1.0 == 0.0) {
    toLong().toString()
} else {
    String.format(Locale.US, "%.2f", this)
}
