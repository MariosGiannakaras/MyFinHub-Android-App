package app.myfinhub.android.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBrandMark
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
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
                subtitle = "Η καθημερινή οικονομική σου εικόνα",
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                    ) {
                        Text(
                            "Οι λογαριασμοί σου",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            "Υπόλοιπα και πρόσφατη δραστηριότητα",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FloatingActionButton(onClick = onOpenQuickEntry) {
                        Icon(MyFinHubIcons.Add, contentDescription = "Νέα κίνηση")
                    }
                }
            }

            item { PrimaryAccountsCard(state.accounts.take(3), amountsVisible) }
            item { RecentActivityCard(state.recentItems, amountsVisible) }
            if (state.attentionItems.isNotEmpty()) item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }
            if (state.upcomingItems.isNotEmpty()) item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }
        }
    }
}

@Composable
private fun PrimaryAccountsCard(accounts: List<HomeAccount>, amountsVisible: Boolean) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Βασικοί λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (accounts.isEmpty()) {
                Text("Δεν υπάρχουν διαθέσιμοι λογαριασμοί.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                accounts.forEachIndexed { index, account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (amountsVisible) {
                                    "${account.name}, ${formatHomeEuro(account.balance)}"
                                } else {
                                    "${account.name}, ποσό κρυφό"
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val savings = account.group == HomeAccountGroup.SAVINGS
                        MyFinHubIconBadge(
                            icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,
                            tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,
                            contentDescription = null,
                        )
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
}

@Composable
private fun RecentActivityCard(items: List<HomeRecentItem>, amountsVisible: Boolean) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Τελευταίες κινήσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text("Δεν υπάρχουν ακόμη κινήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                items.take(6).forEachIndexed { index, item ->
                    val tone = when (item.tone) {
                        HomeRecentTone.INCOME -> FinanceTone.Income
                        HomeRecentTone.EXPENSE -> FinanceTone.Expense
                        HomeRecentTone.TRANSFER -> FinanceTone.Transfer
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubIconBadge(
                            icon = when (item.tone) {
                                HomeRecentTone.INCOME -> MyFinHubIcons.Income
                                HomeRecentTone.EXPENSE -> MyFinHubIcons.Expense
                                HomeRecentTone.TRANSFER -> MyFinHubIcons.Transfer
                            },
                            tone = tone,
                            contentDescription = null,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(item.dateLabel, item.subtitle).filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubAmountText(
                            text = if (amountsVisible) formatHomeEuro(item.amount) else "•••• €",
                            tone = tone,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (index != items.take(6).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubIconBadge(MyFinHubIcons.Attention, FinanceTone.Attention, null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.dueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onOpen(item.id) }) { Text("Έλεγχος") }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
