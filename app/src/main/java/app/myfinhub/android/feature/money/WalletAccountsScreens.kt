package app.myfinhub.android.feature.money

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubFilterChip
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class WalletAccountGroup(val label: String) {
    DAILY("Καθημερινά"),
    SAVINGS("Αποταμίευση"),
    OTHER("Άλλοι λογαριασμοί"),
}

private enum class WalletSection(val label: String) {
    ACCOUNTS("Λογαριασμοί"),
    CARDS("Κάρτες"),
    DEBTS("Οφειλές"),
}

internal fun walletAccountGroup(account: MoneyAccount): WalletAccountGroup = when {
    account.canonicalKind == "savings" || account.kind.contains("Αποταμί", ignoreCase = true) -> WalletAccountGroup.SAVINGS
    account.excludeFromAvailable -> WalletAccountGroup.OTHER
    account.canonicalKind in setOf("bank", "cash") ||
        account.kind.contains("Τράπεζ", ignoreCase = true) ||
        account.kind.contains("Μετρη", ignoreCase = true) -> WalletAccountGroup.DAILY
    else -> WalletAccountGroup.OTHER
}

internal fun walletAccountsInGroup(state: MoneyUiState, group: WalletAccountGroup): List<MoneyAccount> =
    state.accounts.filter { walletAccountGroup(it) == group }

internal fun walletAvailableTotal(state: MoneyUiState): Double =
    walletAccountsInGroup(state, WalletAccountGroup.DAILY)
        .filterNot(MoneyAccount::excludeFromAvailable)
        .sumOf(MoneyAccount::balance)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonicalWalletScreen(
    state: MoneyUiState,
    accountsRequest: Int = 0,
    initiallyShowCards: Boolean = false,
    initiallyShowDebts: Boolean = false,
    onOpenAccount: (String) -> Unit,
    onOpenNetPosition: () -> Unit,
    onOpenCard: (String) -> Unit,
    onAddCard: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenLending: () -> Unit,
    amountsVisibleOverride: Boolean? = null,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var storedAmountsVisible by remember(context) { mutableStateOf(AmountVisibilityPreference.read(context)) }
    val amountsVisible = amountsVisibleOverride ?: storedAmountsVisible
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AmountVisibilityPreference.KEY) storedAmountsVisible = AmountVisibilityPreference.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var selectedName by rememberSaveable(accountsRequest, initiallyShowCards, initiallyShowDebts) {
        mutableStateOf(
            when {
                initiallyShowDebts -> WalletSection.DEBTS.name
                initiallyShowCards -> WalletSection.CARDS.name
                else -> WalletSection.ACCOUNTS.name
            },
        )
    }
    val selected = WalletSection.entries.firstOrNull { it.name == selectedName } ?: WalletSection.ACCOUNTS
    var sectionSheetOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Πορτοφόλι",
                subtitle = selected.label,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("wallet_list"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            item("wallet-section-selector") {
                WalletSectionSelector(
                    selected = selected,
                    onSelected = { section -> selectedName = section.name },
                    onOpenSheet = { sectionSheetOpen = true },
                )
            }
            when (selected) {
                WalletSection.ACCOUNTS -> walletAccountsContent(
                    state = state,
                    amountsVisible = amountsVisible,
                    onOpenAccount = onOpenAccount,
                    onOpenNetPosition = onOpenNetPosition,
                )
                WalletSection.CARDS -> walletCardsContent(
                    state = state,
                    amountsVisible = amountsVisible,
                    onOpenCard = onOpenCard,
                    onAddCard = onAddCard,
                )
                WalletSection.DEBTS -> walletDebtsContent(
                    state = state,
                    amountsVisible = amountsVisible,
                    onOpenCards = { selectedName = WalletSection.CARDS.name },
                    onOpenLoans = onOpenLoans,
                    onOpenLending = onOpenLending,
                )
            }
        }
    }

    if (sectionSheetOpen) {
        ModalBottomSheet(onDismissRequest = { sectionSheetOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = MyFinHubSpacing.lg,
                    end = MyFinHubSpacing.lg,
                    bottom = MyFinHubSpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Text("Ενότητα πορτοφολιού", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                WalletSection.entries.forEach { section ->
                    Surface(
                        onClick = {
                            selectedName = section.name
                            sectionSheetOpen = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = if (section == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(MyFinHubSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(section.icon(), contentDescription = null)
                            Text(section.label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletSectionSelector(
    selected: WalletSection,
    onSelected: (WalletSection) -> Unit,
    onOpenSheet: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    if (largeFont) {
        OutlinedButton(onClick = onOpenSheet, modifier = Modifier.fillMaxWidth()) {
            Icon(selected.icon(), contentDescription = null)
            Text("  Ενότητα: ${selected.label}")
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            WalletSection.entries.forEach { section ->
                MyFinHubFilterChip(
                    selected = section == selected,
                    onClick = { onSelected(section) },
                    label = section.label,
                    icon = section.icon(),
                    tone = FinanceTone.Neutral,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun WalletSection.icon(): ImageVector = when (this) {
    WalletSection.ACCOUNTS -> MyFinHubIcons.Account
    WalletSection.CARDS -> MyFinHubIcons.Card
    WalletSection.DEBTS -> MyFinHubIcons.Plan
}

private fun LazyListScope.walletAccountsContent(
    state: MoneyUiState,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
    onOpenNetPosition: () -> Unit,
) {
    item("available-total") {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
            Text("Διαθέσιμα καθημερινά", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MyFinHubAmountText(
                text = walletAmountText(walletAvailableTotal(state), amountsVisible),
                tone = if (walletAvailableTotal(state) >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Τραπεζικοί λογαριασμοί και μετρητά · η αποταμίευση εμφανίζεται ξεχωριστά",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.accounts.isEmpty()) {
        item("accounts-empty") {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Δεν υπάρχουν διαθέσιμοι λογαριασμοί στην τρέχουσα canonical εικόνα.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        WalletAccountGroup.entries.forEach { group ->
            val accounts = walletAccountsInGroup(state, group)
            if (accounts.isNotEmpty()) {
                item("account-heading-${group.name}") {
                    Text(group.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(accounts, key = MoneyAccount::id) { account ->
                    WalletAccountRow(
                        account = account,
                        amountsVisible = amountsVisible,
                        onClick = { onOpenAccount(account.id) },
                    )
                }
            }
        }
    }

    item("net-position-link") {
        Surface(
            onClick = onOpenNetPosition,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(MyFinHubSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(MyFinHubIcons.Money, FinanceTone.Neutral, null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Καθαρή θέση", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Λογαριασμοί + απαιτήσεις − δάνεια − πιστωτικό χρέος",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MyFinHubAmountText(
                    walletAmountText(canonicalNetPosition(state), amountsVisible),
                    if (canonicalNetPosition(state) >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                )
            }
        }
    }
}

@Composable
private fun WalletAccountRow(
    account: MoneyAccount,
    amountsVisible: Boolean,
    onClick: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val amountText = walletAmountText(account.balance, amountsVisible)
    val tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (amountsVisible) "${account.name}, $amountText" else "${account.name}, ποσό κρυφό"
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        if (largeFont) {
            Column(
                modifier = Modifier.padding(vertical = MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    WalletAccountMark(account)
                    WalletAccountIdentity(account, Modifier.weight(1f), largeFont = true)
                }
                MyFinHubAmountText(amountText, tone, modifier = Modifier.align(Alignment.End))
            }
        } else {
            Row(
                modifier = Modifier.padding(vertical = MyFinHubSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WalletAccountMark(account)
                WalletAccountIdentity(account, Modifier.weight(1f), largeFont = false)
                MyFinHubAmountText(amountText, tone)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun WalletAccountIdentity(account: MoneyAccount, modifier: Modifier, largeFont: Boolean) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(
            account.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (largeFont) 2 else 1,
            overflow = if (largeFont) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        Text(
            account.institution ?: account.kind,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (largeFont) 2 else 1,
            overflow = if (largeFont) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WalletAccountMark(account: MoneyAccount) {
    val provider = financialProvider(account.id, account.institution ?: account.name)
    if (provider != null) {
        MyFinHubProviderMark(provider, modifier = Modifier.size(40.dp), contentDescription = provider.institutionLabel)
    } else {
        MyFinHubIconBadge(
            icon = when (walletAccountGroup(account)) {
                WalletAccountGroup.SAVINGS -> MyFinHubIcons.Savings
                WalletAccountGroup.DAILY -> if (account.kind.contains("Μετρη", ignoreCase = true)) MyFinHubIcons.Money else MyFinHubIcons.Account
                WalletAccountGroup.OTHER -> MyFinHubIcons.Account
            },
            tone = if (walletAccountGroup(account) == WalletAccountGroup.SAVINGS) FinanceTone.Savings else FinanceTone.Neutral,
            contentDescription = null,
        )
    }
}

private fun LazyListScope.walletCardsContent(
    state: MoneyUiState,
    amountsVisible: Boolean,
    onOpenCard: (String) -> Unit,
    onAddCard: () -> Unit,
) {
    item("cards-action") {
        MyFinHubPrimaryAction(
            label = "Νέα κάρτα",
            onClick = onAddCard,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.cards.isEmpty()) {
        item("cards-empty") {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text("Δεν υπάρχουν ενεργές κάρτες στο MyFinHub.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        items(state.cards, key = MoneyCard::id) { card ->
            WalletCardRow(card = card, amountsVisible = amountsVisible, onClick = { onOpenCard(card.id) })
        }
    }
}

@Composable
private fun WalletCardRow(card: MoneyCard, amountsVisible: Boolean, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val isCredit = card.canonicalKind == "credit" || card.kind.contains("Πιστω", ignoreCase = true)
    val provider = financialProvider(card.bankId, card.nickname)
    val identity = listOf(card.kind, card.network).filter(String::isNotBlank).distinct().joinToString(" · ")
    val debtText = if (isCredit) walletAmountText(card.currentBalance, amountsVisible) else null
    val spokenIdentity = buildString {
        append(card.nickname)
        if (card.last4.isNotBlank()) append(", τελευταία ψηφία ${card.last4}")
        if (identity.isNotBlank()) append(", $identity")
        if (debtText != null) append(", οφειλή $debtText")
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallet_card_${card.id}")
            .semantics(mergeDescendants = true) { contentDescription = spokenIdentity },
        color = MaterialTheme.colorScheme.background,
    ) {
        if (largeFont) {
            Column(modifier = Modifier.padding(vertical = MyFinHubSpacing.xs), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm), verticalAlignment = Alignment.Top) {
                    if (provider != null) {
                        MyFinHubProviderMark(provider, modifier = Modifier.size(40.dp), contentDescription = provider.institutionLabel)
                    } else {
                        MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Neutral, null)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(card.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("•••• ${card.last4.ifBlank { "—" }} · $identity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                debtText?.let {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.End)) {
                        Text("Οφειλή", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MyFinHubAmountText(it, FinanceTone.Neutral)
                    }
                }
            }
        } else {
            Row(modifier = Modifier.padding(vertical = MyFinHubSpacing.xs), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (provider != null) {
                    MyFinHubProviderMark(provider, modifier = Modifier.size(40.dp), contentDescription = provider.institutionLabel)
                } else {
                    MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Neutral, null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("•••• ${card.last4.ifBlank { "—" }} · $identity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                debtText?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Οφειλή", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MyFinHubAmountText(it, FinanceTone.Neutral)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun LazyListScope.walletDebtsContent(
    state: MoneyUiState,
    amountsVisible: Boolean,
    onOpenCards: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenLending: () -> Unit,
) {
    item("debt-credit") {
        WalletAggregateRow(
            title = "Πιστωτικό χρέος",
            subtitle = if (state.aggregateCreditOutstanding != null) "Συνολικό υπόλοιπο · περιλαμβάνει και μη ενεργές κάρτες" else "Μερική εικόνα από διαθέσιμες πιστωτικές",
            amount = canonicalCreditOutstanding(state),
            amountsVisible = amountsVisible,
            onClick = onOpenCards,
        )
    }
    item("debt-loans") {
        WalletAggregateRow(
            title = "Δάνεια",
            subtitle = if (state.loans.isEmpty()) "Συνολικό υπόλοιπο · χωρίς αναλυτικές εγγραφές" else "${state.loans.size} διαθέσιμες εγγραφές",
            amount = state.loanOutstanding,
            amountsVisible = amountsVisible,
            onClick = onOpenLoans,
        )
    }
    item("debt-receivables") {
        WalletAggregateRow(
            title = "Απαιτήσεις",
            subtitle = if (state.lendingItems.isEmpty()) "Συνολικό ποσό · χωρίς αναλυτικές εγγραφές" else "${state.lendingItems.size} διαθέσιμες εγγραφές",
            amount = state.lendingReceivable,
            amountsVisible = amountsVisible,
            onClick = onOpenLending,
            receivable = true,
        )
    }
    item("debt-note") {
        Text(
            "Όπου δεν υπάρχουν αναλυτικές εγγραφές, εμφανίζεται το διαθέσιμο συνολικό ποσό χωρίς να υπονοείται μηδενική οφειλή ή απαίτηση.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WalletAggregateRow(
    title: String,
    subtitle: String,
    amount: Double,
    amountsVisible: Boolean,
    onClick: () -> Unit,
    receivable: Boolean = false,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        if (largeFont) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    MyFinHubIconBadge(if (receivable) MyFinHubIcons.Income else MyFinHubIcons.Plan, FinanceTone.Neutral, null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MyFinHubAmountText(
                    walletAmountText(amount, amountsVisible),
                    FinanceTone.Neutral,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(vertical = MyFinHubSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(if (receivable) MyFinHubIcons.Income else MyFinHubIcons.Plan, FinanceTone.Neutral, null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MyFinHubAmountText(walletAmountText(amount, amountsVisible), FinanceTone.Neutral)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun CanonicalNetPositionScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
    amountsVisibleOverride: Boolean? = null,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var storedAmountsVisible by remember(context) { mutableStateOf(AmountVisibilityPreference.read(context)) }
    val amountsVisible = amountsVisibleOverride ?: storedAmountsVisible
    var calculationExpanded by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AmountVisibilityPreference.KEY) storedAmountsVisible = AmountVisibilityPreference.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val accountAssets = state.accounts.sumOf(MoneyAccount::balance)
    val creditDebt = canonicalCreditOutstanding(state)
    val net = canonicalNetPosition(state)
    val partialCredit = state.aggregateCreditOutstanding == null

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Καθαρή θέση",
                subtitle = state.asOfDate?.let { "Εικόνα έως ${formatWalletDate(it)}" }
                    ?: "Τελευταία διαθέσιμη canonical εικόνα",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Text("Σύνολο", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    MyFinHubAmountText(
                        walletAmountText(net, amountsVisible),
                        if (net >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "Λογαριασμοί + απαιτήσεις − δάνεια − πιστωτικό χρέος",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Text("Περιουσιακά στοιχεία", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { NetPositionRow("Λογαριασμοί", accountAssets, amountsVisible) }
            item { NetPositionRow("Απαιτήσεις", state.lendingReceivable, amountsVisible) }
            item { Text("Υποχρεώσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { NetPositionRow("Δάνεια", state.loanOutstanding, amountsVisible, liability = true) }
            item {
                NetPositionRow(
                    title = "Πιστωτικό χρέος",
                    amount = creditDebt,
                    amountsVisible = amountsVisible,
                    liability = true,
                    note = if (partialCredit) "Μερική εικόνα από ενεργές κάρτες" else "Περιλαμβάνει canonical χρέος ανεξάρτητα από ορατές ενεργές κάρτες",
                )
            }
            item {
                TextButton(onClick = { calculationExpanded = !calculationExpanded }) {
                    Text(if (calculationExpanded) "Απόκρυψη υπολογισμού" else "Προβολή υπολογισμού")
                }
            }
            if (calculationExpanded) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text("Υπολογισμός", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Λογαριασμοί: ${walletAmountText(accountAssets, amountsVisible)}")
                            Text("+ Απαιτήσεις: ${walletAmountText(state.lendingReceivable, amountsVisible)}")
                            Text("− Δάνεια: ${walletAmountText(state.loanOutstanding, amountsVisible)}")
                            Text("− Πιστωτικό χρέος: ${walletAmountText(creditDebt, amountsVisible)}")
                            HorizontalDivider()
                            Text("= ${walletAmountText(net, amountsVisible)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetPositionRow(
    title: String,
    amount: Double,
    amountsVisible: Boolean,
    liability: Boolean = false,
    note: String? = null,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val amountText = walletAmountText(amount, amountsVisible)
    if (largeFont) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.xs), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            MyFinHubAmountText(amountText, FinanceTone.Neutral, modifier = Modifier.align(Alignment.End))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            MyFinHubAmountText(amountText, FinanceTone.Neutral)
        }
    }
    if (liability) {
        Text(
            "Υποχρέωση",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun walletAmountText(value: Double, visible: Boolean): String =
    if (visible) NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value) else "•••• €"

private fun formatWalletDate(raw: String): String = runCatching {
    LocalDate.parse(raw.take(10))
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("el-GR")))
}.getOrDefault(raw)
