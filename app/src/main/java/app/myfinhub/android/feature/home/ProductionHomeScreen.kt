package app.myfinhub.android.feature.home

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.financialProvider
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
import app.myfinhub.android.designsystem.MyFinHubProviderMark
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
    @Suppress("UNUSED_PARAMETER") onOpenRecent: (String) -> Unit = {},
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

    val explicitlyPrimary = state.accounts.filter(HomeAccount::isPrimary)
    val primaryAccounts = (if (explicitlyPrimary.isNotEmpty()) explicitlyPrimary else state.accounts.take(3)).take(3)
    val primaryIds = primaryAccounts.map(HomeAccount::id).toSet()
    val secondaryAccounts = state.accounts.filterNot { it.id in primaryIds }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "MyFinHub",
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
                PrimaryAccountsSection(
                    accounts = primaryAccounts,
                    amountsVisible = amountsVisible,
                    onOpenAccount = onOpenAccount,
                    onOpenQuickEntry = onOpenQuickEntry,
                )
            }
            if (secondaryAccounts.isNotEmpty()) {
                item { SecondaryAccountsCard(secondaryAccounts, amountsVisible, onOpenAccount) }
            }
            if (state.attentionItems.isNotEmpty()) item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }
            if (state.upcomingItems.isNotEmpty()) item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }
        }
    }
}

@Composable
private fun PrimaryAccountsSection(
    accounts: List<HomeAccount>,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
    onOpenQuickEntry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
            Text("Κύριοι λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Οι 3 βασικοί λογαριασμοί",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenQuickEntry) { Text("Νέα κίνηση") }
        }
        if (accounts.isEmpty()) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text("Δεν υπάρχουν διαθέσιμοι λογαριασμοί.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            accounts.forEach { account ->
                PrimaryAccountCard(account, amountsVisible, onOpenAccount)
            }
        }
    }
}

@Composable
private fun PrimaryAccountCard(
    account: HomeAccount,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
) {
    val savings = account.group == HomeAccountGroup.SAVINGS
    val delta = if (account.balanceTrend.size >= 2) {
        account.balanceTrend.last() - account.balanceTrend.first()
    } else {
        0.0
    }
    MyFinHubSectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAccount(account.id) }
            .semantics(mergeDescendants = true) {
                contentDescription = if (amountsVisible) {
                    "${account.name}, ${formatHomeEuro(account.balance)}"
                } else {
                    "${account.name}, ποσό κρυφό"
                }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val provider = financialProvider(account.id, account.institution ?: account.name)
                if (provider != null) {
                    MyFinHubProviderMark(provider, modifier = Modifier.size(36.dp), contentDescription = provider.institutionLabel)
                } else {
                    MyFinHubIconBadge(
                        icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,
                        tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,
                        contentDescription = null,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    account.institution?.takeIf(String::isNotBlank)?.let { institution ->
                        Text(institution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MyFinHubAmountText(
                    text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                    tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            AccountSparkline(values = account.balanceTrend)
            Text(
                text = if (amountsVisible && account.balanceTrend.size >= 2) {
                    "Τάση 7 ημερών · ${formatSignedHomeEuro(delta)}"
                } else {
                    "Τάση 7 ημερών"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSparkline(values: List<Double>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        if (values.size < 2) {
            drawLine(
                color = guideColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
            )
            return@Canvas
        }
        val minimum = values.minOrNull() ?: 0.0
        val maximum = values.maxOrNull() ?: minimum
        val range = (maximum - minimum).takeIf { it > 0.005 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / values.lastIndex.toFloat()
            val normalized = ((value - minimum) / range).toFloat()
            val y = size.height - normalized * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(
            color = guideColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun SecondaryAccountsCard(
    accounts: List<HomeAccount>,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Δευτερεύοντες λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            accounts.forEachIndexed { index, account ->
                val savings = account.group == HomeAccountGroup.SAVINGS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount(account.id) }
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
                    val provider = financialProvider(account.id, account.institution ?: account.name)
                    if (provider != null) {
                        MyFinHubProviderMark(provider, modifier = Modifier.size(32.dp), contentDescription = provider.institutionLabel)
                    } else {
                        MyFinHubIconBadge(
                            icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,
                            tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,
                            contentDescription = null,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        account.institution?.takeIf(String::isNotBlank)?.let { institution ->
                            Text(institution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    MyFinHubAmountText(
                        text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                        tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (index != accounts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

private fun formatSignedHomeEuro(value: Double): String =
    (if (value > 0.005) "+" else "") + formatHomeEuro(value)
