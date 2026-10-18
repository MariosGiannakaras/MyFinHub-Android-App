package app.myfinhub.android.app

import app.myfinhub.android.feature.plan.RecurringManageItem
import app.myfinhub.android.feature.quickentry.QuickEntryUiState

/**
 * Web recurringAccountChoice(availableAccountIds, preferred item account,
 * settings.defaultExpenseAccount): never open a payment on a credit account
 * or silently reuse a deleted default. An empty eligible set fails closed.
 */
internal fun recurringQuickEntryAccountChoice(
    item: RecurringManageItem,
    quickEntry: QuickEntryUiState,
): String? {
    val available = quickEntry.accounts.filter { it.kind != "credit" }.map { it.id }
        .filter(String::isNotBlank)
    return sequenceOf(item.accountId, quickEntry.defaultExpenseAccountId)
        .firstOrNull { it.isNotBlank() && it in available }
        ?: available.firstOrNull()
}
