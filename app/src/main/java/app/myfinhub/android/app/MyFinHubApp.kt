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
import app.myfinhub.android.feature.home.HomeScreen
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomeViewModel
import app.myfinhub.android.feature.insights.InsightsScreen
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.InsightsViewModel
import app.myfinhub.android.feature.money.CardDetailScreen
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.money.LendingEditorScreen
import app.myfinhub.android.feature.money.LendingScreen
import app.myfinhub.android.feature.money.LoanEditorScreen
import app.myfinhub.android.feature.money.LoansScreen
import app.myfinhub.android.feature.money.MoneyAction
import app.myfinhub.android.feature.money.MoneyScreen
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.MoneyViewModel
import app.myfinhub.android.feature.money.SavingsScreen
import app.myfinhub.android.feature.money.reduceMoney
import app.myfinhub.android.feature.plan.PlanAction
import app.myfinhub.android.feature.plan.PlanBudgetsScreen
import app.myfinhub.android.feature.plan.PlanForecastScreen
import app.myfinhub.android.feature.plan.PlanItemEditorScreen
import app.myfinhub.android.feature.plan.PlanScreen
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlanViewModel
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.QuickEntryViewModel
import app.myfinhub.android.feature.utilities.ChangeHistoryScreen
import app.myfinhub.android.feature.utilities.DataTransferScreen
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
                        onAction = onHomeAction,
                        onOpenAttention = { id -> homeBackStack.add(AppRoute.HomeAttention(id)) },
                        onOpenSettings = { homeBackStack.add(AppRoute.Settings) },
                        onOpenDataTransfer = { homeBackStack.add(AppRoute.DataTransfer) },
                        onOpenChangeHistory = { homeBackStack.add(AppRoute.ChangeHistory) },
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
                    )
                }
                entry<AppRoute.DataTransfer> {
                    DataTransferScreen(
                        state = frontendUtilitiesState,
                        onAction = onFrontendUtilitiesAction,
                        onBack = { homeBackStack.removeLastOrNull() },
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
                        onOpenDetail = { eventId -> activityBackStack.add(AppRoute.ActivityDetail(eventId)) },
                        onOpenQuickEntry = { activityBackStack.add(AppRoute.QuickEntry) },
                    )
                }
                entry<AppRoute.ActivityDetail> { route ->
                    val item = activityState.items.firstOrNull { it.id == route.eventId }
                    ActivityDetailScreen(
                        item = item,
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
                    MoneyScreen(
                        state = frontendMoneyState,
                        secretState = cardSecretState,
                        onCardActivated = onCardDetailOpened,
                        onCardDeactivated = onCardDetailClosed,
                        onRevealCardSecrets = onRevealCardSecrets,
                        onHideCardSecrets = onHideCardSecrets,
                        onDeleteCard = onDeleteCard,
                        onOpenCard = { cardId -> moneyBackStack.add(AppRoute.CardDetail(cardId)) },
                        onOpenSavings = { moneyBackStack.add(AppRoute.Savings) },
                        onOpenLoans = { moneyBackStack.add(AppRoute.Loans) },
                        onOpenLending = { moneyBackStack.add(AppRoute.Lending) },
                    )
                }
                entry<AppRoute.CardDetail> { route ->
                    DisposableEffect(route.cardId) {
                        onCardDetailOpened(route.cardId)
                        onDispose { onCardDetailClosed(route.cardId) }
                    }
                    CardDetailScreen(
                        card = frontendMoneyState.cards.firstOrNull { it.id == route.cardId },
                        secretState = cardSecretState,
                        onReveal = onRevealCardSecrets,
                        onHideSecrets = onHideCardSecrets,
                        onSaveCvv = onSaveLocalCvv,
                        onDeleteCvv = onDeleteLocalCvv,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Savings> {
                    SavingsScreen(
                        state = frontendMoneyState,
                        onAction = onFrontendMoneyAction,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Loans> {
                    LoansScreen(
                        state = frontendMoneyState,
                        onOpenLoan = { loanId -> moneyBackStack.add(AppRoute.LoanDetail(loanId)) },
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.LoanDetail> { route ->
                    LoanEditorScreen(
                        loan = frontendMoneyState.loans.firstOrNull { it.id == route.loanId },
                        onAction = onFrontendMoneyAction,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Lending> {
                    LendingScreen(
                        state = frontendMoneyState,
                        onOpenItem = { itemId -> moneyBackStack.add(AppRoute.LendingDetail(itemId)) },
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.LendingDetail> { route ->
                    LendingEditorScreen(
                        item = frontendMoneyState.lendingItems.firstOrNull { it.id == route.lendingId },
                        onAction = onFrontendMoneyAction,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Plan> {
                    PlanScreen(
                        state = planState,
                        onAction = onPlanAction,
                        onOpenItem = { itemId -> planBackStack.add(AppRoute.PlanItem(itemId)) },
                        onOpenBudgets = { planBackStack.add(AppRoute.PlanBudgets) },
                        onOpenForecast = { planBackStack.add(AppRoute.PlanForecast) },
                    )
                }
                entry<AppRoute.PlanItem> { route ->
                    PlanItemEditorScreen(
                        item = planState.items.firstOrNull { it.id == route.itemId },
                        onAction = onPlanAction,
                        onBack = { planBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.PlanBudgets> {
                    PlanBudgetsScreen(
                        state = planState,
                        onAction = onPlanAction,
                        onBack = { planBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.PlanForecast> {
                    PlanForecastScreen(
                        state = planState,
                        onAction = onPlanAction,
                        onBack = { planBackStack.removeLastOrNull() },
                    )
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
