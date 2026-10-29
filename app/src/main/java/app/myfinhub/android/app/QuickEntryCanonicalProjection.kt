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
    val canonicalAccounts = document.canonicalAccounts().filter { it.kind != "credit" }
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

    val cards = document.canonicalCards()
        .filter { it.active && it.kind == "credit" }
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
    val selectedAccount = base.accountId.takeIf(accountIds::contains)
        ?: if (base.kind == QuickEntryKind.INCOME) defaultIncomeId else defaultExpenseId
    val selectedFromCandidate = base.fromAccountId.takeIf(accountIds::contains) ?: defaultExpenseId
    val selectedFrom = if (base.kind == QuickEntryKind.SAVING) {
        accountOptions.firstOrNull {
            it.id == selectedFromCandidate && it.isSavingContributionSource
        }?.id ?: accountOptions.firstOrNull(QuickEntryAccountOption::isSavingContributionSource)?.id.orEmpty()
    } else selectedFromCandidate
    val selectedTo = base.toAccountId.takeIf { it in accountIds && it != selectedFrom }
        ?: accountOptions.firstOrNull { option -> option.isSavingsDestination && option.id != selectedFrom }?.id
        ?: defaultDestinationId
    val selectedCard = base.cardId.takeIf(cardIds::contains) ?: cards.firstOrNull()?.id.orEmpty()
    val selectedAsOf = runCatching { LocalDate.parse(base.dateText) }.getOrDefault(today).toString()
    // Web recommendedPayableStatement precedence: due > closed > open.
    // Only the user may choose a link; never silently allocate a repayment.
    val sourceStatementIdCounts = document.canonicalStatementSourceIdCounts()
    val creditStatements = document.creditStatementViews(selectedCard, selectedAsOf)
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
