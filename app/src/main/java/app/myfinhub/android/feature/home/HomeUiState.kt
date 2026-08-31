package app.myfinhub.android.feature.home

data class HomeUiState(
    val amountsVisible: Boolean = false,
    val accounts: List<HomeAccount>,
    val attentionItems: List<HomeAttentionItem>,
    val upcomingItems: List<HomeUpcomingItem>,
    val monthFlow: HomeMonthFlow,
    val quickEntryOpen: Boolean = false,
    val selectedQuickEntryType: HomeQuickEntryType? = null,
) {
    val liquidTotal: Double
        get() = accounts
            .filter { account -> account.group == HomeAccountGroup.LIQUID }
            .sumOf(HomeAccount::balance)
}

data class HomeAccount(
    val id: String,
    val name: String,
    val role: String,
    val balance: Double,
    val group: HomeAccountGroup,
)

enum class HomeAccountGroup {
    LIQUID,
    SAVINGS,
}

data class HomeAttentionItem(
    val id: String,
    val title: String,
    val reason: String,
    val dueLabel: String,
    val tone: HomeAttentionTone,
)

enum class HomeAttentionTone {
    URGENT,
    INFO,
}

data class HomeUpcomingItem(
    val id: String,
    val title: String,
    val dateLabel: String,
    val amount: Double,
)

data class HomeMonthFlow(
    val income: Double,
    val expense: Double,
    val saving: Double,
    val budget: Double,
) {
    val budgetProgress: Float
        get() = if (budget <= 0.0) 0f else (expense / budget).toFloat().coerceIn(0f, 1f)
}

enum class HomeQuickEntryType(val label: String) {
    EXPENSE("Έξοδο"),
    INCOME("Έσοδο"),
    TRANSFER("Μεταφορά"),
    CARD_PAYMENT("Πληρωμή κάρτας"),
}

sealed interface HomeAction {
    data object ToggleAmounts : HomeAction
    data object OpenQuickEntry : HomeAction
    data object CloseQuickEntry : HomeAction
    data class SelectQuickEntry(val type: HomeQuickEntryType) : HomeAction
    data class DismissAttention(val id: String) : HomeAction
}

fun reduceHomeState(state: HomeUiState, action: HomeAction): HomeUiState = when (action) {
    HomeAction.ToggleAmounts -> state.copy(amountsVisible = !state.amountsVisible)
    HomeAction.OpenQuickEntry -> state.copy(quickEntryOpen = true, selectedQuickEntryType = null)
    HomeAction.CloseQuickEntry -> state.copy(quickEntryOpen = false, selectedQuickEntryType = null)
    is HomeAction.SelectQuickEntry -> state.copy(selectedQuickEntryType = action.type)
    is HomeAction.DismissAttention -> state.copy(
        attentionItems = state.attentionItems.filterNot { it.id == action.id },
    )
}

fun syntheticHomeUiState(): HomeUiState = HomeUiState(
    accounts = listOf(
        HomeAccount(
            id = "cash",
            name = "Μετρητά",
            role = "Καθημερινά",
            balance = 185.40,
            group = HomeAccountGroup.LIQUID,
        ),
        HomeAccount(
            id = "payroll",
            name = "Κύριος λογαριασμός",
            role = "Μισθοδοσία",
            balance = 2_465.80,
            group = HomeAccountGroup.LIQUID,
        ),
        HomeAccount(
            id = "savings",
            name = "Αποταμίευση",
            role = "Μαξιλάρι ασφαλείας",
            balance = 6_240.00,
            group = HomeAccountGroup.SAVINGS,
        ),
    ),
    attentionItems = listOf(
        HomeAttentionItem(
            id = "scheduled-review",
            title = "Έλεγχος προγραμματισμένης πληρωμής",
            reason = "Η πληρωμή ρεύματος χρειάζεται επιβεβαίωση.",
            dueLabel = "Σήμερα",
            tone = HomeAttentionTone.URGENT,
        ),
        HomeAttentionItem(
            id = "transaction-review",
            title = "2 κινήσεις για κατηγοριοποίηση",
            reason = "Ολοκλήρωσε το Smart Review όταν έχεις χρόνο.",
            dueLabel = "Όποτε θέλεις",
            tone = HomeAttentionTone.INFO,
        ),
    ),
    upcomingItems = listOf(
        HomeUpcomingItem(
            id = "rent",
            title = "Ενοίκιο",
            dateLabel = "25 Αυγ",
            amount = 650.00,
        ),
        HomeUpcomingItem(
            id = "subscription",
            title = "Συνδρομή streaming",
            dateLabel = "28 Αυγ",
            amount = 13.99,
        ),
    ),
    monthFlow = HomeMonthFlow(
        income = 2_450.00,
        expense = 1_318.35,
        saving = 1_131.65,
        budget = 1_800.00,
    ),
)
