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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.core.ui.AccountIbanRow
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
    onOpenSettings: () -> Unit,
    onOpenQuickEntry: () -> Unit,
    onOpenAccount: (String) -> Unit = {},
    onOpenAllAccounts: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenRecent: (String) -> Unit = {},
    amountsVisibleOverride: Boolean? = null,
    accountMetadata: Map<String, AccountMetadataRecord> = emptyMap(),
    accountMetadataLoaded: Boolean = false,
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
                        modifier = Modifier.size(MyFinHubDesignMetrics.minimumTouchTarget),
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
            item {
                HomePrimaryAccountsSection(
                    accounts = primaryAccounts,
                    amountsVisible = amountsVisible,
                    accountMetadata = accountMetadata,
                    accountMetadataLoaded = accountMetadataLoaded,
                    onOpenAccount = onOpenAccount,
                )
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
            if (state.attentionItems.isNotEmpty()) {
                item {
                    HomeAttentionSection(
                        items = state.attentionItems.take(2),
                        onOpen = onOpenAttention,
                    )
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
                text = "Μόνο καθημερινά μετρητά και λογαριασμός μισθοδοσίας · χωρίς καβάτζα, αποταμίευση ή άλλες τράπεζες",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MyFinHubAmountText(
            text = if (amountsVisible) formatHomeEuro(amount) else "•••• €",
            tone = if (amount >= 0.0) FinanceTone.Income else FinanceTone.Expense,
            style = MaterialTheme.typography.displaySmall,
        )
        MyFinHubPrimaryAction(
            label = "Νέα κίνηση",
            onClick = onOpenQuickEntry,
            modifier = Modifier.fillMaxWidth(),
        )
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
                    account,
                    amountsVisible,
                    ibanRecord = accountMetadata[account.id],
                    accountMetadataLoaded = accountMetadataLoaded,
                    onClick = { onOpenAccount(account.id) },
                )
            }
        }
    }
}

@Composable
private fun HomePrimaryAccountCard(
    account: HomeAccount,
    amountsVisible: Boolean,
    ibanRecord: AccountMetadataRecord?,
    accountMetadataLoaded: Boolean,
    onClick: () -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeAccountIdentityMark(account)
                HomeAccountIdentity(account, Modifier.weight(1f))
            }
            if (account.primaryRole != HomePrimaryRole.CASH) {
                AccountIbanRow(ibanRecord, accountMetadataLoaded, amountsVisible)
            }
            MyFinHubAmountText(
                if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                if (account.balance >= 0) FinanceTone.Income else FinanceTone.Expense,
                style = MaterialTheme.typography.headlineMedium,
            )
            if (amountsVisible && account.balanceTrend.size > 1) {
                HomeAccountTrendChart(account)
            } else {
                Text("Η εξέλιξη υπολοίπου είναι κρυφή", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (account.primaryRole == HomePrimaryRole.SAVINGS) {
                val goal = account.savingsTarget
                val rate = account.savingsTargetRate
                Text(
                    text = when {
                        goal != null -> "Στόχος: ${if (amountsVisible) formatHomeEuro(goal) else "•••• €"}"
                        rate != null -> "Στόχος αποταμίευσης: ${(rate * 100).toInt()}% εσόδων"
                        else -> "Δεν έχει οριστεί στόχος"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (account.previousMonthTrend.size > 1) {
                    Text("Τρέχων μήνας · Προηγούμενος μήνας", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                account.balanceChange30d?.let { change ->
                    Text("Μεταβολή 30 ημερών: " +
                        if (!amountsVisible) "•••• €"
                        else (if (change > 0) "+" else "") + formatHomeEuro(change),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                Text(if (account.primaryRole == HomePrimaryRole.SAVINGS) "Μεταφορά / Κινήσεις" else "Συναλλαγές")
            }
        }
    }
}

@Composable
private fun HomeAccountTrendChart(account: HomeAccount) {
    val mainColor = when (account.primaryRole) {
        HomePrimaryRole.CASH -> MaterialTheme.colorScheme.tertiary
        HomePrimaryRole.PAYROLL -> MaterialTheme.colorScheme.secondary
        HomePrimaryRole.SAVINGS, null -> MaterialTheme.colorScheme.primary
    }
    val comparisonColor = MaterialTheme.colorScheme.outline
    val values = account.balanceTrend
    val comparison = account.previousMonthTrend
    val rawTarget = account.savingsTarget
    Canvas(
        modifier = Modifier.fillMaxWidth().height(80.dp)
            .semantics { contentDescription = "Εξέλιξη υπολοίπου ${account.name}" },
    ) {
        val original = values + comparison
        if (values.size < 2 || original.isEmpty()) return@Canvas
        val low = original.minOrNull() ?: 0.0
        val high = original.maxOrNull() ?: 0.0
        val observedSpan = maxOf(1.0, high - low)
        // Web accountChartTarget: don't squash an entire curve for a remote target.
        val allowance = maxOf(observedSpan * 2.5, maxOf(kotlin.math.abs(low), kotlin.math.abs(high), 1.0) * 0.08)
        val target = rawTarget?.takeIf { it.isFinite() && it >= low - allowance && it <= high + allowance }
        val all = original + listOfNotNull(target)
        val minimum = (all.minOrNull() ?: 0.0) - observedSpan * .12
        val maximum = (all.maxOrNull() ?: 0.0) + observedSpan * .12
        val span = maxOf(1.0, maximum - minimum)
        val horizontalPad = 3.dp.toPx()
        val verticalPad = 4.dp.toPx()
        val availableWidth = (size.width - 2 * horizontalPad).coerceAtLeast(1f)
        val availableHeight = (size.height - 2 * verticalPad).coerceAtLeast(1f)
        fun x(index: Int): Float =
            horizontalPad + availableWidth * index / (values.size - 1)
        fun y(value: Double): Float =
            verticalPad + ((maximum - value) / span * availableHeight).toFloat()
        val boundary = account.monthBoundaryIndex.coerceIn(0, values.lastIndex)
        val boundaryX = x(boundary)
        fun drawCurve(indices: IntRange, color: androidx.compose.ui.graphics.Color, dashed: Boolean = false) {
            if (indices.count() < 2) return
            val path = Path()
            indices.forEachIndexed { index, i ->
                if (index == 0) path.moveTo(x(i), y(values[i])) else path.lineTo(x(i), y(values[i]))
            }
            drawPath(path, color, style = Stroke(
                width = 2.6.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
            ))
        }
        if (boundary > 0 && boundaryX > horizontalPad + 1f) {
            drawLine(comparisonColor.copy(alpha = .35f),
                androidx.compose.ui.geometry.Offset(boundaryX, 0f),
                androidx.compose.ui.geometry.Offset(boundaryX, size.height), 1.dp.toPx())
        }
        if (comparison.size > 1) {
            val chartWidth = (size.width - horizontalPad - boundaryX).coerceAtLeast(1f)
            val line = Path()
            comparison.forEachIndexed { index, value ->
                val xp = boundaryX + chartWidth * index / (comparison.size - 1)
                if (index == 0) line.moveTo(xp, y(value)) else line.lineTo(xp, y(value))
            }
            drawPath(line, comparisonColor, style = Stroke(2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))))
        }
        target?.let {
            drawLine(mainColor.copy(alpha = .55f),
                androidx.compose.ui.geometry.Offset(boundaryX, y(it)),
                androidx.compose.ui.geometry.Offset(size.width - horizontalPad, y(it)),
                strokeWidth = 1.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }
        if (boundary > 1) drawCurve(0 until boundary, mainColor.copy(alpha = .65f))
        if (boundary in 1 until values.size) {
            drawLine(mainColor.copy(alpha = .75f),
                androidx.compose.ui.geometry.Offset(x(boundary - 1), y(values[boundary - 1])),
                androidx.compose.ui.geometry.Offset(x(boundary), y(values[boundary])),
                strokeWidth = 2.dp.toPx())
        }
        drawCurve(boundary until values.size, mainColor)
        drawCircle(mainColor, radius = 2.35.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(x(values.lastIndex), y(values.last())))
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
    val provider = financialProvider(account.providerId ?: account.id, account.institution ?: account.name)
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
