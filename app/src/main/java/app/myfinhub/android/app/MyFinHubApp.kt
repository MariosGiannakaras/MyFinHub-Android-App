package app.myfinhub.android.app

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityAction
import app.myfinhub.android.feature.activity.ActivityDetailScreen
import app.myfinhub.android.feature.activity.ActivityFilter
import app.myfinhub.android.feature.activity.ActivityScreen
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.activity.ActivityViewModel
import app.myfinhub.android.feature.home.HomeAction
import app.myfinhub.android.feature.home.HomeAttentionDetailScreen
import app.myfinhub.android.feature.home.HomeQuickEntryType
import app.myfinhub.android.feature.home.HomeScreen
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomeViewModel
import app.myfinhub.android.feature.insights.InsightsScreen
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.InsightsViewModel
import app.myfinhub.android.feature.money.CanonicalCardDetailScreen
import app.myfinhub.android.feature.money.CanonicalLendingScreen
import app.myfinhub.android.feature.money.CanonicalLoansScreen
import app.myfinhub.android.feature.money.CanonicalMoneyScreen
import app.myfinhub.android.feature.money.CanonicalSavingsScreen
import app.myfinhub.android.feature.money.CardDetail2026Screen
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.money.Lending2026Screen
import app.myfinhub.android.feature.money.LendingEditor2026Screen
import app.myfinhub.android.feature.money.LoanEditor2026Screen
import app.myfinhub.android.feature.money.Loans2026Screen
import app.myfinhub.android.feature.money.Money2026Screen
import app.myfinhub.android.feature.money.MoneyAction
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.MoneyViewModel
import app.myfinhub.android.feature.money.Savings2026Screen
import app.myfinhub.android.feature.money.reduceMoney
import app.myfinhub.android.feature.plan.CanonicalBudgetScreen
import app.myfinhub.android.feature.plan.CanonicalPlanScreen
import app.myfinhub.android.feature.plan.Plan2026Screen
import app.myfinhub.android.feature.plan.PlanAction
import app.myfinhub.android.feature.plan.PlanBudgets2026Screen
import app.myfinhub.android.feature.plan.PlanItemEditor2026Screen
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlanViewModel
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.QuickEntryViewModel
import app.myfinhub.android.feature.utilities.AppDiagnosticsSnapshot
import app.myfinhub.android.feature.utilities.ChangeHistoryScreen
import app.myfinhub.android.feature.utilities.FrontendUtilitiesAction
import app.myfinhub.android.feature.utilities.FrontendUtilitiesUiState
import app.myfinhub.android.feature.utilities.SettingsScreen
import app.myfinhub.android.feature.utilities.reduceFrontendUtilities

@Composable
fun MyFinHubApp(
    homeViewModel: HomeViewModel = viewModel(),
    activityViewModel: ActivityViewModel = viewModel(),
    quickEntryViewModel: QuickEntryViewModel = viewModel(),
    moneyViewModel: MoneyViewModel = viewModel(),
    planViewModel: PlanViewModel = viewModel(),
    insightsViewModel: InsightsViewModel = viewModel(),
) {
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val activityState by activityViewModel.state.collectAsStateWithLifecycle()
    val quickEntryState by quickEntryViewModel.state.collectAsStateWithLifecycle()
    val moneyState by moneyViewModel.state.collectAsStateWithLifecycle()
    val planState by planViewModel.state.collectAsStateWithLifecycle()
    val insightsState by insightsViewModel.state.collectAsStateWithLifecycle()

    MyFinHubTheme {
        MyFinHubAppContent(
            homeState = homeState,
            onHomeAction = homeViewModel::onAction,
            activityState = activityState,
            onActivityAction = activityViewModel::onAction,
            quickEntryState = quickEntryState,
            onQuickEntryAction = quickEntryViewModel::onAction,
            moneyState = moneyState,
            onDeleteCard = moneyViewModel::deleteCard,
            planState = planState,
            onPlanAction = planViewModel::onAction,
            insightsState = insightsState,
        )
    }
}

@Composable
internal fun MyFinHubAppContent(
    homeState: HomeUiState,
    onHomeAction: (HomeAction) -> Unit,
    activityState: ActivityUiState = ActivityUiState(),
    onActivityAction: (ActivityAction) -> Unit = {},
    quickEntryState: QuickEntryUiState = QuickEntryUiState(),
    onQuickEntryAction: (QuickEntryAction) -> Unit = {},
    moneyState: MoneyUiState = MoneyUiState(),
    cardSecretState: CardSecretUiState = CardSecretUiState.Hidden(),
    onCardDetailOpened: (String) -> Unit = {},
    onCardDetailClosed: (String) -> Unit = {},
    onRevealCardSecrets: () -> Unit = {},
    onHideCardSecrets: () -> Unit = {},
    onSaveLocalCvv: (CharArray) -> Unit = { value -> value.fill('\u0000') },
    onDeleteLocalCvv: () -> Unit = {},
    onDeleteCard: (String) -> Unit = {},
    planState: PlanUiState = PlanUiState(),
    onPlanAction: (PlanAction) -> Unit = {},
    insightsState: InsightsUiState = InsightsUiState(),
    diagnostics: AppDiagnosticsSnapshot? = null,
    onLogout: (() -> Unit)? = null,
    canonicalProductMode: Boolean = false,
) {
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    var frontendMoneyState by remember(moneyState) { mutableStateOf(moneyState) }
    var frontendUtilitiesState by remember { mutableStateOf(FrontendUtilitiesUiState()) }

    val onFrontendUtilitiesAction: (FrontendUtilitiesAction) -> Unit = { action ->
        frontendUtilitiesState = reduceFrontendUtilities(frontendUtilitiesState, action)
    }
    val onFrontendMoneyAction: (MoneyAction) -> Unit = { action ->
        frontendMoneyState = reduceMoney(frontendMoneyState, action)
    }
    val alwaysShowNavigationLabels = LocalDensity.current.fontScale < 1.3f

    val homeBackStack = rememberNavBackStack(AppRoute.Home)
    val activityBackStack = rememberNavBackStack(AppRoute.Activity)
    val moneyBackStack = rememberNavBackStack(AppRoute.Money)
    val planBackStack = rememberNavBackStack(AppRoute.Plan)
    val insightsBackStack = rememberNavBackStack(AppRoute.Insights)

    val activeBackStack: NavBackStack<NavKey> = when (currentDestination) {
        TopLevelDestination.HOME -> homeBackStack
        TopLevelDestination.ACTIVITY -> activityBackStack
        TopLevelDestination.MONEY -> moneyBackStack
        TopLevelDestination.PLAN -> planBackStack
        TopLevelDestination.INSIGHTS -> insightsBackStack
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination == destination,
                    onClick = { currentDestination = destination },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.label),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(destination.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = alwaysShowNavigationLabels,
                )
            }
        },
    ) {
        NavDisplay(
            backStack = activeBackStack,
            onBack = {
                if (activeBackStack.size > 1) activeBackStack.removeLastOrNull()
            },
            entryProvider = entryProvider {
                entry<AppRoute.Home> {
                    HomeScreen(
                        state = homeState,
                        onAction = { action ->
                            onHomeAction(action)
                            if (action is HomeAction.SelectQuickEntry) {
                                onHomeAction(HomeAction.CloseQuickEntry)
                                onQuickEntryAction(
                                    QuickEntryAction.SelectKind(action.type.toQuickEntryKind()),
                                )
                                homeBackStack.pushIfNew(AppRoute.QuickEntry)
                            }
                        },
                        onOpenAttention = { id -> homeBackStack.pushIfNew(AppRoute.HomeAttention(id)) },
                        onOpenSettings = { homeBackStack.pushIfNew(AppRoute.Settings) },
                        onOpenChangeHistory = { homeBackStack.pushIfNew(AppRoute.ChangeHistory) },
                    )
                }
                entry<AppRoute.HomeAttention> { route ->
                    HomeAttentionDetailScreen(
                        item = homeState.attentionItems.firstOrNull { it.id == route.attentionId },
                        onMarkReviewed = {
                            onHomeAction(HomeAction.DismissAttention(route.attentionId))
                            homeBackStack.removeLastOrNull()
                        },
                        onBack = { homeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Settings> {
                    SettingsScreen(
                        state = frontendUtilitiesState,
                        onAction = onFrontendUtilitiesAction,
                        onBack = { homeBackStack.removeLastOrNull() },
                        diagnostics = diagnostics,
                        onLogout = onLogout,
                    )
                }
                entry<AppRoute.ChangeHistory> {
                    ChangeHistoryScreen(
                        state = frontendUtilitiesState,
                        onAction = onFrontendUtilitiesAction,
                        onBack = { homeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Activity> {
                    ActivityScreen(
                        state = activityState,
                        onAction = onActivityAction,
                        onOpenDetail = { eventId -> activityBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                        onOpenQuickEntry = { activityBackStack.pushIfNew(AppRoute.QuickEntry) },
                    )
                }
                entry<AppRoute.ActivityDetail> { route ->
                    ActivityDetailScreen(
                        item = activityState.items.firstOrNull { it.id == route.eventId },
                        onBack = { activityBackStack.removeLastOrNull() },
                        onSave = { note, category ->
                            onActivityAction(ActivityAction.SaveEdit(route.eventId, note, category))
                        },
                    )
                }
                entry<AppRoute.QuickEntry> {
                    QuickEntryScreen(
                        state = quickEntryState,
                        onAction = onQuickEntryAction,
                        onBack = { activeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Money> {
                    if (canonicalProductMode) {
                        CanonicalMoneyScreen(
                            state = moneyState,
                            secretState = cardSecretState,
                            onCardActivated = onCardDetailOpened,
                            onCardDeactivated = onCardDetailClosed,
                            onRevealCardSecrets = onRevealCardSecrets,
                            onHideCardSecrets = onHideCardSecrets,
                            onDeleteCard = onDeleteCard,
                            onOpenCard = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardDetail(cardId)) },
                            onOpenSavings = { moneyBackStack.pushIfNew(AppRoute.Savings) },
                            onOpenLoans = { moneyBackStack.pushIfNew(AppRoute.Loans) },
                            onOpenLending = { moneyBackStack.pushIfNew(AppRoute.Lending) },
                        )
                    } else {
                        Money2026Screen(
                            state = frontendMoneyState,
                            secretState = cardSecretState,
                            onCardActivated = onCardDetailOpened,
                            onCardDeactivated = onCardDetailClosed,
                            onRevealCardSecrets = onRevealCardSecrets,
                            onHideCardSecrets = onHideCardSecrets,
                            onDeleteCard = onDeleteCard,
                            onOpenCard = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardDetail(cardId)) },
                            onOpenSavings = { moneyBackStack.pushIfNew(AppRoute.Savings) },
                            onOpenLoans = { moneyBackStack.pushIfNew(AppRoute.Loans) },
                            onOpenLending = { moneyBackStack.pushIfNew(AppRoute.Lending) },
                        )
                    }
                }
                entry<AppRoute.CardDetail> { route ->
                    DisposableEffect(route.cardId) {
                        onCardDetailOpened(route.cardId)
                        onDispose { onCardDetailClosed(route.cardId) }
                    }
                    val card = if (canonicalProductMode) {
                        moneyState.cards.firstOrNull { it.id == route.cardId }
                    } else {
                        frontendMoneyState.cards.firstOrNull { it.id == route.cardId }
                    }
                    if (canonicalProductMode) {
                        CanonicalCardDetailScreen(
                            card = card,
                            secretState = cardSecretState,
                            onReveal = onRevealCardSecrets,
                            onHideSecrets = onHideCardSecrets,
                            onSaveCvv = onSaveLocalCvv,
                            onDeleteCvv = onDeleteLocalCvv,
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    } else {
                        CardDetail2026Screen(
                            card = card,
                            secretState = cardSecretState,
                            onReveal = onRevealCardSecrets,
                            onHideSecrets = onHideCardSecrets,
                            onSaveCvv = onSaveLocalCvv,
                            onDeleteCvv = onDeleteLocalCvv,
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    }
                }
                entry<AppRoute.Savings> {
                    if (canonicalProductMode) {
                        CanonicalSavingsScreen(
                            state = moneyState,
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    } else {
                        Savings2026Screen(
                            state = frontendMoneyState,
                            onAction = onFrontendMoneyAction,
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    }
                }
                entry<AppRoute.Loans> {
                    if (canonicalProductMode) {
                        CanonicalLoansScreen(
                            state = moneyState,
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    } else {
                        Loans2026Screen(
                            state = frontendMoneyState,
                            onOpenLoan = { loanId -> moneyBackStack.pushIfNew(AppRoute.LoanDetail(loanId)) },
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    }
                }
                entry<AppRoute.LoanDetail> { route ->
                    LoanEditor2026Screen(
                        loan = frontendMoneyState.loans.firstOrNull { it.id == route.loanId },
                        onAction = onFrontendMoneyAction,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Lending> {
                    if (canonicalProductMode) {
                        CanonicalLendingScreen(
                            state = moneyState,
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    } else {
                        Lending2026Screen(
                            state = frontendMoneyState,
                            onOpenItem = { itemId -> moneyBackStack.pushIfNew(AppRoute.LendingDetail(itemId)) },
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )
                    }
                }
                entry<AppRoute.LendingDetail> { route ->
                    LendingEditor2026Screen(
                        item = frontendMoneyState.lendingItems.firstOrNull { it.id == route.lendingId },
                        onAction = onFrontendMoneyAction,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Plan> {
                    if (canonicalProductMode) {
                        CanonicalPlanScreen(
                            state = planState,
                            onOpenBudget = { planBackStack.pushIfNew(AppRoute.PlanBudgets) },
                        )
                    } else {
                        Plan2026Screen(
                            state = planState,
                            onAction = onPlanAction,
                            onOpenItem = { itemId -> planBackStack.pushIfNew(AppRoute.PlanItem(itemId)) },
                            onOpenBudgets = { planBackStack.pushIfNew(AppRoute.PlanBudgets) },
                        )
                    }
                }
                entry<AppRoute.PlanItem> { route ->
                    PlanItemEditor2026Screen(
                        item = planState.items.firstOrNull { it.id == route.itemId },
                        onAction = onPlanAction,
                        onBack = { planBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.PlanBudgets> {
                    if (canonicalProductMode) {
                        CanonicalBudgetScreen(
                            state = planState,
                            onAction = onPlanAction,
                            onBack = { planBackStack.removeLastOrNull() },
                        )
                    } else {
                        PlanBudgets2026Screen(
                            state = planState,
                            onAction = onPlanAction,
                            onBack = { planBackStack.removeLastOrNull() },
                        )
                    }
                }
                entry<AppRoute.Insights> {
                    InsightsScreen(
                        state = insightsState,
                        onOpenSupportingActivity = {
                            onActivityAction(ActivityAction.FilterChanged(ActivityFilter.EXPENSE))
                            currentDestination = TopLevelDestination.ACTIVITY
                        },
                    )
                }
            },
        )
    }
}

private fun HomeQuickEntryType.toQuickEntryKind(): QuickEntryKind = when (this) {
    HomeQuickEntryType.EXPENSE -> QuickEntryKind.EXPENSE
    HomeQuickEntryType.INCOME -> QuickEntryKind.INCOME
    HomeQuickEntryType.TRANSFER -> QuickEntryKind.TRANSFER
    HomeQuickEntryType.CARD_PAYMENT -> QuickEntryKind.CARD_PAYMENT
}

private fun NavBackStack<NavKey>.pushIfNew(route: NavKey) {
    if (lastOrNull() != route) add(route)
}
