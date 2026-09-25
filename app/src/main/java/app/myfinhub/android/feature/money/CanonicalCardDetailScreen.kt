package app.myfinhub.android.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonicalCardDetailScreen(
    card: MoneyCard?,
    cardId: String = card?.id.orEmpty(),
    cards: List<MoneyCard> = listOfNotNull(card),
    onSelectCard: (String) -> Unit = {},
    cleanupState: CardSecretCleanupUiState = CardSecretCleanupUiState.Idle,
    onRetryCleanup: (String) -> Unit = {},
    onOpenSecureDetails: (String) -> Unit = {},
    onRemoveCard: (String) -> Unit = {},
    onAddPurchase: () -> Unit = {},
    onPayCard: () -> Unit = {},
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit = {},
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var cardPickerOpen by remember(cardId) { mutableStateOf(false) }
    var showAllActivity by remember(cardId) { mutableStateOf(false) }
    var removeDialogOpen by remember(cardId) { mutableStateOf(false) }
    val provider = card?.let { financialProvider(it.bankId, it.nickname) }
    val isCredit = card?.let { it.canonicalKind == "credit" || it.kind.contains("Πιστωτική", ignoreCase = true) } == true
    val availableCredit = card?.limit?.takeIf { isCredit && it > 0.0 }?.let { limit ->
        (limit - card.currentBalance.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = card?.nickname ?: "Κάρτα",
                subtitle = provider?.institutionLabel ?: card?.kind ?: "Δεν είναι διαθέσιμη",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubDesignMetrics.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (card == null) {
                RemovedOrUnavailableCard(
                    cardId = cardId,
                    cleanupState = cleanupState,
                    onRetryCleanup = onRetryCleanup,
                    onBack = onBack,
                )
                return@Column
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (provider != null) {
                            MyFinHubProviderMark(provider, modifier = Modifier.size(40.dp), contentDescription = provider.institutionLabel)
                        } else {
                            MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Transfer, null)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            provider?.institutionLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(if (card.last4.isBlank()) "••••" else "•••• ${card.last4}", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        listOf(card.kind, card.network).filter { it.isNotBlank() }.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cards.size > 1) {
                        OutlinedButton(
                            onClick = { cardPickerOpen = true },
                            modifier = Modifier.fillMaxWidth().testTag("card_switcher"),
                        ) { Text("Αλλαγή κάρτας") }
                    }
                    if (isCredit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MyFinHubAmountText(
                            amountVisibilityText(formatCardEuro(card.currentBalance.coerceAtLeast(0.0)), amountsVisible),
                            FinanceTone.Expense,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        card.limit?.takeIf { it > 0.0 }?.let { limit ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CardMetric("Πιστωτικό όριο", amountVisibilityText(formatCardEuro(limit), amountsVisible))
                                availableCredit?.let {
                                    CardMetric("Διαθέσιμη πίστωση", amountVisibilityText(formatCardEuro(it), amountsVisible), Alignment.End)
                                }
                            }
                        }
                    }
                }
            }

            if (isCredit) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Ενέργειες πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        MyFinHubPrimaryAction(
                            label = "Πληρωμή κάρτας",
                            onClick = onPayCard,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = card.currentBalance > 0.005,
                            icon = null,
                        )
                        OutlinedButton(onClick = onAddPurchase, modifier = Modifier.fillMaxWidth()) {
                            Text("Καταχώριση αγοράς")
                        }
                    }
                }

                CardActivitySection(
                    card = card,
                    amountsVisible = amountsVisible,
                    showAll = showAllActivity,
                    onToggleAll = { showAllActivity = !showAllActivity },
                    onOpenActivity = onOpenActivity,
                )
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text("Διαχείριση κάρτας", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { onOpenSecureDetails(card.id) },
                        modifier = Modifier.fillMaxWidth().testTag("card_secure_details"),
                    ) { Text("Στοιχεία κάρτας") }
                    Text(
                        "Ο αριθμός, η λήξη και το CVV εμφανίζονται πλήρως στην οθόνη στοιχείων κάρτας.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MyFinHubDestructiveTextAction(
                        label = "Αφαίρεση από το MyFinHub",
                        onClick = { removeDialogOpen = true },
                    )
                }
            }
        }
    }

    if (cardPickerOpen) {
        CardPickerSheet(
            selectedCardId = card?.id,
            cards = cards,
            onDismiss = { cardPickerOpen = false },
            onSelect = { selectedId ->
                cardPickerOpen = false
                if (selectedId != card?.id) onSelectCard(selectedId)
            },
        )
    }

    if (removeDialogOpen && card != null) {
        AlertDialog(
            onDismissRequest = { removeDialogOpen = false },
            title = { Text("Αφαίρεση από το MyFinHub;") },
            text = {
                Text(
                    "Η κάρτα θα φύγει από την ενεργή λίστα. Το ιστορικό και τυχόν οφειλή διατηρούνται. " +
                        "Δεν ακυρώνεται στην τράπεζα. Μετά την αφαίρεση θα καθαριστούν τα κρυπτογραφημένα στοιχεία της συσκευής και τυχόν παλιό server-vault υπόλοιπο.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeDialogOpen = false
                        onRemoveCard(card.id)
                    },
                    modifier = Modifier.testTag("confirm_remove_card"),
                ) { Text("Αφαίρεση") }
            },
            dismissButton = { TextButton(onClick = { removeDialogOpen = false }) { Text("Ακύρωση") } },
        )
    }
}

@Composable
private fun RemovedOrUnavailableCard(
    cardId: String,
    cleanupState: CardSecretCleanupUiState,
    onRetryCleanup: (String) -> Unit,
    onBack: () -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            when (val cleanup = cleanupState.takeIf { it.matches(cardId) }) {
                is CardSecretCleanupUiState.Cleaning -> {
                    Text("Η κάρτα αφαιρέθηκε από το MyFinHub.", style = MaterialTheme.typography.titleLarge)
                    Text("Καθαρίζονται τα κρυπτογραφημένα στοιχεία αυτής της συσκευής και τυχόν παλιό server-vault υπόλοιπο.")
                }
                is CardSecretCleanupUiState.Complete -> {
                    Text("Η κάρτα αφαιρέθηκε από το MyFinHub.", style = MaterialTheme.typography.titleLarge)
                    Text("Ο καθαρισμός ολοκληρώθηκε. Το ιστορικό και τυχόν οφειλή διατηρούνται.")
                }
                is CardSecretCleanupUiState.Failure -> {
                    Text("Η κάρτα αφαιρέθηκε, αλλά ο καθαρισμός δεν ολοκληρώθηκε.", style = MaterialTheme.typography.titleLarge)
                    val failedParts = buildList {
                        if (cleanup.serverCleanupPending) add("παλιό server-vault υπόλοιπο")
                        if (cleanup.localCleanupPending) add("τοπικά στοιχεία κάρτας")
                    }
                    Text(
                        failedParts.joinToString(prefix = "Εκκρεμεί: ", separator = " και ", postfix = "."),
                        color = MaterialTheme.colorScheme.error,
                    )
                    MyFinHubPrimaryAction(
                        label = "Δοκιμή καθαρισμού ξανά",
                        onClick = { onRetryCleanup(cardId) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = null,
                    )
                }
                else -> Text("Η κάρτα δεν είναι πλέον διαθέσιμη.")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Επιστροφή στις κάρτες") }
        }
    }
}

@Composable
private fun CardMetric(label: String, value: String, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CardActivitySection(
    card: MoneyCard,
    amountsVisible: Boolean,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Κινήσεις πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (card.activity.isEmpty()) {
                Text("Δεν υπάρχουν ακόμη συνδεδεμένες κινήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val visibleActivity = if (showAll) card.activity else card.activity.take(5)
                visibleActivity.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { onOpenActivity(item.id) }
                            .testTag("card_activity_${item.id}"),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubIconBadge(
                            icon = if (item.kind == MoneyCardActivityKind.PAYMENT) MyFinHubIcons.Income else MyFinHubIcons.Card,
                            tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                            contentDescription = null,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatSignedCardEuro(item.amount), amountsVisible),
                            tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                        )
                    }
                    if (index != visibleActivity.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (card.activity.size > 5) {
                    TextButton(onClick = onToggleAll) {
                        Text(if (showAll) "Λιγότερες κινήσεις" else "Δες όλες τις κινήσεις (${card.activity.size})")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardPickerSheet(
    selectedCardId: String?,
    cards: List<MoneyCard>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = MyFinHubSpacing.lg,
                end = MyFinHubSpacing.lg,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text("Επίλεξε κάρτα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            cards.forEach { option ->
                val optionProvider = financialProvider(option.bankId, option.nickname)
                Surface(
                    onClick = { onSelect(option.id) },
                    modifier = Modifier.fillMaxWidth().testTag("card_picker_${option.id}"),
                    shape = MaterialTheme.shapes.small,
                    color = if (option.id == selectedCardId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(MyFinHubSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (optionProvider != null) {
                            MyFinHubProviderMark(optionProvider, modifier = Modifier.size(40.dp), contentDescription = null)
                        } else {
                            MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Neutral, null)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "•••• ${option.last4.ifBlank { "—" }} · ${option.kind}",
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

private fun CardSecretCleanupUiState.matches(cardId: String): Boolean = when (this) {
    CardSecretCleanupUiState.Idle -> false
    is CardSecretCleanupUiState.Cleaning -> this.cardId == cardId
    is CardSecretCleanupUiState.Complete -> this.cardId == cardId
    is CardSecretCleanupUiState.Failure -> this.cardId == cardId
}

private fun formatCardEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedCardEuro(value: Double): String =
    (if (value > 0.005) "+" else "") + formatCardEuro(value)
