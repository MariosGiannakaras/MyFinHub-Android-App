package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.canonicalAccounts
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.data.canonicalCounterpartyDirectory
import app.myfinhub.android.core.data.canonicalCounterpartyKindForEvent
import app.myfinhub.android.core.data.allCanonicalRecurringItems
import app.myfinhub.android.core.data.creditStatementViews
import app.myfinhub.android.core.data.canonicalStatementSourceIdCounts
import app.myfinhub.android.core.data.matchesFinancialProvider
import app.myfinhub.android.core.data.creditDebtForCardAt
import app.myfinhub.android.core.data.lendingOutstandingForPerson
import app.myfinhub.android.core.data.settingsObject
import app.myfinhub.android.core.data.string
import app.myfinhub.android.feature.quickentry.QuickEntryAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryCardOption
import app.myfinhub.android.feature.quickentry.QuickEntryCategoryOption
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntrySplitPartDraft
import app.myfinhub.android.feature.quickentry.QuickEntryStatementOption
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun projectQuickEntryState(
    document: CanonicalFinanceDocument,
    today: LocalDate,
    previous: QuickEntryUiState?,
): QuickEntryUiState {
    val allAccounts = document.canonicalAccounts()
    val accountIdCounts = allAccounts.groupingBy { it.id }.eachCount()
    // Do not offer an imported duplicate or whitespace-ambiguous finance target.
    val canonicalAccounts = allAccounts.filter {
        it.kind != "credit" && it.id.isNotBlank() && it.id == it.id.trim() &&
            accountIdCounts[it.id] == 1
    }
    val accountOptions = canonicalAccounts.map { account ->
        QuickEntryAccountOption(
            id = account.id,
            label = account.name,
            kind = account.kind,
            bankAccountCategory = account.bankAccountCategory,
        )
    }
    val accountIds = accountOptions.map { it.id }.toSet()
    val settings = document.settingsObject()
    val defaultExpenseId = settings.string("defaultExpenseAccount")
        ?.takeIf(accountIds::contains)
        ?: accountOptions.firstOrNull()?.id.orEmpty()
    val defaultIncomeId = settings.string("defaultIncomeAccount")
        ?.takeIf(accountIds::contains)
        ?: defaultExpenseId
    val defaultDestinationId = accountOptions.firstOrNull {
        it.isSavingsDestination && it.id != defaultExpenseId
    }?.id ?: accountOptions.firstOrNull { it.id != defaultExpenseId }?.id.orEmpty()

    val allCards = document.canonicalCards()
    val cardIdCounts = allCards.groupingBy { it.id }.eachCount()
    val cards = allCards.filter {
        it.active && it.kind == "credit" && it.id.isNotBlank() &&
            it.id == it.id.trim() && cardIdCounts[it.id] == 1
    }
        .map { card ->
            val baseLabel = card.nickname.ifBlank { card.network.ifBlank { "Πιστωτική" } }
            val label = card.last4?.takeIf(String::isNotBlank)?.let { "$baseLabel • $it" } ?: baseLabel
            QuickEntryCardOption(
                id = card.id,
                label = label,
                provider = cardProviderLabel(card.network),
            )
        }
    val cardIds = cards.map { it.id }.toSet()

    val expenseCategories = categoryOptions(
        settings = settings,
        treeKey = "expenseCategoryTree",
        flatKey = "expenseCategories",
    )
    val incomeCategories = categoryOptions(
        settings = settings,
        treeKey = "incomeCategoryTree",
        flatKey = "incomeCategories",
    )

    val base = previous ?: QuickEntryUiState(dateText = today.toString())
    val linkedRecurring = document.allCanonicalRecurringItems().firstOrNull {
        it.id == base.recurringId
    }
    // Linked imported subscriptions can legitimately use a category no longer
    // in the current taxonomy; keep that exact Web category in the edit form.
    val scopedExpenseCategories = if (linkedRecurring?.category?.isNotBlank() == true &&
        expenseCategories.none { it.name == linkedRecurring.category }
    ) expenseCategories + QuickEntryCategoryOption(linkedRecurring.category) else expenseCategories
    val activeCategories = if (base.kind == QuickEntryKind.INCOME) incomeCategories else scopedExpenseCategories
    val category = base.category.takeIf { current -> activeCategories.any { it.name == current } }
        ?: activeCategories.firstOrNull()?.name.orEmpty()
    val subcategory = base.subcategory.takeIf { value ->
        value.isBlank() || activeCategories.firstOrNull { it.name == category }?.subcategories?.contains(value) == true
    }.orEmpty()
    // A remote refresh must not silently move a dirty transaction to another
    // source card or account. Keep the old ID invalid until the owner reselects.
    val preserveDirty = previous?.dirty == true && !previous.persisted && !previous.pendingSync
    val selectedAccount = base.accountId.takeIf(accountIds::contains)
        ?: base.accountId.takeIf { preserveDirty && base.kind.needsPrimaryAccount && it.isNotBlank() }
        ?: if (base.kind == QuickEntryKind.INCOME) defaultIncomeId else defaultExpenseId
    val selectedCard = base.cardId.takeIf(cardIds::contains)
        ?: base.cardId.takeIf { base.kind.needsCard && it.isNotBlank() &&
            (preserveDirty || allCards.any { card -> card.id == it }) }
        ?: cards.firstOrNull()?.id.orEmpty()
    val cardPaymentSources = projectCardPaymentSourceAccountIds(document, selectedCard)
    val selectedFromCandidate = base.fromAccountId.takeIf(accountIds::contains)
        ?: base.fromAccountId.takeIf {
            preserveDirty && (base.kind.needsTransferAccounts || base.kind == QuickEntryKind.CARD_PAYMENT) &&
                it.isNotBlank()
        } ?: defaultExpenseId
    val selectedFrom = when (base.kind) {
        QuickEntryKind.SAVING -> accountOptions.firstOrNull {
            it.id == selectedFromCandidate && it.isSavingContributionSource
        }?.id ?: selectedFromCandidate.takeIf {
            preserveDirty && base.fromAccountId.isNotBlank() && it.isNotBlank()
        } ?: accountOptions.firstOrNull(QuickEntryAccountOption::isSavingContributionSource)?.id.orEmpty()
        QuickEntryKind.CARD_PAYMENT -> selectedFromCandidate.takeIf { it in cardPaymentSources }
            ?: selectedFromCandidate.takeIf { preserveDirty && it.isNotBlank() }
            ?: defaultExpenseId.takeIf { it in cardPaymentSources }
            ?: accountOptions.firstOrNull { it.id in cardPaymentSources }?.id.orEmpty()
        else -> selectedFromCandidate
    }
    val selectedTo = base.toAccountId.takeIf { it in accountIds && it != selectedFrom }
        ?: base.toAccountId.takeIf { preserveDirty && base.kind.needsTransferAccounts && it.isNotBlank() }
        ?: accountOptions.firstOrNull { option -> option.isSavingsDestination && option.id != selectedFrom }?.id
        ?: defaultDestinationId
    val selectedAsOf = runCatching { LocalDate.parse(base.dateText) }.getOrDefault(today).toString()
    // Web recommendedPayableStatement precedence: due > closed > open.
    // Only the user may choose a link; never silently allocate a repayment.
    val creditStatements = projectPayableCreditStatements(document, selectedCard, selectedAsOf)
    val cardPaymentDebt = projectCardPaymentDebt(document, selectedCard, selectedAsOf)
    val lendingOutstanding = document.lendingOutstandingForPerson(base.person, selectedAsOf)
    val selectedStatementId = base.statementId.takeIf { id ->
        creditStatements.count { it.id == id && it.cardId == selectedCard } == 1
    }.orEmpty()
    val splitFallbackCategory = expenseCategories.firstOrNull()?.name ?: "Άλλο"
    val splitParts = base.splitParts.mapIndexed { index, part ->
        val categoryOption = expenseCategories.firstOrNull { it.name == part.category }
        val partCategory = categoryOption?.name ?: splitFallbackCategory
        val partSubcategory = part.subcategory.takeIf { value ->
            value.isBlank() || expenseCategories.firstOrNull { it.name == partCategory }
                ?.subcategories
                ?.contains(value) == true
        }.orEmpty()
        part.copy(
            id = part.id.ifBlank { "part-${index + 1}" },
            category = partCategory,
            subcategory = partSubcategory,
        )
    }.ifEmpty {
        listOf(
            QuickEntrySplitPartDraft("part-1", category = splitFallbackCategory),
            QuickEntrySplitPartDraft("part-2", category = splitFallbackCategory),
        )
    }

    val counterparties = document.canonicalCounterpartyDirectory()
    val matchingCounterpartyId = base.counterpartyId.takeIf { selected ->
        val kind = canonicalCounterpartyKindForEvent(base.kind.canonicalKind)
        kind != null && counterparties.count { it.id == selected && it.kind == kind } == 1
    }.orEmpty()
    return base.copy(
        dateText = base.dateText.ifBlank { today.toString() },
        counterpartyId = matchingCounterpartyId,
        counterparties = counterparties,
        category = category,
        subcategory = subcategory,
        accountId = selectedAccount,
        fromAccountId = selectedFrom,
        toAccountId = selectedTo,
        cardId = selectedCard,
        statementId = selectedStatementId,
        creditStatements = creditStatements,
        cardPaymentSourceAccountIds = cardPaymentSources,
        cardPaymentDebt = cardPaymentDebt,
        lendingOutstanding = lendingOutstanding,
        recurringId = linkedRecurring?.id.orEmpty(),
        splitParts = splitParts,
        accounts = accountOptions,
        creditCards = cards,
        expenseCategories = scopedExpenseCategories,
        incomeCategories = incomeCategories,
        defaultExpenseAccountId = defaultExpenseId,
        defaultIncomeAccountId = defaultIncomeId,
    )
}

/**
 * User actions which change payment card, transaction date, lending person or transaction kind
 * invalidate the statement options calculated at the previous snapshot. Recalculate
 * against the current local canonical projection without doing a server fetch.
 * The ordinary reducer still owns dirty flags and clears stale statement selection.
 */
internal fun reduceQuickEntryWithCanonicalStatements(
    document: CanonicalFinanceDocument,
    state: QuickEntryUiState,
    action: app.myfinhub.android.feature.quickentry.QuickEntryAction,
    today: LocalDate,
): QuickEntryUiState {
    val reduced = app.myfinhub.android.feature.quickentry.reduceQuickEntry(state, action)
    return when (action) {
        is app.myfinhub.android.feature.quickentry.QuickEntryAction.CardChanged,
        is app.myfinhub.android.feature.quickentry.QuickEntryAction.DateChanged,
        is app.myfinhub.android.feature.quickentry.QuickEntryAction.PersonChanged,
        is app.myfinhub.android.feature.quickentry.QuickEntryAction.SelectKind,
        app.myfinhub.android.feature.quickentry.QuickEntryAction.Reset -> {
            val asOf = runCatching { LocalDate.parse(reduced.dateText) }
                .getOrDefault(today).toString()
            val cardPaymentSources = projectCardPaymentSourceAccountIds(document, reduced.cardId)
            val fromId = if (reduced.kind == QuickEntryKind.CARD_PAYMENT) {
                reduced.fromAccountId.takeIf { it in cardPaymentSources }
                    ?: reduced.defaultExpenseAccountId.takeIf { it in cardPaymentSources }
                    ?: reduced.accounts.firstOrNull { it.id in cardPaymentSources }?.id.orEmpty()
            } else reduced.fromAccountId
            reduced.copy(
                fromAccountId = fromId,
                cardPaymentSourceAccountIds = cardPaymentSources,
                cardPaymentDebt = projectCardPaymentDebt(document, reduced.cardId, asOf),
                lendingOutstanding = document.lendingOutstandingForPerson(reduced.person, asOf),
                creditStatements = projectPayableCreditStatements(
                    document, reduced.cardId, asOf,
                ),
            )
        }
        else -> reduced
    }
}

/**
 * The canonical money mutation requires the repayment account to match the
 * selected credit card's authoritative bankId. Never infer bank ownership from
 * display labels or ID prefixes here; share the finance-domain identity matcher.
 */
private fun projectCardPaymentSourceAccountIds(
    document: CanonicalFinanceDocument,
    cardId: String,
): Set<String> {
    val card = document.canonicalCards().singleOrNull { it.id == cardId }
        ?.takeIf { it.active && it.kind == "credit" && it.id.isNotBlank() && it.id == it.id.trim() }
        ?: return emptySet()
    val allAccounts = document.canonicalAccounts()
    val counts = allAccounts.groupingBy { it.id }.eachCount()
    return allAccounts.filter {
        it.kind != "credit" && it.id.isNotBlank() && it.id == it.id.trim() &&
            counts[it.id] == 1 && it.matchesFinancialProvider(card.bankId)
    }
        .map { it.id }.toSet()
}

/**
 * Match the canonical guarded credit-payment mutation's as-of debt formula,
 * including existing legacy card ownership rules. Zero debt remains an explicit
 * value and must not be confused with an unknown/uncalculated UI state.
 */
private fun projectCardPaymentDebt(
    document: CanonicalFinanceDocument,
    cardId: String,
    asOf: String,
): Double? {
    val card = document.canonicalCards().singleOrNull { it.id == cardId }
        ?.takeIf { it.active && it.kind == "credit" && it.id.isNotBlank() && it.id == it.id.trim() }
        ?: return null
    return document.creditDebtForCardAt(card.id, asOf)
}

/** Server-sourced statement IDs must remain unique across every physical source row. */
private fun projectPayableCreditStatements(
    document: CanonicalFinanceDocument,
    cardId: String,
    asOf: String,
): List<QuickEntryStatementOption> {
    val card = document.canonicalCards().singleOrNull { it.id == cardId }
        ?.takeIf { it.active && it.kind == "credit" && it.id.isNotBlank() && it.id == it.id.trim() }
        ?: return emptyList()
    val sourceStatementIdCounts = document.canonicalStatementSourceIdCounts()
    return document.creditStatementViews(card.id, asOf)
        .filter {
            it.id.isNotBlank() && sourceStatementIdCounts[it.id] == 1 &&
                it.remaining.isFinite() && it.remaining > 0.005
        }
        .sortedWith(compareBy<app.myfinhub.android.core.data.CanonicalCreditStatementView> {
            when (it.status) {
                "due" -> 0
                "closed" -> 1
                "open" -> 2
                else -> 3
            }
        }.thenBy { it.dueDate }.thenBy { it.closeDate }.thenBy { it.id })
        .map { statement ->
            val statusLabel = when (statement.status) {
                "due" -> "Ληξιπρόθεσμη"
                "closed" -> "Κλειστή"
                else -> "Ανοιχτή"
            }
            QuickEntryStatementOption(
                id = statement.id,
                cardId = statement.cardId,
                label = statusLabel + " · " + statement.closeDate +
                    " · έως " + statement.dueDate + " · " +
                    String.format(java.util.Locale.forLanguageTag("el-GR"), "%.2f", statement.remaining) + " €",
                remaining = statement.remaining,
            )
        }
}

private fun cardProviderLabel(raw: String): String = when (raw.trim().lowercase()) {
    "visa" -> "Visa"
    "mastercard" -> "Mastercard"
    "amex", "american_express" -> "American Express"
    else -> raw.trim()
}

private fun categoryOptions(
    settings: JsonObject,
    treeKey: String,
    flatKey: String,
): List<QuickEntryCategoryOption> {
    val fromTree = settings.array(treeKey).mapNotNull { raw ->
        val item = raw as? JsonObject ?: return@mapNotNull null
        val name = item.string("name")?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val subcategories = item.array("subcategories")
            .mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
        QuickEntryCategoryOption(name, subcategories)
    }.distinctBy { it.name }
    if (fromTree.isNotEmpty()) return fromTree

    val flat = settings.array(flatKey)
        .mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .map { QuickEntryCategoryOption(it) }
    return flat.ifEmpty { listOf(QuickEntryCategoryOption("Άλλο")) }
}
