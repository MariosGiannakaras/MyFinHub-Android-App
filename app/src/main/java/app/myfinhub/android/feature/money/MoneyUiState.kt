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