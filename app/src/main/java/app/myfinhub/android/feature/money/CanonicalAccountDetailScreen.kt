package app.myfinhub.android.feature.money

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CanonicalAccountDetailScreen(
    account: MoneyAccount?,
    activityItems: List<ActivityItem>,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onNewTransaction: () -> Unit = {},
    referenceDate: LocalDate = LocalDate.now(),
    amountsVisibleOverride: Boolean? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
            modifier = Modifier.fillMaxSize(),
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

            if (activityItems.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Δεν υπάρχουν κινήσεις για αυτόν τον λογαριασμό.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val sections = activityItems.groupBy { it.rawDate.take(10) }.toList()
                sections.forEach { (date, sectionItems) ->
                    item(key = "account-day-$date") {
                        Text(
                            accountDayLabel(date, referenceDate),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MyFinHubSpacing.xxs),
                        )
                    }
                    sectionItems.forEach { item ->
                        item(key = item.id) {
                            AccountLedgerRow(
                                item = item,
                                amountsVisible = amountsVisible,
                                onClick = { onOpenActivity(item.id) },
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
    onClick: () -> Unit,
) {
    val transfer = item.kind == ActivityKind.TRANSFER
    val transferRoute = if (transfer) accountTransferRouteParts(item) else null
    val subtitle = transferRoute?.let { "Από ${it.first} → Προς ${it.second}" } ?: item.subtitle
    val meta = if (item.pendingSync) "Εκκρεμεί επιβεβαίωση" else if (transfer) "" else item.accountLabel
    val tone = if (item.pendingSync || transfer) FinanceTone.Neutral else accountActivityTone(item.kind)
    MyFinHubFinanceRow(
        icon = myFinHubCategoryIcon(item.category, accountActivityIcon(item.kind)),
        iconDescription = item.category ?: item.kind.label,
        title = item.title,
        subtitle = subtitle,
        meta = meta,
        amountText = if (amountsVisible) formatSignedAccountEuro(item.amount) else "•••• €",
        tone = tone,
        onClick = onClick,
    )
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
