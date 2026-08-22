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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myfinhub.android.designsystem.MyFinHubTheme

@Composable
fun MyFinHubApp(viewModel: BootstrapViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyFinHubTheme {
        BootstrapScreen(
            state = state,
            onAcknowledge = { viewModel.onAction(BootstrapAction.AcknowledgeNativeBaseline) },
        )
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
