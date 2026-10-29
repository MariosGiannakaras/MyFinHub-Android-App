package app.myfinhub.android.feature.money

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.ui.AccountIbanRow
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.core.network.isValidIban
import app.myfinhub.android.core.network.normalizeIban
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubFinanceRow
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.myFinHubCategoryIcon
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.activity.matchesWebLedgerSearch
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Read-only within an already scoped account history. Reuse the canonical Web
 * TransactionsPage search index: never filter by amounts, account names or
 * derived presentation-only labels. Preserve original source order and IDs.
 */
internal fun visibleAccountHistorySourceRows(
    items: List<ActivityItem>,
    query: String,
    kind: ActivityKind?,
): List<IndexedValue<ActivityItem>> = items.withIndex().filter { (_, item) ->
    (kind == null || item.kind == kind) && item.matchesWebLedgerSearch(query)
}

internal fun visibleAccountHistoryItems(
    items: List<ActivityItem>,
    query: String,
    kind: ActivityKind?,
): List<ActivityItem> = visibleAccountHistorySourceRows(items, query, kind).map { it.value }

@Composable
fun CanonicalAccountDetailScreen(
    account: MoneyAccount?,
    activityItems: List<ActivityItem>,
    // The production caller supplies counts from the ENTIRE canonical ledger:
    // an event key may be duplicated outside this account's visible history.
    fullActivityIdCounts: Map<String, Int> =
        activityItems.groupingBy(ActivityItem::id).eachCount(),
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onNewTransaction: () -> Unit = {},
    categoryGlyphOverrides: Map<String, String> = emptyMap(),
    referenceDate: LocalDate = LocalDate.now(),
    amountsVisibleOverride: Boolean? = null,
    ibanRecord: AccountMetadataRecord? = null,
    accountMetadataLoaded: Boolean = false,
    onSaveAccountIban: ((String, String?) -> Unit)? = null,
    accountMetadataSaving: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var storedAmountsVisible by remember(context) { mutableStateOf(AmountVisibilityPreference.read(context)) }
    val amountsVisible = amountsVisibleOverride ?: storedAmountsVisible
    var deletingIban by remember(account?.id) { mutableStateOf(false) }
    var editingIban by remember(account?.id) { mutableStateOf(false) }
    var newIban by remember(account?.id) { mutableStateOf("") }
    // Save a small UI-only search state across Activity recreation, but reset both
    // selectors when the canonical account ID changes. Do not cache any finance data.
    var historyQuery by rememberSaveable(account?.id) { mutableStateOf("") }
    var historyKindName by rememberSaveable(account?.id) { mutableStateOf<String?>(null) }
    val historyKind = ActivityKind.entries.firstOrNull { it.name == historyKindName }
    val historyControlsVisible = activityItems.size >= 6
    val sourceHistoryRows = if (historyControlsVisible) {
        visibleAccountHistorySourceRows(activityItems, historyQuery, historyKind)
    } else activityItems.withIndex().toList()
    val visibleHistory = sourceHistoryRows.map { it.value }
    val normalizedInput = normalizeIban(newIban)
    val inputValid = normalizedInput != null && isValidIban(normalizedInput)
    if (deletingIban && account != null && ibanRecord?.iban != null &&
        accountMetadataLoaded && onSaveAccountIban != null && !accountMetadataSaving) {
        AlertDialog(
            onDismissRequest = { deletingIban = false },
            title = { Text("Επιβεβαίωση αφαίρεσης IBAN") },
            text = { Text("Θα αφαιρεθεί το IBAN αυτού του λογαριασμού από τα ασφαλή metadata. Δεν θα διαγραφούν κινήσεις ή υπόλοιπα.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingIban = false
                        onSaveAccountIban(account.id, null)
                    },
                ) { Text("Αφαίρεση IBAN") }
            },
            dismissButton = {
                TextButton(onClick = { deletingIban = false }) { Text("Ακύρωση") }
            },
        )
    }
    if (editingIban && account != null && accountMetadataLoaded &&
        onSaveAccountIban != null && !accountMetadataSaving) {
        AlertDialog(
            onDismissRequest = { editingIban = false; newIban = "" },
            title = { Text("Ενημέρωση IBAN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text("Ο IBAN αποθηκεύεται ξεχωριστά από τα οικονομικά δεδομένα και ελέγχεται από τον server.")
                    OutlinedTextField(
                        value = newIban,
                        onValueChange = { if (it.length <= 64) newIban = it },
                        label = { Text("Νέο IBAN") },
                        singleLine = true,
                        isError = newIban.isNotBlank() && !inputValid,
                    )
                    if (newIban.isNotBlank() && !inputValid)
                        Text("Μη έγκυρο IBAN", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = inputValid,
                    onClick = {
                        onSaveAccountIban(account.id, normalizedInput)
                        editingIban = false
                        newIban = ""
                    },
                ) { Text("Αποθήκευση") }
            },
            dismissButton = {
                TextButton(onClick = { editingIban = false; newIban = "" }) {
                    Text("Ακύρωση")
                }
            },
        )
    }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AmountVisibilityPreference.KEY) storedAmountsVisible = AmountVisibilityPreference.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = account?.name ?: "Λογαριασμός",
                subtitle = account?.institution ?: account?.kind ?: "Λεπτομέρειες λογαριασμού",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("account_detail_history_list"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            if (account == null) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Ο λογαριασμός δεν είναι πλέον διαθέσιμος.")
                    }
                }
                return@LazyColumn
            }

            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        Text("Τρέχον υπόλοιπο", style = MaterialTheme.typography.labelLarge)
                        if (account.canonicalKind == "bank" || account.canonicalKind == "savings") {
                            AccountIbanRow(ibanRecord, accountMetadataLoaded, amountsVisible)
                            if (accountMetadataLoaded && amountsVisible &&
                                onSaveAccountIban != null) {
                                TextButton(
                                    enabled = !accountMetadataSaving,
                                    onClick = { editingIban = true },
                                ) { Text(if (ibanRecord?.iban == null) "Προσθήκη IBAN" else "Αλλαγή IBAN") }
                                if (ibanRecord?.iban != null) {
                                    TextButton(
                                        enabled = !accountMetadataSaving,
                                        onClick = { deletingIban = true },
                                    ) { Text("Αφαίρεση IBAN") }
                                }
                            }
                        }
                        MyFinHubAmountText(
                            text = if (amountsVisible) formatAccountEuro(account.balance) else "•••• €",
                            tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "Οι κινήσεις παρακάτω είναι φιλτραρισμένες σε αυτόν τον λογαριασμό.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                MyFinHubPrimaryAction(
                    label = "Νέα κίνηση από αυτόν τον λογαριασμό",
                    onClick = onNewTransaction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("Κινήσεις λογαριασμού", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (historyControlsVisible) {
                item(key = "account-history-controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        OutlinedTextField(
                            value = historyQuery,
                            onValueChange = { historyQuery = it },
                            label = { Text("Αναζήτηση κινήσεων λογαριασμού") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("account_history_search"),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .testTag("account_history_filters"),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                        ) {
                            val options = listOf(
                                null to "Όλες",
                                ActivityKind.EXPENSE to "Έξοδα",
                                ActivityKind.INCOME to "Έσοδα",
                                ActivityKind.TRANSFER to "Μεταφορές",
                                ActivityKind.CARD_PAYMENT to "Πληρωμές κάρτας",
                            )
                            options.forEach { (kind, title) ->
                                FilterChip(
                                    selected = historyKind == kind,
                                    onClick = { historyKindName = kind?.name },
                                    label = { Text(title) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                        .testTag("account_history_kind_${kind?.name ?: "ALL"}"),
                                )
                            }
                        }
                        Text(
                            "${visibleHistory.size} από ${activityItems.size} κινήσεις",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("account_history_result_count"),
                        )
                        if (historyQuery.isNotEmpty() || historyKindName != null) {
                            TextButton(
                                onClick = {
                                    historyQuery = ""
                                    historyKindName = null
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .testTag("account_history_reset_filters"),
                            ) { Text("Καθαρισμός φίλτρων") }
                        }
                    }
                }
            }

            if (visibleHistory.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (activityItems.isEmpty()) "Δεν υπάρχουν κινήσεις για αυτόν τον λογαριασμό."
                            else "Δεν βρέθηκαν κινήσεις με τα επιλεγμένα φίλτρα.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val sections = sourceHistoryRows.groupBy { it.value.rawDate.take(10) }.toList()
                sections.forEach { (date, sectionItems) ->
                    item(key = "account-day-$date") {
                        Text(
                            accountDayLabel(date, referenceDate),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MyFinHubSpacing.xxs),
                        )
                    }
                    sectionItems.forEach { source ->
                        // Imported historic records may have duplicated or blank IDs.
                        // A source-index key keeps EVERY row visible without crashing LazyColumn.
                        item(key = "account-history-source-${source.index}") {
                            val event = source.value
                            val openId = canonicalCardActivityOpenId(event.id, fullActivityIdCounts)
                            AccountLedgerRow(
                                item = event,
                                amountsVisible = amountsVisible,
                                categoryGlyphOverrides = categoryGlyphOverrides,
                                canOpen = openId != null,
                                warning = canonicalCardActivityIdentityWarning(
                                    event.id, fullActivityIdCounts,
                                ),
                                sourceIndex = source.index,
                                onClick = { openId?.let(onOpenActivity) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountLedgerRow(
    item: ActivityItem,
    amountsVisible: Boolean,
    categoryGlyphOverrides: Map<String, String> = emptyMap(),
    canOpen: Boolean,
    warning: String?,
    sourceIndex: Int,
    onClick: () -> Unit,
) {
    val transfer = item.kind == ActivityKind.TRANSFER
    val transferRoute = if (transfer) accountTransferRouteParts(item) else null
    val subtitle = transferRoute?.let { "Από ${it.first} → Προς ${it.second}" } ?: item.subtitle
    val meta = if (item.pendingSync) "Εκκρεμεί επιβεβαίωση" else if (transfer) "" else item.accountLabel
    val tone = if (item.pendingSync || transfer) FinanceTone.Neutral else accountActivityTone(item.kind)
    Column(modifier = Modifier.fillMaxWidth()) {
        MyFinHubFinanceRow(
        icon = myFinHubCategoryIcon(
            item.category, accountActivityIcon(item.kind),
            categoryGlyphOverrides[
                (if (item.canonicalKind == "income") "income:" else "expense:") +
                    item.category.orEmpty() +
                    (item.subcategory?.takeIf(String::isNotBlank)?.let { ":" + it } ?: "")
            ] ?: categoryGlyphOverrides[
                (if (item.canonicalKind == "income") "income:" else "expense:") +
                    item.category.orEmpty()
            ],
        ),
        iconDescription = item.category ?: item.kind.label,
        title = item.title,
        subtitle = subtitle,
        meta = meta,
        amountText = if (amountsVisible) formatSignedAccountEuro(item.amount) else "•••• €",
        tone = tone,
        onClick = onClick,
        enabled = canOpen,
        modifier = Modifier.testTag("account_history_source_${sourceIndex}"),
    )
        warning?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                ).testTag("account_history_identity_warning_${sourceIndex}"),
            )
        }
    }
}

internal fun accountTransferRouteParts(item: ActivityItem): Pair<String, String>? {
    val route = item.accountLabel.split("→", limit = 2).map { it.trim() }
    return if (route.size == 2 && route.all { it.isNotBlank() }) route[0] to route[1] else null
}

private fun accountDayLabel(rawDate: String, today: LocalDate): String {
    val date = runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: return rawDate
    if (date == today) return "Σήμερα"
    if (date == today.minusDays(1)) return "Χθες"
    return date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("el-GR")))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("el-GR")) else it.toString() }
}

private fun accountActivityIcon(kind: ActivityKind) = when (kind) {
    ActivityKind.INCOME -> MyFinHubIcons.Income
    ActivityKind.EXPENSE -> MyFinHubIcons.Expense
    ActivityKind.TRANSFER -> MyFinHubIcons.Transfer
    ActivityKind.CARD_PAYMENT -> MyFinHubIcons.Card
}

private fun accountActivityTone(kind: ActivityKind) = when (kind) {
    ActivityKind.INCOME -> FinanceTone.Income
    ActivityKind.EXPENSE, ActivityKind.CARD_PAYMENT -> FinanceTone.Expense
    ActivityKind.TRANSFER -> FinanceTone.Neutral
}

private fun formatAccountEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedAccountEuro(value: Double): String {
    val sign = if (value > 0.005) "+" else ""
    return sign + formatAccountEuro(value)
}
