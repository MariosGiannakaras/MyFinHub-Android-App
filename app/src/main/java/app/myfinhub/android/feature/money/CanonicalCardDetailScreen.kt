package app.myfinhub.android.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.core.ui.financialProviderForCard
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubCertifiedProviderMark
import app.myfinhub.android.designsystem.LocalFinancialProviderCatalog
import app.myfinhub.android.core.network.certifiedBrandAsset
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.NumberFormat

/** Display only: no changes to canonical rate precision or payment calculations. */
internal fun formatGreekCreditRate(raw: String): String {
    val rate = raw.toDoubleOrNull()
    if (rate == null || !rate.isFinite()) return "Μη έγκυρο επιτόκιο"
    return NumberFormat.getNumberInstance(Locale.forLanguageTag("el-GR")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(rate) + "%"
}

internal fun formatGreekCreditDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("el-GR")))
}.getOrDefault(iso)

@Composable
fun CanonicalCardDetailScreen(
    card: MoneyCard?,
    cardId: String = card?.id.orEmpty(),
    statements: List<CanonicalCreditStatementView> = emptyList(),
    cleanupState: CardSecretCleanupUiState = CardSecretCleanupUiState.Idle,
    onRetryCleanup: (String) -> Unit = {},
    onOpenSecureDetails: (String) -> Unit = {},
    onRemoveCard: (String) -> Unit = {},
    onSaveCardProfile: ((CardProfileEditRequest) -> Unit)? = null,
    onSaveCreditBillingSettings: ((CreditBillingSettingsRequest) -> Unit)? = null,
    onAddCreditRateVersion: ((CreditRateVersionRequest) -> Unit)? = null,
    profileMutationBlocked: Boolean = false,
    cardProviderOptions: List<Pair<String, String>> = emptyList(),
    onAddPurchase: () -> Unit = {},
    onPayCard: () -> Unit = {},
    onPayStatement: (String, Double) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit = {},
    amountsVisibleOverride: Boolean? = null,
    fullActivityIdCounts: Map<String, Int>? = null,
    fullStatementIdCounts: Map<String, Int>? = null,
    companionMode: Boolean = false,
) {
    val storedAmountsVisible = rememberAmountVisibilityPreference()
    val amountsVisible = amountsVisibleOverride ?: storedAmountsVisible
    var showAllActivity by remember(cardId) { mutableStateOf(false) }
    var removeDialogOpen by remember(cardId) { mutableStateOf(false) }
    var editProfileOpen by remember(cardId) { mutableStateOf(false) }
    var billingEditorOpen by remember(cardId) { mutableStateOf(false) }
    var rateEditorOpen by remember(cardId) { mutableStateOf(false) }
    var creditToolsExpanded by rememberSaveable(cardId) { mutableStateOf(false) }
    val provider = card?.let { financialProviderForCard(it.bankId, it.nickname) }
    val ownerCatalog = LocalFinancialProviderCatalog.current
    val surfaceTone = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) "dark" else "light"
    val certifiedRecord = ownerCatalog.firstOrNull { it.id == card?.bankId }
    val certifiedLogo = certifiedRecord?.certifiedBrandAsset("logo", surfaceTone)
    val isCredit = canUseCreditCardActions(card)
    // Share the same fail-closed canonical financial snapshot as inline Wallet.
    // Imported NaN/Infinity can never become display text or a repayment amount.
    val creditFinancial = card?.takeIf { isCredit }?.let(::creditWorkspaceFinancialSnapshot)

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
                        if (certifiedRecord != null && certifiedLogo != null) {
                            MyFinHubCertifiedProviderMark(
                                providerId=certifiedRecord.id,asset=certifiedLogo,
                                fallbackLabel=certifiedRecord.shortName,
                                modifier=Modifier.size(40.dp),
                                contentDescription=certifiedRecord.displayName,
                            )
                        } else if (provider != null) {
                            MyFinHubProviderMark(provider, modifier = Modifier.size(40.dp), contentDescription = provider.institutionLabel)
                        } else {
                            MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Transfer, null)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(canonicalCardMaskedLastFour(card.last4) ?: "••••", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        listOf(card.kind, card.network).filter { it.isNotBlank() }.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isCredit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MyFinHubAmountText(
                            creditWorkspaceOptionalMoney(creditFinancial?.outstanding, amountsVisible),
                            FinanceTone.Expense,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (card.limit != null && creditFinancial?.limit == null && amountsVisible) {
                            Text(
                                "Μη έγκυρο πιστωτικό όριο",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("card_detail_invalid_limit"),
                            )
                        }
                        creditFinancial?.limit?.let { limit ->
                            if (LocalDensity.current.fontScale >= 1.3f) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                                ) {
                                    CardMetric("Πιστωτικό όριο", creditWorkspaceMoney(limit, amountsVisible))
                                    CardMetric(
                                        "Διαθέσιμη πίστωση",
                                        creditWorkspaceOptionalMoney(creditFinancial?.available, amountsVisible),
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    CardMetric("Πιστωτικό όριο", creditWorkspaceMoney(limit, amountsVisible))
                                    CardMetric(
                                        "Διαθέσιμη πίστωση",
                                        creditWorkspaceOptionalMoney(creditFinancial?.available, amountsVisible),
                                        Alignment.End,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isCredit) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                ) {
                    Text("Ενέργειες πιστωτικής", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (hasPayableCreditDebt(creditFinancial?.outstanding)) {
                            MyFinHubPrimaryAction(
                                label = "Πληρωμή κάρτας",
                                onClick = onPayCard,
                                modifier = Modifier.fillMaxWidth().testTag("card_detail_payment_action"),
                                icon = null,
                            )
                            TextButton(
                                onClick = onAddPurchase,
                                modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                            ) {
                                Text("Καταχώριση αγοράς")
                            }
                        } else {
                            MyFinHubPrimaryAction(
                                label = "Καταχώριση αγοράς",
                                onClick = onAddPurchase,
                                modifier = Modifier.fillMaxWidth().testTag("card_detail_purchase_primary"),
                                icon = null,
                            )
                            Text(
                                if (creditFinancial?.outstanding == null)
                                    "Η οφειλή δεν είναι διαθέσιμη."
                                else
                                    "Δεν υπάρχει οφειλή προς εξόφληση.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("card_detail_payment_unavailable_reason"),
                            )
                        }
                }

                if (!companionMode) {
                TextButton(
                    onClick = { creditToolsExpanded = !creditToolsExpanded },
                    modifier = Modifier.fillMaxWidth().testTag("card_credit_tools_toggle"),
                ) {
                    Text(if (creditToolsExpanded) "Απόκρυψη όρων και προσομοίωσης"
                         else "Όροι και προσομοίωση αποπληρωμής")
                }
                if (creditToolsExpanded) {
                if (onSaveCreditBillingSettings != null || onAddCreditRateVersion != null) {
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("card_credit_terms"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            Text("Όροι πιστωτικής", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Κλείσιμο: " + (card.statementClosingDay?.toString() ?: "Άγνωστο") +
                                    " · Πληρωμή: " + (card.statementDueDay?.toString() ?: "Άγνωστο"),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val currentRate = selectedCreditRate(card.annualRates, LocalDate.now())
                            Text(
                                when (currentRate) {
                                    is CreditRateSelection.Selected ->
                                        "Ονομαστικό ετήσιο επιτόκιο: " +
                                            formatGreekCreditRate(currentRate.record.annualPercent.toString()) + " · από " +
                                            formatGreekCreditDate(currentRate.record.effectiveFrom)
                                    CreditRateSelection.Missing -> "Ονομαστικό ετήσιο επιτόκιο: άγνωστο"
                                    CreditRateSelection.Invalid -> "Ονομαστικό ετήσιο επιτόκιο: μη έγκυρη τιμή"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (card.annualRates.isNotEmpty()) {
                                Text(
                                    card.annualRates.joinToString(
                                        prefix = "Ιστορικό: ", separator = " · ",
                                        transform = ::displayCreditRateHistory,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (onSaveCreditBillingSettings != null) {
                                OutlinedButton(
                                    onClick = { billingEditorOpen = true },
                                    enabled = !profileMutationBlocked,
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("card_credit_billing_edit"),
                                ) { Text("Ρύθμιση κύκλου και λήξης") }
                            }
                            if (onAddCreditRateVersion != null) {
                                OutlinedButton(
                                    onClick = { rateEditorOpen = true },
                                    enabled = !profileMutationBlocked,
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("card_credit_add_rate"),
                                ) { Text("Καταχώριση νέου επιτοκίου") }
                            }
                            Text(
                                "Το ΣΕΠΕ/APR διαφέρει από το ονομαστικό επιτόκιο. " +
                                    "Καμία αλλαγή δεν καταχωρίζει αυτόματα τόκους ή ξαναγράφει statements.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                CanonicalCreditPayoffSimulator(
                    card = card,
                    amountsVisible = amountsVisible,
                )
                }
                CanonicalCardStatementHistorySection(
                    card = card,
                    statements = statements,
                    amountsVisible = amountsVisible,
                    onOpenActivity = onOpenActivity,
                    onPayStatement = onPayStatement,
                    fullActivityIdCounts = fullActivityIdCounts,
                    fullStatementIdCounts = fullStatementIdCounts,
                )
                }
                CardActivitySection(
                    card = card,
                    amountsVisible = amountsVisible,
                    showAll = showAllActivity,
                    onToggleAll = { showAllActivity = !showAllActivity },
                    onOpenActivity = onOpenActivity,
                    fullActivityIdCounts = fullActivityIdCounts,
                )
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(if (companionMode) "Στοιχεία κάρτας" else "Διαχείριση κάρτας", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { onOpenSecureDetails(card.id) },
                        modifier = Modifier.fillMaxWidth().testTag("card_secure_details"),
                    ) { Text("Στοιχεία κάρτας") }
                    Text(
                        "Ο αριθμός, η λήξη και το CVV εμφανίζονται πλήρως στην οθόνη στοιχείων κάρτας.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!companionMode && onSaveCardProfile != null) {
                        OutlinedButton(
                            onClick = { editProfileOpen = true },
                            enabled = !profileMutationBlocked,
                            modifier = Modifier.fillMaxWidth().testTag("card_edit_profile"),
                        ) { Text("Επεξεργασία προφίλ κάρτας") }
                    }
                    if (!companionMode) MyFinHubDestructiveTextAction(
                        label = "Αφαίρεση από το MyFinHub",
                        onClick = { removeDialogOpen = true },
                    )
                }
            }
        }
    }

    if (!companionMode && billingEditorOpen && card != null && onSaveCreditBillingSettings != null) {
        CanonicalCreditBillingSettingsEditor(
            card = card,
            mutationBlocked = profileMutationBlocked,
            onDismiss = { billingEditorOpen = false },
            onSave = { value ->
                onSaveCreditBillingSettings(value)
                billingEditorOpen = false
            },
        )
    }
    if (!companionMode && rateEditorOpen && card != null && onAddCreditRateVersion != null) {
        CanonicalCreditRateVersionEditor(
            card = card,
            mutationBlocked = profileMutationBlocked,
            todayIso = LocalDate.now().toString(),
            onDismiss = { rateEditorOpen = false },
            onSave = { value ->
                onAddCreditRateVersion(value)
                rateEditorOpen = false
            },
        )
    }

    if (!companionMode && editProfileOpen && card != null && onSaveCardProfile != null) {
        CanonicalCardProfileEditor(
            card = card,
            providerOptions = cardProviderOptions,
            mutationBlocked = profileMutationBlocked,
            onDismiss = { editProfileOpen = false },
            onSave = { request ->
                onSaveCardProfile(request)
                editProfileOpen = false
            },
        )
    }

    if (!companionMode && removeDialogOpen && card != null) {
        AlertDialog(
            onDismissRequest = { removeDialogOpen = false },
            title = { Text("Αρχειοθέτηση στο MyFinHub;") },
            text = {
                Text(
                    "Η κάρτα θα μεταφερθεί στις αρχειοθετημένες και μπορεί να επανέλθει. Το ιστορικό και τυχόν οφειλή διατηρούνται. " +
                        "Δεν ακυρώνεται στην τράπεζα. Η αρχειοθέτηση δεν διαγράφει τα κρυπτογραφημένα στοιχεία της κάρτας· για να εμφανιστούν ξανά απαιτείται εξουσιοδοτημένη πρόσβαση.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeDialogOpen = false
                        onRemoveCard(card.id)
                    },
                    modifier = Modifier.testTag("confirm_remove_card"),
                ) { Text("Αρχειοθέτηση") }
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
    fullActivityIdCounts: Map<String, Int>?,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Κινήσεις πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (card.activity.isEmpty()) {
                Text("Δεν υπάρχουν ακόμη συνδεδεμένες κινήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val visibleActivity = if (showAll) card.activity else card.activity.take(5)
                val activityIdCounts = canonicalCardActivityIdCounts(card.activity)
                visibleActivity.forEachIndexed { index, item ->
                    val openId = canonicalCardActivityOpenId(item.id, activityIdCounts, fullActivityIdCounts)
                    val identityWarning = canonicalCardActivityIdentityWarning(item.id, activityIdCounts, fullActivityIdCounts)
                    val isPayment = item.kind == MoneyCardActivityKind.PAYMENT
                    val rowTone = if (isPayment) FinanceTone.Income else FinanceTone.Expense
                    val rowIdentity: @Composable (Modifier) -> Unit = { modifier ->
                        Row(
                            modifier = modifier,
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MyFinHubIconBadge(
                                icon = if (isPayment) MyFinHubIcons.Income else MyFinHubIcons.Card,
                                tone = rowTone,
                                contentDescription = null,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    item.dateLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                identityWarning?.let { warning ->
                                    Text(
                                        warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    val rowAmount: @Composable () -> Unit = {
                        MyFinHubAmountText(
                            text = creditWorkspaceSignedMoney(item.amount, amountsVisible),
                            tone = rowTone,
                        )
                    }
                    val rowModifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                        .clickable(enabled = openId != null) { openId?.let(onOpenActivity) }
                        .testTag(
                            if (openId != null) "card_activity_${item.id}"
                            else "card_activity_unavailable_$index",
                        )
                    if (LocalDensity.current.fontScale >= 1.3f) {
                        Column(
                            modifier = rowModifier.padding(vertical = MyFinHubSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                        ) {
                            rowIdentity(Modifier.fillMaxWidth())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) { rowAmount() }
                        }
                    } else {
                        Row(
                            modifier = rowModifier,
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            rowIdentity(Modifier.weight(1f))
                            rowAmount()
                        }
                    }
                    if (index != visibleActivity.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (card.activity.size > 5) {
                    TextButton(
                        onClick = onToggleAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                    ) {
                        Text(if (showAll) "Λιγότερες κινήσεις" else "Δες όλες τις κινήσεις (${card.activity.size})")
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

