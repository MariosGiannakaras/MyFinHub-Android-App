package app.myfinhub.android.app

import app.myfinhub.android.core.data.CREDIT_ACCOUNT_ID
import app.myfinhub.android.core.data.CanonicalEvent
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalLegacyTransaction
import app.myfinhub.android.core.data.accountBalances
import app.myfinhub.android.core.data.dashboardAccountHistory
import app.myfinhub.android.core.data.dashboardHistoryStart
import app.myfinhub.android.core.data.dashboardPreviousMonthHistory
import app.myfinhub.android.core.data.dashboardBalanceChange
import app.myfinhub.android.core.data.isSavingsAccount
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.availableMoney
import app.myfinhub.android.core.data.canonicalAccounts
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.data.canonicalSavingsGoals
import app.myfinhub.android.core.data.creditStatementViews
import app.myfinhub.android.core.data.canonicalEvents
import app.myfinhub.android.core.data.canonicalScheduled
import app.myfinhub.android.core.data.creditDebtForCardAt
import app.myfinhub.android.core.data.categoryContributionsBetween
import app.myfinhub.android.core.data.effectiveLegacyTransactions
import app.myfinhub.android.core.data.legacyFlowImpact
import app.myfinhub.android.core.data.eventFlowImpact
import app.myfinhub.android.core.data.loanOutstanding
import app.myfinhub.android.core.data.canonicalOperationalMonthlyFlow
import app.myfinhub.android.core.data.number
import app.myfinhub.android.core.data.overallBudget
import app.myfinhub.android.core.data.receivableOutstanding
import app.myfinhub.android.core.data.canonicalLoanBalanceRows
import app.myfinhub.android.core.data.canonicalLoanDefinition
import app.myfinhub.android.core.data.isOwnLoan
import app.myfinhub.android.core.data.canonicalLendingPersonRows
import app.myfinhub.android.core.data.canonicalLendingHistoryRows
import app.myfinhub.android.core.data.canonicalLoanPaymentHistory
import app.myfinhub.android.core.data.canonicalSavingsBreakdown
import app.myfinhub.android.core.data.webSavingsMonthlyTargetSnapshot
import app.myfinhub.android.core.data.webSavingsMonthlyTrend
import app.myfinhub.android.core.data.canonicalSavingsHistoryPresentation
import app.myfinhub.android.core.data.settingsObject
import app.myfinhub.android.core.data.string
import app.myfinhub.android.core.ui.financialAccountDisplayName
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.feature.activity.ActivityAccountOption
import app.myfinhub.android.feature.activity.ActivityCategoryOption
import app.myfinhub.android.feature.activity.ActivityFilter
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityMonthlyOverview
import app.myfinhub.android.feature.activity.ActivitySplitPart
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.activity.greekActivityLabelOrder
import app.myfinhub.android.feature.activity.activityTypeLabel
import app.myfinhub.android.feature.activity.cleanLegacyActivityNote
import app.myfinhub.android.feature.home.HomeAccount
import app.myfinhub.android.feature.home.HomeAccountGroup
import app.myfinhub.android.feature.home.HomeAttentionItem
import app.myfinhub.android.feature.home.HomeAttentionTone
import app.myfinhub.android.feature.home.HomeMonthFlow
import app.myfinhub.android.feature.home.HomeRecentItem
import app.myfinhub.android.feature.home.HomeRecentTone
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomePrimaryRole
import app.myfinhub.android.feature.home.HomeUpcomingItem
import app.myfinhub.android.feature.insights.InsightCategory
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.TrendPoint
import app.myfinhub.android.feature.money.MoneyAccount
import app.myfinhub.android.feature.money.SavingsHistoryItem
import app.myfinhub.android.feature.money.LoanItem
import app.myfinhub.android.feature.money.LoanPaymentHistoryItem
import app.myfinhub.android.feature.money.LendingHistoryItem
import app.myfinhub.android.feature.money.LendingItem
import app.myfinhub.android.feature.money.MoneyCard
import app.myfinhub.android.feature.money.CardAnnualRateVersion
import app.myfinhub.android.feature.money.MoneyCardActivity
import app.myfinhub.android.feature.money.MoneyCardActivityKind
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.VaultState
import app.myfinhub.android.feature.plan.BudgetDraft
import app.myfinhub.android.feature.plan.PlanUiState
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

/**
 * Exact Web TransactionsPage rowBalance: first real account in the event/legacy
 * ledger, balance from the canonical statement at transaction date inclusive.
 * Run only for an opened detail: computing for all ledger rows would repeatedly
 * scan canonical transactions. Pending/invalid/synthetic rows show no balance.
 */
internal fun canonicalActivityBalanceAtDate(
    document: CanonicalFinanceDocument,
    item: ActivityItem,
): Double? {
    if (item.pendingSync) return null
    val date = item.rawDate.take(10)
    if (runCatching { LocalDate.parse(date) }.isFailure) return null
    // Web TransactionsPage derives event accountIds exclusively from actual
    // ledger legs. Optional accountId/from/to are *legacy* transaction fields
    // and can disagree with event legs after a reconciliation/import.
    val account = if (item.isLegacyTransaction) {
        sequenceOf(item.accountId, item.fromAccountId, item.toAccountId)
            .filterNotNull().firstOrNull(String::isNotBlank)
    } else {
        item.legAccountIds.firstOrNull { it.isNotBlank() && it != CREDIT_ACCOUNT_ID }
    } ?: return null
    if (account == CREDIT_ACCOUNT_ID) return null
    if (document.canonicalAccounts().none { it.id == account && it.kind != "credit" }) return null
    return document.accountBalances(date)[account]
}

/** Mirrors web accountSelection: exact daily cash, payroll and savings semantic slots. */
internal fun primaryHomeRoles(accounts: List<app.myfinhub.android.core.data.CanonicalAccount>): Map<HomePrimaryRole, String> {
    fun choose(test: (app.myfinhub.android.core.data.CanonicalAccount) -> Boolean): String? =
        accounts.firstOrNull { test(it) && it.showInQuickChoices }?.id
            ?: accounts.firstOrNull(test)?.id
    return buildMap {
        choose { it.kind == "cash" && it.cashRole == "daily" }?.let { put(HomePrimaryRole.CASH, it) }
        choose { it.kind == "bank" && it.bankAccountCategory == "payroll" }?.let { put(HomePrimaryRole.PAYROLL, it) }
        choose { it.isSavingsAccount() }?.let { put(HomePrimaryRole.SAVINGS, it) }
    }
}


/** Web TransactionsPage has an arbitrary user-selected reporting month.
 * Produce only the requested month and its comparison; neither ledger amounts
 * nor canonical FinanceData are mutated by navigation.
 */
internal fun projectCanonicalActivityMonthOverview(
    document: CanonicalFinanceDocument,
    selectedMonth: YearMonth,
    legacy: List<CanonicalLegacyTransaction> = document.effectiveLegacyTransactions(),
    events: List<CanonicalEvent> = document.canonicalEvents(),
): ActivityMonthlyOverview {
    val selected = selectedMonth.toString()
    val previous = selectedMonth.minusMonths(1).toString()
    val flow = document.canonicalOperationalMonthlyFlow(selected)
    val prior = document.canonicalOperationalMonthlyFlow(previous)
    fun sourceCount(month: String): Int =
        legacy.count { it.date.startsWith("$month-") } +
            events.count { it.date.startsWith("$month-") &&
                it.kind != "card_purchase" && it.kind != "card_payment" }
    return ActivityMonthlyOverview(
        month = selected, income = flow.income, expense = flow.expense, net = flow.net,
        transactionCount = sourceCount(selected),
        previousIncome = prior.income, previousExpense = prior.expense,
        previousNet = prior.net, previousTransactionCount = sourceCount(previous),
    )
}

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
    // Web DashboardPage reads selectors.selectMonthlyFlow -> savings.operationalMonthlyFlow.
    // Pay & Save must not inflate operating expense or income on the Home summary.
    val flow = document.canonicalOperationalMonthlyFlow(month, asOf)
    val currentSavingsBreakdown = document.canonicalSavingsBreakdown(month, asOf)
    val budget = document.overallBudget(month)
    val scheduled = document.canonicalScheduled()
    val events = document.canonicalEvents()
    val legacy = document.effectiveLegacyTransactions()
    // Never collapse chronology by ID. Real events may share an imported ID
    // or another event ID; preserve the corresponding raw source ordinal.
    val eventChronology = document.state.array("events").mapNotNull { raw ->
        val event = raw as? JsonObject ?: return@mapNotNull null
        if (event.string("id") == null) return@mapNotNull null
        event.string("createdAt") ?: event.string("updatedAt") ?: event.string("date").orEmpty()
    }
// Never key financial metadata by a bare ID. Canonical seed rows can share an
// ID, and a legacy ID can collide with an event ID. Such a join incorrectly
// multiplies another row's expense and corrupts Activity/Analytics reports.
val contributionsBySource = document.categoryContributionsBetween("0001-01-01", "9999-12-31")
    .groupBy { it.sourceKind to it.sourceOrdinal }
val legacySignedFlow = legacy.map { tx ->
    val impact = document.legacyFlowImpact(tx)
    impact.income to impact.expense
}
val eventSignedFlow = events.map { event ->
    val impact = eventFlowImpact(event)
    impact.income to impact.expense
}
val activityCardLabels = document.canonicalCards().associate { card ->
    val base = card.nickname.ifBlank { card.network.ifBlank { "Κάρτα" } }
    val last4 = card.last4.orEmpty()
    card.id to if (last4.isBlank()) base else "$base • $last4"
}
val legacyOverrideIds = (document.state["overrides"] as? JsonObject)?.keys.orEmpty()
val uniqueEventCounterpartyIds = document.state.array("events")
    .filterIsInstance<JsonObject>()
    .groupBy { it.string("id").orEmpty() }
    .filterKeys(String::isNotBlank)
    .mapNotNull { (id, rows) ->
        if (rows.size == 1) id to rows.single().string("counterpartyId") else null
    }.toMap()
val activityItems = buildActivityItems(
    legacy, events, accountNames, activityCardLabels, legacyOverrideIds,
).map { item ->
    val sourceKind = if (item.isLegacyTransaction) "legacy" else "event"
    val signed = if (item.isLegacyTransaction) {
        legacySignedFlow.getOrNull(item.canonicalSourceOrdinal)
    } else {
        eventSignedFlow.getOrNull(item.canonicalSourceOrdinal)
    }
    item.copy(
        categoryContributions = contributionsBySource[sourceKind to item.canonicalSourceOrdinal].orEmpty()
            .groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amount } },
        financeIncomeContribution = signed?.first,
        financeExpenseContribution = signed?.second,
        counterpartyId = if (item.isLegacyTransaction) null
            else uniqueEventCounterpartyIds[item.id],
    )
}

    val oldHome = previous?.homeState
    val homeRoles = primaryHomeRoles(accounts)
    val rolesById = homeRoles.entries.associate { (role, id) -> id to role }
    val historyStart = dashboardHistoryStart(YearMonth.from(today))
    val monthBoundaryIndex = (YearMonth.from(today).atDay(1).toEpochDay() - historyStart.toEpochDay()).toInt()
    val history = document.dashboardAccountHistory(homeRoles.values.toList(), historyStart, today)
    val previousMonth = YearMonth.from(today).minusMonths(1)
    val previousSavings = homeRoles[HomePrimaryRole.SAVINGS]?.let {
        document.dashboardPreviousMonthHistory(it, YearMonth.from(today), today)
    }.orEmpty()
    val savingsAccounts = accounts.filter { it.isSavingsAccount() }
    val savingsGoal = if (savingsAccounts.size == 1) {
        document.state.array("savingsGoals").mapNotNull { (it as? JsonObject)?.number("targetAmount") }
            .filter { it.isFinite() && it > 0.0 }
            .singleOrNull()
    } else null
    val savingsRate = document.settingsObject().number("savingsTargetRate") ?: 0.2
    val rawHomeAccounts = accounts.filter { it.kind != "credit" }.map { account ->
        val provider = financialProvider(account.providerId ?: account.id, account.provider ?: account.name, account.providerId)
        val role = rolesById[account.id]
        HomeAccount(
            id = account.id,
            name = financialAccountDisplayName(account.id, account.name, account.kind, account.providerId),
            role = accountKindLabel(account.kind),
            institution = provider?.institutionLabel ?: account.provider,
            providerId = account.providerId,
            balance = balances[account.id] ?: 0.0,
            group = if (account.isSavingsAccount() || account.excludeFromAvailable) HomeAccountGroup.SAVINGS
                else HomeAccountGroup.LIQUID,
            isPrimary = role != null,
            primaryRole = role,
            balanceTrend = history[account.id] ?: emptyList(),
            monthBoundaryIndex = if (role != null) monthBoundaryIndex else 0,
            balanceChange30d = if (role != null) document.dashboardBalanceChange(account.id, today) else null,
            previousMonthTrend = if (role == HomePrimaryRole.SAVINGS) previousSavings else emptyList(),
            savingsTarget = if (role == HomePrimaryRole.SAVINGS) savingsGoal else null,
            savingsTargetRate = if (role == HomePrimaryRole.SAVINGS) savingsRate else null,
        )
    }
    val homeAccountsById = rawHomeAccounts.associateBy(HomeAccount::id)
    val homeAccounts = HomePrimaryRole.entries.mapNotNull { role ->
        homeRoles[role]?.let(homeAccountsById::get)
    } + rawHomeAccounts.filterNot { it.id in rolesById }
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
                    sourceLabel = item.accountId?.let { accountNames[it] ?: it },
                    dueDateLabel = formatDate(item.dueDate),
                    amount = -abs(item.amount),
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

    // When a user picked a reporting month it survives fresh server FinanceData
    // projections. Otherwise the overview follows the real current calendar.
    val oldActivity = previous?.activityState
    val selectedMonth = oldActivity?.takeIf { it.overviewMonthManuallySelected }
        ?.monthOverview?.month?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.from(today)
    val monthOverview = projectCanonicalActivityMonthOverview(
        document, selectedMonth, legacy, events,
    )
    // Automatic current-month mode rolls forward on a new calendar month;
    // manually selected historic months never change behind the user's back.
    val monthRolledWhileSelected = oldActivity?.monthSourceSelected == true &&
        !oldActivity.overviewMonthManuallySelected &&
        oldActivity.monthOverview?.month != selectedMonth.toString()
    val activityAccountOptions = accounts
        .filter { it.kind != "credit" }
        .map { account -> ActivityAccountOption(account.id, account.name) }
    val activity = ActivityUiState(
        query = oldActivity?.query.orEmpty(),
        filter = oldActivity?.filter ?: ActivityFilter.ALL,
        sortOldestFirst = oldActivity?.sortOldestFirst ?: false,
        ledgerCategoryFilter = oldActivity?.ledgerCategoryFilter,
        ledgerDateFrom = if (monthRolledWhileSelected) selectedMonth.atDay(1).toString()
            else oldActivity?.ledgerDateFrom,
        ledgerDateTo = if (monthRolledWhileSelected) selectedMonth.atEndOfMonth().toString()
            else oldActivity?.ledgerDateTo,
        monthSourceSelected = oldActivity?.monthSourceSelected == true,
        overviewMonthManuallySelected = oldActivity?.overviewMonthManuallySelected == true,
        // Web transaction type options never include card-only events.
        // Discard stale pre-parity card filters on reprojection rather than
        // restoring an invisible selection with permanently empty results.
        typeFilterId = oldActivity?.typeFilterId?.takeUnless {
            it == "card_purchase" || it == "card_payment"
        },
        categoryFilter = oldActivity?.categoryFilter,
        dateFrom = oldActivity?.dateFrom,
        dateTo = oldActivity?.dateTo,
        accountFilterId = oldActivity?.accountFilterId?.takeIf { selectedId ->
            activityAccountOptions.any { it.id == selectedId }
        },
        selectedId = oldActivity?.selectedId?.takeIf { id ->
            activityItems.count { it.id == id } == 1
        },
        items = activityItems,
        monthOverview = monthOverview,
        expenseCategories = quickEntry.expenseCategories.map { ActivityCategoryOption(it.name, it.subcategories) },
        incomeCategories = quickEntry.incomeCategories.map { ActivityCategoryOption(it.name, it.subcategories) },
        accountOptions = activityAccountOptions,
    )

    val canonicalCardProfiles = document.canonicalCards()
    val rateHistoryByCard = document.state.array("cards")
        .filterIsInstance<JsonObject>()
        .mapNotNull { row ->
            val id = row.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val versions = row.array("creditRateHistory")
                .filterIsInstance<JsonObject>()
                .mapNotNull { version ->
                    val date = version.string("effectiveFrom")
                        ?: return@mapNotNull null
                    val rate = version.number("annualPercent")
                        ?.takeIf { it.isFinite() && it in 0.0..100.0 }
                        ?: return@mapNotNull null
                    CardAnnualRateVersion(date, rate)
                }.sortedBy(CardAnnualRateVersion::effectiveFrom)
            id to versions
        }.toMap()
    // Web cards.ts allCards() orders by createdAt and then localeCompare(id).
    // Keep this display ordering local to active cards: changing the canonical
    // source array would reorder unrelated archived/history projections.
    val cardIdCollator = java.text.Collator.getInstance(Locale.US)
    val activeCards = canonicalCardProfiles.filter { it.active }
        .sortedWith(Comparator { left, right ->
            val byCreation = left.createdAt.orEmpty().compareTo(right.createdAt.orEmpty())
            if (byCreation != 0) byCreation
            else cardIdCollator.compare(left.id, right.id)
        })
    val globalCreditOutstanding = (-(balances[CREDIT_ACCOUNT_ID] ?: 0.0)).coerceAtLeast(0.0)
    val money = MoneyUiState(
        accounts = accounts.filter { it.kind != "credit" }.map { account ->
            val provider = financialProvider(account.providerId ?: account.id, account.provider ?: account.name, account.providerId)
            MoneyAccount(
                id = account.id,
                name = financialAccountDisplayName(account.id, account.name, account.kind, account.providerId),
                balance = balances[account.id] ?: 0.0,
                kind = accountKindLabel(account.kind),
                institution = provider?.institutionLabel ?: account.provider,
                canonicalKind = account.kind,
                excludeFromAvailable = account.excludeFromAvailable,
                bankAccountCategory = account.bankAccountCategory,
                cashRole = account.cashRole,
                cashType = account.cashType,
                providerId = account.providerId,
            )
        },
        // Web archivedCardsForBank: descending (archivedAt ?? updatedAt).
        // On equal dates preserve allCards() createdAt/id chronology, not the
        // physical order of historical imported card records.
        archivedCards = canonicalCardProfiles.filterNot { it.active }
            .sortedWith(Comparator { left, right ->
                val leftTime = left.archivedAt ?: left.updatedAt
                val rightTime = right.archivedAt ?: right.updatedAt
                val byArchive = rightTime.orEmpty().compareTo(leftTime.orEmpty())
                if (byArchive != 0) byArchive else {
                    val byCreation = left.createdAt.orEmpty().compareTo(right.createdAt.orEmpty())
                    if (byCreation != 0) byCreation
                    else cardIdCollator.compare(left.id, right.id)
                }
            })
            .map { card ->
            // Archived card identity and debt survive: secrets stay locked and absent.
            MoneyCard(
                id = card.id,
                nickname = card.nickname.ifBlank { card.network.ifBlank { "Κάρτα" } },
                last4 = card.last4.orEmpty(),
                kind = cardKindLabel(card.kind),
                currentBalance = document.creditDebtForCardAt(card.id, asOf),
                limit = card.creditLimit,
                vaultState = VaultState.LOCKED,
                network = card.network.ifBlank { "VISA" },
                bankId = card.bankId,
                canonicalKind = card.kind,
                designId = card.designId,
                formFactor = card.formFactor,
                archivedAt = card.archivedAt,
            )
        },
        creditStatementsByCard = activeCards.filter { it.kind == "credit" }
            .associate { card -> card.id to document.creditStatementViews(card.id, asOf) },
        cards = activeCards.map { card ->
            val eventOutstanding = document.creditDebtForCardAt(card.id, asOf)
            // A card may have multiple events sharing an imported ID. Sort
            // by each event's own source ordinal chronology, not an ID lookup.
            val cardActivity = events.withIndex()
                .filter { (_, event) -> event.cardId == card.id && event.kind in setOf("card_purchase", "card_payment") }
                .sortedWith(
                    compareByDescending<IndexedValue<CanonicalEvent>> { it.value.date }
                        .thenByDescending { eventChronology.getOrNull(it.index).orEmpty() }
                        .thenByDescending { it.value.id },
                )
                .map { (_, event) ->
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
                bankId = card.bankId,
                canonicalKind = card.kind,
                designId = card.designId,
                formFactor = card.formFactor,
                holderName = card.holderName,
                statementClosingDay = card.statementClosingDay,
                statementDueDay = card.statementDueDay,
                statementBoundaryRule = card.statementBoundaryRule,
                annualRates = rateHistoryByCard[card.id].orEmpty(),
                archivedAt = card.archivedAt,
                activity = cardActivity,
            )
        },
        savingsGoal = savingsGoal,
        savingsGoalItems = document.canonicalSavingsGoals(today).map { goal ->
            app.myfinhub.android.feature.money.SavingsGoalItem(
                id = goal.id,
                name = goal.name,
                targetAmount = goal.targetAmount,
                targetDate = goal.targetDate,
                backingBalance = goal.backingBalance,
                progressPercent = goal.progressPercent,
                deadlinePast = goal.deadlinePast,
            )
        },
        savingsCurrent = savingsAccounts.sumOf { balances[it.id] ?: 0.0 },
        loanOutstanding = document.loanOutstanding(asOf),
        lendingReceivable = document.receivableOutstanding(asOf),
        loans = document.canonicalLoanBalanceRows(asOf).map { loan ->
            LoanItem(
                id = loan.id,
                name = loan.name,
                lender = loan.provider,
                remaining = loan.remaining,
                monthlyPayment = loan.installment,
                nextPaymentLabel = loan.typicalPaymentDay?.let { "Συνήθης ημέρα πληρωμής: $it" }.orEmpty(),
                originalAmount = loan.total,
                remainingInstallments = loan.remainingInstallments,
                isSelfLoan = loan.isSelfLoan,
                liabilityRepayment = loan.isLiabilityRepayment,
                defaultAccountId = loan.defaultAccountId,
                typicalPaymentDay = loan.typicalPaymentDay,
                kind = loan.kind,
                totalInstallments = loan.totalInstallments,
                firstExpectedDate = loan.firstExpectedDate,
                expenseCategory = loan.expenseCategory,
            )
        },
        loanPaymentsById = document.canonicalLoanPaymentHistory(asOf)
            .mapValues { (_, rows) ->
                rows.map { row ->
                    LoanPaymentHistoryItem(
                        eventId = row.eventId,
                        date = row.date,
                        note = row.note,
                        amount = row.amount,
                        accountId = row.accountId,
                        installmentCount = row.installmentCount,
                        eventKind = row.eventKind,
                    )
                }
            },
        lendingItems = document.canonicalLendingPersonRows(asOf)
            .filter { it.outstanding > 0.005 }
            .map { row ->
                LendingItem(
                    id = "lending-person-" + row.person,
                    personLabel = row.person,
                    amount = row.outstanding,
                    dueLabel = "",
                )
            },
        // Web lending history includes settled people and survives zero balances.
        lendingHistory = document.canonicalLendingHistoryRows(asOf).map { row ->
            LendingHistoryItem(
                id = row.id,
                personLabel = row.person,
                date = row.date,
                action = row.action,
                amount = row.amount,
                note = row.note,
                runningOutstanding = row.runningOutstanding,
                accountId = row.accountId,
                origin = row.origin,
            )
        },
        savingsSources = currentSavingsBreakdown.bySource,
        savingsMonthlyTarget = document.webSavingsMonthlyTargetSnapshot(month, asOf),
        savingsMonthlyTrend = document.webSavingsMonthlyTrend(month, asOf),
        // Web SavingsPage mobile uses the latest 12 dated rows, preserving
        // duplicate historical IDs and a distinct primary user note/source.
        savingsHistory = currentSavingsBreakdown.rows.take(12).map { row ->
            val display = canonicalSavingsHistoryPresentation(row)
            SavingsHistoryItem(
                id = row.id, date = row.date, amount = row.amount,
                primary = display.primary, sourceLabel = display.sourceLabel,
                hasUserNote = display.hasUserNote, origin = row.origin,
                fromAccountId = row.fromAccountId, toAccountId = row.toAccountId,
            )
        },
        aggregateCreditOutstanding = globalCreditOutstanding,
        asOfDate = asOf,
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
    val item: ActivityItem,
)

private fun buildActivityItems(
    legacy: List<CanonicalLegacyTransaction>,
    events: List<CanonicalEvent>,
    accountNames: Map<String, String>,
    cardLabels: Map<String, String>,
    legacyOverrideIds: Set<String>,
): List<ActivityItem> {
    val rows = buildList {
        legacy.forEachIndexed { index, tx ->
            val displayNote = cleanLegacyActivityNote(tx.note)
            val kind = when (tx.type) {
                "income" -> ActivityKind.INCOME
                "transfer" -> ActivityKind.TRANSFER
                else -> ActivityKind.EXPENSE
            }
            add(ActivitySortRow(
                date = tx.date,
                item = ActivityItem(
                    id = tx.id,
                    dateLabel = formatDate(tx.date),
                    kind = kind,
                    title = tx.category ?: displayNote.ifBlank { legacyTypeLabel(tx.type) },
                    subtitle = displayNote.ifBlank { legacyTypeLabel(tx.type) },
                    amount = if (tx.type == "expense") -tx.amount else tx.amount,
                    accountLabel = legacyActivityAccountPath(
                        tx.accountId, tx.fromAccountId, tx.toAccountId, accountNames,
                    ),
                    category = tx.category,
                    subcategory = tx.subcategory,
                    note = displayNote,
                    rawDate = tx.date,
                    accountId = tx.accountId,
                    fromAccountId = tx.fromAccountId,
                    toAccountId = tx.toAccountId,
                    canonicalKind = tx.type,
                    typeLabel = activityTypeLabel(tx.type),
                    isLegacyTransaction = true,
                    canonicalSourceOrdinal = index,
                    isLegacyOverridden = tx.id in legacyOverrideIds,
                ),
            ))
        }
        events.forEachIndexed { index, event ->
            // Web TransactionsPage uses each event's canonical ledger legs when
            // identifying its accounts; accountId/from/to may all be absent.
            // Web TransactionsPage preserves exact historical leg account IDs,
            // including empty and whitespace-only keys. Only its synthetic
            // credit-card account is excluded; never coerce/mutate FinanceData.
            val legAccounts = event.legs.map { it.accountId }
                .filter { it != "credit-card" }.distinct()
            // Web TransactionsPage accountText(e.legs): show only actual
            // non-credit ledger account IDs, even if optional from/to hints
            // claim another route. Empty real legs display an em dash.
            val displayedAccounts = legAccounts
                .joinToString(" → ") { accountNames[it] ?: it }
                .ifEmpty { "—" }
            add(ActivitySortRow(
                date = event.date,
                item = ActivityItem(
                    id = event.id,
                    dateLabel = formatDate(event.date),
                    kind = eventActivityKind(event.kind),
                    title = event.category ?: event.note.ifBlank { eventKindLabel(event.kind) },
                    subtitle = event.note.ifBlank { eventKindLabel(event.kind) },
                    amount = eventDisplayAmount(event),
                    accountLabel = displayedAccounts,
                    category = event.category,
                    subcategory = event.subcategory,
                    note = event.note,
                    rawDate = event.date,
                    accountId = event.accountId,
                    fromAccountId = event.fromAccountId,
                    toAccountId = event.toAccountId,
                    cardId = event.cardId,
                    cardLabel = event.cardId?.let(cardLabels::get),
                    canonicalKind = event.kind,
                    canonicalSourceOrdinal = index,
                    typeLabel = activityTypeLabel(event.kind),
                    legAccountIds = legAccounts.toSet(),
                    splitParts = if (event.kind == "split") event.parts.map { part ->
                        ActivitySplitPart(
                            label = part.label,
                            category = part.category,
                            subcategory = part.subcategory,
                            amount = part.amount,
                        )
                    } else emptyList(),
                ),
            ))
        }
    }
    // Web TransactionsPage sorts by date, then canonical row ID. Its stable
    // Array.sort retains original seed-before-event source order when both
    // keys collide. createdAt/updatedAt belong to other projections (such
    // as card activity) and must not reorder this transaction ledger.
    // Web TransactionsPage ties use id.localeCompare(...), NOT Kotlin's
    // Unicode String.compareTo: mixed-case imported IDs otherwise reorder.
    // Array.sort is stable, preserving original source order for equal keys.
    val idOrder = greekActivityLabelOrder()
    return rows.sortedWith(
        compareByDescending<ActivitySortRow> { it.date }
            .thenComparator { left, right -> idOrder.compare(right.item.id, left.item.id) },
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

/** Web TransactionsPage legacy accountIds is [accountId, from, to].filter(Boolean).
 * Retain the original order and repeated IDs; neither a route inference nor a
 * distinct set is equivalent when all three optional fields are populated.
 */
private fun legacyActivityAccountPath(
    accountId: String?,
    fromAccountId: String?,
    toAccountId: String?,
    names: Map<String, String>,
): String = listOfNotNull(accountId, fromAccountId, toAccountId)
    .filter(String::isNotEmpty)
    .joinToString(" → ") { names[it] ?: it }
    .ifEmpty { "—" }

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
