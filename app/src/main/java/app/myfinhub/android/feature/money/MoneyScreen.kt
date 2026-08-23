package app.myfinhub.android.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(
    state: MoneyUiState,
    onOpenCard: (String) -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f

    Scaffold(
        topBar = { TopAppBar(title = { Text("Χρήματα", modifier = Modifier.semantics { heading() }) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Λογαριασμοί") }
            items(state.accounts, key = MoneyAccount::id) { account ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    if (largeFont) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(account.name, fontWeight = FontWeight.SemiBold)
                            Text(account.kind, style = MaterialTheme.typography.bodySmall)
                            Text(formatEuro(account.balance), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(account.name, fontWeight = FontWeight.SemiBold)
                                Text(account.kind, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(formatEuro(account.balance), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                SectionHeader("Αποταμίευση")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val goal = state.savingsGoal
                    if (goal != null && goal > 0.0) {
                        LinearProgressIndicator(
                            progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${formatEuro(state.savingsCurrent)} από ${formatEuro(goal)}")
                    } else {
                        Text("Τρέχον υπόλοιπο αποταμίευσης: ${formatEuro(state.savingsCurrent)}")
                    }
                }
            }
            item { SectionHeader("Κάρτες") }
            items(state.cards, key = MoneyCard::id) { card ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clickable(role = Role.Button) { onOpenCard(card.id) },
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(card.nickname, fontWeight = FontWeight.SemiBold)
                        Text("${card.kind} •••• ${card.last4}", style = MaterialTheme.typography.bodySmall)
                        if (card.limit != null) {
                            Text("Υπόλοιπο ${formatEuro(card.currentBalance)} / όριο ${formatEuro(card.limit)}")
                        }
                    }
                }
            }
            item {
                SectionHeader("Υποχρεώσεις & απαιτήσεις")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Υπόλοιπο δανείων: ${formatEuro(state.loanOutstanding)}")
                    Text("Αναμενόμενες επιστροφές: ${formatEuro(state.lendingReceivable)}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    card: MoneyCard?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Κάρτα") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (card == null) {
                Text("Η κάρτα δεν είναι διαθέσιμη.")
            } else {
                Text(card.nickname, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${card.kind} •••• ${card.last4}")
                Text("PAN/λήξη παραμένουν στο server vault και θα αποκαλύπτονται μόνο μετά από έγκυρο owner+AAL2 session.")
                Text(
                    if (card.vaultState == VaultState.AVAILABLE) {
                        "Υπάρχει server-vault αναφορά για αυτή την κάρτα. Η αποκάλυψη ενεργοποιείται στο Phase 5 secure flow."
                    } else {
                        "Δεν υπάρχει διαθέσιμη server-vault αναφορά για αυτή την κάρτα."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Το CVV δεν αποθηκεύεται στον server και θα παραμένει αποκλειστικά device-local.")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).semantics { heading() },
    )
}

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
