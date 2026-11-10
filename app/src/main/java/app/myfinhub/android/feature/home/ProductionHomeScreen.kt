package app.myfinhub.android.feature.home

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.financialProviderForAccount
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.core.ui.FinancialProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBrandMark
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
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
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import java.text.NumberFormat
import java.util.Locale

/** Production Home: one usable-money figure, one primary action and only immediate context. */
@Composable
fun ProductionHomeScreen(
    state: HomeUiState,
    @Suppress("UNUSED_PARAMETER") onAction: (HomeAction) -> Unit,
    onOpenAttention: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenQuickEntry: () -> Unit,
    onAccountQuickEntry: ((String, HomePrimaryRole) -> Unit)? = null,
    onAccountTransactions: ((String) -> Unit)? = null,
    onOpenAccount: (String) -> Unit = {},
    onOpenAllAccounts: () -> Unit = {},
    onOpenRecent: (String) -> Unit = {},
    amountsVisibleOverride: Boolean? = null,
    accountMetadata: Map<String, AccountMetadataRecord> = emptyMap(),
    accountMetadataLoaded: Boolean = false,
    canOpenRecent: (String) -> Boolean = { true },
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

    val primaryAccounts = homePrimaryAccounts(state.accounts)
    val primaryIds = primaryAccounts.mapTo(mutableSetOf(), HomeAccount::id)
    val secondaryAccounts = state.accounts.filterNot { it.id in primaryIds }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "MyFinHub",
                navigation = { MyFinHubBrandMark() },
                trailing = {
                    IconButton(
                        onClick = { AmountVisibilityPreference.write(context, !amountsVisible) },
                        modifier = Modifier.size(MyFinHubDesignMetrics.minimumTouchTarget),
                    ) {
                        Icon(
                            imageVector = if (amountsVisible) MyFinHubIcons.VisibilityOff else MyFinHubIcons.Visibility,
                            contentDescription = if (amountsVisible) "Απόκρυψη ποσών" else "Εμφάνιση ποσών",
                            modifier = Modifier.size(MyFinHubDesignMetrics.standardIconSize),
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(MyFinHubDesignMetrics.minimumTouchTarget)
                            .testTag("home_settings"),
                    ) {
                        Icon(
                            imageVector = MyFinHubIcons.Settings,
                            contentDescription = "Ρυθμίσεις",
                            modifier = Modifier.size(MyFinHubDesignMetrics.standardIconSize),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_list"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.lg),
        ) {
            item {
                HomeAvailableSummary(
                    amount = state.liquidTotal,
                    amountsVisible = amountsVisible,
                    onOpenQuickEntry = onOpenQuickEntry,
                )
            }
            if (state.attentionItems.isNotEmpty()) {
                item(key = "home-attention") {
                    HomeAttentionSection(items = state.attentionItems.take(2), onOpen = onOpenAttention)
                }
            }
            item {
                HomePrimaryAccountsSection(
                    accounts = primaryAccounts,
                    amountsVisible = amountsVisible,
                    accountMetadata = accountMetadata,
                    accountMetadataLoaded = accountMetadataLoaded,
                    onOpenAccount = onOpenAccount,
                    onQuickEntry = onOpenQuickEntry,
                    onAccountQuickEntry = onAccountQuickEntry,
                    onAccountTransactions = onAccountTransactions,
                )
            }
            if (state.recentItems.isNotEmpty()) {
                item(key = "home-recent") {
                    HomeRecentSection(
                        items = state.recentItems.take(4),
                        amountsVisible = amountsVisible,
                        onOpen = onOpenRecent,
                        canOpen = canOpenRecent,
                    )
                }
            }
            if (secondaryAccounts.isNotEmpty()) {
                item {
                    HomeSecondaryAccountsSection(
                        accounts = secondaryAccounts,
                        amountsVisible = amountsVisible,
                        onOpenAccount = onOpenAccount,
                        onOpenAllAccounts = onOpenAllAccounts,
                    )
                }
            } else {
                item {
                    TextButton(
                        onClick = onOpenAllAccounts,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Όλοι οι λογαριασμοί") }
                }
            }
        }
    }
}

internal fun homePrimaryAccounts(accounts: List<HomeAccount>): List<HomeAccount> {
    return HomePrimaryRole.entries.mapNotNull { role ->
        accounts.firstOrNull { it.primaryRole == role }
    }
}

@Composable
private fun HomeAvailableSummary(
    amount: Double,
    amountsVisible: Boolean,
    onOpenQuickEntry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
            Text(
                text = "Διαθέσιμα τώρα",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Μετρητά και μισθοδοσία",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MyFinHubAmountText(
            text = if (amountsVisible) formatHomeEuro(amount) else "•••• €",
            tone = if (amount >= 0.0) FinanceTone.Income else FinanceTone.Expense,
            style = MaterialTheme.typography.displaySmall,
        )
        if (amountsVisible && amount.isFinite() && amount < 0.0) {
            Text(
                "Το άθροισμα των καθημερινών υπολοίπων είναι αρνητικό.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MyFinHubPrimaryAction(
            label = "Νέα κίνηση",
            onClick = onOpenQuickEntry,
            modifier = Modifier.fillMaxWidth().testTag("home_global_quick_entry"),
        )
    }
}

@Composable
private fun HomeRecentSection(
    items: List<HomeRecentItem>,
    amountsVisible: Boolean,
    onOpen: (String) -> Unit,
    canOpen: (String) -> Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text(
            "Πρόσφατες κινήσεις",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        MyFinHubSectionCard(
            modifier = Modifier.fillMaxWidth().testTag("home_recent_summary"),
            contentPadding = PaddingValues(0.dp),
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    Surface(
                        onClick = { onOpen(item.id) },
                        enabled = canOpen(item.id),
                        modifier = Modifier.fillMaxWidth()
                            .testTag("home_recent_${item.id}"),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                                vertical = MyFinHubDesignMetrics.rowVerticalPadding,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    item.dateLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MyFinHubAmountText(
                                text = if (amountsVisible) formatHomeEuro(item.amount) else "•••• €",
                                tone = when (item.tone) {
                                    HomeRecentTone.INCOME -> FinanceTone.Income
                                    HomeRecentTone.EXPENSE -> FinanceTone.Expense
                                    HomeRecentTone.TRANSFER -> FinanceTone.Transfer
                                },
                            )
                        }
                    }
                    if (index != items.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAttentionSection(
    items: List<HomeAttentionItem>,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text(
            text = "Χρειάζεται προσοχή",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        MyFinHubSectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    HomeAttentionRow(item = item, onOpen = { onOpen(item.id) })
                    if (index != items.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAttentionRow(
    item: HomeAttentionItem,
    onOpen: () -> Unit,
) {
    val tone = if (item.tone == HomeAttentionTone.URGENT) FinanceTone.Attention else FinanceTone.Neutral
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                vertical = MyFinHubDesignMetrics.rowVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            MyFinHubIconBadge(
                icon = MyFinHubIcons.Attention,
                tone = tone,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    item.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.dueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.tone == HomeAttentionTone.URGENT) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun HomePrimaryAccountsSection(
    accounts: List<HomeAccount>,
    amountsVisible: Boolean,
    accountMetadata: Map<String, AccountMetadataRecord>,
    accountMetadataLoaded: Boolean,
    onOpenAccount: (String) -> Unit,
    onQuickEntry: () -> Unit,
    onAccountQuickEntry: ((String, HomePrimaryRole) -> Unit)?,
    onAccountTransactions: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
        Text("Κύριοι λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HomePrimaryRole.entries.forEach { role ->
            val account = accounts.firstOrNull { it.primaryRole == role }
            if (account == null) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(when (role) {
                            HomePrimaryRole.CASH -> "Μετρητά"
                            HomePrimaryRole.PAYROLL -> "Μισθοδοσίας"
                            HomePrimaryRole.SAVINGS -> "Αποταμιευτικός"
                        }, style = MaterialTheme.typography.titleMedium)
                        Text("Δεν έχει οριστεί αντίστοιχος λογαριασμός.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                HomePrimaryAccountCard(
                    account = account,
                    amountsVisible = amountsVisible,
                    ibanRecord = accountMetadata[account.id],
                    accountMetadataLoaded = accountMetadataLoaded,
                    onQuickEntry = {
                        if (onAccountQuickEntry != null) onAccountQuickEntry(account.id, role)
                        else onQuickEntry()
                    },
                    onTransactions = {
                        if (onAccountTransactions != null) onAccountTransactions(account.id)
                        else onOpenAccount(account.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomePrimaryAccountCard(
    account: HomeAccount,
    amountsVisible: Boolean,
    @Suppress("UNUSED_PARAMETER") ibanRecord: AccountMetadataRecord?,
    @Suppress("UNUSED_PARAMETER") accountMetadataLoaded: Boolean,
    @Suppress("UNUSED_PARAMETER") onQuickEntry: () -> Unit,
    onTransactions: () -> Unit,
) {
    val amountText = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €"
    val tone = if (account.balance < 0.0) FinanceTone.Expense else FinanceTone.Income
    val largeFont = LocalDensity.current.fontScale >= 1.3f

    // A mobile balance list is not a dashboard of charts, goals, IBAN and buttons.
    // Account tap opens the filtered ledger; Quick Entry stays one global Home action.
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Surface(
            onClick = onTransactions,
            modifier = Modifier.fillMaxWidth()
                .testTag("home_account_${account.id}")
                .semantics(mergeDescendants = true) {
                    contentDescription = if (amountsVisible) {
                        "${account.name}, $amountText. Προβολή κινήσεων"
                    } else "${account.name}, ποσό κρυφό. Προβολή κινήσεων"
                },
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                    vertical = MyFinHubDesignMetrics.rowVerticalPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeAccountIdentityMark(account)
                    if (account.primaryRole == HomePrimaryRole.CASH) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Πορτοφόλι", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        HomeAccountIdentity(account, Modifier.weight(1f))
                    }
                    if (!largeFont) {
                        MyFinHubAmountText(amountText, tone)
                    }
                }
                if (largeFont) {
                    MyFinHubAmountText(amountText, tone, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

@Composable
private fun HomeSecondaryAccountsSection(
    accounts: List<HomeAccount>,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
    onOpenAllAccounts: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text("Λοιποί λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        MyFinHubSectionCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                accounts.forEachIndexed { index, account ->
                    HomeAccountRow(account, amountsVisible) { onOpenAccount(account.id) }
                    if (index != accounts.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        TextButton(onClick = onOpenAllAccounts, modifier = Modifier.align(Alignment.End)) {
            Text("Όλοι οι λογαριασμοί")
        }
    }
}

@Composable
private fun HomeAccountRow(
    account: HomeAccount,
    amountsVisible: Boolean,
    onClick: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val amountText = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €"
    val tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (amountsVisible) {
                    "${account.name}, $amountText"
                } else {
                    "${account.name}, ποσό κρυφό"
                }
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (largeFont) {
            Column(
                modifier = Modifier.padding(
                    horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                    vertical = MyFinHubDesignMetrics.rowVerticalPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    HomeAccountIdentityMark(account)
                    HomeAccountIdentity(account, Modifier.weight(1f))
                }
                MyFinHubAmountText(amountText, tone, modifier = Modifier.align(Alignment.End))
            }
        } else {
            Row(
                modifier = Modifier.padding(
                    horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                    vertical = MyFinHubDesignMetrics.rowVerticalPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeAccountIdentityMark(account)
                HomeAccountIdentity(account, Modifier.weight(1f))
                MyFinHubAmountText(amountText, tone)
            }
        }
    }
}

@Composable
private fun HomeAccountIdentity(account: HomeAccount, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            account.institution ?: account.role,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeAccountIdentityMark(account: HomeAccount) {
    // Only exact owner-assigned provider IDs can select downloaded artwork.
    // Never guess logos from a bank-name substring or allow foreign image hosts.
    val record = LocalFinancialProviderCatalog.current.firstOrNull { it.id == account.providerId }
    val tone = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) "dark" else "light"
    val asset = record?.certifiedBrandAsset("logo", tone)
    if (record != null && asset != null) {
        MyFinHubCertifiedProviderMark(
            providerId = record.id, asset = asset,
            fallbackLabel = record.shortName,
            modifier = Modifier.size(36.dp),
            contentDescription = record.displayName,
        )
        return
    }
    val provider = financialProviderForAccount(account.id, account.institution ?: account.name, account.providerId)
    if (provider != null) {
        // Piraeus' actual dark-blue mark needs a light contrasting surface in dark mode.
        // Never tint or recolor the bank's approved logo asset.
        if (provider == FinancialProvider.PIRAEUS) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = androidx.compose.ui.graphics.Color.White,
            ) {
                MyFinHubProviderMark(
                    provider = provider,
                    modifier = Modifier.size(36.dp).padding(4.dp),
                    contentDescription = provider.institutionLabel,
                )
            }
        } else {
            MyFinHubProviderMark(
                provider = provider,
                modifier = Modifier.size(36.dp),
                contentDescription = provider.institutionLabel,
            )
        }
        return
    }
    MyFinHubIconBadge(
        icon = when {
            account.group == HomeAccountGroup.SAVINGS -> MyFinHubIcons.Savings
            account.role.contains("Μετρη", ignoreCase = true) || account.name.contains("Μετρη", ignoreCase = true) -> MyFinHubIcons.Money
            else -> MyFinHubIcons.Account
        },
        tone = if (account.group == HomeAccountGroup.SAVINGS) FinanceTone.Savings else FinanceTone.Neutral,
        contentDescription = null,
    )
}

private fun formatHomeEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
