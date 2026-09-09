package app.myfinhub.android.feature.home

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.financeToneColors
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import java.text.NumberFormat
import java.util.Locale

/** Physical-device production Home backed only by canonical finance data. */
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
                trailing = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(MyFinHubDesignMetrics.minimumTouchTarget),
                    ) {
                        Icon(
                            imageVector = MyFinHubIcons.Filter,
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
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                HomeFinancialSnapshot(
                    state = state,
                    amountsVisible = amountsVisible,
                    onOpenQuickEntry = onOpenQuickEntry,
                )
            }
            if (state.attentionItems.isNotEmpty()) {
                item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }
            }
            item {
                PrimaryAccountsSection(
                    accounts = primaryAccounts,
                    amountsVisible = amountsVisible,
                    onOpenAccount = onOpenAccount,
                )
            }
            if (state.upcomingItems.isNotEmpty()) {
                item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }
            }
            if (secondaryAccounts.isNotEmpty()) {
                item { SecondaryAccountsCard(secondaryAccounts, amountsVisible, onOpenAccount) }
            }
        }
    }
}

@Composable
private fun HomeFinancialSnapshot(
    state: HomeUiState,
    amountsVisible: Boolean,
    onOpenQuickEntry: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val netFlow = state.monthFlow.income - state.monthFlow.expense
    val incomeText = if (amountsVisible) formatHomeEuro(state.monthFlow.income) else "•••• €"
    val expenseText = if (amountsVisible) formatHomeEuro(state.monthFlow.expense) else "•••• €"
    val netFlowText = if (amountsVisible) formatSignedHomeEuro(netFlow) else "•••• €"

    MyFinHubHeroCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(MyFinHubSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            MyFinHubHeroHeading(
                eyebrow = "Σήμερα",
                title = "Διαθέσιμα τώρα",
                supporting = "Μετρητά και καθημερινοί λογαριασμοί",
            )
            MyFinHubHeroValue(
                text = if (amountsVisible) formatHomeEuro(state.liquidTotal) else "•••• €",
            )
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubHeroMetric(label = "Έσοδα μήνα", value = incomeText)
                    MyFinHubHeroMetric(label = "Έξοδα μήνα", value = expenseText)
                    MyFinHubHeroMetric(label = "Καθαρή ροή", value = netFlowText)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                ) {
                    MyFinHubHeroMetric(
                        label = "Έσοδα μήνα",
                        value = incomeText,
                        modifier = Modifier.weight(1f),
                    )
                    MyFinHubHeroMetric(
                        label = "Έξοδα μήνα",
                        value = expenseText,
                        modifier = Modifier.weight(1f),
                    )
                    MyFinHubHeroMetric(
                        label = "Καθαρή ροή",
                        value = netFlowText,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            MyFinHubHeroAction(
                label = "Νέα κίνηση",
                onClick = onOpenQuickEntry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrimaryAccountsSection(
    accounts: List<HomeAccount>,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
            Text(
                "Κύριοι λογαριασμοί",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Οι βασικοί λογαριασμοί σου με τάση 7 ημερών",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (accounts.isEmpty()) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Δεν υπάρχουν διαθέσιμοι λογαριασμοί.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    val delta = if (account.balanceTrend.size >= 2) {
        account.balanceTrend.last() - account.balanceTrend.first()
    } else {
        0.0
    }
    val tone = when {
        delta > 0.005 -> FinanceTone.Income
        delta < -0.005 -> FinanceTone.Expense
        else -> FinanceTone.Neutral
    }

    Surface(
        onClick = { onOpenAccount(account.id) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (amountsVisible) {
                    "${account.name}, ${formatHomeEuro(account.balance)}, τάση 7 ημερών ${formatSignedHomeEuro(delta)}"
                } else {
                    "${account.name}, ποσό κρυφό"
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = MyFinHubDesignMetrics.cardElevation,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                vertical = MyFinHubSpacing.xs,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountIdentityMark(account)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                ) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    account.institution?.takeIf(String::isNotBlank)?.let { institution ->
                        Text(
                            institution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MyFinHubAmountText(
                    text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                    tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountSparkline(
                    values = account.balanceTrend,
                    tone = tone,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                )
                Text(
                    text = if (amountsVisible && account.balanceTrend.size >= 2) {
                        "7 ημέρες · ${formatSignedHomeEuro(delta)}"
                    } else {
                        "Τάση 7 ημερών"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = financeToneColors(tone).accent,
                )
            }
        }
    }
}

@Composable
private fun AccountIdentityMark(account: HomeAccount) {
    val provider = financialProvider(account.id, account.institution ?: account.name)
    if (provider != null) {
        MyFinHubProviderMark(
            provider = provider,
            modifier = Modifier.size(36.dp),
            contentDescription = provider.institutionLabel,
        )
        return
    }

    val savings = account.group == HomeAccountGroup.SAVINGS
    val tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral
    val colors = financeToneColors(tone)
    Surface(
        modifier = Modifier.size(36.dp),
        shape = MaterialTheme.shapes.small,
        color = colors.container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AccountSparkline(
    values: List<Double>,
    tone: FinanceTone,
    modifier: Modifier = Modifier,
) {
    val lineColor = financeToneColors(tone).accent
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        drawLine(
            color = guideColor,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx(),
        )
        if (values.size < 2) return@Canvas

        val minimum = values.minOrNull() ?: 0.0
        val maximum = values.maxOrNull() ?: minimum
        val rawRange = maximum - minimum
        val range = rawRange.takeIf { it > 0.005 } ?: 1.0
        val verticalInset = 3.dp.toPx()
        val usableHeight = (size.height - verticalInset * 2f).coerceAtLeast(1f)
        val path = Path()
        var lastPoint = Offset.Zero

        values.forEachIndexed { index, value ->
            val x = size.width * index / values.lastIndex.toFloat()
            val normalized = if (rawRange > 0.005) ((value - minimum) / range).toFloat() else 0.5f
            val y = verticalInset + (1f - normalized) * usableHeight
            val point = Offset(x, y)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            lastPoint = point
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = lineColor,
            radius = 2.5.dp.toPx(),
            center = lastPoint,
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
            Text(
                "Δευτερεύοντες λογαριασμοί",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            accounts.forEachIndexed { index, account ->
                Surface(
                    onClick = { onOpenAccount(account.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (amountsVisible) {
                                "${account.name}, ${formatHomeEuro(account.balance)}"
                            } else {
                                "${account.name}, ποσό κρυφό"
                            }
                        },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MyFinHubSpacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountIdentityMark(account)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            account.institution?.takeIf(String::isNotBlank)?.let { institution ->
                                Text(
                                    institution,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        MyFinHubAmountText(
                            text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                            tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (index != accounts.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ProductionAttentionCard(
    items: List<HomeAttentionItem>,
    onOpen: (String) -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text(
                "Χρειάζεται προσοχή",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            items.take(2).forEachIndexed { index, item ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        val tone = if (item.tone == HomeAttentionTone.URGENT) {
                            FinanceTone.Attention
                        } else {
                            FinanceTone.Neutral
                        }
                        val colors = financeToneColors(tone)
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = MaterialTheme.shapes.small,
                            color = colors.container,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MyFinHubIcons.Attention,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.reason,
                                style = MaterialTheme.typography.bodySmall,
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
                    TextButton(
                        onClick = { onOpen(item.id) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Προβολή")
                    }
                }
                if (index != items.take(2).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ProductionUpcomingCard(
    items: List<HomeUpcomingItem>,
    amountsVisible: Boolean,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text(
                "Επόμενες υποχρεώσεις",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            items.take(3).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val colors = financeToneColors(FinanceTone.Attention)
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = MaterialTheme.shapes.small,
                        color = colors.container,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = MyFinHubIcons.Plan,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            item.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MyFinHubAmountText(
                        text = if (amountsVisible) formatHomeEuro(item.amount) else "•••• €",
                        tone = FinanceTone.Expense,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (index != items.take(3).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun formatHomeEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedHomeEuro(value: Double): String =
    (if (value > 0.005) "+" else "") + formatHomeEuro(value)
