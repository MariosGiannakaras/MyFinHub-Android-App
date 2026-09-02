package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.myfinhub.android.core.security.SecureWindowProtection
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubActionCard
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSectionHeading
import app.myfinhub.android.designsystem.MyFinHubSpacing
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
    onOpenSavings: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenLending: () -> Unit,
) {
    var activeCardId by remember(state.cards) { mutableStateOf(state.cards.firstOrNull()?.id) }
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
                subtitle = "Η συγχρονισμένη οικονομική σου εικόνα",
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
                            subtitle = "Τρέχοντα συγχρονισμένα υπόλοιπα",
                            icon = MyFinHubIcons.Account,
                            tone = FinanceTone.Neutral,
                        )
                        if (state.accounts.isEmpty()) {
                            EmptyFinanceText("Δεν υπάρχουν διαθέσιμοι λογαριασμοί.")
                        } else {
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
                                        text = formatCanonicalEuro(account.balance),
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
                        subtitle = "Υπόλοιπο λογαριασμών αποταμίευσης",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Savings,
                    )
                    MyFinHubAmountText(
                        text = formatCanonicalEuro(state.savingsCurrent),
                        tone = FinanceTone.Savings,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val goal = state.savingsGoal
                    if (goal != null && goal > 0.0) {
                        LinearProgressIndicator(
                            progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Στόχος ${formatCanonicalEuro(goal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Δεν έχει οριστεί συγχρονισμένος στόχος.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                MyFinHubSectionHeading(
                    title = "Κάρτες",
                    subtitle = "Ασφαλής πρόσβαση μόνο στα πραγματικά στοιχεία",
                    icon = MyFinHubIcons.Card,
                    tone = FinanceTone.Transfer,
                )
            }
            if (state.cards.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        EmptyFinanceText("Δεν υπάρχουν ενεργές κάρτες.")
                    }
                }
            } else {
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
            }

            item {
                if (state.loanOutstanding > 0.005 || state.loans.isNotEmpty()) {
                    MyFinHubActionCard(onClick = onOpenLoans, modifier = Modifier.fillMaxWidth()) {
                        MyFinHubSectionHeading(
                            title = "Δάνεια",
                            subtitle = "Συνολικό υπόλοιπο οφειλών",
                            icon = MyFinHubIcons.Plan,
                            tone = FinanceTone.Expense,
                        )
                        MyFinHubAmountText(
                            text = formatCanonicalEuro(state.loanOutstanding),
                            tone = FinanceTone.Expense,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (state.loans.isEmpty()) {
                                "Οι αναλυτικές εγγραφές δεν είναι διαθέσιμες για ασφαλή επεξεργασία."
                            } else {
                                "${state.loans.size} διαθέσιμες εγγραφές"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            MyFinHubSectionHeading(
                                title = "Δάνεια",
                                subtitle = "Δεν υπάρχει καταγεγραμμένο υπόλοιπο",
                                icon = MyFinHubIcons.Plan,
                                tone = FinanceTone.Neutral,
                            )
                        }
                    }
                }
            }

            item {
                if (state.lendingReceivable > 0.005 || state.lendingItems.isNotEmpty()) {
                    MyFinHubActionCard(onClick = onOpenLending, modifier = Modifier.fillMaxWidth()) {
                        MyFinHubSectionHeading(
                            title = "Απαιτήσεις",
                            subtitle = "Χρήματα που αναμένεις να επιστραφούν",
                            icon = MyFinHubIcons.Income,
                            tone = FinanceTone.Income,
                        )
                        MyFinHubAmountText(
                            text = formatCanonicalEuro(state.lendingReceivable),
                            tone = FinanceTone.Income,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (state.lendingItems.isEmpty()) {
                                "Οι αναλυτικές εγγραφές δεν είναι διαθέσιμες για ασφαλή επεξεργασία."
                            } else {
                                "${state.lendingItems.size} διαθέσιμες εγγραφές"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            MyFinHubSectionHeading(
                                title = "Απαιτήσεις",
                                subtitle = "Δεν υπάρχει καταγεγραμμένο υπόλοιπο",
                                icon = MyFinHubIcons.Income,
                                tone = FinanceTone.Neutral,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanonicalSavingsScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Αποταμίευση",
                subtitle = "Συγχρονισμένα δεδομένα",
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
                            text = formatCanonicalEuro(state.savingsCurrent),
                            tone = FinanceTone.Savings,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        val goal = state.savingsGoal
                        if (goal != null && goal > 0.0) {
                            LinearProgressIndicator(
                                progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Στόχος ${formatCanonicalEuro(goal)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Δεν υπάρχει συγχρονισμένος στόχος αποταμίευσης στον λογαριασμό σου.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.savingsGoal == null) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                "Επεξεργασία στόχου μη διαθέσιμη",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Η εφαρμογή δεν θα εμφανίσει ή αποθηκεύσει τοπικό στόχο σαν να έχει συγχρονιστεί. Η επεξεργασία θα ενεργοποιηθεί όταν υπάρχει αντίστοιχη canonical δυνατότητα.",
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
fun CanonicalLoansScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Δάνεια",
                subtitle = "Συγχρονισμένο υπόλοιπο",
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
                            text = formatCanonicalEuro(state.loanOutstanding),
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
                                "Δεν υπάρχουν διαθέσιμες λεπτομέρειες ανά δάνειο",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Το συνολικό υπόλοιπο προκύπτει από τα συγχρονισμένα οικονομικά δεδομένα. Δεν εμφανίζονται εκτιμώμενα ονόματα, πιστωτές, δόσεις ή ημερομηνίες όταν αυτά δεν παρέχονται με ασφαλή canonical μορφή.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.loans, key = LoanItem::id) { loan ->
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
                                MyFinHubAmountText(formatCanonicalEuro(loan.remaining), FinanceTone.Expense)
                            }
                            if (loan.nextPaymentLabel.isNotBlank()) {
                                Text(
                                    loan.nextPaymentLabel,
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
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Απαιτήσεις",
                subtitle = "Συγχρονισμένο υπόλοιπο",
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
                            text = formatCanonicalEuro(state.lendingReceivable),
                            tone = FinanceTone.Income,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
            if (state.lendingItems.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                "Δεν υπάρχουν διαθέσιμες λεπτομέρειες ανά απαίτηση",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Το συνολικό ποσό παραμένει ορατό, αλλά δεν εμφανίζονται υποθετικά πρόσωπα ή ημερομηνίες όταν αυτά δεν υπάρχουν στα συγχρονισμένα δεδομένα.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.lendingItems, key = LendingItem::id) { item ->
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
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
                            MyFinHubAmountText(formatCanonicalEuro(item.amount), FinanceTone.Income)
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
