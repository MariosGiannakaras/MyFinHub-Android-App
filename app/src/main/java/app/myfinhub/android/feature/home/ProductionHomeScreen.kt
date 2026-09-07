package app.myfinhub.android.feature.home

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBrandMark
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubHeroAction
import app.myfinhub.android.designsystem.MyFinHubHeroCard
import app.myfinhub.android.designsystem.MyFinHubHeroHeading
import app.myfinhub.android.designsystem.MyFinHubHeroMetric
import app.myfinhub.android.designsystem.MyFinHubHeroValue
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import java.text.NumberFormat
import java.util.Locale

/** Physical-device production Home backed by canonical finance data. */
@Composable
fun ProductionHomeScreen(
    state: HomeUiState,
    @Suppress("UNUSED_PARAMETER") onAction: (HomeAction) -> Unit,
    onOpenAttention: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQuickEntry: () -> Unit,
    onOpenAccount: (String) -> Unit = {},
    onOpenRecent: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var amountsVisible by remember(context) { mutableStateOf(AmountVisibilityPreference.read(context)) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AmountVisibilityPreference.KEY) amountsVisible = AmountVisibilityPreference.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "MyFinHub",
                subtitle = "Η οικονομική σου εικόνα σήμερα",
                navigation = { MyFinHubBrandMark() },
                trailing = { TextButton(onClick = onOpenSettings) { Text("Ρυθμίσεις") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item { FinancialSnapshotCard(state = state, amountsVisible = amountsVisible, onOpenQuickEntry = onOpenQuickEntry) }
            if (state.attentionItems.isNotEmpty()) item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }
            if (state.upcomingItems.isNotEmpty()) item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }
            item { MonthContextCard(state = state, amountsVisible = amountsVisible) }
            item { RecentActivityCard(state.recentItems, amountsVisible, onOpenRecent) }
            item { PrimaryAccountsCard(state.accounts.take(2), amountsVisible, onOpenAccount) }
        }
    }
}

@Composable
private fun FinancialSnapshotCard(
    state: HomeUiState,
    amountsVisible: Boolean,
    onOpenQuickEntry: () -> Unit,
) {
    MyFinHubHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md)) {
            MyFinHubHeroHeading(
                eyebrow = "Διαθέσιμα τώρα",
                title = "Τι μπορείς να χρησιμοποιήσεις",
                supporting = "Το καθαρό διαθέσιμο των ενεργών λογαριασμών σου",
            )
            MyFinHubHeroValue(if (amountsVisible) formatHomeEuro(state.liquidTotal) else "•••• €")
            MyFinHubHeroAction(
                label = "Νέα κίνηση",
                onClick = onOpenQuickEntry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MonthContextCard(state: HomeUiState, amountsVisible: Boolean) {
    val net = state.monthFlow.income - state.monthFlow.expense
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Ο μήνας μέχρι τώρα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md)) {
                SnapshotMetric(
                    label = "Έσοδα",
                    value = if (amountsVisible) formatHomeEuro(state.monthFlow.income) else "•••• €",
                    tone = FinanceTone.Income,
                    modifier = Modifier.weight(1f),
                )
                SnapshotMetric(
                    label = "Έξοδα",
                    value = if (amountsVisible) formatHomeEuro(state.monthFlow.expense) else "•••• €",
                    tone = FinanceTone.Expense,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Καθαρή ροή", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MyFinHubAmountText(
                    text = if (amountsVisible) formatHomeEuro(net) else "•••• €",
                    tone = if (net >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SnapshotMetric(label: String, value: String, tone: FinanceTone, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MyFinHubAmountText(text = value, tone = tone, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PrimaryAccountsCard(accounts: List<HomeAccount>, amountsVisible: Boolean, onOpenAccount: (String) -> Unit) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Γρήγορη πρόσβαση", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (accounts.isEmpty()) {
                Text("Δεν υπάρχουν διαθέσιμοι λογαριασμοί.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else accounts.forEachIndexed { index, account ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenAccount(account.id) }.semantics(mergeDescendants = true) {
                        contentDescription = if (amountsVisible) "${account.name}, ${formatHomeEuro(account.balance)}" else "${account.name}, ποσό κρυφό"
                    },
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val savings = account.group == HomeAccountGroup.SAVINGS
                    MyFinHubIconBadge(if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account, if (savings) FinanceTone.Savings else FinanceTone.Neutral, null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(account.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MyFinHubAmountText(
                        text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                        tone = if (account.balance >= 0) FinanceTone.Income else FinanceTone.Expense,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (index != accounts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun RecentActivityCard(items: List<HomeRecentItem>, amountsVisible: Boolean, onOpenRecent: (String) -> Unit) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Πρόσφατες κινήσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) Text("Δεν υπάρχουν ακόμη κινήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else items.take(4).forEachIndexed { index, item ->
                val tone = when (item.tone) { HomeRecentTone.INCOME -> FinanceTone.Income; HomeRecentTone.EXPENSE -> FinanceTone.Expense; HomeRecentTone.TRANSFER -> FinanceTone.Transfer }
                Row(modifier = Modifier.fillMaxWidth().clickable { onOpenRecent(item.id) }.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    MyFinHubIconBadge(when (item.tone) { HomeRecentTone.INCOME -> MyFinHubIcons.Income; HomeRecentTone.EXPENSE -> MyFinHubIcons.Expense; HomeRecentTone.TRANSFER -> MyFinHubIcons.Transfer }, tone, null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(listOf(item.dateLabel, item.subtitle).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MyFinHubAmountText(
                        text = if (amountsVisible) formatHomeEuro(item.amount) else "•••• €",
                        tone = tone,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (index != items.take(4).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ProductionAttentionCard(items: List<HomeAttentionItem>, onOpen: (String) -> Unit) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Χρειάζεται προσοχή", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            items.take(2).forEachIndexed { index, item ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm), verticalAlignment = Alignment.Top) {
                        MyFinHubIconBadge(MyFinHubIcons.Attention, FinanceTone.Attention, null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.dueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { onOpen(item.id) }, modifier = Modifier.align(Alignment.End)) { Text("Έλεγχος") }
                }
                if (index != items.take(2).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ProductionUpcomingCard(items: List<HomeUpcomingItem>, amountsVisible: Boolean) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Επόμενες υποχρεώσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            items.take(3).forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    MyFinHubIconBadge(MyFinHubIcons.Plan, FinanceTone.Attention, null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MyFinHubAmountText(
                        text = if (amountsVisible) formatHomeEuro(item.amount) else "•••• €",
                        tone = FinanceTone.Expense,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (index != items.take(3).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun formatHomeEuro(value: Double): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
