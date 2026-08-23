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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.security.SecureWindowProtection
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
                val goal = state.savingsGoal
                SectionHeader(if (goal != null) "Στόχος αποταμίευσης" else "Αποταμίευση")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),
    onReveal: () -> Unit = {},
    onHideSecrets: () -> Unit = {},
    onSaveCvv: (CharArray) -> Unit = { value -> value.fill('\u0000') },
    onDeleteCvv: () -> Unit = {},
    onBack: () -> Unit,
) {
    val relevantState = when (secretState) {
        is CardSecretUiState.Hidden -> secretState.takeIf { it.cardId == null || it.cardId == card?.id }
        is CardSecretUiState.Loading -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Revealed -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Failure -> secretState.takeIf { it.cardId == card?.id }
        CardSecretUiState.AuthRejected -> secretState
    } ?: CardSecretUiState.Hidden(card?.id)

    SecureWindowProtection(active = relevantState is CardSecretUiState.Revealed)
    var cvvDraft by remember(card?.id) { mutableStateOf("") }

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
                Text(
                    "PAN/λήξη αποκαλύπτονται μόνο από το owner+AAL2 server vault. Το CVV παραμένει αποκλειστικά σε κρυπτογραφημένο vault αυτής της συσκευής.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (relevantState) {
                    is CardSecretUiState.Hidden -> {
                        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                            Text("Αποκάλυψη ασφαλών στοιχείων")
                        }
                        Text(
                            "Η οθόνη και το recent-app thumbnail προστατεύονται μόνο όσο εμφανίζονται τα πραγματικά στοιχεία.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is CardSecretUiState.Loading -> {
                        CircularProgressIndicator()
                        Text("Ανάκτηση ασφαλών στοιχείων…")
                    }

                    is CardSecretUiState.Failure -> {
                        Text(relevantState.message, color = MaterialTheme.colorScheme.error)
                        if (relevantState.retryable) {
                            Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                                Text("Δοκιμή ξανά")
                            }
                        }
                    }

                    CardSecretUiState.AuthRejected -> {
                        Text(
                            "Η ασφαλής συνεδρία δεν είναι πλέον έγκυρη. Θα χρειαστεί νέα σύνδεση.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is CardSecretUiState.Revealed -> {
                        SecretValue(label = "PAN", value = relevantState.pan ?: "Δεν έχει αποθηκευτεί")
                        SecretValue(label = "Λήξη", value = relevantState.expiry ?: "Δεν έχει αποθηκευτεί")
                        SecretValue(label = "CVV", value = relevantState.cvv ?: "Δεν έχει αποθηκευτεί στη συσκευή")

                        TextButton(onClick = onHideSecrets) {
                            Text("Απόκρυψη στοιχείων")
                        }

                        OutlinedTextField(
                            value = cvvDraft,
                            onValueChange = { input ->
                                cvvDraft = input.filter(Char::isDigit).take(4)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Νέο CVV για αυτή τη συσκευή") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                val chars = cvvDraft.toCharArray()
                                cvvDraft = ""
                                onSaveCvv(chars)
                            },
                            enabled = !relevantState.cvvSaving && cvvDraft.length in 3..4,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (relevantState.cvvSaving) "Αποθήκευση…" else "Αποθήκευση CVV στη συσκευή")
                        }
                        if (relevantState.cvv != null) {
                            TextButton(
                                onClick = onDeleteCvv,
                                enabled = !relevantState.cvvSaving,
                            ) {
                                Text("Διαγραφή τοπικού CVV")
                            }
                        }
                        relevantState.message?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
