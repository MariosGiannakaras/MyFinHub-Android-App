package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.canonicalAccounts
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.data.settingsObject
import app.myfinhub.android.core.data.string
import app.myfinhub.android.feature.quickentry.QuickEntryAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryCardOption
import app.myfinhub.android.feature.quickentry.QuickEntryCategoryOption
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntrySplitPartDraft
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
        it.kind == "savings" && it.id != defaultExpenseId
    }?.id ?: accountOptions.firstOrNull { it.id != defaultExpenseId }?.id.orEmpty()

    val cards = document.canonicalCards()
        .filter { it.active && it.kind == "credit" }
        .map { card ->
            val baseLabel = card.nickname.ifBlank { card.network.ifBlank { "Πιστωτική" } }
            val label = card.last4?.takeIf(String::isNotBlank)?.let { "$baseLabel • $it" } ?: baseLabel
            QuickEntryCardOption(card.id, label)
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
    val activeCategories = if (base.kind == QuickEntryKind.INCOME) incomeCategories else expenseCategories
    val category = base.category.takeIf { current -> activeCategories.any { it.name == current } }
        ?: activeCategories.firstOrNull()?.name.orEmpty()
    val subcategory = base.subcategory.takeIf { value ->
        value.isBlank() || activeCategories.firstOrNull { it.name == category }?.subcategories?.contains(value) == true
    }.orEmpty()
    val selectedAccount = base.accountId.takeIf(accountIds::contains)
        ?: if (base.kind == QuickEntryKind.INCOME) defaultIncomeId else defaultExpenseId
    val selectedFrom = base.fromAccountId.takeIf(accountIds::contains) ?: defaultExpenseId
    val selectedTo = base.toAccountId.takeIf { it in accountIds && it != selectedFrom }
        ?: accountOptions.firstOrNull { option -> option.kind == "savings" && option.id != selectedFrom }?.id
        ?: defaultDestinationId
    val selectedCard = base.cardId.takeIf(cardIds::contains) ?: cards.firstOrNull()?.id.orEmpty()
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

    return base.copy(
        dateText = base.dateText.ifBlank { today.toString() },
        category = category,
        subcategory = subcategory,
        accountId = selectedAccount,
        fromAccountId = selectedFrom,
        toAccountId = selectedTo,
        cardId = selectedCard,
        splitParts = splitParts,
        accounts = accountOptions,
        creditCards = cards,
        expenseCategories = expenseCategories,
        incomeCategories = incomeCategories,
        defaultExpenseAccountId = defaultExpenseId,
        defaultIncomeAccountId = defaultIncomeId,
    )
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
