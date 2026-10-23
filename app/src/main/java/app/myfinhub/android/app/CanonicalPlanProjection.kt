package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalAccount
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.availableMoney
import app.myfinhub.android.core.data.cashFlowForecast
import app.myfinhub.android.core.data.activeCanonicalRecurringItems
import app.myfinhub.android.core.data.allCanonicalRecurringItems
import app.myfinhub.android.core.data.canonicalWebRecurringManagementOrder
import app.myfinhub.android.core.data.canonicalRecurringPaymentHistory
import app.myfinhub.android.core.data.canonicalRecurringActualPayments
import app.myfinhub.android.core.data.canonicalTypicalRecurringPaymentDay
import app.myfinhub.android.core.data.nextCanonicalRecurringDate
import app.myfinhub.android.core.data.bool
import app.myfinhub.android.core.data.canonicalAccounts
import app.myfinhub.android.core.data.canonicalScheduled
import app.myfinhub.android.core.data.canonicalScheduledManagementOrder
import app.myfinhub.android.core.data.canonicalLifecycle
import app.myfinhub.android.core.data.number
import app.myfinhub.android.core.data.overallBudget
import app.myfinhub.android.core.data.categoryBudgetSpending
import app.myfinhub.android.core.data.webBudgetProgress
import app.myfinhub.android.core.data.canonicalTransactionRuleOverview
import app.myfinhub.android.core.data.string
import app.myfinhub.android.core.data.settingsObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import app.myfinhub.android.core.ui.financialAccountDisplayName
import app.myfinhub.android.feature.plan.BudgetDraft
import app.myfinhub.android.feature.plan.CategoryBudget
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlanningRule
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.plan.PlannedUrgency
import app.myfinhub.android.feature.plan.RecurringManageItem
import app.myfinhub.android.feature.plan.RecurringActualPaymentRow
import app.myfinhub.android.feature.plan.ScheduledManageItem
import app.myfinhub.android.feature.plan.ScheduledAccountOption
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds the production Plan projection from canonical finance data.
 *
 * The forecast is intentionally explainable: current available money + expected income − pending
 * obligations ± the net available-money impact of scheduled transfers. Recurring obligations are
 * included once per next occurrence. Independently keyed scheduled/recurring records are retained:
 * matching display fields do not establish a canonical relationship between obligations.
 */
internal fun projectCanonicalPlanState(
    document: CanonicalFinanceDocument,
    today: LocalDate,
    previous: PlanUiState?,
): PlanUiState {
    val month = YearMonth.from(today)
    val canonicalBudget = document.overallBudget(month.toString())
    val budgetProgressRows = document.webBudgetProgress(month.toString(), today.toString())
    // The Web category picker uses the owner's canonical expense taxonomy, not fixed Android labels.
    val expenseCategoryTree = document.settingsObject().array("expenseCategoryTree")
        .mapNotNull { (it as? JsonObject)?.string("name")?.trim() }
        .filter { it.isNotEmpty() }
    val flatExpenseCategories = document.settingsObject().array("expenseCategories")
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        .filter { it.isNotEmpty() }
    val allowedBudgetCategories =
        (if (expenseCategoryTree.isNotEmpty()) expenseCategoryTree else flatExpenseCategories)
            .distinct()
    val categorizedBudgetTotal = document.categoryBudgetSpending(month.toString(), today.toString())
        .values.sum().coerceAtLeast(0.0)
    // H7 production forecast is a fixed, explainable 30-day window. Legacy preview-only
    // horizon controls must never change canonical production semantics.
    val horizonDays = 30
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
    // Web portfolio windows remain a separate projection from the existing H7 spendable headline.
    val webForecasts = if (document.canonicalAccounts().any { it.kind != "credit" }) {
        listOf(30, 60, 90).map { document.cashFlowForecast(today, it) }
    } else {
        emptyList()
    }
    val webThirty = webForecasts.firstOrNull()?.projectedPortfolio ?: 0.0

    val recurringIdentityCounts = document.allCanonicalRecurringItems().groupingBy { it.id }.eachCount()
    val management = document.canonicalWebRecurringManagementOrder(today).map { recurring ->
        val payments = document.canonicalRecurringPaymentHistory(recurring.id)
        // Historical duplicate IDs remain viewable but cannot uniquely own payments.
        val unique = recurringIdentityCounts[recurring.id] == 1
        val actualToDate = if (unique) document.canonicalRecurringActualPayments(
            recurring.id, "0001-01-01", today.toString(),
        ) else null
        val actualThisMonth = if (unique) document.canonicalRecurringActualPayments(
            recurring.id, month.atDay(1).toString(), today.toString(),
        ) else null
        RecurringManageItem(
            id = recurring.id,
            title = recurring.name,
            amount = recurring.amount,
            status = recurring.effectiveStatus,
            nextDueDateIso = document.nextCanonicalRecurringDate(recurring, today)?.toString().orEmpty(),
            category = recurring.category,
            accountId = recurring.accountId,
            cadenceLabel = recurring.cadenceLabel,
            logoAssetKey = recurring.logoAssetKey,
            source = recurring.source,
            endDateIso = recurring.endDate,
            paymentCount = payments.paymentCount,
            lastPaymentDateIso = payments.lastPaymentDateIso,
            typicalPaymentDay = document.canonicalTypicalRecurringPaymentDay(recurring),
            lastActualPaymentDateIso = actualToDate?.lastPaymentDateIso,
            lastActualPaymentAmount = actualToDate?.lastPaymentAmount,
            actualPaidThisMonth = actualThisMonth?.takeIf { it.count > 0 }?.totalAmount,
            actualPaymentsThisMonth = actualThisMonth?.count ?: 0,
            actualPaymentHistory = actualToDate?.records.orEmpty().map { record ->
                RecurringActualPaymentRow(
                    eventId = record.eventId, dateIso = record.dateIso, amount = record.amount,
                )
            },
            amountMode = recurring.amountMode,
            estimateMin = recurring.estimateMin,
            estimateMax = recurring.estimateMax,
            recurrenceUnit = recurring.recurrenceUnit ?: "month",
            recurrenceInterval = recurring.recurrenceInterval,
            day = recurring.day,
            firstExpectedDateIso = recurring.firstExpectedDate,
        )
    }
    return PlanUiState(
        items = items,
        recurringManageItems = management,
        scheduledAccounts = document.canonicalAccounts()
            .filter { it.kind != "credit" }
            .map { account ->
                ScheduledAccountOption(
                    account.id,
                    financialAccountDisplayName(account.id,account.name,account.kind,account.providerId),
                )
            },
        scheduledManageItems = document.canonicalScheduledManagementOrder().map { row ->
            ScheduledManageItem(
                id = row.id,
                title = row.note.ifBlank { scheduledPlanKindLabel(row.kind) },
                kind = row.kind,
                dueDateIso = row.dueDate,
                amount = row.amount,
                status = row.canonicalLifecycle(today.toString()),
                accountId = row.accountId,
                fromAccountId = row.fromAccountId,
                toAccountId = row.toAccountId,
            )
        },
        forecastWindows = webForecasts.map { f ->
            app.myfinhub.android.feature.plan.ForecastWindow(
                days = f.horizonDays,
                label = f.horizonDays.toString() + " ημέρες",
                expectedIncome = f.movements.sumOf { it.portfolioDelta.coerceAtLeast(0.0) },
                expectedOutflow = -f.movements.sumOf { it.portfolioDelta.coerceAtMost(0.0) },
                balanceDeltaFromThirtyDays = f.projectedPortfolio - webThirty,
                projectedPortfolio = f.projectedPortfolio,
                minimumPortfolio = f.minimumPortfolio,
                accountsWithLowBalance = f.accounts.count { it.firstLowDate != null },
                accountsWithNegativeBalance = f.accounts.count { it.firstNegativeDate != null },
                omittedSourceCount = f.omitted.size,
                omittedWarnings = f.omitted,
                sourceCounts = f.sourceCounts,
                currentPortfolio = f.currentPortfolio,
                minimumPortfolioDateIso = f.minimumPortfolioDate.toString(),
                insufficientData = f.insufficientData,
                // Never resample or rederive canonical projections in the UI.
                portfolioTimeline = f.points.map { point ->
                    app.myfinhub.android.feature.plan.ForecastPortfolioPoint(
                        dateIso = point.date.toString(),
                        portfolio = point.portfolio,
                        balancesByAccount = point.balances,
                    )
                },
                accountBreakdown = f.accounts.map { account ->
                    val real = accountsById[account.accountId]
                    app.myfinhub.android.feature.plan.ForecastAccountBreakdown(
                        accountId = account.accountId,
                        accountLabel = real?.let {
                            financialAccountDisplayName(it.id, it.name, it.kind, it.providerId)
                        } ?: account.accountId,
                        current = account.current,
                        projected = account.projected,
                        minimum = account.minimum,
                        firstLowDateIso = account.firstLowDate?.toString(),
                        firstNegativeDateIso = account.firstNegativeDate?.toString(),
                    )
                },
                knownMovements = f.movements.map { movement ->
                    app.myfinhub.android.feature.plan.ForecastKnownMovement(
                        id = movement.id,
                        dateIso = movement.date.toString(),
                        label = movement.label,
                        source = movement.source,
                        portfolioDelta = movement.portfolioDelta,
                        accountImpacts = movement.legs.map { leg ->
                            val real = accountsById[leg.accountId]
                            app.myfinhub.android.feature.plan.ForecastAccountImpact(
                                accountId = leg.accountId,
                                accountLabel = real?.let {
                                    financialAccountDisplayName(it.id, it.name, it.kind, it.providerId)
                                } ?: leg.accountId,
                                delta = leg.amount,
                            )
                        },
                    )
                },
            )
        },
        // Read-only Web Settings > Rules. Existing history is never rewritten.
        rules = document.canonicalTransactionRuleOverview().map { entry ->
            PlanningRule(
                id = entry.id, title = entry.name,
                description = "Όταν " + entry.condition + " · Τότε " +
                    entry.action + " · " + entry.scopeLabel,
                enabled = entry.enabled, warning = entry.invalidReason,
                historicalMatchCount = entry.historicalMatchCount,
            )
        },
        budgetCategories = allowedBudgetCategories,
        categoryBudgets = budgetProgressRows.filter { it.scope == "category" && !it.category.isNullOrBlank() }
            .map { row ->
                CategoryBudget(
                    id = row.id,
                    name = row.category.orEmpty(),
                    monthlyLimitText = row.limit.toPlainPlanMoney(),
                    alertThresholdText = (row.alertThreshold * 100).roundToInt().toString(),
                    spent = row.used,
                    rawSpent = row.rawUsed,
                    remaining = row.remaining,
                    status = row.status,
                )
            },
        budget = BudgetDraft(
            monthlyLimitText = canonicalBudget?.amount?.toPlainPlanMoney() ?: "",
            alertThresholdText = (canonicalBudget?.alertThreshold ?: 80).toString(),
        ),
        forecastHorizonDays = horizonDays,
        forecastStartDateIso = today.toString(),
        forecastEndDateIso = horizonEnd.toString(),
        forecastStartBalance = startBalance,
        forecastExpectedIncome = forecastIncome,
        forecastObligations = forecastObligations,
        forecastTransferImpact = transferImpact,
        forecastEndBalance = startBalance + forecastIncome - forecastObligations + transferImpact,
        forecastEndDateLabel = horizonEnd.format(
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("el-GR")),
        ),
        budgetSpent = categorizedBudgetTotal,
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
        account.id to financialAccountDisplayName(account.id, account.name, account.kind, account.providerId)
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

    val recurring = document.activeCanonicalRecurringItems().map { item ->
        val dueDate = document.nextCanonicalRecurringDate(item, today)
        PlannedItem(
            id = item.id,
            title = item.name,
            dueLabel = dueDate?.let { formatPlanDueDate(it, today) } ?: "Επαναλαμβανόμενο",
            dueDateIso = dueDate?.toString().orEmpty(),
            amount = item.amount,
            kind = PlannedKind.RECURRING,
            flow = PlannedFlow.OBLIGATION,
            category = item.category,
            accountLabel = accountNames[item.accountId].orEmpty(),
            note = item.note,
            urgency = urgencyFor(dueDate, today),
        )
    }

    return (scheduled + recurring)
        .sortedWith(
            compareBy<PlannedItem> { it.dueDateIso.ifBlank { "9999-12-31" } }
                .thenBy { it.title.lowercase(Locale.forLanguageTag("el-GR")) }
                .thenBy { it.id },
        )
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
