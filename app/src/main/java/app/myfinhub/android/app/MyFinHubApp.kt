package app.myfinhub.android.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.myfinhub.android.designsystem.MyFinHubTheme

@Composable
fun MyFinHubApp(viewModel: BootstrapViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }

    MyFinHubTheme {
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
            when (currentDestination) {
                TopLevelDestination.HOME -> BootstrapScreen(
                    state = state,
                    onAcknowledge = { viewModel.onAction(BootstrapAction.AcknowledgeNativeBaseline) },
                )

                else -> DestinationPlaceholder(destination = currentDestination)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BootstrapScreen(
    state: BootstrapUiState,
    onAcknowledge: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
    ) { innerPadding ->
        BootstrapContent(
            state = state,
            contentPadding = innerPadding,
            onAcknowledge = onAcknowledge,
        )
    }
}

@Composable
private fun BootstrapContent(
    state: BootstrapUiState,
    contentPadding: PaddingValues,
    onAcknowledge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = state.subtitle,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = state.phase,
            style = MaterialTheme.typography.titleMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Implementation baseline", style = MaterialTheme.typography.titleMedium)
                Text(state.architectureNote)
                Text("Production finance data is intentionally not connected in this checkpoint.")
            }
        }
        Button(
            onClick = onAcknowledge,
            enabled = !state.acknowledged,
        ) {
            Text(if (state.acknowledged) "Baseline confirmed" else "Confirm native baseline")
        }
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
            Text(
                text = "Mobile-first prototype pending",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("This destination is reserved by the Phase 0 information-architecture hypothesis. No desktop UI has been copied into it.")
        }
    }
}
