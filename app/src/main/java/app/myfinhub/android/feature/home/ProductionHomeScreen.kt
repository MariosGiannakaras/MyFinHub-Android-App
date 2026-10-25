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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
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
    onOpenSettings: () -> Unit,
    onOpenQuickEntry: () -> Unit,
    onAccountQuickEntry: ((String, HomePrimaryRole) -> Unit)? = null,
    onAccountTransactions: ((String) -> Unit)? = null,
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
                    onQuickEntry = onOpenQuickEntry,
                    onAccountQuickEntry = onAccountQuickEntry,
                    onAccountTransactions = onAccountTransactions,
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
            modifier = Modifier.fillMaxWidth().testTag("home_global_quick_entry"),
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
    ibanRecord: AccountMetadataRecord?,
    accountMetadataLoaded: Boolean,
    onQuickEntry: () -> Unit,
    onTransactions: () -> Unit,
) {
    val savings = account.primaryRole == HomePrimaryRole.SAVINGS
    val delta = account.balanceChange30d
    val color = when {
        savings -> Color(0xFF2463F4)
        account.primaryRole == HomePrimaryRole.PAYROLL -> Color(0xFFA15CFF)
        else -> Color(0xFF31C77B)
    }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            val deltaLabel = delta?.let { change ->
                val arrow = if (change > .005) "↑" else if (change < -.005) "↓" else "→"
                val prefix = if (change > .005) "+" else if (change < -.005) "−" else ""
                "30ημ. $arrow " +
                    if (amountsVisible) prefix + formatHomeEuro(kotlin.math.abs(change)) else "•••• €"
            }
            val enlarged = LocalDensity.current.fontScale >= 1.3f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                HomeAccountIdentityMark(account)
                Column(modifier = Modifier.weight(1f)) {
                    if (account.primaryRole == HomePrimaryRole.CASH) {
                        // Web renders one cash label followed by the wallet context;
                        // HomeAccountIdentity would duplicate "Μετρητά" here.
                        Text(account.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("Πορτοφόλι", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        HomeAccountIdentity(account, Modifier.fillMaxWidth())
                        AccountIbanRow(ibanRecord, accountMetadataLoaded, amountsVisible)
                    }
                }
                if (!enlarged && deltaLabel != null) {
                    Text(deltaLabel, style = MaterialTheme.typography.labelSmall,
                        color = if ((delta ?: 0.0) < -.005) MaterialTheme.colorScheme.error else color,
                        modifier = Modifier.testTag("home_account_delta_${account.id}")
                            .semantics {
                                contentDescription = if (amountsVisible) "Μεταβολή 30 ημερών: $deltaLabel"
                                    else "Μεταβολή 30 ημερών: κρυφό ποσό"
                            })
                }
            }
            if (enlarged && deltaLabel != null) {
                Text(deltaLabel, style = MaterialTheme.typography.labelMedium,
                    color = if ((delta ?: 0.0) < -.005) MaterialTheme.colorScheme.error else color,
                    modifier = Modifier.testTag("home_account_delta_${account.id}")
                        .semantics {
                            contentDescription = if (amountsVisible) "Μεταβολή 30 ημερών: $deltaLabel"
                                else "Μεταβολή 30 ημερών: κρυφό ποσό"
                        })
            }
            // Desktop Web uses a shared amount/chart zone; on compact screens
            // the amount overlays the left while the chart fills the right.
            // Monetary privacy never removes the chart or exposes EUR via semantics.
            Box(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                HomeAccountTrendChart(
                    account = account,
                    modifier = Modifier.fillMaxWidth(.67f).align(Alignment.BottomEnd),
                )
                Text(
                    if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (savings) {
                Text(
                    "Τρέχων μήνας · Προηγ. μήνας" +
                        if (account.savingsTarget != null) " · Στόχος" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when {
                        account.savingsTarget != null ->
                            "Στόχος " + if (amountsVisible) formatHomeEuro(account.savingsTarget) else "•••• €"
                        account.savingsTargetRate != null ->
                            "Στόχος ${(account.savingsTargetRate * 100).toInt()}% εσόδων"
                        else -> "Δεν έχει οριστεί στόχος"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Προηγ. μήνας · Τρέχων μήνας",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                TextButton(
                    onClick = onQuickEntry,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("home_account_quick_entry_${account.id}")
                        .semantics { contentDescription = if (savings) "Μεταφορά για ${account.name}" else "Νέα κίνηση για ${account.name}" },
                ) { Text(if (savings) "Μεταφορά" else "Νέα κίνηση") }
                TextButton(
                    onClick = onTransactions,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Συναλλαγές") }
            }
        }
    }
}

@Composable
private fun HomeAccountTrendChart(account: HomeAccount, modifier: Modifier = Modifier) {
    val mainColor = when (account.primaryRole) {
        HomePrimaryRole.CASH -> Color(0xFF31C77B)
        HomePrimaryRole.PAYROLL -> Color(0xFFA15CFF)
        HomePrimaryRole.SAVINGS, null -> Color(0xFF2463F4)
    }
    val comparisonColor = Color(0xFFA9C5FF)
    val values = account.balanceTrend
    val comparison = account.previousMonthTrend
    val rawTarget = account.savingsTarget
    Canvas(
        modifier = modifier.height(83.dp)
            .testTag("home_account_history_${account.id}")
            .semantics { contentDescription = "Εξέλιξη υπολοίπου ${account.name}" },
    ) {
        val current = if (values.size > 1) values else listOf(values.firstOrNull() ?: account.balance,
            values.firstOrNull() ?: account.balance)
        val original = current + comparison
        val low = original.minOrNull() ?: 0.0
        val high = original.maxOrNull() ?: 0.0
        val observedSpan = maxOf(1.0, high - low)
        val allowance = maxOf(observedSpan * 2.5, maxOf(kotlin.math.abs(low), kotlin.math.abs(high), 1.0) * .08)
        val target = rawTarget?.takeIf { it.isFinite() && it >= low - allowance && it <= high + allowance }
        val all = original + listOfNotNull(target)
        val rawMin = all.minOrNull() ?: 0.0
        val rawMax = all.maxOrNull() ?: 0.0
        val rawSpan = maxOf(1.0, rawMax - rawMin)
        val minimum = rawMin - rawSpan * .07
        val maximum = rawMax + rawSpan * .07
        val span = maxOf(1.0, maximum - minimum)
        val horizontalPad = 3.dp.toPx()
        val verticalPad = 3.dp.toPx()
        val availableWidth = (size.width - 2 * horizontalPad).coerceAtLeast(1f)
        val availableHeight = (size.height - 2 * verticalPad).coerceAtLeast(1f)
        fun x(index: Int): Float =
            horizontalPad + availableWidth * index / current.lastIndex
        fun y(value: Double): Float =
            verticalPad + ((maximum - value) / span * availableHeight).toFloat()
        val boundary = account.monthBoundaryIndex.coerceIn(0, current.lastIndex)
        val boundaryX = x(boundary)
        // The three selected-month guides and the month-boundary curtain
        // are read-only chart geometry, not additional canonical data.
        if (boundary > 0 && boundaryX > horizontalPad + 1f) {
            drawLine(comparisonColor.copy(alpha = .38f),
                androidx.compose.ui.geometry.Offset(boundaryX, 0f),
                androidx.compose.ui.geometry.Offset(boundaryX, size.height), 1.dp.toPx())
        }
        for (fraction in listOf(.25f, .5f, .75f)) {
            val guideX = boundaryX + (size.width - horizontalPad - boundaryX) * fraction
            drawLine(comparisonColor.copy(alpha = .20f),
                androidx.compose.ui.geometry.Offset(guideX, 3.dp.toPx()),
                androidx.compose.ui.geometry.Offset(guideX, size.height - 3.dp.toPx()),
                .8.dp.toPx())
        }
        val points = current.mapIndexed { index, value -> HomeCurvePoint(x(index), y(value)) }
        fun drawCurve(first: Int, last: Int, color: Color, width: Float = 2.35f) {
            if (last <= first) return
            val segments = homeMonotoneSegments(points, first, last)
            if (segments.isEmpty()) return
            val path = Path().apply {
                moveTo(points[first].x, points[first].y)
                segments.forEach { segment ->
                    cubicTo(segment.control1.x, segment.control1.y,
                        segment.control2.x, segment.control2.y,
                        segment.end.x, segment.end.y)
                }
            }
            drawPath(path, color, style = Stroke(width = width.dp.toPx()))
        }
        if (comparison.size > 1) {
            val overlay = comparison.mapIndexed { index, value ->
                HomeCurvePoint(boundaryX + (size.width - horizontalPad - boundaryX) *
                    index / comparison.lastIndex, y(value))
            }
            val path = Path().apply {
                moveTo(overlay.first().x, overlay.first().y)
                homeMonotoneSegments(overlay).forEach { segment ->
                    cubicTo(segment.control1.x, segment.control1.y,
                        segment.control2.x, segment.control2.y, segment.end.x, segment.end.y)
                }
            }
            drawPath(path, comparisonColor, style = Stroke(width = 1.9.dp.toPx()))
        }
        target?.let {
            drawLine(mainColor.copy(alpha = .65f),
                androidx.compose.ui.geometry.Offset(boundaryX, y(it)),
                androidx.compose.ui.geometry.Offset(size.width - horizontalPad, y(it)),
                strokeWidth = 1.3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)))
        }
        if (boundary > 1) drawCurve(0, boundary - 1, mainColor.copy(alpha = .55f))
        if (boundary > 0) drawCurve(boundary - 1, boundary, mainColor.copy(alpha = .72f), 1.8f)
        drawCurve(boundary, current.lastIndex, mainColor)
        // Web: dots for changed selected-month closing balances only.
        for (index in maxOf(1, boundary)..(current.lastIndex - 1)) {
            if (kotlin.math.abs(current[index] - current[index - 1]) > .005) {
                drawCircle(mainColor, radius = 1.35.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x(index), y(current[index])))
            }
        }
        drawCircle(mainColor, radius = 2.35.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(x(current.lastIndex), y(current.last())))
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
