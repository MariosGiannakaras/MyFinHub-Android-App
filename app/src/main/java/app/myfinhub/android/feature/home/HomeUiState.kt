package app.myfinhub.android.feature.home

data class HomeUiState(
    val amountsVisible: Boolean = false,
    val accounts: List<HomeAccount>,
    val recentItems: List<HomeRecentItem> = emptyList(),
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
    val isPrimary: Boolean = false,
    val balanceTrend: List<Double> = listOf(balance, balance),
    val institution: String? = null,
)

enum class HomeAccountGroup {
    LIQUID,
    SAVINGS,
}

data class HomeRecentItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val dateLabel: String,
    val amount: Double,
    val tone: HomeRecentTone,
)

enum class HomeRecentTone {
    INCOME,
    EXPENSE,
    TRANSFER,
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

/** Explicit preview/test fixture. Production state is always projected from the canonical document. */
fun syntheticHomeUiState(): HomeUiState = HomeUiState(
    accounts = listOf(
        HomeAccount(
            id = "cash",
            name = "Μετρητά",
            role = "Μετρητά",
            balance = 185.40,
            group = HomeAccountGroup.LIQUID,
            isPrimary = true,
            balanceTrend = listOf(164.20, 176.80, 153.40, 194.10, 188.60, 181.20, 185.40),
        ),
        HomeAccount(
            id = "piraeus-payroll",
            name = "Πειραιώς Μισθοδοσίας",
            role = "Μισθοδοσίας",
            balance = 2_465.80,
            group = HomeAccountGroup.LIQUID,
            isPrimary = true,
            balanceTrend = listOf(2_128.40, 2_094.20, 2_028.70, 1_962.10, 1_884.00, 2_512.30, 2_465.80),
        ),
        HomeAccount(
            id = "piraeus-savings",
            name = "Πειραιώς Αποταμίευση",
            role = "Αποταμιευτικός",
            balance = 6_240.00,
            group = HomeAccountGroup.SAVINGS,
            isPrimary = true,
            balanceTrend = listOf(5_940.00, 5_940.00, 6_040.00, 6_040.00, 6_140.00, 6_140.00, 6_240.00),
        ),
        HomeAccount(
            id = "revolut-main",
            name = "Revolut",
            role = "Καθημερινός",
            balance = 428.35,
            group = HomeAccountGroup.LIQUID,
            balanceTrend = listOf(510.20, 494.10, 481.60, 472.00, 451.20, 439.70, 428.35),
        ),
    ),
    recentItems = listOf(
        HomeRecentItem("recent-1", "Σούπερ μάρκετ", "Πειραιώς Μισθοδοσίας", "Σήμερα", -42.60, HomeRecentTone.EXPENSE),
        HomeRecentItem("recent-2", "Μισθός", "Πειραιώς Μισθοδοσίας", "Χθες", 1_650.00, HomeRecentTone.INCOME),
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
