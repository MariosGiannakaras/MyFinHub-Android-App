package app.myfinhub.android.app

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.myfinhub.android.feature.home.HomeScreen
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomeViewModel
import app.myfinhub.android.feature.insights.InsightsScreen
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.InsightsViewModel
import app.myfinhub.android.feature.money.CardDetailScreen
import app.myfinhub.android.feature.money.MoneyScreen
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.MoneyViewModel
import app.myfinhub.android.feature.plan.PlanAction
import app.myfinhub.android.feature.plan.PlanScreen
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlanViewModel
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.QuickEntryViewModel

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
    planState: PlanUiState = PlanUiState(),
    onPlanAction: (PlanAction) -> Unit = {},
    insightsState: InsightsUiState = InsightsUiState(),
) {
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
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
                    HomeScreen(state = homeState, onAction = onHomeAction)
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
                        state = moneyState,
                        onOpenCard = { cardId -> moneyBackStack.add(AppRoute.CardDetail(cardId)) },
                    )
                }
                entry<AppRoute.CardDetail> { route ->
                    CardDetailScreen(
                        card = moneyState.cards.firstOrNull { it.id == route.cardId },
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Plan> {
                    PlanScreen(state = planState, onAction = onPlanAction)
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
