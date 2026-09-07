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
import app.myfinhub.android.designsystem.MyFinHubHeroCard
import app.myfinhub.android.designsystem.MyFinHubHeroHeading
import app.myfinhub.android.designsystem.MyFinHubHeroMetric
import app.myfinhub.android.designsystem.MyFinHubHeroValue
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
    onAddCard: () -> Unit = {},
    onOpenAccount: (String) -> Unit = {},
    onOpenSavings: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenLending: () -> Unit,
) {
    var activeCardId by remember(state.cards) { mutableStateOf(state.cards.firstOrNull()?.id) }
    val revealedCardId = (secretState as? CardSecretUiState.Revealed)?.cardId
    val accountTotal = state.accounts.sumOf(MoneyAccount::balance)
    val netPosition = accountTotal + state.lendingReceivable - state.loanOutstanding

    SecureWindowProtection(active = revealedCardId != null && revealedCardId == activeCardId)
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
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md)) {
                        MyFinHubHeroHeading(
                            eyebrow = "Καθαρή θέση",
                            title = "Η συνολική σου θέση",
                            supporting = "Λογαριασμοί και απαιτήσεις, μείον τις οφειλές",
                        )
                        MyFinHubHeroValue(formatCanonicalEuro(netPosition))
                        Text(
                            "Οι αναλυτικές αξίες εμφανίζονται στις αντίστοιχες ενότητες παρακάτω.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                        )
                    }
                }
            }

            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubSectionHeading(
                            title = "Λογαριασμοί",
                            subtitle = "${state.accounts.size} συγχρονισμένοι λογαριασμοί",
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
                        subtitle = "Πρόοδος προς τον συγχρονισμένο στόχο",
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
                            text = formatCanonicalEuro(state.savingsCurrent),
                            tone = FinanceTone.Savings,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (goal != null && goal > 0.0) {
                            Text(
                                "από ${formatCanonicalEuro(goal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (goal != null && goal > 0.0) {
                        LinearProgressIndicator(
                            progress = { (state.savingsCurrent / goal).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
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
                    TextButton(onClick = onAddCard) { Text("Προσθήκη") }
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
                MyFinHubSectionHeading(
                    title = "Υποχρεώσεις & επιστροφές",
                    subtitle = "Συνολική εικόνα χωρίς υποθετικές λεπτομέρειες",
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
                                if (state.loans.isEmpty()) "Συνολικό συγχρονισμένο υπόλοιπο" else "${state.loans.size} διαθέσιμες εγγραφές",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubAmountText(
                            text = formatCanonicalEuro(state.loanOutstanding),
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
                                if (state.lendingItems.isEmpty()) "Αναμενόμενες επιστροφές" else "${state.lendingItems.size} διαθέσιμες εγγραφές",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubAmountText(
                            text = formatCanonicalEuro(state.lendingReceivable),
                            tone = FinanceTone.Income,
                            style = MaterialTheme.typography.titleMedium,
                        )
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