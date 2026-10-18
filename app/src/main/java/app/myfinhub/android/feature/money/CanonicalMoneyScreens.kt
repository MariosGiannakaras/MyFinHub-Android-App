package app.myfinhub.android.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubActionCard
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubHeroCard
import app.myfinhub.android.designsystem.MyFinHubHeroHeading
import app.myfinhub.android.designsystem.MyFinHubHeroMetric
import app.myfinhub.android.designsystem.MyFinHubHeroValue
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSectionHeading
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.util.Locale

/**
 * Production-facing Money overview.
 *
 * Unlike the retained local parity editors, this surface renders only canonical values. Aggregate
 * debt/receivable totals remain useful even when the canonical document does not expose enough
 * structured detail to support safe per-item editing on Android.
 */
@Composable
fun CanonicalMoneyScreen(
    state: MoneyUiState,
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),
    onCardActivated: (String) -> Unit = {},
    onCardDeactivated: (String) -> Unit = {},
    onRevealCardSecrets: () -> Unit = {},
    onHideCardSecrets: () -> Unit = {},
    onDeleteCard: (String) -> Unit = {},
    onOpenCard: (String) -> Unit,
    onAddCard: () -> Unit = {},
    onOpenAccount: (String) -> Unit = {},
    onOpenSavings: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenLending: () -> Unit,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var activeCardId by remember(state.cards) { mutableStateOf(state.cards.firstOrNull()?.id) }
    var cardDeleteConfirmationId by remember(state.cards) { mutableStateOf<String?>(null) }
    val accountTotal = state.accounts.sumOf(MoneyAccount::balance)
    val creditOutstanding = canonicalCreditOutstanding(state)
    val netPosition = canonicalNetPosition(state)

    DisposableEffect(activeCardId) {
        activeCardId?.let(onCardActivated)
        onDispose { activeCardId?.let(onCardDeactivated) }
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Περιουσία",
                subtitle = "Τι έχεις και τι οφείλεις",
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
                MyFinHubHeroCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubHeroHeading(
                            eyebrow = "Καθαρή θέση",
                            title = "Η συνολική σου θέση",
                            supporting = "Λογαριασμοί + απαιτήσεις − δάνεια − οφειλές πιστωτικών",
                        )
                        MyFinHubHeroValue(amountVisibilityText(formatCanonicalEuro(netPosition), amountsVisible))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        ) {
                            MyFinHubHeroMetric(
                                label = "Λογαριασμοί",
                                value = amountVisibilityText(formatCanonicalEuro(accountTotal), amountsVisible),
                                modifier = Modifier.weight(1f),
                            )
                            MyFinHubHeroMetric(
                                label = "Απαιτήσεις",
                                value = amountVisibilityText(formatCanonicalEuro(state.lendingReceivable), amountsVisible),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        ) {
                            MyFinHubHeroMetric(
                                label = "Δάνεια",
                                value = amountVisibilityText(formatCanonicalEuro(-state.loanOutstanding), amountsVisible),
                                modifier = Modifier.weight(1f),
                            )
                            MyFinHubHeroMetric(
                                label = "Πιστωτικές",
                                value = amountVisibilityText(formatCanonicalEuro(-creditOutstanding), amountsVisible),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubSectionHeading(
                            title = "Λογαριασμοί",
                            subtitle = "${state.accounts.size} λογαριασμοί στην καθαρή θέση",
                            icon = MyFinHubIcons.Account,
                            tone = FinanceTone.Neutral,
                        )
                        if (state.accounts.isEmpty()) {
                            EmptyFinanceText("Δεν υπάρχουν διαθέσιμοι λογαριασμοί.")
                        } else {
                            state.accounts.forEachIndexed { index, account ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenAccount(account.id) },
                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val provider = financialProvider(account.id, account.institution ?: account.name)
                                    if (provider != null) {
                                        MyFinHubProviderMark(provider, modifier = Modifier.size(34.dp), contentDescription = provider.institutionLabel)
                                    } else {
                                        MyFinHubIconBadge(
                                            icon = MyFinHubIcons.Account,
                                            tone = if (account.kind.contains("Αποταμί", ignoreCase = true)) FinanceTone.Savings else FinanceTone.Neutral,
                                            contentDescription = null,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(account.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            account.institution ?: account.kind,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    MyFinHubAmountText(
                                        text = amountVisibilityText(formatCanonicalEuro(account.balance), amountsVisible),
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
            }

            item {
                MyFinHubActionCard(onClick = onOpenSavings, modifier = Modifier.fillMaxWidth()) {
                    MyFinHubSectionHeading(
                        title = "Αποταμίευση",
                        subtitle = "Μέρος των λογαριασμών σου · ήδη στην καθαρή θέση",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Savings,
                    )
                    val goal = state.savingsGoal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatCanonicalEuro(state.savingsCurrent), amountsVisible),
                            tone = FinanceTone.Savings,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (goal != null && goal > 0.0) {
                            Text(
                                "από ${amountVisibilityText(formatCanonicalEuro(goal), amountsVisible)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (goal != null && goal > 0.0) {
                        if (amountsVisible) LinearProgressIndicator(
                            progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "Δεν έχει οριστεί στόχος αποταμίευσης.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubSectionHeading(
                        title = "Κάρτες",
                        subtitle = "${state.cards.size} ενεργές κάρτες",
                        icon = MyFinHubIcons.Card,
                        tone = FinanceTone.Transfer,
                    )
                    FilledTonalButton(onClick = onAddCard) { Text("Νέα κάρτα") }
                }
            }
            state.frontendMessage?.takeIf { it.isNotBlank() }?.let { message ->
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.cards.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            EmptyFinanceText("Δεν υπάρχουν ενεργές κάρτες.")
                            FilledTonalButton(onClick = onAddCard) { Text("Δημιουργία πρώτης κάρτας") }
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        CreditCardStack(
                            cards = state.cards,
                            secretState = secretState,
                            onActiveCardChanged = { activeCardId = it },
                            onOpenCard = onOpenCard,
                            onDeleteCard = onDeleteCard,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        activeCardId?.let { cardId ->
                            MyFinHubDestructiveTextAction(
                                label = "Διαγραφή ενεργής κάρτας",
                                onClick = { cardDeleteConfirmationId = cardId },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                MyFinHubSectionHeading(
                    title = "Υποχρεώσεις & επιστροφές",
                    subtitle = "Όσα οφείλεις και όσα αναμένεις να επιστραφούν",
                    icon = MyFinHubIcons.Plan,
                    tone = FinanceTone.Neutral,
                )
            }

            item {
                MyFinHubActionCard(onClick = onOpenLoans, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubIconBadge(MyFinHubIcons.Plan, FinanceTone.Expense, null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Δάνεια", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.loans.isEmpty()) "Συνολικό υπόλοιπο δανείων" else "${state.loans.size} διαθέσιμες εγγραφές",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatCanonicalEuro(state.loanOutstanding), amountsVisible),
                            tone = FinanceTone.Expense,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            item {
                MyFinHubActionCard(onClick = onOpenLending, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubIconBadge(MyFinHubIcons.Income, FinanceTone.Income, null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Απαιτήσεις", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.lendingItems.isEmpty()) "Ποσά που αναμένεις να επιστραφούν" else "${state.lendingItems.size} διαθέσιμες εγγραφές",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatCanonicalEuro(state.lendingReceivable), amountsVisible),
                            tone = FinanceTone.Income,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }

    cardDeleteConfirmationId?.let { cardId ->
        val card = state.cards.firstOrNull { it.id == cardId }
        if (card != null) {
            CanonicalCardDeleteDialog(
                card = card,
                onDismiss = { cardDeleteConfirmationId = null },
                onConfirm = {
                    cardDeleteConfirmationId = null
                    onDeleteCard(cardId)
                },
            )
        }
    }
}

@Composable
internal fun CanonicalCardDeleteDialog(
    card: MoneyCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Διαγραφή κάρτας;") },
        text = {
            Text(
                "Η ${card.nickname} ••••${card.last4.ifBlank { "••••" }} θα αφαιρεθεί. Η ενέργεια δεν αναιρείται.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Διαγραφή", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ακύρωση") }
        },
    )
}

internal fun canonicalCreditOutstanding(state: MoneyUiState): Double = state.aggregateCreditOutstanding ?: state.cards
    .filter { card -> card.canonicalKind == "credit" || card.kind.contains("Πιστωτική", ignoreCase = true) }
    .sumOf { card -> card.currentBalance.coerceAtLeast(0.0) }

internal fun canonicalNetPosition(state: MoneyUiState): Double =
    state.accounts.sumOf(MoneyAccount::balance) +
        state.lendingReceivable -
        state.loanOutstanding -
        canonicalCreditOutstanding(state)

@Composable
fun CanonicalSavingsScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
    onSaveGoal: ((String?, String, String, String?) -> Unit)? = null,
    onDeleteGoal: ((String) -> Unit)? = null,
    mutationBlocked: Boolean = false,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var amountDraft by rememberSaveable { mutableStateOf("") }
    var dateDraft by rememberSaveable { mutableStateOf("") }
    var editorError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    fun editGoal(goal: SavingsGoalItem?) {
        editingId = goal?.id
        nameDraft = goal?.name.orEmpty()
        amountDraft = goal?.targetAmount?.toString().orEmpty()
        dateDraft = goal?.targetDate.orEmpty()
        editorError = null
        editorVisible = true
    }
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Αποταμίευση",
                subtitle = "Από τους λογαριασμούς αποταμίευσης",
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
                        MyFinHubIconBadge(MyFinHubIcons.Savings, FinanceTone.Savings, null)
                        Text("Συνολική αποταμίευση", style = MaterialTheme.typography.titleMedium)
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatCanonicalEuro(state.savingsCurrent), amountsVisible),
                            tone = FinanceTone.Savings,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "Το ποσό είναι μέρος των λογαριασμών σου και έχει ήδη συμπεριληφθεί στην καθαρή θέση.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val goal = state.savingsGoal
                        if (goal != null && goal > 0.0 && state.savingsGoalItems.isEmpty()) {
                            if (amountsVisible) LinearProgressIndicator(
                                progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Στόχος ${amountVisibilityText(formatCanonicalEuro(goal), amountsVisible)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            state.savingsMonthlyTarget?.let { performance ->
                item("savings-month-target") {
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth().testTag("savings_month_target"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text("Στόχος αποταμίευσης μήνα",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (performance.targetRate > 0.0)
                                    "${kotlin.math.round(performance.targetRate * 100).toInt()}% των πραγματικών εσόδων"
                                else "Δεν έχει οριστεί ποσοστιαίος στόχος",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Αποταμίευση μήνα: " +
                                    amountVisibilityText(formatCanonicalEuro(performance.saved), amountsVisible),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                if (performance.targetAmount > 0.0)
                                    "Ποσό στόχου: " +
                                        amountVisibilityText(formatCanonicalEuro(performance.targetAmount), amountsVisible)
                                else "Δεν υπάρχουν καταγεγραμμένα έσοδα για χρηματικό στόχο.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            performance.progressPercent?.let { percent ->
                                if (amountsVisible) LinearProgressIndicator(
                                    progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("Πρόοδος στόχου: ${kotlin.math.round(percent).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Αποταμίευση προηγούμενου μήνα: " +
                                    amountVisibilityText(formatCanonicalEuro(performance.previousMonthSaved), amountsVisible),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.savingsSources.values.any { kotlin.math.abs(it) > 0.005 }) {
                item("saving-source-breakdown") {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            Text("Πηγές αποταμίευσης μήνα",
                                style = MaterialTheme.typography.titleMedium)
                            listOf(
                                "pay_and_save" to "Pay & Save",
                                "manual_transfer" to "Μεταφορές στην αποταμίευση",
                                "cash_offset" to "Σύνθετη αποταμίευση",
                            ).forEach { (key, label) ->
                                val amount = state.savingsSources[key] ?: 0.0
                                if (kotlin.math.abs(amount) > 0.005) {
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(label)
                                        MyFinHubAmountText(
                                            amountVisibilityText(formatCanonicalEuro(amount), amountsVisible),
                                            FinanceTone.Savings,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.savingsHistory.isNotEmpty()) {
            item("savings-history-heading") {
                MyFinHubSectionHeading(
                    title = "Πρόσφατη αποταμίευση",
                    subtitle = "Έως 12 τελευταίες κινήσεις του μήνα · μόνο για ανάγνωση",
                    icon = MyFinHubIcons.Savings,
                    tone = FinanceTone.Savings,
                )
            }
                // Web history may contain duplicate legacy IDs. Compose identity is
                // presentation-only: never expose any row as an unsafe mutation target.
                itemsIndexed(state.savingsHistory, key = { index, row ->
                    "savings-history-$index-${row.origin}-${row.id}"
                }) { index, row ->
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("savings_history_row_$index"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.primary, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        row.date + " · " +
                                            if (row.hasUserNote) "Τύπος: ${row.sourceLabel}"
                                            else "Χωρίς ξεχωριστό σχόλιο",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                MyFinHubAmountText(
                                    amountVisibilityText(formatCanonicalEuro(row.amount), amountsVisible),
                                    FinanceTone.Savings,
                                )
                            }
                        }
                    }
                }
            }
            if (onSaveGoal != null) {
                item("savings-goal-add") {
                    TextButton(
                        enabled = !mutationBlocked,
                        onClick = { editGoal(null) },
                        modifier = Modifier.testTag("savings_goal_new"),
                    ) { Text("Νέος στόχος αποταμίευσης") }
                }
            }
            if (state.savingsGoalItems.isNotEmpty()) {
                item("savings-goals-heading") {
                    Text("Στόχοι αποταμίευσης",
                        style = MaterialTheme.typography.titleMedium)
                }
                items(state.savingsGoalItems, key = { "goal-" + it.id }) { goal ->
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                        .testTag("savings_goal_" + goal.id)) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(goal.name, style = MaterialTheme.typography.titleMedium)
                            MyFinHubAmountText(
                                amountVisibilityText(formatCanonicalEuro(goal.targetAmount), amountsVisible),
                                FinanceTone.Savings,
                            )
                            if (amountsVisible) LinearProgressIndicator(
                                progress = { (goal.progressPercent / 100.0).toFloat().coerceIn(0f,1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                (if (amountsVisible) goal.progressPercent.toInt().toString() + "%"
                                    else amountVisibilityText("100%", false)) + " · Κοινό υπόλοιπο αποταμίευσης: " +
                                    amountVisibilityText(formatCanonicalEuro(goal.backingBalance), amountsVisible),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            goal.targetDate?.let { deadline ->
                                Text(
                                    (if (goal.deadlinePast) "Η υπάρχουσα προθεσμία έχει παρέλθει: "
                                        else "Προθεσμία: ") + deadline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (goal.deadlinePast) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (onSaveGoal != null) {
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = { editGoal(goal) },
                                    modifier = Modifier.testTag("savings_goal_edit_" + goal.id),
                                ) { Text("Επεξεργασία στόχου") }
                            }
                            if (onDeleteGoal != null) {
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = { confirmDeleteId = goal.id },
                                    modifier = Modifier.testTag("savings_goal_delete_" + goal.id),
                                ) { Text("Διαγραφή στόχου") }
                            }
                        }
                    }
                }
            }
            if (state.savingsGoalItems.isEmpty() && state.savingsGoal == null) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                if (onSaveGoal != null) "Δεν έχεις ορίσει στόχο αποταμίευσης" else "Ο στόχος δεν είναι διαθέσιμος ακόμη",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (onSaveGoal != null) "Δημιούργησε στόχο από την ενέργεια παραπάνω. Οι αποταμιεύσεις σου δεν μεταφέρονται αυτόματα." else "Μπορείς να βλέπεις το αποταμιευμένο ποσό σου. Η επεξεργασία στόχου θα εμφανιστεί όταν υποστηρίζεται από τον λογαριασμό σου.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (editorVisible && onSaveGoal != null) {
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) editorVisible = false },
            title = { Text(if (editingId == null) "Νέος στόχος" else "Επεξεργασία στόχου") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    OutlinedTextField(
                        value = nameDraft, onValueChange = { nameDraft = it; editorError = null },
                        label = { Text("Όνομα") }, singleLine = true,
                        enabled = !mutationBlocked, modifier = Modifier.testTag("savings_goal_name"),
                    )
                    OutlinedTextField(
                        value = amountDraft, onValueChange = { amountDraft = it; editorError = null },
                        label = { Text("Ποσό στόχου (€)") }, singleLine = true,
                        enabled = !mutationBlocked, modifier = Modifier.testTag("savings_goal_amount"),
                    )
                    OutlinedTextField(
                        value = dateDraft, onValueChange = { dateDraft = it; editorError = null },
                        label = { Text("Προθεσμία YYYY-MM-DD (προαιρετική)") }, singleLine = true,
                        enabled = !mutationBlocked, modifier = Modifier.testTag("savings_goal_date"),
                    )
                    editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked,
                    modifier = Modifier.testTag("savings_goal_save"),
                    onClick = {
                        val amount = amountDraft.replace(',', '.').toDoubleOrNull()
                        val date = dateDraft.trim().takeIf(String::isNotEmpty)
                        val parsed = date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                        val previous = state.savingsGoalItems.firstOrNull { it.id == editingId }
                        val today = state.asOfDate?.let { runCatching {
                            java.time.LocalDate.parse(it)
                        }.getOrNull() } ?: java.time.LocalDate.now()
                        editorError = when {
                            nameDraft.trim().isEmpty() || nameDraft.trim().length > 500 ->
                                "Συμπλήρωσε όνομα στόχου έως 500 χαρακτήρες."
                            amount == null || !amount.isFinite() || amount <= 0.0 ->
                                "Το ποσό στόχου πρέπει να είναι θετικό."
                            date != null && parsed == null -> "Η προθεσμία δεν είναι έγκυρη."
                            parsed != null && parsed.isBefore(today) && previous?.targetDate != date ->
                                "Δεν μπορείς να ορίσεις νέα προθεσμία στο παρελθόν."
                            else -> null
                        }
                        if (editorError == null) {
                            onSaveGoal(editingId, nameDraft.trim(), amountDraft, date)
                            editorVisible = false
                        }
                    },
                ) { Text("Αποθήκευση") }
            },
            dismissButton = {
                TextButton(onClick = { editorVisible = false }) { Text("Ακύρωση") }
            },
        )
    }
    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) confirmDeleteId = null },
            title = { Text("Διαγραφή στόχου;") },
            text = { Text("Αφαιρείται μόνο ο στόχος, όχι η αποταμίευση ή οι κινήσεις σου.") },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && onDeleteGoal != null,
                    modifier = Modifier.testTag("savings_goal_confirm_delete"),
                    onClick = {
                        onDeleteGoal?.invoke(id)
                        confirmDeleteId = null
                    },
                ) { Text("Διαγραφή στόχου") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Ακύρωση") }
            },
        )
    }
}

/** Historical duplicate IDs are distinct read-only rows, but never safe payment targets. */
internal fun canonicalLoanRowKey(loan: LoanItem, index: Int): String = "${loan.id}#row#${index}"

internal fun canonicalLoanPaymentIsUnambiguous(loan: LoanItem, rows: List<LoanItem>): Boolean =
    rows.count { it.id == loan.id } == 1 &&
        loan.remaining > 0.005 && (loan.isSelfLoan || loan.remainingInstallments > 0)

@Composable
fun CanonicalLoansScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
    onRecordPayment: (String, String, String, Int, Double?, String?) -> Unit = { _, _, _, _, _, _ -> },
    mutationBlocked: Boolean = false,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var selectedLoanId by rememberSaveable { mutableStateOf<String?>(null) }
    state.loans.firstOrNull { it.id == selectedLoanId &&
        canonicalLoanPaymentIsUnambiguous(it, state.loans) }?.let { loan ->
        CanonicalLoanPaymentDialog(
            loan = loan, accounts = state.accounts,
            onDismiss = { selectedLoanId = null },
            onConfirm = { date, account, count, amount, savings ->
                onRecordPayment(loan.id, account, date, count, amount, savings)
                selectedLoanId = null
            },
        )
    }
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Δάνεια",
                subtitle = "Συνολικό υπόλοιπο οφειλών",
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
                            text = amountVisibilityText(formatCanonicalEuro(state.loanOutstanding), amountsVisible),
                            tone = FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
            if (state.loans.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                "Δεν υπάρχουν αναλυτικά στοιχεία δανείων",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Το συνολικό υπόλοιπο παραμένει διαθέσιμο. Αν προστεθούν στοιχεία ανά δάνειο, θα εμφανιστούν εδώ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(state.loans, key = { index, loan ->
                    canonicalLoanRowKey(loan, index)
                }) { _, loan ->
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MyFinHubIconBadge(MyFinHubIcons.Plan, FinanceTone.Expense, null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(loan.name, style = MaterialTheme.typography.titleMedium)
                                    if (loan.lender.isNotBlank()) {
                                        Text(
                                            loan.lender,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                MyFinHubAmountText(amountVisibilityText(formatCanonicalEuro(loan.remaining), amountsVisible), FinanceTone.Expense)
                            }
                            if (loan.nextPaymentLabel.isNotBlank()) {
                                Text(
                                    loan.nextPaymentLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (canonicalLoanPaymentIsUnambiguous(loan, state.loans)) {
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = { selectedLoanId = loan.id },
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("loan_record_payment_" + loan.id),
                                ) { Text(if (loan.isSelfLoan) "Καταχώριση επιστροφής" else "Πληρωμή δόσης") }
                            } else if (state.loans.count { it.id == loan.id } > 1) {
                                Text(
                                    "Διπλό ιστορικό αναγνωριστικό δανείου · οι πληρωμές απαιτούν μοναδικό ID.",
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
}

@Composable
fun CanonicalLendingScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
    onRecordRepayment: (LendingItem) -> Unit = {},
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Απαιτήσεις",
                subtitle = "Ποσά που αναμένεις να επιστραφούν",
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
                            text = amountVisibilityText(formatCanonicalEuro(state.lendingReceivable), amountsVisible),
                            tone = FinanceTone.Income,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
            // Settled people can retain dated history without a current receivable.
            if (state.lendingItems.isEmpty() && state.lendingHistory.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                "Δεν υπάρχουν αναλυτικά στοιχεία απαιτήσεων",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Το συνολικό ποσό παραμένει διαθέσιμο. Αν προστεθούν πρόσωπα ή ημερομηνίες επιστροφής, θα εμφανιστούν εδώ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.lendingItems, key = LendingItem::id) { item ->
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MyFinHubIconBadge(MyFinHubIcons.Income, FinanceTone.Income, null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.personLabel, style = MaterialTheme.typography.titleMedium)
                                    if (item.dueLabel.isNotBlank()) {
                                        Text(
                                            item.dueLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                MyFinHubAmountText(amountVisibilityText(formatCanonicalEuro(item.amount), amountsVisible), FinanceTone.Income)
                            }
                            TextButton(onClick = { onRecordRepayment(item) }) {
                                Text("Καταχώριση επιστροφής")
                            }
                        }
                    }
                }
            }
            if (state.lendingHistory.isNotEmpty()) {
                item {
                    MyFinHubSectionHeading(
                        title = "Ιστορικό δανεικών",
                        subtitle = "Καταγεγραμμένες χορηγήσεις, επιστροφές και διαγραφές",
                        icon = MyFinHubIcons.Income,
                        tone = FinanceTone.Neutral,
                    )
                }
                // Web history can contain independent historical rows with repeated IDs.
                // Identity is render-only; never use a history list key as a mutation target.
                itemsIndexed(
                    items = state.lendingHistory,
                    key = { index, row -> "lending-history-$index-${row.id}" },
                ) { _, history ->
                    val typeLabel = when (history.action) {
                        "lent" -> "Έδωσα"
                        "repaid" -> "Επιστροφή"
                        "forgiven" -> "Διαγραφή απαίτησης"
                        else -> "Κίνηση"
                    }
                    val tone = if (history.action == "lent") FinanceTone.Expense else FinanceTone.Income
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                "${history.personLabel} · ${history.date}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                typeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (history.note.isNotBlank()) {
                                Text(history.note, style = MaterialTheme.typography.bodySmall)
                            }
                            MyFinHubAmountText(
                                text = amountVisibilityText(formatCanonicalEuro(history.amount), amountsVisible),
                                tone = tone,
                            )
                            Text(
                                "Ιστορικό υπόλοιπο κινήσεων: " +
                                    amountVisibilityText(formatCanonicalEuro(history.runningOutstanding), amountsVisible),
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
private fun EmptyFinanceText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatCanonicalEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
