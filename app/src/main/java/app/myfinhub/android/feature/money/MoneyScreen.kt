package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),
    onCardActivated: (String) -> Unit = {},
    onCardDeactivated: (String) -> Unit = {},
    onRevealCardSecrets: () -> Unit = {},
    onHideCardSecrets: () -> Unit = {},
    onDeleteCard: (String) -> Unit = {},
    onOpenCard: (String) -> Unit,
    onOpenSavings: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenLending: () -> Unit = {},
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    var activeCardId by remember { mutableStateOf<String?>(state.cards.firstOrNull()?.id) }
    val revealedCardId = (secretState as? CardSecretUiState.Revealed)?.cardId

    SecureWindowProtection(active = revealedCardId != null && revealedCardId == activeCardId)

    DisposableEffect(activeCardId) {
        activeCardId?.let(onCardActivated)
        onDispose { activeCardId?.let(onCardDeactivated) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Χρήματα", modifier = Modifier.semantics { heading() }) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("money_list"),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.name, fontWeight = FontWeight.SemiBold)
                                Text(account.kind, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(formatEuro(account.balance), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item { SectionHeader("Αποταμίευση") }
            item {
                ElevatedCard(
                    onClick = onOpenSavings,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val goal = state.savingsPlan.targetAmountText.replace(',', '.').toDoubleOrNull()
                            ?: state.savingsGoal
                        Text(state.savingsPlan.name, fontWeight = FontWeight.SemiBold)
                        if (goal != null && goal > 0.0) {
                            LinearProgressIndicator(
                                progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${formatEuro(state.savingsCurrent)} από ${formatEuro(goal)}")
                        } else {
                            Text("Τρέχον υπόλοιπο: ${formatEuro(state.savingsCurrent)}")
                        }
                        Text(
                            if (state.savingsPlan.paused) "Στόχος σε παύση" else "Στόχος ${state.savingsPlan.targetDateLabel} · ${state.savingsPlan.monthlyContributionText.toMoneyPreview()} / μήνα",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Διαχείριση στόχου", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item { SectionHeader("Κάρτες") }
            item {
                CreditCardStack(
                    cards = state.cards,
                    secretState = secretState,
                    onActiveCardChanged = { activeCardId = it },
                    onRevealSecrets = onRevealCardSecrets,
                    onHideSecrets = onHideCardSecrets,
                    onOpenCard = onOpenCard,
                    onDeleteCard = onDeleteCard,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            item { SectionHeader("Υποχρεώσεις & απαιτήσεις") }
            item {
                ElevatedCard(
                    onClick = onOpenLoans,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    MoneySummaryCardContent(
                        title = "Δάνεια",
                        amount = state.loans.filterNot(LoanItem::paused).sumOf(LoanItem::remaining).takeIf { it > 0.0 }
                            ?: state.loanOutstanding,
                        subtitle = "${state.loans.count { !it.paused }} ενεργό · επόμενες δόσεις και πρόοδος αποπληρωμής",
                        largeFont = largeFont,
                    )
                }
            }
            item {
                ElevatedCard(
                    onClick = onOpenLending,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    MoneySummaryCardContent(
                        title = "Απαιτήσεις",
                        amount = state.lendingItems.filterNot(LendingItem::settled).sumOf(LendingItem::amount).takeIf { it > 0.0 }
                            ?: state.lendingReceivable,
                        subtitle = "${state.lendingItems.count { !it.settled }} ανοιχτή · αναμενόμενες επιστροφές χρημάτων",
                        largeFont = largeFont,
                    )
                }
            }
            state.frontendMessage?.let { message ->
                item {
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    state: MoneyUiState,
    onAction: (MoneyAction) -> Unit,
    onBack: () -> Unit,
) {
    val plan = state.savingsPlan
    val target = plan.targetAmountText.replace(',', '.').toDoubleOrNull()
    val remaining = target?.minus(state.savingsCurrent)?.coerceAtLeast(0.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Αποταμίευση", modifier = Modifier.semantics { heading() }) },
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
            Text(plan.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(formatEuro(state.savingsCurrent), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (target != null && target > 0.0) {
                LinearProgressIndicator(
                    progress = { (state.savingsCurrent / target).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    remaining?.let { "Απομένουν ${formatEuro(it)} για τον στόχο." }.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = plan.targetAmountText,
                onValueChange = { onAction(MoneyAction.SavingsTargetChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Στόχος") },
                suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            OutlinedTextField(
                value = plan.targetDateLabel,
                onValueChange = { onAction(MoneyAction.SavingsDateChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Χρονικός στόχος") },
                singleLine = true,
            )
            OutlinedTextField(
                value = plan.monthlyContributionText,
                onValueChange = { onAction(MoneyAction.SavingsContributionChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Μηνιαία συνεισφορά") },
                suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )

            Button(
                onClick = { onAction(MoneyAction.SaveSavingsDraft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Έλεγχος στόχου")
            }
            OutlinedButton(
                onClick = { onAction(MoneyAction.ToggleSavingsPause) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (plan.paused) "Επανενεργοποίηση στόχου" else "Παύση στόχου")
            }
            state.frontendMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    state: MoneyUiState,
    onOpenLoan: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Δάνεια", modifier = Modifier.semantics { heading() }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Συνολικό υπόλοιπο", style = MaterialTheme.typography.labelLarge)
                        Text(
                            formatEuro(state.loans.filterNot(LoanItem::paused).sumOf(LoanItem::remaining)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${state.loans.count { !it.paused }} ενεργά δάνεια",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.loans, key = LoanItem::id) { loan ->
                ElevatedCard(
                    onClick = { onOpenLoan(loan.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(loan.name, fontWeight = FontWeight.SemiBold)
                                Text(loan.lender, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(formatEuro(loan.remaining), fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(
                            progress = { (1.0 - loan.remaining / loan.originalAmount.coerceAtLeast(loan.remaining)).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Επόμενη δόση ${formatEuro(loan.monthlyPayment)} · ${loan.nextPaymentLabel}${if (loan.paused) " · Σε παύση" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.loans.isEmpty()) {
                item { EmptyMoneyState("Δεν υπάρχουν δάνεια.") }
            }
            state.frontendMessage?.let { message ->
                item { FrontendMessage(message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanEditorScreen(
    loan: LoanItem?,
    onAction: (MoneyAction) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(loan?.id) { mutableStateOf(loan?.name.orEmpty()) }
    var lender by remember(loan?.id) { mutableStateOf(loan?.lender.orEmpty()) }
    var remainingText by remember(loan?.id) { mutableStateOf(loan?.remaining?.toMoneyInput().orEmpty()) }
    var paymentText by remember(loan?.id) { mutableStateOf(loan?.monthlyPayment?.toMoneyInput().orEmpty()) }
    var nextPayment by remember(loan?.id) { mutableStateOf(loan?.nextPaymentLabel.orEmpty()) }
    var validationError by remember(loan?.id) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Δάνειο") },
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
            if (loan == null) {
                Text("Το δάνειο δεν είναι διαθέσιμο.")
            } else {
                Text(loan.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(
                    "Αρχικό ποσό ${formatEuro(loan.originalAmount)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it; validationError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Όνομα") }, singleLine = true)
                OutlinedTextField(value = lender, onValueChange = { lender = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Πιστωτής") }, singleLine = true)
                OutlinedTextField(
                    value = remainingText,
                    onValueChange = { remainingText = it; validationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Υπόλοιπο") },
                    suffix = { Text("€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = paymentText,
                    onValueChange = { paymentText = it; validationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Μηνιαία δόση") },
                    suffix = { Text("€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(value = nextPayment, onValueChange = { nextPayment = it; validationError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Επόμενη πληρωμή") }, singleLine = true)
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        val remaining = remainingText.replace(',', '.').toDoubleOrNull()
                        val payment = paymentText.replace(',', '.').toDoubleOrNull()
                        when {
                            name.isBlank() -> validationError = "Συμπλήρωσε όνομα."
                            remaining == null || remaining < 0.0 -> validationError = "Το υπόλοιπο δεν μπορεί να είναι αρνητικό."
                            payment == null || payment <= 0.0 -> validationError = "Η δόση πρέπει να είναι μεγαλύτερη από μηδέν."
                            nextPayment.isBlank() -> validationError = "Συμπλήρωσε επόμενη πληρωμή."
                            else -> {
                                onAction(MoneyAction.UpdateLoan(loan.id, name, lender, remaining, payment, nextPayment))
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Αποθήκευση") }
                OutlinedButton(
                    onClick = { onAction(MoneyAction.ToggleLoanPause(loan.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (loan.paused) "Επανενεργοποίηση" else "Παύση δανείου") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendingScreen(
    state: MoneyUiState,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Απαιτήσεις", modifier = Modifier.semantics { heading() }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Αναμενόμενες επιστροφές", style = MaterialTheme.typography.labelLarge)
                        Text(
                            formatEuro(state.lendingItems.filterNot(LendingItem::settled).sumOf(LendingItem::amount)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${state.lendingItems.count { !it.settled }} ανοιχτές απαιτήσεις", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.lendingItems, key = LendingItem::id) { item ->
                ElevatedCard(
                    onClick = { onOpenItem(item.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.personLabel, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.dueLabel}${if (item.settled) " · Τακτοποιήθηκε" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(formatEuro(item.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (state.lendingItems.isEmpty()) {
                item { EmptyMoneyState("Δεν υπάρχουν ανοιχτές απαιτήσεις.") }
            }
            state.frontendMessage?.let { message -> item { FrontendMessage(message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendingEditorScreen(
    item: LendingItem?,
    onAction: (MoneyAction) -> Unit,
    onBack: () -> Unit,
) {
    var personLabel by remember(item?.id) { mutableStateOf(item?.personLabel.orEmpty()) }
    var amountText by remember(item?.id) { mutableStateOf(item?.amount?.toMoneyInput().orEmpty()) }
    var dueLabel by remember(item?.id) { mutableStateOf(item?.dueLabel.orEmpty()) }
    var note by remember(item?.id) { mutableStateOf(item?.note.orEmpty()) }
    var validationError by remember(item?.id) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Απαίτηση") },
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
            if (item == null) {
                Text("Η απαίτηση δεν είναι διαθέσιμη.")
            } else {
                Text(item.personLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                OutlinedTextField(value = personLabel, onValueChange = { personLabel = it; validationError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Πρόσωπο / περιγραφή") }, singleLine = true)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; validationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ποσό") },
                    suffix = { Text("€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(value = dueLabel, onValueChange = { dueLabel = it; validationError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Αναμενόμενη ημερομηνία") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Σημείωση") }, minLines = 3)
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        val amount = amountText.replace(',', '.').toDoubleOrNull()
                        when {
                            personLabel.isBlank() -> validationError = "Συμπλήρωσε περιγραφή."
                            amount == null || amount <= 0.0 -> validationError = "Το ποσό πρέπει να είναι μεγαλύτερο από μηδέν."
                            dueLabel.isBlank() -> validationError = "Συμπλήρωσε αναμενόμενη ημερομηνία."
                            else -> {
                                onAction(MoneyAction.UpdateLending(item.id, personLabel, amount, dueLabel, note))
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Αποθήκευση") }
                OutlinedButton(
                    onClick = { onAction(MoneyAction.ToggleLendingSettled(item.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (item.settled) "Άνοιγμα ξανά" else "Σήμανση ως τακτοποιημένη") }
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
                                cvvDraft = input.filter { it in '0'..'9' }.take(4)
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
private fun MoneySummaryCardContent(
    title: String,
    amount: Double,
    subtitle: String,
    largeFont: Boolean,
) {
    if (largeFont) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(formatEuro(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(formatEuro(amount), fontWeight = FontWeight.SemiBold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun EmptyMoneyState(message: String) {
    Text(
        message,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FrontendMessage(message: String) {
    Text(
        message,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun String.toMoneyPreview(): String =
    replace(',', '.').toDoubleOrNull()?.let(::formatEuro) ?: if (isBlank()) "Δεν έχει οριστεί" else "$this €"

private fun Double.toMoneyInput(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.2f", this)

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
