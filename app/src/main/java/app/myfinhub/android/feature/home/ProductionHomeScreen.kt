package app.myfinhub.android.feature.home

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSectionHeading
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.financeToneColors
import app.myfinhub.android.designsystem.myFinHubPressScale
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
                subtitle = "Η οικονομική σου εικόνα",
                navigation = { MyFinHubBrandMark() },
                trailing = { TextButton(onClick = onOpenSettings) { Text("Ρυθμίσεις") } },
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
                HomeSnapshotCard(
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
            if (secondaryAccounts.isNotEmpty()) {
                item { SecondaryAccountsCard(secondaryAccounts, amountsVisible, onOpenAccount) }
            }
            if (state.upcomingItems.isNotEmpty()) {
                item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }
            }
        }
    }
}

@Composable
private fun HomeSnapshotCard(
    state: HomeUiState,
    amountsVisible: Boolean,
    onOpenQuickEntry: () -> Unit,
) {
    val monthNetFlow = state.monthFlow.income - state.monthFlow.expense
    MyFinHubHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            MyFinHubHeroHeading(
                eyebrow = "Σήμερα",
                title = "Διαθέσιμα τώρα",
                supporting = "Σύνολο ρευστών λογαριασμών",
            )
            MyFinHubHeroValue(
                text = if (amountsVisible) formatHomeEuro(state.liquidTotal) else "•••• €",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.lg),
            ) {
                MyFinHubHeroMetric(
                    label = "Έσοδα μήνα",
                    value = if (amountsVisible) formatHomeEuro(state.monthFlow.income) else "•••• €",
                    modifier = Modifier.weight(1f),
                )
                MyFinHubHeroMetric(
                    label = "Έξοδα μήνα",
                    value = if (amountsVisible) formatHomeEuro(state.monthFlow.expense) else "•••• €",
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = if (amountsVisible) {
                    "Καθαρή ροή μήνα ${formatSignedHomeEuro(monthNetFlow)}"
                } else {
                    "Καθαρή ροή μήνα •••• €"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            MyFinHubHeroAction(
                label = "Νέα κίνηση",
                onClick = onOpenQuickEntry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = MyFinHubIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(MyFinHubDesignMetrics.compactIconSize),
                )
                Spacer(modifier = Modifier.width(MyFinHubDesignMetrics.buttonIconGap))
                Text("Νέα κίνηση", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PrimaryAccountsSection(
    accounts: List<HomeAccount>,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
        MyFinHubSectionHeading(
            title = "Κύριοι λογαριασμοί",
            subtitle = if (accounts.size == 1) "1 βασικός λογαριασμός" else "${accounts.size} βασικοί λογαριασμοί",
        )
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
    val savings = account.group == HomeAccountGroup.SAVINGS
    val delta = if (account.balanceTrend.size >= 2) {
        account.balanceTrend.last() - account.balanceTrend.first()
    } else {
        0.0
    }
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current

    MyFinHubSectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .myFinHubPressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = { onOpenAccount(account.id) },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (amountsVisible) {
                    "${account.name}, ${formatHomeEuro(account.balance)}, τάση 7 ημερών ${formatSignedHomeEuro(delta)}"
                } else {
                    "${account.name}, ποσό κρυφό"
                }
            },
        contentPadding = PaddingValues(
            horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
            vertical = MyFinHubDesignMetrics.rowVerticalPadding,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val provider = financialProvider(account.id, account.institution ?: account.name)
                if (provider != null) {
                    MyFinHubProviderMark(
                        provider = provider,
                        modifier = Modifier.size(36.dp),
                        contentDescription = provider.institutionLabel,
                    )
                } else {
                    MyFinHubIconBadge(
                        icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,
                        tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,
                        contentDescription = null,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    account.institution?.takeIf(String::isNotBlank)?.let { institution ->
                        Text(
                            text = institution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MyFinHubAmountText(
                    text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                    tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            AccountSparkline(values = account.balanceTrend)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "7 ημέρες",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (amountsVisible && account.balanceTrend.size >= 2) {
                        formatSignedHomeEuro(delta)
                    } else {
                        "•••• €"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = financeToneColors(trendTone(delta)).accent,
                )
            }
        }
    }
}

@Composable
private fun AccountSparkline(values: List<Double>) {
    val delta = if (values.size >= 2) values.last() - values.first() else 0.0
    val lineColor = financeToneColors(trendTone(delta)).accent
    val guideColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
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
        val rawRange = maximum - minimum

        drawLine(
            color = guideColor.copy(alpha = 0.7f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
        )

        if (rawRange <= 0.005) {
            val y = size.height / 2f
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.25.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(size.width, y),
            )
            return@Canvas
        }

        val path = Path()
        var latestPoint = Offset.Zero
        values.forEachIndexed { index, value ->
            val x = size.width * index / values.lastIndex.toFloat()
            val normalized = ((value - minimum) / rawRange).toFloat()
            val y = size.height * (0.85f - normalized * 0.70f)
            val point = Offset(x, y)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            if (index == values.lastIndex) latestPoint = point
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.25.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = latestPoint,
        )
    }
}

@Composable
private fun SecondaryAccountsCard(
    accounts: List<HomeAccount>,
    amountsVisible: Boolean,
    onOpenAccount: (String) -> Unit,
) {
    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
            vertical = MyFinHubSpacing.sm,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            MyFinHubSectionHeading(
                title = "Δευτερεύοντες λογαριασμοί",
                subtitle = "${accounts.size} ακόμη διαθέσιμοι",
            )
            accounts.forEachIndexed { index, account ->
                val savings = account.group == HomeAccountGroup.SAVINGS
                val interactionSource = remember(account.id) { MutableInteractionSource() }
                val indication = LocalIndication.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                        .myFinHubPressScale(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = indication,
                            onClick = { onOpenAccount(account.id) },
                        )
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
                        MyFinHubProviderMark(
                            provider = provider,
                            modifier = Modifier.size(32.dp),
                            contentDescription = provider.institutionLabel,
                        )
                    } else {
                        MyFinHubIconBadge(
                            icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,
                            tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                    ) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        account.institution?.takeIf(String::isNotBlank)?.let { institution ->
                            Text(
                                text = institution,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    MyFinHubAmountText(
                        text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                        tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,
                        style = MaterialTheme.typography.titleSmall,
                    )
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
    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
            vertical = MyFinHubSpacing.sm,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            MyFinHubSectionHeading(
                title = "Χρειάζεται προσοχή",
                subtitle = if (items.size == 1) "1 θέμα χρειάζεται ενέργεια" else "${items.size} θέματα χρειάζονται ενέργεια",
                icon = MyFinHubIcons.Attention,
                tone = FinanceTone.Attention,
            )
            items.take(2).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    MyFinHubIconBadge(
                        icon = MyFinHubIcons.Attention,
                        tone = if (item.tone == HomeAttentionTone.URGENT) FinanceTone.Attention else FinanceTone.Neutral,
                        contentDescription = null,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (item.reason.isNotBlank()) {
                            Text(
                                text = item.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = item.dueLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.tone == HomeAttentionTone.URGENT) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        TextButton(
                            onClick = { onOpen(item.id) },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Προβολή")
                        }
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
    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
            vertical = MyFinHubSpacing.sm,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            MyFinHubSectionHeading(
                title = "Επόμενες υποχρεώσεις",
                subtitle = "Οι κοντινότερες προγραμματισμένες χρεώσεις",
                icon = MyFinHubIcons.Plan,
                tone = FinanceTone.Attention,
            )
            items.take(3).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubIconBadge(
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Attention,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = item.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MyFinHubAmountText(
                        text = if (amountsVisible) formatHomeEuro(item.amount) else "•••• €",
                        tone = FinanceTone.Expense,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (index != items.take(3).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun trendTone(delta: Double): FinanceTone = when {
    delta > 0.005 -> FinanceTone.Income
    delta < -0.005 -> FinanceTone.Expense
    else -> FinanceTone.Neutral
}

private fun formatHomeEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedHomeEuro(value: Double): String =
    (if (value > 0.005) "+" else "") + formatHomeEuro(value)
