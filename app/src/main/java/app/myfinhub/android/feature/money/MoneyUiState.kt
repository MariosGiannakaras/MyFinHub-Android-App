package app.myfinhub.android.feature.money

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MoneyAccount(
    val id: String,
    val name: String,
    val balance: Double,
    val kind: String,
)

data class MoneyCard(
    val id: String,
    val nickname: String,
    val last4: String,
    val kind: String,
    val currentBalance: Double,
    val limit: Double?,
    val vaultState: VaultState,
    val network: String = "VISA",
    val bankId: String = "",
)

enum class VaultState { LOCKED, AVAILABLE }

data class SavingsPlan(
    val name: String = "Ταμείο ασφαλείας",
    val targetAmountText: String,
    val targetDateLabel: String = "",
    val monthlyContributionText: String = "",
    val paused: Boolean = false,
)

data class LoanItem(
    val id: String,
    val name: String,
    val lender: String,
    val remaining: Double,
    val monthlyPayment: Double,
    val nextPaymentLabel: String,
    val originalAmount: Double,
    val paused: Boolean = false,
)

data class LendingItem(
    val id: String,
    val personLabel: String,
    val amount: Double,
    val dueLabel: String,
    val note: String = "",
    val settled: Boolean = false,
)

/**
 * Product-facing money state.
 *
 * Defaults are deliberately empty. Production projection must supply only values that exist in the
 * canonical document. Synthetic names, dates and counterparties belong exclusively to explicit
 * preview/test fixtures and must never leak into a signed-in user's finance surface.
 */
data class MoneyUiState(
    val accounts: List<MoneyAccount> = emptyList(),
    val cards: List<MoneyCard> = emptyList(),
    val savingsGoal: Double? = null,
    val savingsCurrent: Double = 0.0,
    val loanOutstanding: Double = 0.0,
    val lendingReceivable: Double = 0.0,
    val savingsPlan: SavingsPlan = SavingsPlan(targetAmountText = ""),
    val loans: List<LoanItem> = emptyList(),
    val lendingItems: List<LendingItem> = emptyList(),
    val frontendMessage: String? = null,
)

sealed interface MoneyAction {
    data class SavingsTargetChanged(val value: String) : MoneyAction
    data class SavingsDateChanged(val value: String) : MoneyAction
    data class SavingsContributionChanged(val value: String) : MoneyAction
    data object ToggleSavingsPause : MoneyAction
    data object SaveSavingsDraft : MoneyAction

    data class UpdateLoan(
        val id: String,
        val name: String,
        val lender: String,
        val remaining: Double,
        val monthlyPayment: Double,
        val nextPaymentLabel: String,
    ) : MoneyAction

    data class ToggleLoanPause(val id: String) : MoneyAction

    data class UpdateLending(
        val id: String,
        val personLabel: String,
        val amount: Double,
        val dueLabel: String,
        val note: String,
    ) : MoneyAction

    data class ToggleLendingSettled(val id: String) : MoneyAction
}

fun reduceMoney(state: MoneyUiState, action: MoneyAction): MoneyUiState = when (action) {
    is MoneyAction.SavingsTargetChanged -> state.copy(
        savingsPlan = state.savingsPlan.copy(targetAmountText = action.value),
        frontendMessage = null,
    )
    is MoneyAction.SavingsDateChanged -> state.copy(
        savingsPlan = state.savingsPlan.copy(targetDateLabel = action.value),
        frontendMessage = null,
    )
    is MoneyAction.SavingsContributionChanged -> state.copy(
        savingsPlan = state.savingsPlan.copy(monthlyContributionText = action.value),
        frontendMessage = null,
    )
    MoneyAction.ToggleSavingsPause -> state.copy(
        savingsPlan = state.savingsPlan.copy(paused = !state.savingsPlan.paused),
        frontendMessage = "Η κατάσταση του τοπικού προσχεδίου ενημερώθηκε.",
    )
    MoneyAction.SaveSavingsDraft -> {
        val target = state.savingsPlan.targetAmountText.replace(',', '.').toDoubleOrNull()
        val contribution = state.savingsPlan.monthlyContributionText.replace(',', '.').toDoubleOrNull()
        when {
            target == null || target <= 0.0 -> state.copy(frontendMessage = "Ο στόχος πρέπει να είναι μεγαλύτερος από μηδέν.")
            contribution == null || contribution < 0.0 -> state.copy(frontendMessage = "Η μηνιαία συνεισφορά δεν μπορεί να είναι αρνητική.")
            state.savingsPlan.targetDateLabel.isBlank() -> state.copy(frontendMessage = "Συμπλήρωσε χρονικό στόχο.")
            else -> state.copy(frontendMessage = "Το τοπικό προσχέδιο ενημερώθηκε. Δεν έχει συγχρονιστεί με τον λογαριασμό.")
        }
    }
    is MoneyAction.UpdateLoan -> state.copy(
        loans = state.loans.map { loan ->
            if (loan.id != action.id) loan else loan.copy(
                name = action.name.trim(),
                lender = action.lender.trim(),
                remaining = action.remaining,
                monthlyPayment = action.monthlyPayment,
                nextPaymentLabel = action.nextPaymentLabel.trim(),
            )
        },
        frontendMessage = "Το τοπικό προσχέδιο του δανείου ενημερώθηκε. Δεν έχει συγχρονιστεί.",
    )
    is MoneyAction.ToggleLoanPause -> state.copy(
        loans = state.loans.map { loan -> if (loan.id == action.id) loan.copy(paused = !loan.paused) else loan },
        frontendMessage = "Η τοπική κατάσταση του δανείου ενημερώθηκε. Δεν έχει συγχρονιστεί.",
    )
    is MoneyAction.UpdateLending -> state.copy(
        lendingItems = state.lendingItems.map { item ->
            if (item.id != action.id) item else item.copy(
                personLabel = action.personLabel.trim(),
                amount = action.amount,
                dueLabel = action.dueLabel.trim(),
                note = action.note.trim(),
            )
        },
        frontendMessage = "Το τοπικό προσχέδιο της απαίτησης ενημερώθηκε. Δεν έχει συγχρονιστεί.",
    )
    is MoneyAction.ToggleLendingSettled -> state.copy(
        lendingItems = state.lendingItems.map { item ->
            if (item.id == action.id) item.copy(settled = !item.settled) else item
        },
        frontendMessage = "Η τοπική κατάσταση της απαίτησης ενημερώθηκε. Δεν έχει συγχρονιστεί.",
    )
}

class MoneyViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(syntheticMoneyUiState())
    val state: StateFlow<MoneyUiState> = mutableState.asStateFlow()

    fun deleteCard(cardId: String) {
        mutableState.update { state -> state.copy(cards = state.cards.filterNot { it.id == cardId }) }
    }

    fun onAction(action: MoneyAction) {
        mutableState.update { state -> reduceMoney(state, action) }
    }
}

/** Explicit preview/test fixture. Never use as a product-state default. */
fun syntheticMoneyUiState(): MoneyUiState = MoneyUiState(
    accounts = syntheticMoneyAccounts(),
    cards = syntheticMoneyCards(),
    savingsGoal = 6_000.0,
    savingsCurrent = 2_850.0,
    loanOutstanding = 4_240.0,
    lendingReceivable = 310.0,
    savingsPlan = SavingsPlan(
        targetAmountText = "6000",
        targetDateLabel = "Δεκ 2027",
        monthlyContributionText = "250",
    ),
    loans = syntheticLoans(4_240.0),
    lendingItems = syntheticLendingItems(310.0),
)

fun syntheticMoneyAccounts() = listOf(
    MoneyAccount("acc-main", "Κύριος λογαριασμός", 2_148.37, "Τράπεζα"),
    MoneyAccount("acc-save", "Αποταμίευση", 2_850.00, "Αποταμίευση"),
    MoneyAccount("acc-cash", "Μετρητά", 145.20, "Μετρητά"),
)

fun syntheticMoneyCards() = listOf(
    MoneyCard(
        id = "card-1",
        nickname = "Καθημερινή",
        last4 = "4242",
        kind = "Χρεωστική",
        currentBalance = 0.0,
        limit = null,
        vaultState = VaultState.AVAILABLE,
        network = "VISA",
        bankId = "piraeus",
    ),
    MoneyCard(
        id = "card-2",
        nickname = "Πιστωτική",
        last4 = "1881",
        kind = "Πιστωτική",
        currentBalance = 312.20,
        limit = 2_000.0,
        vaultState = VaultState.LOCKED,
        network = "MASTERCARD",
        bankId = "revolut",
    ),
)

fun syntheticLoans(totalOutstanding: Double = 4_240.0): List<LoanItem> {
    val primary = totalOutstanding.coerceAtLeast(0.0)
    if (primary == 0.0) return emptyList()
    val original = maxOf(primary, 7_500.0)
    return listOf(
        LoanItem(
            id = "loan-main",
            name = "Προσωπικό δάνειο",
            lender = "Τράπεζα",
            remaining = primary,
            monthlyPayment = minOf(185.0, primary),
            nextPaymentLabel = "12 Σεπ",
            originalAmount = original,
        ),
    )
}

fun syntheticLendingItems(totalReceivable: Double = 310.0): List<LendingItem> {
    val total = totalReceivable.coerceAtLeast(0.0)
    if (total == 0.0) return emptyList()
    return listOf(
        LendingItem(
            id = "lend-main",
            personLabel = "Επιστροφή χρημάτων",
            amount = total,
            dueLabel = "15 Σεπ",
            note = "Προσωπική απαίτηση",
        ),
    )
}

private fun Double.toMoneyInput(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(java.util.Locale.US, "%.2f", this)
