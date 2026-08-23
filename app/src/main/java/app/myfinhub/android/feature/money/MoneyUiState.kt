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
    val network: String = "VISA",
    val currentBalance: Double,
    val limit: Double?,
    val vaultState: VaultState,
)

enum class VaultState { LOCKED, AVAILABLE }

data class MoneyUiState(
    val accounts: List<MoneyAccount> = syntheticMoneyAccounts(),
    val cards: List<MoneyCard> = syntheticMoneyCards(),
    val savingsGoal: Double? = 6_000.0,
    val savingsCurrent: Double = 2_850.0,
    val loanOutstanding: Double = 4_240.0,
    val lendingReceivable: Double = 310.0,
)

class MoneyViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(MoneyUiState())
    val state: StateFlow<MoneyUiState> = mutableState.asStateFlow()

    fun deleteCard(cardId: String) {
        mutableState.update { state -> state.copy(cards = state.cards.filterNot { it.id == cardId }) }
    }
}

fun syntheticMoneyAccounts() = listOf(
    MoneyAccount("acc-main", "Κύριος λογαριασμός", 2_148.37, "Τράπεζα"),
    MoneyAccount("acc-save", "Αποταμίευση", 2_850.00, "Αποταμίευση"),
    MoneyAccount("acc-cash", "Μετρητά", 145.20, "Μετρητά"),
)

fun syntheticMoneyCards() = listOf(
    MoneyCard("card-1", "Piraeus Καθημερινή", "4242", "Χρεωστική", "VISA", 0.0, null, VaultState.AVAILABLE),
    MoneyCard("card-2", "Revolut Πιστωτική", "1881", "Πιστωτική", "MASTERCARD", 312.20, 2_000.0, VaultState.LOCKED),
)