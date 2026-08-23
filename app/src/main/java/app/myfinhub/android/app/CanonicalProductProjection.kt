package app.myfinhub.android.app

import app.myfinhub.android.core.data.CREDIT_ACCOUNT_ID
import app.myfinhub.android.core.data.CanonicalEvent
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalLegacyTransaction
import app.myfinhub.android.core.data.accountBalances
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.availableMoney
import app.myfinhub.android.core.data.bool
import app.myfinhub.android.core.data.canonicalAccounts
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.data.canonicalEvents
import app.myfinhub.android.core.data.canonicalScheduled
import app.myfinhub.android.core.data.cardOutstanding
import app.myfinhub.android.core.data.categoryTotals
import app.myfinhub.android.core.data.effectiveLegacyTransactions
import app.myfinhub.android.core.data.loanOutstanding
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.core.data.number
import app.myfinhub.android.core.data.obj
import app.myfinhub.android.core.data.overallBudget
import app.myfinhub.android.core.data.receivableOutstanding
import app.myfinhub.android.core.data.settingsObject
import app.myfinhub.android.core.data.string
import app.myfinhub.android.feature.activity.ActivityFilter
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.home.HomeAccount
import app.myfinhub.android.feature.home.HomeAccountGroup
import app.myfinhub.android.feature.home.HomeAttentionItem
import app.myfinhub.android.feature.home.HomeAttentionTone
import app.myfinhub.android.feature.home.HomeMonthFlow
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomeUpcomingItem
import app.myfinhub.android.feature.insights.InsightCategory
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.TrendPoint
import app.myfinhub.android.feature.money.MoneyAccount
import app.myfinhub.android.feature.money.MoneyCard
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.VaultState
import app.myfinhub.android.feature.plan.BudgetDraft
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

/** UI-only projection of the current canonical server document. */
data class CanonicalProductProjection(
    val document: CanonicalFinanceDocument,
    val homeState: HomeUiState,
    val activityState: ActivityUiState,
    val quickEntryState: QuickEntryUiState,
    val moneyState: MoneyUiState,
    val planState: PlanUiState,
    val insightsState: InsightsUiState,
)

fun projectCanonicalProduct(
    document: CanonicalFinanceDocument,
    today: LocalDate,
    previous: CanonicalProductProjection? = null,
): CanonicalProductProjection {
    val asOf = today.toString()
    val month = YearMonth.from(today).toString()
    val accounts = document.canonicalAccounts()
    val accountNames = accounts.associate { it.id to it.name }
    val balances = document.accountBalances(asOf)
    val flow = document.monthlyFlow(month)
    val budget = document.overallBudget(month)
    val scheduled = document.canonicalScheduled()
    val events = document.canonicalEvents()
    val legacy = document.effectiveLegacyTransactions()

    val homeAccounts = accounts.filter { it.kind != "credit" }.map { account ->
        HomeAccount(
            id = account.id,
            name = account.name,
            role = account.shortName ?: accountKindLabel(account.kind),
            balance = balances[account.id] ?: 0.0,
            group = if (account.kind == "savings") HomeAccountGroup.SAVINGS else HomeAccountGroup.LIQUID,
        )
    }
    val pendingScheduled = scheduled.filter { it.status == "pending" }
    val attention = pendingScheduled.filter { it.dueDate.isNotBlank() && it.dueDate <= asOf }
        .sortedBy { it.dueDate }
        .take(4)
        .map { item ->
            HomeAttentionItem(
                id = item.id,
                title = item.note.ifBlank { scheduledKindLabel(item.kind) },
                reason = "Προγραμματισμένη κίνηση σε εκκρεμότητα.",
                dueLabel = if (item.dueDate == asOf) "Σήμερα" else "Καθυστερημένη",
                tone = HomeAttentionTone.URGENT,
            )
        }
    val upcoming = pendingScheduled.filter { it.dueDate > asOf }
        .sortedBy { it.dueDate }
        .take(5)
        .map { item ->
            HomeUpcomingItem(
                id = item.id,
                title = item.note.ifBlank { scheduledKindLabel(item.kind) },
                dateLabel = formatDate(item.dueDate),
                amount = if (item.kind == "expense") -item.amount else item.amount,
            )
        }
    val oldHome = previous?.homeState
    val home = HomeUiState(
        amountsVisible = oldHome?.amountsVisible ?: false,
        accounts = homeAccounts,
        attentionItems = attention,
        upcomingItems = upcoming,
        monthFlow = HomeMonthFlow(
            income = flow.income,
            expense = flow.expense,
            saving = flow.saving,
            budget = budget?.amount ?: 0.0,
        ),
        quickEntryOpen = oldHome?.quickEntryOpen ?: false,
        selectedQuickEntryType = oldHome?.selectedQuickEntryType,
    )

    val activityItems = buildActivityItems(legacy, events, accountNames)
    val oldActivity = previous?.activityState
    val activity = ActivityUiState(
        query = oldActivity?.query.orEmpty(),
        filter = oldActivity?.filter ?: ActivityFilter.ALL,
        selectedId = oldActivity?.selectedId?.takeIf { id -> activityItems.any { it.id == id } },
        items = activityItems,
    )

    val defaultExpenseAccount = document.settingsObject().string("defaultExpenseAccount")
        ?.takeIf(accountNames::containsKey)
        ?: accounts.firstOrNull { it.kind != "credit" }?.id.orEmpty()
    val destination = accounts.firstOrNull { it.kind == "savings" && it.id != defaultExpenseAccount }
        ?: accounts.firstOrNull { it.kind != "credit" && it.id != defaultExpenseAccount }
    val quickEntry = previous?.quickEntryState ?: QuickEntryUiState(
        fromAccount = accountNames[defaultExpenseAccount].orEmpty(),
        destination = destination?.name.orEmpty(),
    )

    val activeCards = document.canonicalCards().filter { it.active }
    val globalCreditOutstanding = (-(balances[CREDIT_ACCOUNT_ID] ?: 0.0)).coerceAtLeast(0.0)
    val activeCreditCards = activeCards.filter { it.kind == "credit" }
    val moneyCards = activeCards.map { card ->
        val eventOutstanding = document.cardOutstanding(card.id, asOf)
        MoneyCard(
            id = card.id,
            nickname = card.nickname.ifBlank { card.network.ifBlank { "Κάρτα" } },
            last4 = card.last4.orEmpty(),
            kind = cardKindLabel(card.kind),
            currentBalance = if (card.kind == "credit" && activeCreditCards.size == 1) {
                maxOf(eventOutstanding, globalCreditOutstanding)
            } else {
                eventOutstanding
            },
            limit = card.creditLimit,
            vaultState = if (card.vaultRef.isNullOrBlank()) VaultState.LOCKED else VaultState.AVAILABLE,
        )
    }
    val savingsCurrent = accounts.filter { it.kind == "savings" }.sumOf { balances[it.id] ?: 0.0 }
    val money = MoneyUiState(
        accounts = accounts.filter { it.kind != "credit" }.map { account ->
            MoneyAccount(account.id, account.name, balances[account.id] ?: 0.0, accountKindLabel(account.kind))
        },
        cards = moneyCards,
        savingsGoal = null,
        savingsCurrent = savingsCurrent,
        loanOutstanding = document.loanOutstanding(),
        lendingReceivable = document.receivableOutstanding(),
    )

    val plannedItems = buildPlannedItems(document, today)
    val canonicalBudget = budget
    val oldPlan = previous?.planState
    val budgetDraft = oldPlan?.budget ?: BudgetDraft(
        monthlyLimitText = canonicalBudget?.amount?.toPlainMoney() ?: "",
        alertThresholdText = (canonicalBudget?.alertThreshold ?: 80).toString(),
    )
    val pendingForecastDelta = pendingScheduled.filter { it.dueDate >= asOf }
        .sumOf { item ->
            when (item.kind) {
                "income" -> item.amount
                "expense" -> -item.amount
                else -> 0.0
            }
        }
    val plan = PlanUiState(
        items = plannedItems,
        budget = budgetDraft,
        forecastEndBalance = document.availableMoney(asOf) + pendingForecastDelta,
        message = oldPlan?.message,
    )

    val trendMonths = (3L downTo 0L).map { offset -> YearMonth.from(today).minusMonths(offset) }
    val trend = trendMonths.map { trendMonth ->
        val monthly = document.monthlyFlow(trendMonth.toString())
        TrendPoint(
            label = monthLabel(trendMonth),
            income = monthly.income,
            expense = monthly.expense,
        )
    }
    val categories = document.categoryTotals(month).entries.sortedByDescending(Map.Entry<String, Double>::value)
    val categoryTotal = categories.sumOf(Map.Entry<String, Double>::value)
    val insightCategories = categories.take(8).map { (name, amount) ->
        InsightCategory(
            name = name,
            amount = amount,
            share = if (categoryTotal <= 0.0) 0f else (amount / categoryTotal).toFloat(),
        )
    }
    val averageSpend = trend.map(TrendPoint::expense).average().takeIf(Double::isFinite) ?: 0.0
    val savingsRate = if (flow.income > 0.0) {
        (((flow.income - flow.expense) / flow.income) * 100.0).roundToInt()
    } else {
        0
    }
    val insights = InsightsUiState(
        monthlyTrend = trend,
        categories = insightCategories,
        averageMonthlySpend = averageSpend,
        savingsRate = savingsRate,
    )

    return CanonicalProductProjection(
        document = document,
        homeState = home,
        activityState = activity,
        quickEntryState = quickEntry,
        moneyState = money,
        planState = plan,
        insightsState = insights,
    )
}

private fun buildActivityItems(
    legacy: List<CanonicalLegacyTransaction>,
    events: List<CanonicalEvent>,
    accountNames: Map<String, String>,
): List<ActivityItem> {
    val legacyItems = legacy.map { tx ->
        val kind = when (tx.type) {
            "income" -> ActivityKind.INCOME
            "transfer" -> ActivityKind.TRANSFER
            else -> ActivityKind.EXPENSE
        }
        val kindLabel = kind.label.removeSuffix("α").removeSuffix("ές")
        ActivityItem(
            id = tx.id,
            dateLabel = formatDate(tx.date),
            kind = kind,
            title = tx.note.ifBlank { tx.category ?: kindLabel },
            subtitle = tx.category ?: legacyTypeLabel(tx.type),
            amount = when (tx.type) {
                "expense" -> -tx.amount
                else -> tx.amount
            },
            accountLabel = accountLabel(tx.accountId, tx.fromAccountId, tx.toAccountId, accountNames),
            category = tx.category,
        )
    }
    val eventItems = events.map { event ->
        val kind = eventActivityKind(event.kind)
        ActivityItem(
            id = event.id,
            dateLabel = formatDate(event.date),
            kind = kind,
            title = event.note.ifBlank { event.category ?: eventKindLabel(event.kind) },
            subtitle = event.category ?: eventKindLabel(event.kind),
            amount = eventDisplayAmount(event),
            accountLabel = accountLabel(event.accountId, event.fromAccountId, event.toAccountId, accountNames),
            category = event.category,
        )
    }
    return (legacyItems + eventItems).sortedByDescending { item ->
        (legacy.firstOrNull { it.id == item.id }?.date ?: events.firstOrNull { it.id == item.id }?.date).orEmpty()
    }
}

private fun eventActivityKind(kind: String): ActivityKind = when (kind) {
    "income", "refund", "repayment" -> ActivityKind.INCOME
    "transfer", "withdrawal", "saving_cash_offset", "reconciliation" -> ActivityKind.TRANSFER
    "card_payment" -> ActivityKind.CARD_PAYMENT
    else -> ActivityKind.EXPENSE
}

private fun eventDisplayAmount(event: CanonicalEvent): Double = when (event.kind) {
    "expense", "card_purchase", "split", "lending", "card_payment" -> -event.amount
    else -> event.amount
}

private fun buildPlannedItems(document: CanonicalFinanceDocument, today: LocalDate): List<PlannedItem> {
    val scheduled = document.canonicalScheduled().filter { it.status == "pending" }.map { item ->
        PlannedItem(
            id = item.id,
            title = item.note.ifBlank { scheduledKindLabel(item.kind) },
            dueLabel = formatDate(item.dueDate),
            amount = item.amount,
            kind = PlannedKind.SCHEDULED,
        )
    }
    val recurring = document.seed.array("recurring").mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        if (item.bool("active") == false || item.string("status") in setOf("paused", "stopped")) return@mapNotNull null
        val id = item.string("id") ?: return@mapNotNull null
        val name = item.string("name") ?: id
        val amount = item.number("amount") ?: 0.0
        val firstExpected = item.string("firstExpectedDate")
        val day = item.number("day")?.toInt()
        val due = firstExpected?.takeIf(String::isNotBlank) ?: day?.let { "Κάθε μήνα, ημέρα $it" } ?: "Επαναλαμβανόμενο"
        PlannedItem(id, name, due, amount, PlannedKind.RECURRING)
    }
    return (scheduled + recurring).sortedBy { it.dueLabel }.take(20)
}

private fun accountLabel(
    accountId: String?,
    fromAccountId: String?,
    toAccountId: String?,
    names: Map<String, String>,
): String = when {
    fromAccountId != null && toAccountId != null -> "${names[fromAccountId] ?: fromAccountId} → ${names[toAccountId] ?: toAccountId}"
    accountId != null -> names[accountId] ?: accountId
    else -> "—"
}

private fun accountKindLabel(kind: String): String = when (kind) {
    "cash" -> "Μετρητά"
    "bank" -> "Τράπεζα"
    "savings" -> "Αποταμίευση"
    "credit" -> "Πίστωση"
    else -> kind.ifBlank { "Λογαριασμός" }
}

private fun cardKindLabel(kind: String): String = when (kind) {
    "debit" -> "Χρεωστική"
    "prepaid" -> "Προπληρωμένη"
    "credit" -> "Πιστωτική"
    else -> kind.ifBlank { "Κάρτα" }
}

private fun scheduledKindLabel(kind: String): String = when (kind) {
    "expense" -> "Προγραμματισμένο έξοδο"
    "income" -> "Προγραμματισμένο έσοδο"
    "transfer" -> "Προγραμματισμένη μεταφορά"
    else -> "Προγραμματισμένη κίνηση"
}

private fun legacyTypeLabel(type: String): String = when (type) {
    "income" -> "Έσοδο"
    "expense" -> "Έξοδο"
    "transfer" -> "Μεταφορά"
    "adjustment" -> "Προσαρμογή"
    else -> type
}

private fun eventKindLabel(kind: String): String = when (kind) {
    "expense" -> "Έξοδο"
    "income" -> "Έσοδο"
    "transfer" -> "Μεταφορά"
    "saving_cash_offset" -> "Αποταμίευση"
    "withdrawal" -> "Ανάληψη"
    "refund" -> "Επιστροφή"
    "lending" -> "Δανεισμός"
    "repayment" -> "Αποπληρωμή"
    "card_purchase" -> "Αγορά με κάρτα"
    "card_payment" -> "Πληρωμή κάρτας"
    "reconciliation" -> "Συμφωνία υπολοίπου"
    "split" -> "Μοίρασμα"
    else -> kind
}

private fun formatDate(raw: String): String {
    val date = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() ?: return raw
    return date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("el-GR")))
}

private fun monthLabel(month: YearMonth): String = month.atDay(1)
    .format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("el-GR")))

private fun Double.toPlainMoney(): String = if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.US, "%.2f", this)
