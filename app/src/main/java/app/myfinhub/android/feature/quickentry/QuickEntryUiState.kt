package app.myfinhub.android.feature.quickentry

import androidx.lifecycle.ViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class QuickEntryKind(
    val label: String,
    val description: String,
    val canonicalKind: String,
) {
    EXPENSE("Έξοδο", "Πλήρωσα για κάτι", "expense"),
    INCOME("Έσοδο", "Μπήκαν χρήματα", "income"),
    TRANSFER("Μεταφορά", "Μεταξύ λογαριασμών", "transfer"),
    WITHDRAWAL("Ανάληψη", "Τράπεζα προς μετρητά", "withdrawal"),
    SAVING("Αποταμίευση", "Μετέφερα χρήματα στην άκρη", "saving_cash_offset"),
    REFUND("Επιστροφή", "Μου επέστρεψαν χρήματα", "refund"),
    LENDING("Δανεικά", "Πλήρωσα για άλλον", "lending"),
    REPAYMENT("Επιστροφή δανεικών", "Μου επέστρεψαν δανεικά", "repayment"),
    CARD_PURCHASE("Αγορά με κάρτα", "Χρέωση πιστωτικής κάρτας", "card_purchase"),
    CARD_PAYMENT("Πληρωμή κάρτας", "Εξόφληση πιστωτικής", "card_payment"),
    RECONCILIATION("Διόρθωση υπολοίπου", "Συμφωνία με πραγματικό υπόλοιπο", "reconciliation"),
    SPLIT("Σύνθετη αγορά", "Μία πληρωμή, πολλές κατηγορίες", "split"),
    ;

    val needsPrimaryAccount: Boolean
        get() = when (this) {
            EXPENSE, INCOME, REFUND, LENDING, REPAYMENT, RECONCILIATION, SPLIT -> true
            else -> false
        }

    val needsTransferAccounts: Boolean
        get() = this == TRANSFER || this == WITHDRAWAL || this == SAVING

    val needsCard: Boolean
        get() = this == CARD_PURCHASE || this == CARD_PAYMENT

    val usesCategory: Boolean
        get() = when (this) {
            EXPENSE, INCOME, REFUND, LENDING, REPAYMENT, CARD_PURCHASE -> true
            else -> false
        }
}

data class QuickEntryAccountOption(
    val id: String,
    val label: String,
    val kind: String,
)

data class QuickEntryCardOption(
    val id: String,
    val label: String,
)

data class QuickEntryCategoryOption(
    val name: String,
    val subcategories: List<String> = emptyList(),
)

data class QuickEntrySplitPartDraft(
    val id: String,
    val label: String = "",
    val category: String = "Άλλο",
    val subcategory: String = "",
    val amountText: String = "",
) {
    val amount: Double?
        get() = amountText.replace(',', '.').toDoubleOrNull()
}

data class QuickEntryUiState(
    val kind: QuickEntryKind = QuickEntryKind.EXPENSE,
    val amountText: String = "",
    val dateText: String = LocalDate.now().toString(),
    val note: String = "",
    val category: String = "Τρόφιμα",
    val subcategory: String = "",
    val accountId: String = "acc-main",
    val fromAccountId: String = "acc-main",
    val toAccountId: String = "acc-save",
    val cardId: String = "card-credit",
    val person: String = "",
    val expectedReturnDateText: String = "",
    val actualBalanceText: String = "",
    val splitParts: List<QuickEntrySplitPartDraft> = defaultSplitParts(),
    val accounts: List<QuickEntryAccountOption> = defaultAccountOptions(),
    val creditCards: List<QuickEntryCardOption> = listOf(
        QuickEntryCardOption("card-credit", "Πιστωτική • 1881"),
    ),
    val expenseCategories: List<QuickEntryCategoryOption> = defaultExpenseCategories(),
    val incomeCategories: List<QuickEntryCategoryOption> = listOf(
        QuickEntryCategoryOption("Μισθός"),
        QuickEntryCategoryOption("Επιστροφή"),
        QuickEntryCategoryOption("Άλλο"),
    ),
    val defaultExpenseAccountId: String = "acc-main",
    val defaultIncomeAccountId: String = "acc-main",
    val validationMessage: String? = null,
    val savedSummary: String? = null,
    val persisted: Boolean = false,
    val dirty: Boolean = false,
) {
    val amount: Double?
        get() {
            if (kind == QuickEntryKind.SPLIT) {
                val values = splitParts.mapNotNull(QuickEntrySplitPartDraft::amount)
                if (values.size != splitParts.size || values.any { it <= 0.0 || !it.isFinite() }) return null
                return values.sum()
            }
            return amountText.replace(',', '.').toDoubleOrNull()
        }

    val activeCategoryOptions: List<QuickEntryCategoryOption>
        get() = if (kind == QuickEntryKind.INCOME) incomeCategories else expenseCategories

    val activeSubcategoryOptions: List<String>
        get() = activeCategoryOptions.firstOrNull { it.name == category }?.subcategories.orEmpty()

    val splitTotal: Double
        get() = splitParts.mapNotNull(QuickEntrySplitPartDraft::amount)
            .filter { it.isFinite() && it > 0.0 }
            .sum()
}

sealed interface QuickEntryAction {
    data class SelectKind(val kind: QuickEntryKind) : QuickEntryAction
    data class AmountChanged(val value: String) : QuickEntryAction
    data class DateChanged(val value: String) : QuickEntryAction
    data class NoteChanged(val value: String) : QuickEntryAction
    data class CategoryChanged(val value: String) : QuickEntryAction
    data class SubcategoryChanged(val value: String) : QuickEntryAction
    data class AccountChanged(val id: String) : QuickEntryAction
    data class FromAccountChanged(val id: String) : QuickEntryAction
    data class ToAccountChanged(val id: String) : QuickEntryAction
    data class CardChanged(val id: String) : QuickEntryAction
    data class PersonChanged(val value: String) : QuickEntryAction
    data class ExpectedReturnDateChanged(val value: String) : QuickEntryAction
    data class ActualBalanceChanged(val value: String) : QuickEntryAction
    data object AddSplitPart : QuickEntryAction
    data class RemoveSplitPart(val id: String) : QuickEntryAction
    data class SplitPartLabelChanged(val id: String, val value: String) : QuickEntryAction
    data class SplitPartCategoryChanged(val id: String, val value: String) : QuickEntryAction
    data class SplitPartSubcategoryChanged(val id: String, val value: String) : QuickEntryAction
    data class SplitPartAmountChanged(val id: String, val value: String) : QuickEntryAction
    data object Save : QuickEntryAction
    data object Reset : QuickEntryAction
}

fun reduceQuickEntry(state: QuickEntryUiState, action: QuickEntryAction): QuickEntryUiState = when (action) {
    is QuickEntryAction.SelectKind -> selectKind(state, action.kind)
    is QuickEntryAction.AmountChanged -> state.changed(amountText = action.value)
    is QuickEntryAction.DateChanged -> state.changed(dateText = action.value)
    is QuickEntryAction.NoteChanged -> state.changed(note = action.value)
    is QuickEntryAction.CategoryChanged -> state.changed(category = action.value, subcategory = "")
    is QuickEntryAction.SubcategoryChanged -> state.changed(subcategory = action.value)
    is QuickEntryAction.AccountChanged -> state.changed(accountId = action.id)
    is QuickEntryAction.FromAccountChanged -> state.changed(fromAccountId = action.id)
    is QuickEntryAction.ToAccountChanged -> state.changed(toAccountId = action.id)
    is QuickEntryAction.CardChanged -> state.changed(cardId = action.id)
    is QuickEntryAction.PersonChanged -> state.changed(person = action.value)
    is QuickEntryAction.ExpectedReturnDateChanged -> state.changed(expectedReturnDateText = action.value)
    is QuickEntryAction.ActualBalanceChanged -> state.changed(actualBalanceText = action.value)
    QuickEntryAction.AddSplitPart -> addSplitPart(state)
    is QuickEntryAction.RemoveSplitPart -> removeSplitPart(state, action.id)
    is QuickEntryAction.SplitPartLabelChanged -> updateSplitPart(state, action.id) { it.copy(label = action.value) }
    is QuickEntryAction.SplitPartCategoryChanged -> updateSplitPart(state, action.id) {
        it.copy(category = action.value, subcategory = "")
    }
    is QuickEntryAction.SplitPartSubcategoryChanged -> updateSplitPart(state, action.id) {
        it.copy(subcategory = action.value)
    }
    is QuickEntryAction.SplitPartAmountChanged -> updateSplitPart(state, action.id) {
        it.copy(amountText = action.value)
    }
    QuickEntryAction.Reset -> resetDraft(state)
    QuickEntryAction.Save -> validateAndPreview(state)
}

private fun selectKind(state: QuickEntryUiState, kind: QuickEntryKind): QuickEntryUiState {
    val categoryOptions = if (kind == QuickEntryKind.INCOME) state.incomeCategories else state.expenseCategories
    val category = state.category.takeIf { current -> categoryOptions.any { it.name == current } }
        ?: categoryOptions.firstOrNull()?.name.orEmpty()
    val accountId = when (kind) {
        QuickEntryKind.INCOME -> state.defaultIncomeAccountId.takeIf { id -> state.accounts.any { it.id == id } }
        else -> state.defaultExpenseAccountId.takeIf { id -> state.accounts.any { it.id == id } }
    } ?: state.accounts.firstOrNull()?.id.orEmpty()
    val fromId = state.defaultExpenseAccountId.takeIf { id -> state.accounts.any { it.id == id } }
        ?: state.accounts.firstOrNull()?.id.orEmpty()
    val preferredTo = when (kind) {
        QuickEntryKind.WITHDRAWAL -> state.accounts.firstOrNull { it.kind == "cash" && it.id != fromId }
        QuickEntryKind.SAVING -> state.accounts.firstOrNull { it.kind == "savings" && it.id != fromId }
        else -> state.accounts.firstOrNull { it.kind == "savings" && it.id != fromId }
            ?: state.accounts.firstOrNull { it.id != fromId }
    }
    val partCategory = state.expenseCategories.firstOrNull()?.name ?: "Άλλο"

    return state.copy(
        kind = kind,
        category = category,
        subcategory = "",
        accountId = accountId,
        fromAccountId = fromId,
        toAccountId = preferredTo?.id ?: state.toAccountId,
        cardId = state.cardId.takeIf { id -> state.creditCards.any { it.id == id } }
            ?: state.creditCards.firstOrNull()?.id.orEmpty(),
        splitParts = if (kind == QuickEntryKind.SPLIT && state.splitParts.any { it.category.isBlank() }) {
            state.splitParts.map { part -> if (part.category.isBlank()) part.copy(category = partCategory) else part }
        } else {
            state.splitParts
        },
        validationMessage = null,
        savedSummary = null,
        persisted = false,
        dirty = true,
    )
}

private fun QuickEntryUiState.changed(
    amountText: String = this.amountText,
    dateText: String = this.dateText,
    note: String = this.note,
    category: String = this.category,
    subcategory: String = this.subcategory,
    accountId: String = this.accountId,
    fromAccountId: String = this.fromAccountId,
    toAccountId: String = this.toAccountId,
    cardId: String = this.cardId,
    person: String = this.person,
    expectedReturnDateText: String = this.expectedReturnDateText,
    actualBalanceText: String = this.actualBalanceText,
): QuickEntryUiState = copy(
    amountText = amountText,
    dateText = dateText,
    note = note,
    category = category,
    subcategory = subcategory,
    accountId = accountId,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    cardId = cardId,
    person = person,
    expectedReturnDateText = expectedReturnDateText,
    actualBalanceText = actualBalanceText,
    validationMessage = null,
    savedSummary = null,
    persisted = false,
    dirty = true,
)

private fun addSplitPart(state: QuickEntryUiState): QuickEntryUiState {
    if (state.splitParts.size >= 12) return state
    val existing = state.splitParts.map { it.id }.toSet()
    val id = generateSequence(1) { it + 1 }.map { "part-$it" }.first { it !in existing }
    val category = state.expenseCategories.firstOrNull()?.name ?: "Άλλο"
    return state.copy(
        splitParts = state.splitParts + QuickEntrySplitPartDraft(id = id, category = category),
        validationMessage = null,
        savedSummary = null,
        persisted = false,
        dirty = true,
    )
}

private fun removeSplitPart(state: QuickEntryUiState, id: String): QuickEntryUiState {
    if (state.splitParts.size <= 2) return state
    return state.copy(
        splitParts = state.splitParts.filterNot { it.id == id },
        validationMessage = null,
        savedSummary = null,
        persisted = false,
        dirty = true,
    )
}

private fun updateSplitPart(
    state: QuickEntryUiState,
    id: String,
    transform: (QuickEntrySplitPartDraft) -> QuickEntrySplitPartDraft,
): QuickEntryUiState = state.copy(
    splitParts = state.splitParts.map { part -> if (part.id == id) transform(part) else part },
    validationMessage = null,
    savedSummary = null,
    persisted = false,
    dirty = true,
)

private fun resetDraft(state: QuickEntryUiState): QuickEntryUiState {
    val categoryOptions = if (state.kind == QuickEntryKind.INCOME) state.incomeCategories else state.expenseCategories
    val category = categoryOptions.firstOrNull()?.name.orEmpty()
    return state.copy(
        amountText = "",
        dateText = LocalDate.now().toString(),
        note = "",
        category = category,
        subcategory = "",
        person = "",
        expectedReturnDateText = "",
        actualBalanceText = "",
        splitParts = defaultSplitParts(state.expenseCategories.firstOrNull()?.name ?: "Άλλο"),
        validationMessage = null,
        savedSummary = null,
        persisted = false,
        dirty = false,
    )
}

private fun validateAndPreview(state: QuickEntryUiState): QuickEntryUiState {
    val date = runCatching { LocalDate.parse(state.dateText) }.getOrNull()
        ?: return state.invalid("Συμπλήρωσε έγκυρη ημερομηνία.")

    if (state.kind != QuickEntryKind.RECONCILIATION && state.kind != QuickEntryKind.SPLIT) {
        val amount = state.amount
        if (amount == null || amount <= 0.0 || !amount.isFinite()) {
            return state.invalid("Βάλε ποσό μεγαλύτερο από μηδέν.")
        }
    }

    if (state.kind.needsPrimaryAccount && state.accounts.none { it.id == state.accountId }) {
        return state.invalid("Διάλεξε διαθέσιμο λογαριασμό.")
    }

    if (state.kind.needsTransferAccounts || state.kind == QuickEntryKind.CARD_PAYMENT) {
        if (state.accounts.none { it.id == state.fromAccountId }) {
            return state.invalid("Διάλεξε λογαριασμό προέλευσης.")
        }
    }

    if (state.kind.needsTransferAccounts) {
        val destination = state.accounts.firstOrNull { it.id == state.toAccountId }
            ?: return state.invalid("Διάλεξε λογαριασμό προορισμού.")
        if (state.fromAccountId == state.toAccountId) {
            return state.invalid("Οι λογαριασμοί πρέπει να είναι διαφορετικοί.")
        }
        if (state.kind == QuickEntryKind.WITHDRAWAL && destination.kind != "cash") {
            return state.invalid("Η ανάληψη πρέπει να καταλήγει σε λογαριασμό μετρητών.")
        }
        if (state.kind == QuickEntryKind.SAVING && destination.kind != "savings") {
            return state.invalid("Η αποταμίευση πρέπει να καταλήγει σε λογαριασμό αποταμίευσης.")
        }
    }

    if (state.kind.needsCard && state.creditCards.none { it.id == state.cardId }) {
        return state.invalid("Διάλεξε ενεργή πιστωτική κάρτα.")
    }

    if (state.kind.usesCategory && state.activeCategoryOptions.none { it.name == state.category }) {
        return state.invalid("Διάλεξε διαθέσιμη κατηγορία.")
    }
    if (state.subcategory.isNotBlank() && state.subcategory !in state.activeSubcategoryOptions) {
        return state.invalid("Διάλεξε διαθέσιμη υποκατηγορία.")
    }

    if (state.kind == QuickEntryKind.LENDING || state.kind == QuickEntryKind.REPAYMENT) {
        if (state.person.isBlank()) return state.invalid("Συμπλήρωσε το πρόσωπο για τα δανεικά.")
    }
    if (state.kind == QuickEntryKind.LENDING && state.expectedReturnDateText.isNotBlank()) {
        val expected = runCatching { LocalDate.parse(state.expectedReturnDateText) }.getOrNull()
            ?: return state.invalid("Η αναμενόμενη επιστροφή δεν είναι έγκυρη.")
        if (expected.isBefore(date)) {
            return state.invalid("Η αναμενόμενη επιστροφή δεν μπορεί να είναι πριν από την ημερομηνία κίνησης.")
        }
    }

    if (state.kind == QuickEntryKind.RECONCILIATION) {
        val actual = state.actualBalanceText.replace(',', '.').toDoubleOrNull()
        if (actual == null || !actual.isFinite()) {
            return state.invalid("Συμπλήρωσε έγκυρο πραγματικό υπόλοιπο.")
        }
    }

    if (state.kind == QuickEntryKind.SPLIT) {
        if (state.splitParts.size < 2) return state.invalid("Η σύνθετη αγορά χρειάζεται τουλάχιστον δύο μέρη.")
        state.splitParts.forEachIndexed { index, part ->
            val amount = part.amount
            if (amount == null || amount <= 0.0 || !amount.isFinite()) {
                return state.invalid("Το ποσό στο μέρος ${index + 1} πρέπει να είναι θετικό.")
            }
            val category = state.expenseCategories.firstOrNull { it.name == part.category }
                ?: return state.invalid("Διάλεξε κατηγορία για το μέρος ${index + 1}.")
            if (part.subcategory.isNotBlank() && part.subcategory !in category.subcategories) {
                return state.invalid("Διάλεξε διαθέσιμη υποκατηγορία για το μέρος ${index + 1}.")
            }
        }
    }

    val summary = when (state.kind) {
        QuickEntryKind.RECONCILIATION -> "${state.kind.label} · πραγματικό υπόλοιπο ${state.actualBalanceText} €"
        QuickEntryKind.SPLIT -> "${state.kind.label} · ${formatMoney(state.splitTotal)} € · ${state.splitParts.size} μέρη"
        else -> "${state.kind.label} · ${formatMoney(state.amount ?: 0.0)} € · ${state.note.trim().ifBlank { state.kind.label }}"
    }
    return state.copy(
        validationMessage = null,
        savedSummary = summary,
        persisted = false,
    )
}

private fun QuickEntryUiState.invalid(message: String): QuickEntryUiState = copy(
    validationMessage = message,
    savedSummary = null,
    persisted = false,
)

private fun formatMoney(value: Double): String = if (value % 1.0 == 0.0) {
    value.toLong().toString()
} else {
    String.format(java.util.Locale.US, "%.2f", value)
}

private fun defaultAccountOptions(): List<QuickEntryAccountOption> = listOf(
    QuickEntryAccountOption("acc-main", "Κύριος λογαριασμός", "bank"),
    QuickEntryAccountOption("acc-save", "Αποταμίευση", "savings"),
    QuickEntryAccountOption("acc-cash", "Μετρητά", "cash"),
)

private fun defaultExpenseCategories(): List<QuickEntryCategoryOption> = listOf(
    QuickEntryCategoryOption("Τρόφιμα", listOf("Σούπερ μάρκετ", "Καφές")),
    QuickEntryCategoryOption("Μετακίνηση"),
    QuickEntryCategoryOption("Λογαριασμοί"),
    QuickEntryCategoryOption("Άλλο"),
)

private fun defaultSplitParts(category: String = "Τρόφιμα"): List<QuickEntrySplitPartDraft> = listOf(
    QuickEntrySplitPartDraft(id = "part-1", category = category),
    QuickEntrySplitPartDraft(id = "part-2", category = category),
)

class QuickEntryViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(QuickEntryUiState())
    val state: StateFlow<QuickEntryUiState> = mutableState.asStateFlow()

    fun onAction(action: QuickEntryAction) {
        mutableState.update { reduceQuickEntry(it, action) }
    }
}
