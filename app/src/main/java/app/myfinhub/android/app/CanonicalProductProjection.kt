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
import app.myfinhub.android.core.data.creditDebtForCardAt
import app.myfinhub.android.core.data.categoryTotals
import app.myfinhub.android.core.data.categoryContributionsBetween
import app.myfinhub.android.core.data.effectiveLegacyTransactions
import app.myfinhub.android.core.data.loanOutstanding
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.core.data.number
import app.myfinhub.android.core.data.overallBudget
import app.myfinhub.android.core.data.receivableOutstanding
import app.myfinhub.android.core.data.settingsObject
import app.myfinhub.android.core.data.string
import app.myfinhub.android.core.ui.financialAccountDisplayName
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.feature.activity.ActivityAccountOption
import app.myfinhub.android.feature.activity.ActivityCategoryOption
import app.myfinhub.android.feature.activity.ActivityFilter
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.home.HomeAccount
import app.myfinhub.android.feature.home.HomeAccountGroup
import app.myfinhub.android.feature.home.HomeAttentionItem
import app.myfinhub.android.feature.home.HomeAttentionTone
import app.myfinhub.android.feature.home.HomeMonthFlow
import app.myfinhub.android.feature.home.HomeRecentItem
import app.myfinhub.android.feature.home.HomeRecentTone
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomeUpcomingItem
import app.myfinhub.android.feature.insights.InsightCategory
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.TrendPoint
import app.myfinhub.android.feature.money.MoneyAccount
import app.myfinhub.android.feature.money.MoneyCard
import app.myfinhub.android.feature.money.MoneyCardActivity
import app.myfinhub.android.feature.money.MoneyCardActivityKind
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.VaultState
import app.myfinhub.android.feature.plan.BudgetDraft
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
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

private val PRIMARY_HOME_ACCOUNT_IDS = listOf("cash", "piraeus-payroll", "piraeus-savings")

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
    val eventChronology = document.state.array("events").mapNotNull { raw ->
    val event = raw as? JsonObject ?: return@mapNotNull null
    val id = event.string("id") ?: return@mapNotNull null
    id to (event.string("createdAt") ?: event.string("updatedAt") ?: event.string("date").orEmpty())
}.toMap()
val contributionsById = document.categoryContributionsBetween("0001-01-01", "9999-12-31")
    .groupBy { it.transactionId }
val activityItems = buildActivityItems(legacy, events, accountNames, eventChronology).map { item ->
    item.copy(categoryContributions = contributionsById[item.id].orEmpty()
        .groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amount } })
}

    val oldHome = previous?.homeState
    val primaryHomeAccountIds = PRIMARY_HOME_ACCOUNT_IDS.toSet()
    val homeTrendBalances = (6L downTo 0L).map { offset ->
        document.accountBalances(today.minusDays(offset).toString())
    }
    val rawHomeAccounts = accounts.filter { it.kind != "credit" }.map { account ->
        val provider = financialProvider(account.id, account.name)
        HomeAccount(
            id = account.id,
            name = financialAccountDisplayName(account.id, account.name, account.kind),
            role = accountKindLabel(account.kind),
            institution = provider?.institutionLabel,
            balance = balances[account.id] ?: 0.0,
            group = if (account.kind == "savings" || account.excludeFromAvailable) {
                HomeAccountGroup.SAVINGS
            } else {
                HomeAccountGroup.LIQUID
            },
            isPrimary = account.id in primaryHomeAccountIds,
            balanceTrend = homeTrendBalances.map { dailyBalances -> dailyBalances[account.id] ?: 0.0 },
        )
    }
    val homeAccountsById = rawHomeAccounts.associateBy(HomeAccount::id)
    val homeAccounts = PRIMARY_HOME_ACCOUNT_IDS.mapNotNull(homeAccountsById::get) +
        rawHomeAccounts.filterNot { it.id in primaryHomeAccountIds }
    val homeRecentItems = activityItems.take(6).map { item ->
        HomeRecentItem(
            id = item.id,
            title = item.title,
            subtitle = item.accountLabel,
            dateLabel = item.dateLabel,
            amount = item.amount,
            tone = when (item.kind) {
                ActivityKind.INCOME -> HomeRecentTone.INCOME
                ActivityKind.TRANSFER -> HomeRecentTone.TRANSFER
                else -> HomeRecentTone.EXPENSE
            },
        )
    }
    val pendingScheduled = scheduled.filter { it.status == "pending" }
    val home = HomeUiState(
        amountsVisible = oldHome?.amountsVisible ?: false,
        accounts = homeAccounts,
        recentItems = homeRecentItems,
        attentionItems = pendingScheduled
            .filter { it.kind == "expense" && it.dueDate.isNotBlank() && it.dueDate <= asOf }
            .sortedBy { it.dueDate }
            .take(3)
            .map { item ->
                HomeAttentionItem(
                    id = item.id,
                    title = item.note.ifBlank { scheduledKindLabel(item.kind) },
                    reason = "Προγραμματισμένη υποχρέωση σε εκκρεμότητα.",
                    dueLabel = if (item.dueDate == asOf) "Σήμερα" else "Καθυστερημένη",
                    tone = HomeAttentionTone.URGENT,
                )
            },
        upcomingItems = pendingScheduled
            .filter { it.kind == "expense" && it.dueDate > asOf }
            .sortedBy { it.dueDate }
            .take(4)
            .map { item ->
                HomeUpcomingItem(
                    id = item.id,
                    title = item.note.ifBlank { scheduledKindLabel(item.kind) },
                    dateLabel = formatDate(item.dueDate),
                    amount = -item.amount,
                )
            },
        monthFlow = HomeMonthFlow(
            income = flow.income,
            expense = flow.expense,
            saving = flow.saving,
            budget = budget?.amount ?: 0.0,
        ),
        quickEntryOpen = oldHome?.quickEntryOpen ?: false,
        selectedQuickEntryType = oldHome?.selectedQuickEntryType,
    )

    val quickEntry = projectQuickEntryState(
        document = document,
        today = today,
        previous = previous?.quickEntryState,
    )

    val oldActivity = previous?.activityState
    val activityAccountOptions = accounts
        .filter { it.kind != "credit" }
        .map { account -> ActivityAccountOption(account.id, account.name) }
    val activity = ActivityUiState(
        query = oldActivity?.query.orEmpty(),
        filter = oldActivity?.filter ?: ActivityFilter.ALL,
        categoryFilter = oldActivity?.categoryFilter,
        dateFrom = oldActivity?.dateFrom,
        dateTo = oldActivity?.dateTo,
        accountFilterId = oldActivity?.accountFilterId?.takeIf { selectedId ->
            activityAccountOptions.any { it.id == selectedId }
        },
        selectedId = oldActivity?.selectedId?.takeIf { id -> activityItems.any { it.id == id } },
        items = activityItems,
        expenseCategories = quickEntry.expenseCategories.map { ActivityCategoryOption(it.name, it.subcategories) },
        incomeCategories = quickEntry.incomeCategories.map { ActivityCategoryOption(it.name, it.subcategories) },
        accountOptions = activityAccountOptions,
    )

    val activeCards = document.canonicalCards().filter { it.active }
    val bankIdsByCard = document.cards().associate { it.id to it.bankId }
    val globalCreditOutstanding = (-(balances[CREDIT_ACCOUNT_ID] ?: 0.0)).coerceAtLeast(0.0)
    val money = MoneyUiState(
        accounts = accounts.filter { it.kind != "credit" }.map { account ->
            val provider = financialProvider(account.id, account.name)
            MoneyAccount(
                id = account.id,
                name = financialAccountDisplayName(account.id, account.name, account.kind),
                balance = balances[account.id] ?: 0.0,
                kind = accountKindLabel(account.kind),
                institution = provider?.institutionLabel,
            )
        },
        cards = activeCards.map { card ->
            val eventOutstanding = document.creditDebtForCardAt(card.id, asOf)
            val cardActivity = events
                .filter { event -> event.cardId == card.id && event.kind in setOf("card_purchase", "card_payment") }
                .sortedWith(compareByDescending<CanonicalEvent> { it.date }.thenByDescending { eventChronology[it.id].orEmpty() }.thenByDescending { it.id })
                .map { event ->
                    val payment = event.kind == "card_payment"
                    MoneyCardActivity(
                        id = event.id,
                        dateLabel = formatDate(event.date),
                        title = event.note.ifBlank { if (payment) "Πληρωμή κάρτας" else event.category ?: "Αγορά" },
                        amount = if (payment) abs(event.amount) else -abs(event.amount),
                        kind = if (payment) MoneyCardActivityKind.PAYMENT else MoneyCardActivityKind.PURCHASE,
                    )
                }
            MoneyCard(
                id = card.id,
                nickname = card.nickname.ifBlank { card.network.ifBlank { "Κάρτα" } },
                last4 = card.last4.orEmpty(),
                kind = cardKindLabel(card.kind),
                currentBalance = eventOutstanding,
                limit = card.creditLimit,
                vaultState = if (card.vaultRef.isNullOrBlank()) VaultState.LOCKED else VaultState.AVAILABLE,
                network = card.network.ifBlank { "VISA" },
                bankId = bankIdsByCard[card.id].orEmpty(),
                canonicalKind = card.kind,
                activity = cardActivity,
            )
        },
        savingsGoal = null,
        savingsCurrent = accounts.filter { it.kind == "savings" }.sumOf { balances[it.id] ?: 0.0 },
        loanOutstanding = document.loanOutstanding(),
        lendingReceivable = document.receivableOutstanding(),
        aggregateCreditOutstanding = globalCreditOutstanding,
    )

    val plan = projectCanonicalPlanState(
        document = document,
        today = today,
        previous = previous?.planState,
    )

    val insights = projectCanonicalInsightsState(
    document = document,
    today = today,
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

private data class ActivitySortRow(
    val date: String,
    val chronology: String,
    val sourceOrder: Int,
    val item: ActivityItem,
)

private fun buildActivityItems(
    legacy: List<CanonicalLegacyTransaction>,
    events: List<CanonicalEvent>,
    accountNames: Map<String, String>,
    eventChronology: Map<String, String>,
): List<ActivityItem> {
    val rows = buildList {
        legacy.forEachIndexed { index, tx ->
            val kind = when (tx.type) {
                "income" -> ActivityKind.INCOME
                "transfer" -> ActivityKind.TRANSFER
                else -> ActivityKind.EXPENSE
            }
            add(ActivitySortRow(
                date = tx.date,
                chronology = tx.date,
                sourceOrder = index,
                item = ActivityItem(
                    id = tx.id,
                    dateLabel = formatDate(tx.date),
                    kind = kind,
                    title = tx.category ?: tx.note.ifBlank { legacyTypeLabel(tx.type) },
                    subtitle = tx.note.ifBlank { legacyTypeLabel(tx.type) },
                    amount = if (tx.type == "expense") -tx.amount else tx.amount,
                    accountLabel = accountLabel(tx.accountId, tx.fromAccountId, tx.toAccountId, accountNames),
                    category = tx.category,
                    subcategory = tx.subcategory,
                    rawDate = tx.date,
                    accountId = tx.accountId,
                    fromAccountId = tx.fromAccountId,
                    toAccountId = tx.toAccountId,
                ),
            ))
        }
        events.forEachIndexed { index, event ->
            add(ActivitySortRow(
                date = event.date,
                chronology = eventChronology[event.id].orEmpty().ifBlank { event.date },
                sourceOrder = index,
                item = ActivityItem(
                    id = event.id,
                    dateLabel = formatDate(event.date),
                    kind = eventActivityKind(event.kind),
                    title = event.category ?: event.note.ifBlank { eventKindLabel(event.kind) },
                    subtitle = event.note.ifBlank { eventKindLabel(event.kind) },
                    amount = eventDisplayAmount(event),
                    accountLabel = accountLabel(event.accountId, event.fromAccountId, event.toAccountId, accountNames),
                    category = event.category,
                    subcategory = event.subcategory,
                    rawDate = event.date,
                    accountId = event.accountId,
                    fromAccountId = event.fromAccountId,
                    toAccountId = event.toAccountId,
                ),
            ))
        }
    }
    return rows.sortedWith(
        compareByDescending<ActivitySortRow> { it.date }
            .thenByDescending { it.chronology }
            .thenByDescending { it.sourceOrder }
            .thenByDescending { it.item.id },
    ).map(ActivitySortRow::item)
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

private fun buildPlannedItems(document: CanonicalFinanceDocument): List<PlannedItem> {
    val scheduled = document.canonicalScheduled().filter { it.status == "pending" }.map { item ->
        PlannedItem(
            id = item.id,
            title = item.note.ifBlank { scheduledKindLabel(item.kind) },
            dueLabel = formatDate(item.dueDate),
            amount = item.amount,
            kind = PlannedKind.SCHEDULED,
            flow = when (item.kind) {
                "income" -> PlannedFlow.INCOME
                "transfer" -> PlannedFlow.TRANSFER
                else -> PlannedFlow.OBLIGATION
            },
        )
    }
    // Canonical RecurringItem represents recurring costs/subscriptions in the shared schema.
    val recurring = document.seed.array("recurring").mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        if (item.bool("active") == false || item.string("status") in setOf("paused", "stopped")) return@mapNotNull null
        val id = item.string("id") ?: return@mapNotNull null
        val due = item.string("firstExpectedDate")?.takeIf(String::isNotBlank)
            ?: item.number("day")?.toInt()?.let { "Κάθε μήνα, ημέρα $it" }
            ?: "Επαναλαμβανόμενο"
        PlannedItem(
            id = id,
            title = item.string("name") ?: id,
            dueLabel = due,
            amount = item.number("amount") ?: 0.0,
            kind = PlannedKind.RECURRING,
            flow = PlannedFlow.OBLIGATION,
        )
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
    "virtual" -> "Virtual"
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

private fun Double.toPlainMoney(): String = if (this % 1.0 == 0.0) {
    toLong().toString()
} else {
    String.format(Locale.US, "%.2f", this)
}
