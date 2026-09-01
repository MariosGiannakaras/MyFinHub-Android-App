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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.security.SecureWindowProtection
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubActionCard
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSectionHeading
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun Money2026Screen(
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
    var activeCardId by remember { mutableStateOf<String?>(state.cards.firstOrNull()?.id) }
    val revealedCardId = (secretState as? CardSecretUiState.Revealed)?.cardId

    SecureWindowProtection(active = revealedCardId != null && revealedCardId == activeCardId)
    DisposableEffect(activeCardId) {
        activeCardId?.let(onCardActivated)
        onDispose { activeCardId?.let(onCardDeactivated) }
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Χρήματα",
                subtitle = "Λογαριασμοί, στόχοι, κάρτες και υποχρεώσεις",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("money_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = MyFinHubSpacing.lg,
                top = MyFinHubSpacing.xs,
                end = MyFinHubSpacing.lg,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubSectionHeading(
                            title = "Λογαριασμοί",
                            subtitle = "Η διαθέσιμη εικόνα σου σήμερα",
                            icon = MyFinHubIcons.Account,
                            tone = FinanceTone.Neutral,
                        )
                        state.accounts.forEachIndexed { index, account ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MyFinHubIconBadge(
                                    icon = MyFinHubIcons.Account,
                                    tone = if (account.kind.contains("Αποταμί", ignoreCase = true)) {
                                        FinanceTone.Savings
                                    } else {
                                        FinanceTone.Neutral
                                    },
                                    contentDescription = null,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        account.kind,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                MyFinHubAmountText(
                                    text = formatEuro2026(account.balance),
                                    tone = if (account.balance >= 0) FinanceTone.Income else FinanceTone.Expense,
                                )
                            }
                            if (index != state.accounts.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubSectionHeading(
                            title = "Αποταμίευση",
                            subtitle = state.savingsPlan.name,
                            icon = MyFinHubIcons.Savings,
                            tone = FinanceTone.Savings,
                        )
                        val goal = state.savingsPlan.targetAmountText.replace(',', '.').toDoubleOrNull()
                            ?: state.savingsGoal
                        if (goal != null && goal > 0.0) {
                            LinearProgressIndicator(
                                progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${formatEuro2026(state.savingsCurrent)} από ${formatEuro2026(goal)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            MyFinHubAmountText(
                                text = formatEuro2026(state.savingsCurrent),
                                tone = FinanceTone.Savings,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Text(
                            if (state.savingsPlan.paused) {
                                "Στόχος σε παύση"
                            } else {
                                "Στόχος ${state.savingsPlan.targetDateLabel} · ${state.savingsPlan.monthlyContributionText.toMoneyPreview2026()} / μήνα"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenSavings) {
                            Text("Διαχείριση στόχου")
                        }
                    }
                }
            }

            item {
                MyFinHubSectionHeading(
                    title = "Κάρτες",
                    subtitle = "Οι κάρτες σου, με ασφαλή πρόσβαση στα στοιχεία τους",
                    icon = MyFinHubIcons.Card,
                    tone = FinanceTone.Transfer,
                )
            }
            item {
                CreditCardStack(
                    cards = state.cards,
                    secretState = secretState,
                    onActiveCardChanged = { activeCardId = it },
                    onRevealSecrets = onRevealCardSecrets,
                    onHideSecrets = onHideCardSecrets,
                    onOpenCard = onOpenCard,
                    onDeleteCard = onDeleteCard,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                MyFinHubActionCard(onClick = onOpenLoans, modifier = Modifier.fillMaxWidth()) {
                    MyFinHubSectionHeading(
                        title = "Δάνεια",
                        subtitle = "Επόμενες δόσεις και πρόοδος αποπληρωμής",
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Expense,
                    )
                    MyFinHubAmountText(
                        text = formatEuro2026(
                            if (state.loans.isNotEmpty()) state.loans.filterNot(LoanItem::paused).sumOf(LoanItem::remaining)
                            else state.loanOutstanding,
                        ),
                        tone = FinanceTone.Expense,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${state.loans.count { !it.paused }} ενεργό",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                MyFinHubActionCard(onClick = onOpenLending, modifier = Modifier.fillMaxWidth()) {
                    MyFinHubSectionHeading(
                        title = "Απαιτήσεις",
                        subtitle = "Αναμενόμενες επιστροφές χρημάτων",
                        icon = MyFinHubIcons.Income,
                        tone = FinanceTone.Income,
                    )
                    MyFinHubAmountText(
                        text = formatEuro2026(
                            if (state.lendingItems.isNotEmpty()) state.lendingItems.filterNot(LendingItem::settled).sumOf(LendingItem::amount)
                            else state.lendingReceivable,
                        ),
                        tone = FinanceTone.Income,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${state.lendingItems.count { !it.settled }} ανοιχτή",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.frontendMessage?.let { message ->
                item {
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

@Composable
fun Savings2026Screen(
    state: MoneyUiState,
    onAction: (MoneyAction) -> Unit,
    onBack: () -> Unit,
) {
    val plan = state.savingsPlan
    val target = plan.targetAmountText.replace(',', '.').toDoubleOrNull()
    val remaining = target?.minus(state.savingsCurrent)?.coerceAtLeast(0.0)

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Αποταμίευση",
                subtitle = plan.name,
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubIconBadge(MyFinHubIcons.Savings, FinanceTone.Savings, null)
                    MyFinHubAmountText(
                        text = formatEuro2026(state.savingsCurrent),
                        tone = FinanceTone.Savings,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (target != null && target > 0.0) {
                        LinearProgressIndicator(
                            progress = { (state.savingsCurrent / target).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        remaining?.let {
                            Text(
                                "Απομένουν ${formatEuro2026(it)} για τον στόχο.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
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
                    MyFinHubPrimaryAction(
                        label = "Αποθήκευση",
                        onClick = { onAction(MoneyAction.SaveSavingsDraft) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = MyFinHubIcons.Savings,
                    )
                    OutlinedButton(
                        onClick = { onAction(MoneyAction.ToggleSavingsPause) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (plan.paused) "Επανενεργοποίηση στόχου" else "Παύση στόχου")
                    }
                }
            }
            state.frontendMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Loans2026Screen(
    state: MoneyUiState,
    onOpenLoan: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Δάνεια",
                subtitle = "Υπόλοιπα και επόμενες δόσεις",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Συνολικό υπόλοιπο", style = MaterialTheme.typography.labelLarge)
                        MyFinHubAmountText(
                            text = formatEuro2026(state.loans.filterNot(LoanItem::paused).sumOf(LoanItem::remaining)),
                            tone = FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "${state.loans.count { !it.paused }} ενεργά δάνεια",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.loans, key = LoanItem::id) { loan ->
                MyFinHubActionCard(onClick = { onOpenLoan(loan.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubIconBadge(MyFinHubIcons.Plan, FinanceTone.Expense, null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(loan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(loan.lender, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MyFinHubAmountText(formatEuro2026(loan.remaining), FinanceTone.Expense)
                    }
                    LinearProgressIndicator(
                        progress = { (1.0 - loan.remaining / loan.originalAmount.coerceAtLeast(loan.remaining)).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Επόμενη δόση ${formatEuro2026(loan.monthlyPayment)} · ${loan.nextPaymentLabel}${if (loan.paused) " · Σε παύση" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.loans.isEmpty()) {
                item { Text("Δεν υπάρχουν δάνεια.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun LoanEditor2026Screen(
    loan: LoanItem?,
    onAction: (MoneyAction) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(loan?.id) { mutableStateOf(loan?.name.orEmpty()) }
    var lender by remember(loan?.id) { mutableStateOf(loan?.lender.orEmpty()) }
    var remainingText by remember(loan?.id) { mutableStateOf(loan?.remaining?.toMoneyInput2026().orEmpty()) }
    var paymentText by remember(loan?.id) { mutableStateOf(loan?.monthlyPayment?.toMoneyInput2026().orEmpty()) }
    var nextPayment by remember(loan?.id) { mutableStateOf(loan?.nextPaymentLabel.orEmpty()) }
    var validationError by remember(loan?.id) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Δάνειο",
                subtitle = loan?.name ?: "Δεν είναι διαθέσιμο",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (loan == null) {
                Text("Το δάνειο δεν είναι διαθέσιμο.")
            } else {
                Text("Επεξεργασία", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Αρχικό ποσό ${formatEuro2026(loan.originalAmount)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                MyFinHubPrimaryAction(
                    label = "Αποθήκευση",
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
                )
                OutlinedButton(
                    onClick = { onAction(MoneyAction.ToggleLoanPause(loan.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loan.paused) "Επανενεργοποίηση" else "Παύση δανείου")
                }
            }
        }
    }
}

@Composable
fun Lending2026Screen(
    state: MoneyUiState,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Απαιτήσεις",
                subtitle = "Χρήματα που περιμένεις να επιστραφούν",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Αναμενόμενες επιστροφές", style = MaterialTheme.typography.labelLarge)
                        MyFinHubAmountText(
                            text = formatEuro2026(state.lendingItems.filterNot(LendingItem::settled).sumOf(LendingItem::amount)),
                            tone = FinanceTone.Income,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text("${state.lendingItems.count { !it.settled }} ανοιχτές απαιτήσεις", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.lendingItems, key = LendingItem::id) { item ->
                MyFinHubActionCard(onClick = { onOpenItem(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubIconBadge(MyFinHubIcons.Income, FinanceTone.Income, null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.personLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.dueLabel}${if (item.settled) " · Τακτοποιήθηκε" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubAmountText(formatEuro2026(item.amount), FinanceTone.Income)
                    }
                }
            }
            if (state.lendingItems.isEmpty()) {
                item { Text("Δεν υπάρχουν ανοιχτές απαιτήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun LendingEditor2026Screen(
    item: LendingItem?,
    onAction: (MoneyAction) -> Unit,
    onBack: () -> Unit,
) {
    var personLabel by remember(item?.id) { mutableStateOf(item?.personLabel.orEmpty()) }
    var amountText by remember(item?.id) { mutableStateOf(item?.amount?.toMoneyInput2026().orEmpty()) }
    var dueLabel by remember(item?.id) { mutableStateOf(item?.dueLabel.orEmpty()) }
    var note by remember(item?.id) { mutableStateOf(item?.note.orEmpty()) }
    var validationError by remember(item?.id) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Απαίτηση",
                subtitle = item?.personLabel ?: "Δεν είναι διαθέσιμη",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (item == null) {
                Text("Η απαίτηση δεν είναι διαθέσιμη.")
            } else {
                Text("Επεξεργασία", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                MyFinHubPrimaryAction(
                    label = "Αποθήκευση",
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
                )
                OutlinedButton(
                    onClick = { onAction(MoneyAction.ToggleLendingSettled(item.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (item.settled) "Άνοιγμα ξανά" else "Σήμανση ως τακτοποιημένη")
                }
            }
        }
    }
}

@Composable
fun CardDetail2026Screen(
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
            MyFinHubScreenHeader(
                title = "Κάρτα",
                subtitle = card?.nickname ?: "Δεν είναι διαθέσιμη",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (card == null) {
                Text("Η κάρτα δεν είναι διαθέσιμη.")
            } else {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Transfer, null)
                        Text(card.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${card.kind} •••• ${card.last4}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
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
                                    Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("Δοκιμή ξανά") }
                                }
                            }
                            CardSecretUiState.AuthRejected -> {
                                Text(
                                    "Η ασφαλής συνεδρία δεν είναι πλέον έγκυρη. Θα χρειαστεί νέα σύνδεση.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            is CardSecretUiState.Revealed -> {
                                SecretValue2026("PAN", relevantState.pan ?: "Δεν έχει αποθηκευτεί")
                                SecretValue2026("Λήξη", relevantState.expiry ?: "Δεν έχει αποθηκευτεί")
                                SecretValue2026("CVV", relevantState.cvv ?: "Δεν έχει αποθηκευτεί στη συσκευή")
                                TextButton(onClick = onHideSecrets) { Text("Απόκρυψη στοιχείων") }
                                OutlinedTextField(
                                    value = cvvDraft,
                                    onValueChange = { input -> cvvDraft = input.filter { it in '0'..'9' }.take(4) },
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
                                    TextButton(onClick = onDeleteCvv, enabled = !relevantState.cvvSaving) {
                                        Text("Διαγραφή τοπικού CVV")
                                    }
                                }
                                relevantState.message?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretValue2026(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun String.toMoneyPreview2026(): String =
    replace(',', '.').toDoubleOrNull()?.let(::formatEuro2026) ?: if (isBlank()) "Δεν έχει οριστεί" else "$this €"

private fun Double.toMoneyInput2026(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.2f", this)

private fun formatEuro2026(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
