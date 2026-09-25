package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceMutation
import app.myfinhub.android.core.data.CanonicalTransactionEntryDraft
import app.myfinhub.android.core.data.CanonicalTransactionSplitDraft
import app.myfinhub.android.core.data.createCanonicalTransactionEntryMutation
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState

internal fun createQuickEntryCanonicalMutation(
    document: CanonicalFinanceDocument,
    state: QuickEntryUiState,
    eventId: String,
    nowIso: String,
): CanonicalFinanceMutation {
    require(state.validationMessage == null) { "Η φόρμα κίνησης δεν είναι έγκυρη." }

    val actualBalance = if (state.kind == QuickEntryKind.RECONCILIATION) {
        state.actualBalanceText.replace(',', '.').toDoubleOrNull()
    } else {
        null
    }
    val draft = CanonicalTransactionEntryDraft(
        kind = state.kind.canonicalKind,
        date = state.dateText,
        amount = when (state.kind) {
            QuickEntryKind.RECONCILIATION -> null
            else -> state.amount
        },
        note = state.note,
        category = state.category.takeIf { state.kind.usesCategory },
        subcategory = state.subcategory.takeIf { state.kind.usesCategory && it.isNotBlank() },
        accountId = state.accountId.takeIf { state.kind.needsPrimaryAccount },
        fromAccountId = state.fromAccountId.takeIf {
            state.kind.needsTransferAccounts || state.kind == QuickEntryKind.CARD_PAYMENT
        },
        toAccountId = state.toAccountId.takeIf { state.kind.needsTransferAccounts },
        person = state.person.takeIf {
            state.kind == QuickEntryKind.LENDING || state.kind == QuickEntryKind.REPAYMENT
        },
        expectedReturnDate = state.expectedReturnDateText.takeIf {
            state.kind == QuickEntryKind.LENDING && it.isNotBlank()
        },
        cardId = state.cardId.takeIf { state.kind.needsCard },
        actualBalance = actualBalance,
        parts = if (state.kind == QuickEntryKind.SPLIT) {
            state.splitParts.map { part ->
                CanonicalTransactionSplitDraft(
                    id = part.id,
                    label = part.label,
                    category = part.category,
                    subcategory = part.subcategory.takeIf(String::isNotBlank),
                    amount = requireNotNull(part.amount) { "Κάθε μέρος χρειάζεται έγκυρο ποσό." },
                )
            }
        } else {
            emptyList()
        },
    )

    return createCanonicalTransactionEntryMutation(
        document = document,
        draft = draft,
        eventId = eventId,
        nowIso = nowIso,
    )
}
