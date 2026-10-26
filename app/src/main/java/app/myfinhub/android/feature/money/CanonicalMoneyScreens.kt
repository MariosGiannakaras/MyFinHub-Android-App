package app.myfinhub.android.feature.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.ui.financialProviderForAccount
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
import app.myfinhub.android.designsystem.MyFinHubCertifiedProviderMark
import app.myfinhub.android.designsystem.LocalFinancialProviderCatalog
import app.myfinhub.android.core.network.certifiedBrandAsset
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
                                    val ownerRecord=LocalFinancialProviderCatalog.current.firstOrNull {
                                        it.id == account.providerId
                                    }
                                    val tone=if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                                        "dark" else "light"
                                    val certified=ownerRecord?.certifiedBrandAsset("logo",tone)
                                    val provider = financialProviderForAccount(account.id, account.institution ?: account.name, account.providerId)
                                    if (ownerRecord != null && certified != null) {
                                        MyFinHubCertifiedProviderMark(
                                            providerId=ownerRecord.id,asset=certified,
                                            fallbackLabel=ownerRecord.shortName,
                                            modifier=Modifier.size(34.dp),
                                            contentDescription=ownerRecord.displayName,
                                        )
                                    } else if (provider != null) {
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
    onSaveMonthlySavingsRate: ((String) -> Unit)? = null,
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
    var expandedGoalOrdinal by rememberSaveable { mutableStateOf<Int?>(null) }
    var targetEditorVisible by rememberSaveable { mutableStateOf(false) }
    var targetPercentDraft by rememberSaveable { mutableStateOf("20") }
    var targetPercentError by rememberSaveable { mutableStateOf<String?>(null) }

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
                            if (onSaveMonthlySavingsRate != null) {
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = {
                                        targetPercentDraft =
                                            (performance.targetRate * 100.0).let { rate ->
                                                if (rate == rate.toLong().toDouble()) rate.toLong().toString()
                                                else rate.toString()
                                            }
                                        targetPercentError = null
                                        targetEditorVisible = true
                                    },
                                    modifier = Modifier.testTag("savings_month_target_edit"),
                                ) { Text("Αλλαγή ποσοστού στόχου") }
                            }
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
            val monthlyTrend = state.savingsMonthlyTrend
            if (monthlyTrend != null && state.savingsMonthlyTarget != null) {
                item("savings-month-trend") {
                    val trend = monthlyTrend
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth().testTag("savings_month_trend_card"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            Text("Εξέλιξη αποταμίευσης μήνα",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Πραγματική αποταμίευση (συνεχής) · Στόχος (διακεκομμένη)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (amountsVisible) {
                                // A read-only Web five-point chart; never expose hidden
                                // balances through the shape while privacy is enabled.
                                val actualColor = MaterialTheme.colorScheme.primary
                                val targetColor = MaterialTheme.colorScheme.tertiary
                                Canvas(
                                    modifier = Modifier.fillMaxWidth().height(112.dp)
                                        .testTag("savings_month_trend_chart")
                                        .semantics {
                                            contentDescription = "Γράφημα αποταμίευσης: " +
                                                "πραγματική " + formatCanonicalEuro(trend.actual.lastOrNull() ?: 0.0) +
                                                ", στόχος " + formatCanonicalEuro(trend.target.lastOrNull() ?: 0.0)
                                        },
                                ) {
                                    val count = trend.days.size
                                    if (count >= 2 && trend.scaleMax > 0.0) {
                                        val topPad = 8.dp.toPx()
                                        val height = (size.height - topPad * 2).coerceAtLeast(1f)
                                        val scale = trend.scaleMax
                                        fun line(values: List<Double>): Path = Path().apply {
                                            values.forEachIndexed { index, value ->
                                                val x = size.width * index / (count - 1)
                                                val clipped = (value / scale).coerceIn(0.0, 1.0)
                                                val y = topPad + height * (1f - clipped.toFloat())
                                                if (index == 0) moveTo(x, y) else lineTo(x, y)
                                            }
                                        }
                                        drawPath(
                                            path = line(trend.target),
                                            color = targetColor,
                                            style = Stroke(
                                                width = 2.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(
                                                    floatArrayOf(8.dp.toPx(), 5.dp.toPx()),
                                                ),
                                            ),
                                        )
                                        drawPath(
                                            path = line(trend.actual),
                                            color = actualColor,
                                            style = Stroke(width = 2.5.dp.toPx()),
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    trend.days.forEach { day ->
                                        Text(day.toString(),style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                Text(
                                    "Το γράφημα είναι κρυμμένο μαζί με τα οικονομικά ποσά.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                itemsIndexed(state.savingsGoalItems, key = { index, goal ->
                    "goal-$index-${goal.id}"
                }) { index, goal ->
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
                            val advice = state.savingsGoalAdvisories.getOrNull(index)
                            if (advice != null) {
                                TextButton(
                                    onClick = {
                                        expandedGoalOrdinal =
                                            if (expandedGoalOrdinal == index) null else index
                                    },
                                    modifier = Modifier.testTag("savings_goal_projection_" + goal.id),
                                ) {
                                    Text(if (expandedGoalOrdinal == index)
                                        "Κλείσιμο προβολής" else "Προβολή στόχου")
                                }
                                if (expandedGoalOrdinal == index) {
                                    CanonicalSavingsGoalAdvisoryDetails(
                                        advice = advice,
                                        amountsVisible = amountsVisible,
                                        modifier = Modifier.testTag(
                                            "savings_goal_projection_details_" + goal.id),
                                    )
                                }
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
    if (targetEditorVisible && onSaveMonthlySavingsRate != null) {
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) targetEditorVisible = false },
            title = { Text("Μηνιαίος στόχος αποταμίευσης") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "Ποσοστό πραγματικών εσόδων από 0 έως 100. " +
                            "Η αλλαγή στόχου δεν μεταφέρει χρήματα.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = targetPercentDraft,
                        onValueChange = { targetPercentDraft = it; targetPercentError = null },
                        label = { Text("Στόχος (%)") }, singleLine = true,
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("savings_month_target_percent"),
                    )
                    targetPercentError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked,
                    modifier = Modifier.testTag("savings_month_target_save"),
                    onClick = {
                        val percent = targetPercentDraft.replace(',', '.').toDoubleOrNull()
                        targetPercentError = if (percent == null || !percent.isFinite() ||
                            percent !in 0.0..100.0
                        ) "Βάλε ποσοστό από 0 έως 100." else null
                        if (targetPercentError == null) {
                            onSaveMonthlySavingsRate(targetPercentDraft)
                            targetEditorVisible = false
                        }
                    },
                ) { Text("Αποθήκευση στόχου") }
            },
            dismissButton = {
                TextButton(onClick = { targetEditorVisible = false }) { Text("Ακύρωση") }
            },
        )
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
private fun CanonicalLoanFinanceRow(
    loan: LoanItem,
    amountsVisible: Boolean,
    mutationBlocked: Boolean,
    eligible: Boolean,
    duplicateIdentity: Boolean,
    history: List<LoanPaymentHistoryItem>,
    accounts: List<MoneyAccount>,
    onPay: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    var showHistory by rememberSaveable(loan.id) { mutableStateOf(false) }
    var historyLimit by rememberSaveable(loan.id) { mutableStateOf(10) }
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
                MyFinHubAmountText(
                    amountVisibilityText(formatCanonicalEuro(loan.remaining), amountsVisible),
                    FinanceTone.Expense,
                )
            }
            if (loan.nextPaymentLabel.isNotBlank()) {
                Text(loan.nextPaymentLabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!loan.isSelfLoan && loan.totalInstallments > 0) {
                val paidInstallments = (loan.totalInstallments -
                    loan.remainingInstallments).coerceIn(0, loan.totalInstallments)
                Text(
                    "$paidInstallments / ${loan.totalInstallments} πληρωμένες δόσεις",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("loan_paid_installment_count_" + loan.id),
                )
                CanonicalLoanInstallmentSegments(loan)
            }
            if (!loan.isSelfLoan && loan.remainingInstallments > 0) {
                Text(
                    "Απομένουν ${loan.remainingInstallments} δόσεις" +
                        if (loan.monthlyPayment > 0.0) " · ${amountVisibilityText(formatCanonicalEuro(loan.monthlyPayment), amountsVisible)} / δόση"
                        else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (eligible) {
                TextButton(
                    enabled = !mutationBlocked,
                    onClick = onPay,
                    modifier = Modifier.fillMaxWidth()
                        .testTag("loan_record_payment_" + loan.id),
                ) {
                    Text(if (loan.isSelfLoan) "Καταχώριση επιστροφής" else "Πληρωμή δόσης")
                }
            } else if (duplicateIdentity) {
                Text(
                    "Διπλό ιστορικό ID δανείου · η πληρωμή απαιτεί μοναδικό αναγνωριστικό.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onEdit != null && !duplicateIdentity) {
                TextButton(
                    onClick = onEdit,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth()
                        .testTag("loan_edit_" + loan.id),
                ) { Text("Επεξεργασία στοιχείων δανείου") }
            }
            if (history.isNotEmpty() && !duplicateIdentity) {
                TextButton(
                    onClick = { showHistory = !showHistory; historyLimit = 10 },
                    modifier = Modifier.fillMaxWidth()
                        .testTag("loan_history_toggle_" + loan.id),
                ) {
                    Text(if (showHistory) "Απόκρυψη πληρωμών"
                        else "Συνδεδεμένες πληρωμές (${history.size})")
                }
                if (showHistory) {
                    history.take(historyLimit).forEachIndexed { i, entry ->
                        val accountLabel = entry.accountId?.let { accountId ->
                            accounts.singleOrNull { it.id == accountId }?.name
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .testTag("loan_payment_history_${i}"),
                            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                        ) {
                            Text(
                                entry.date +
                                    if (entry.installmentCount > 1)
                                        " · ${entry.installmentCount} δόσεις" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (entry.note.isNotBlank()) {
                                Text(entry.note, style = MaterialTheme.typography.bodySmall)
                            }
                            if (accountLabel != null) {
                                Text(accountLabel, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MyFinHubAmountText(
                                amountVisibilityText(
                                    formatCanonicalEuro(entry.amount), amountsVisible,
                                ),
                                FinanceTone.Expense,
                            )
                        }
                    }
                    if (history.size > historyLimit) {
                        TextButton(
                            onClick = {
                                historyLimit = (historyLimit + 10).coerceAtMost(history.size)
                            },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("loan_history_more_" + loan.id),
                        ) { Text("Περισσότερες πληρωμές (${history.size - historyLimit})") }
                    }
                }
            }
        }
    }
}

/** Mirrors Web LoansPage active/completed groups and client-only sorting. */
@Composable
fun CanonicalLoansScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
    onRecordPayment: (String, String, String, Int, Double?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onSaveLoanMetadata: ((LoanMetadataEditRequest) -> Unit)? = null,
    expenseCategories: List<String> = emptyList(),
    mutationBlocked: Boolean = false,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var selectedLoanId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingLoan by rememberSaveable { mutableStateOf(false) }
    var editingLoanId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortKey by rememberSaveable { mutableStateOf("remaining") }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var activeLimit by rememberSaveable { mutableStateOf(20) }
    var completedLimit by rememberSaveable { mutableStateOf(20) }
    val sorted = state.loans.withIndex().sortedWith { a, b ->
        val first = when (sortKey) {
            "name" -> a.value.name.lowercase(Locale.forLanguageTag("el-GR"))
                .compareTo(b.value.name.lowercase(Locale.forLanguageTag("el-GR")))
            "amount" -> a.value.remaining.compareTo(b.value.remaining)
            "next" -> (a.value.typicalPaymentDay ?: 99)
                .compareTo(b.value.typicalPaymentDay ?: 99)
            else -> a.value.remainingInstallments
                .compareTo(b.value.remainingInstallments)
                .takeUnless { it == 0 } ?: a.value.remaining.compareTo(b.value.remaining)
        }
        val withTie = if (first == 0) a.value.id.compareTo(b.value.id) else first
        if (withTie != 0) {
            if (ascending) withTie else -withTie
        } else a.index.compareTo(b.index)
    }
    val active = sorted.filter {
        it.value.remaining > 0.005 &&
            (it.value.isSelfLoan || it.value.remainingInstallments > 0)
    }
    val completed = sorted.filterNot { it in active }
    val editorLoan = editingLoanId?.let { targetId ->
        state.loans.singleOrNull {
            it.id == targetId && !it.isSelfLoan &&
                it.kind in setOf("loan", "installment")
        }
    }
    if (onSaveLoanMetadata != null && (creatingLoan || editorLoan != null)) {
        CanonicalLoanMetadataEditor(
            initial = editorLoan,
            accounts = state.accounts,
            categories = expenseCategories,
            mutationBlocked = mutationBlocked,
            onDismiss = { creatingLoan = false; editingLoanId = null },
            onSave = { request ->
                onSaveLoanMetadata(request)
                creatingLoan = false
                editingLoanId = null
            },
        )
    }
    state.loans.singleOrNull { it.id == selectedLoanId &&
        canonicalLoanPaymentIsUnambiguous(it, state.loans) }?.let { loan ->
        CanonicalLoanPaymentDialog(
            loan = loan, accounts = state.accounts, amountsVisible = amountsVisible,
            onDismiss = { selectedLoanId = null },
            onConfirm = { date, account, count, amount, savings ->
                if (!mutationBlocked && canonicalLoanPaymentIsUnambiguous(loan, state.loans)) {
                    onRecordPayment(loan.id, account, date, count, amount, savings)
                }
                selectedLoanId = null
            },
        )
    }
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Δάνεια",
                subtitle = "Ενεργές και ολοκληρωμένες υποχρεώσεις",
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
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                    .testTag("loan_total_outstanding")) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Συνολικό υπόλοιπο", style = MaterialTheme.typography.labelLarge)
                        MyFinHubAmountText(
                            text = amountVisibilityText(
                                formatCanonicalEuro(state.loanOutstanding), amountsVisible,
                            ),
                            tone = FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "${active.size} ενεργά · ${completed.size} ολοκληρωμένα",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (onSaveLoanMetadata != null) {
                            TextButton(
                                onClick = { creatingLoan = true },
                                enabled = !mutationBlocked,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("loan_create_new"),
                            ) { Text("Νέο δάνειο ή αγορά σε δόσεις") }
                        }
                    }
                }
            }
            if (state.loans.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Δεν υπάρχουν καταχωρισμένα δάνεια.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                        .testTag("loan_sort_controls")) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text("Ταξινόμηση", style = MaterialTheme.typography.titleSmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                listOf("remaining" to "Δόσεις", "amount" to "Ποσό")
                                    .forEach { (key, label) ->
                                        androidx.compose.material3.FilterChip(
                                            selected = sortKey == key,
                                            onClick = { sortKey = key; activeLimit = 20; completedLimit = 20 },
                                            label = { Text(label) },
                                        )
                                    }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                                listOf("name" to "Όνομα", "next" to "Ημέρα")
                                    .forEach { (key, label) ->
                                        androidx.compose.material3.FilterChip(
                                            selected = sortKey == key,
                                            onClick = { sortKey = key; activeLimit = 20; completedLimit = 20 },
                                            label = { Text(label) },
                                        )
                                    }
                            }
                            TextButton(
                                onClick = { ascending = !ascending },
                                modifier = Modifier.testTag("loan_sort_direction"),
                            ) { Text(if (ascending) "Αύξουσα ↑" else "Φθίνουσα ↓") }
                        }
                    }
                }
                item {
                    MyFinHubSectionHeading(
                        title = "Ενεργές οφειλές",
                        subtitle = "${active.size} δάνεια με υπόλοιπο",
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Expense,
                    )
                }
                if (active.isEmpty()) item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Δεν υπάρχουν ενεργές οφειλές.")
                    }
                }
                itemsIndexed(
                    active.take(activeLimit),
                    key = { _, indexed -> canonicalLoanRowKey(indexed.value, indexed.index) },
                ) { _, indexed ->
                    CanonicalLoanFinanceRow(
                        loan = indexed.value,
                        amountsVisible = amountsVisible,
                        mutationBlocked = mutationBlocked,
                        eligible = canonicalLoanPaymentIsUnambiguous(indexed.value, state.loans),
                        duplicateIdentity = state.loans.count { it.id == indexed.value.id } > 1,
                        history = state.loanPaymentsById[indexed.value.id].orEmpty(),
                        accounts = state.accounts,
                        onPay = { selectedLoanId = indexed.value.id },
                        onEdit = if (onSaveLoanMetadata != null &&
                            !indexed.value.isSelfLoan &&
                            indexed.value.kind in setOf("loan", "installment") &&
                            state.loans.count { it.id == indexed.value.id } == 1
                        ) ({ editingLoanId = indexed.value.id }) else null,
                    )
                }
                if (active.size > activeLimit) item {
                    TextButton(
                        onClick = { activeLimit = (activeLimit + 20).coerceAtMost(active.size) },
                        modifier = Modifier.fillMaxWidth().testTag("loan_more_active"),
                    ) { Text("Περισσότερα ενεργά (${active.size - activeLimit})") }
                }
                if (completed.isNotEmpty()) {
                    item {
                        MyFinHubSectionHeading(
                            title = "Ολοκληρωμένα δάνεια",
                            subtitle = "Ιστορικά στοιχεία χωρίς ενεργή πληρωμή",
                            icon = MyFinHubIcons.Plan,
                            tone = FinanceTone.Neutral,
                        )
                    }
                    itemsIndexed(
                        completed.take(completedLimit),
                        key = { _, indexed -> "completed-" +
                            canonicalLoanRowKey(indexed.value, indexed.index) },
                    ) { _, indexed ->
                        CanonicalLoanFinanceRow(
                            loan = indexed.value,
                            amountsVisible = amountsVisible,
                            mutationBlocked = true,
                            eligible = false,
                            duplicateIdentity = state.loans.count { it.id == indexed.value.id } > 1,
                            history = state.loanPaymentsById[indexed.value.id].orEmpty(),
                            accounts = state.accounts,
                            onPay = {},
                            onEdit = if (onSaveLoanMetadata != null &&
                                !indexed.value.isSelfLoan &&
                                indexed.value.kind in setOf("loan", "installment") &&
                                state.loans.count { it.id == indexed.value.id } == 1
                            ) ({ editingLoanId = indexed.value.id }) else null,
                        )
                    }
                    if (completed.size > completedLimit) item {
                        TextButton(
                            onClick = {
                                completedLimit = (completedLimit + 20).coerceAtMost(completed.size)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("loan_more_completed"),
                        ) { Text("Περισσότερα ολοκληρωμένα (${completed.size - completedLimit})") }
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
    onRecordLending: (String) -> Unit = {},
    mutationBlocked: Boolean = false,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var settlementPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var actionFilter by rememberSaveable { mutableStateOf("all") }
    var peopleLimit by rememberSaveable { mutableStateOf(24) }
    var historyLimit by rememberSaveable { mutableStateOf(30) }
    // The canonical person rows are the source of receivable balances; historical
    // rows only provide independently auditable movements, never synthetic balances.
    val allPeople = (
        state.lendingItems.map { it.personLabel } +
            state.lendingHistory.map { it.personLabel }
    ).filter { it.isNotBlank() }.distinct().sortedBy {
        it.lowercase(Locale.forLanguageTag("el-GR"))
    }
    val displayedPeople = allPeople.filter {
        query.isBlank() || it.contains(query.trim(), ignoreCase = true)
    }
    val focusPerson = selectedPerson?.takeIf { it in allPeople }
    val currentRow = state.lendingItems.singleOrNull { it.personLabel == focusPerson }
    val visibleHistory = state.lendingHistory.filter { entry ->
        (focusPerson == null || entry.personLabel == focusPerson) &&
            (actionFilter == "all" || entry.action == actionFilter)
    }
    val displayedTotal = state.lendingReceivable
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Απαιτήσεις",
                subtitle = "Δανεικά ανά πρόσωπο και πραγματικές κινήσεις",
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
                MyFinHubSectionCard(
                    modifier = Modifier.fillMaxWidth().testTag("lending_totals")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Αναμενόμενες επιστροφές", style = MaterialTheme.typography.labelLarge)
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatCanonicalEuro(displayedTotal), amountsVisible),
                            tone = FinanceTone.Income,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "${state.lendingItems.size} ενεργές απαιτήσεις · " +
                                "${allPeople.size} πρόσωπα με στοιχεία",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (allPeople.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Δεν υπάρχουν καταγεγραμμένα πρόσωπα ή κινήσεις δανεικών.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                item {
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth().testTag("lending_person_selector")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text("Πρόσωπα", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it; peopleLimit = 24 },
                                label = { Text("Αναζήτηση προσώπου") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("lending_person_search"),
                            )
                            TextButton(
                                onClick = { selectedPerson = null; historyLimit = 30 },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("lending_all_people"),
                            ) {
                                Text(if (focusPerson == null) "✓ Όλα τα πρόσωπα"
                                else "Προβολή όλων των προσώπων")
                            }
                        }
                    }
                }
                itemsIndexed(
                    displayedPeople.take(peopleLimit),
                    key = { index, person -> "lending-person-${index}-${person}" },
                ) { _, person ->
                    val matches = state.lendingItems.filter { it.personLabel == person }
                    val balance = matches.singleOrNull()?.amount
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MyFinHubIconBadge(MyFinHubIcons.Income, FinanceTone.Income, null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (balance == null) "Ιστορικό χωρίς ενεργή απαίτηση"
                                        else "Ενεργή απαίτηση",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (balance != null) {
                                    MyFinHubAmountText(
                                        amountVisibilityText(formatCanonicalEuro(balance), amountsVisible),
                                        FinanceTone.Income,
                                    )
                                }
                            }
                            TextButton(
                                onClick = { selectedPerson = person; historyLimit = 30 },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("lending_select_person"),
                            ) { Text(if (person == focusPerson) "✓ Επιλεγμένο πρόσωπο" else "Προβολή κινήσεων") }
                            if (person == focusPerson) {
                                state.confirmedLendingPlans.filter { it.person == person }
                                    .forEach { confirmedPlan ->
                                        CanonicalConfirmedLendingPlanDetails(
                                            plan = confirmedPlan,
                                            currentOutstanding = balance,
                                            amountsVisible = amountsVisible,
                                        )
                                    }
                                if (matches.size == 1 && balance != null && balance > 0.005 &&
                                    !state.asOfDate.isNullOrEmpty()) {
                                    TextButton(
                                        onClick = {
                                            settlementPerson =
                                                if (settlementPerson == person) null else person
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                            .testTag("lending_settlement_toggle"),
                                    ) {
                                        Text(if (settlementPerson == person)
                                            "Κλείσιμο εκτίμησης επιστροφών"
                                            else "Εκτίμηση επιστροφών")
                                    }
                                    if (settlementPerson == person) {
                                        CanonicalLendingSettlementPanel(
                                            person = person,
                                            outstanding = balance,
                                            asOf = state.asOfDate,
                                            amountsVisible = amountsVisible,
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { onRecordLending(person) },
                                    enabled = !mutationBlocked,
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("lending_record_new"),
                                ) { Text("Του δίνω · νέα καταχώριση") }
                                if (matches.size == 1 && balance != null && balance > 0.005) {
                                    TextButton(
                                        onClick = { onRecordRepayment(matches.single()) },
                                        enabled = !mutationBlocked,
                                        modifier = Modifier.fillMaxWidth()
                                            .testTag("lending_record_repayment"),
                                    ) { Text("Μου επιστρέφει · νέα καταχώριση") }
                                }
                            }
                        }
                    }
                }
                if (displayedPeople.size > peopleLimit) {
                    item {
                        TextButton(
                            onClick = {
                                peopleLimit = (peopleLimit + 24).coerceAtMost(displayedPeople.size)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("lending_more_people"),
                        ) { Text("Περισσότερα πρόσωπα (${displayedPeople.size - peopleLimit})") }
                    }
                }
                item {
                    MyFinHubSectionHeading(
                        title = "Ιστορικό δανεικών",
                        subtitle = if (focusPerson == null)
                            "Πραγματικές χορηγήσεις, επιστροφές και διαγραφές"
                        else "Κινήσεις: $focusPerson",
                        icon = MyFinHubIcons.Income,
                        tone = FinanceTone.Neutral,
                    )
                }
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                        .testTag("lending_history_filters")) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            ) {
                                androidx.compose.material3.FilterChip(
                                    selected = actionFilter == "all",
                                    onClick = { actionFilter = "all"; historyLimit = 30 },
                                    label = { Text("Όλες") },
                                )
                                androidx.compose.material3.FilterChip(
                                    selected = actionFilter == "lent",
                                    onClick = { actionFilter = "lent"; historyLimit = 30 },
                                    label = { Text("Έδωσα") },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            ) {
                                androidx.compose.material3.FilterChip(
                                    selected = actionFilter == "repaid",
                                    onClick = { actionFilter = "repaid"; historyLimit = 30 },
                                    label = { Text("Επιστροφές") },
                                )
                                androidx.compose.material3.FilterChip(
                                    selected = actionFilter == "forgiven",
                                    onClick = { actionFilter = "forgiven"; historyLimit = 30 },
                                    label = { Text("Διαγραφές") },
                                )
                            }
                        }
                    }
                }
                if (visibleHistory.isEmpty()) {
                    item {
                        MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Δεν υπάρχουν κινήσεις με αυτά τα φίλτρα.")
                        }
                    }
                }
                // Imported event IDs may repeat. The index is only a stable render
                // discriminator, NEVER a write target or an assumed unique event ID.
                itemsIndexed(
                    visibleHistory.take(historyLimit),
                    key = { index, item -> "lending-history-${index}-${item.id}" },
                ) { _, history ->
                    val typeLabel = when (history.action) {
                        "lent" -> "Έδωσα"
                        "repaid" -> "Επιστροφή"
                        "forgiven" -> "Διαγραφή απαίτησης"
                        else -> "Κίνηση"
                    }
                    val tone = if (history.action == "lent")
                        FinanceTone.Expense else FinanceTone.Income
                    val accountLabel = history.accountId?.let { accountId ->
                        state.accounts.singleOrNull { it.id == accountId }?.name
                    }
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                "${history.personLabel} · ${history.date}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(typeLabel, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            if (accountLabel != null || history.origin == "legacy") {
                                Text(
                                    accountLabel ?: "Ιστορικό χωρίς λογαριασμό",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (visibleHistory.size > historyLimit) {
                    item {
                        TextButton(
                            onClick = { historyLimit = (historyLimit + 30).coerceAtMost(visibleHistory.size) },
                            modifier = Modifier.fillMaxWidth().testTag("lending_more_history"),
                        ) {
                            Text("Περισσότερες κινήσεις (${visibleHistory.size - historyLimit})")
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
