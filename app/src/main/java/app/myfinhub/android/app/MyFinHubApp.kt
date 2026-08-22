package app.myfinhub.android.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.HomeAction
import app.myfinhub.android.feature.home.HomeScreen
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomeViewModel

@Composable
fun MyFinHubApp(homeViewModel: HomeViewModel = viewModel()) {
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    MyFinHubTheme {
        MyFinHubAppContent(
            homeState = homeState,
            onHomeAction = homeViewModel::onAction,
        )
    }
}

@Composable
internal fun MyFinHubAppContent(
    homeState: HomeUiState,
    onHomeAction: (HomeAction) -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }

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
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = activeBackStack,
            onBack = {
                if (activeBackStack.size > 1) {
                    activeBackStack.removeLastOrNull()
                }
            },
            entryProvider = entryProvider {
                entry<AppRoute.Home> {
                    HomeScreen(
                        state = homeState,
                        onAction = onHomeAction,
                    )
                }
                entry<AppRoute.Activity> {
                    DestinationPlaceholder(destination = TopLevelDestination.ACTIVITY)
                }
                entry<AppRoute.Money> {
                    DestinationPlaceholder(destination = TopLevelDestination.MONEY)
                }
                entry<AppRoute.Plan> {
                    DestinationPlaceholder(destination = TopLevelDestination.PLAN)
                }
                entry<AppRoute.Insights> {
                    DestinationPlaceholder(destination = TopLevelDestination.INSIGHTS)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationPlaceholder(destination: TopLevelDestination) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(destination.label),
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Mobile-first prototype pending")
            Text("This destination is reserved by the Phase 0 information-architecture hypothesis. No desktop UI has been copied into it.")
        }
    }
}
