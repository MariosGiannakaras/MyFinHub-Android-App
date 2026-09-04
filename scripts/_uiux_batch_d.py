from pathlib import Path

# 1) Route.
p = Path('app/src/main/java/app/myfinhub/android/app/AppRoute.kt')
t = p.read_text()
old = '    @Serializable data object Money : AppRoute\n    @Serializable data class CardDetail(val cardId: String) : AppRoute'
new = '    @Serializable data object Money : AppRoute\n    @Serializable data class AccountDetail(val accountId: String) : AppRoute\n    @Serializable data class CardDetail(val cardId: String) : AppRoute'
assert t.count(old) == 1, 'account route guard failed'
p.write_text(t.replace(old, new))

# 2) Pure account -> activity projection preserves canonical order.
p = Path('app/src/main/java/app/myfinhub/android/feature/money/AccountActivityProjection.kt')
assert not p.exists(), 'account activity projection already exists'
p.write_text('''package app.myfinhub.android.feature.money

import app.myfinhub.android.feature.activity.ActivityItem

internal fun accountActivityItems(
    accountId: String,
    items: List<ActivityItem>,
): List<ActivityItem> {
    val id = accountId.trim()
    if (id.isBlank()) return emptyList()
    return items.filter { item ->
        item.accountId == id || item.fromAccountId == id || item.toAccountId == id
    }
}
''')

# 3) Account detail screen using the same ActivityItem data and amount-visibility preference.
p = Path('app/src/main/java/app/myfinhub/android/feature/money/CanonicalAccountDetailScreen.kt')
assert not p.exists(), 'account detail screen already exists'
p.write_text('''package app.myfinhub.android.feature.money

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubFinanceRow
import app.myfinhub.android.designsystem.MyFinHubIcons
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
                title = account?.name ?: "Λογαριασμός",
                subtitle = account?.kind ?: "Λεπτομέρειες λογαριασμού",
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
                            "Οι παρακάτω κινήσεις είναι μόνο όσες επηρεάζουν αυτόν τον λογαριασμό.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    "Κινήσεις λογαριασμού",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (activityItems.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Δεν υπάρχουν κινήσεις για αυτόν τον λογαριασμό.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val sections = activityItems.groupBy { it.rawDate.take(10) }.toList()
                sections.forEach { (date, sectionItems) ->
                    item(key = "account-day-$date") {
                        Text(
                            accountDayLabel(date),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(sectionItems, key = ActivityItem::id) { item ->
                        MyFinHubFinanceRow(
                            icon = myFinHubCategoryIcon(item.category, accountActivityIcon(item.kind)),
                            iconDescription = item.category ?: item.kind.label,
                            title = item.title,
                            subtitle = item.subtitle,
                            meta = if (item.pendingSync) "Εκκρεμεί επιβεβαίωση" else item.accountLabel,
                            amountText = if (amountsVisible) formatSignedAccountEuro(item.amount) else "•••• €",
                            tone = if (item.pendingSync) FinanceTone.Neutral else accountActivityTone(item.kind),
                            onClick = { onOpenActivity(item.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun accountDayLabel(rawDate: String): String {
    val date = runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: return rawDate
    val today = LocalDate.now()
    if (date == today) return "Σήμερα"
    if (date == today.minusDays(1)) return "Χθες"
    return date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("el-GR")))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("el-GR")) else it.toString() }
}

private fun accountActivityIcon(kind: ActivityKind) = when (kind) {
    ActivityKind.INCOME -> MyFinHubIcons.Income
    ActivityKind.EXPENSE -> MyFinHubIcons.Expense
    ActivityKind.TRANSFER -> MyFinHubIcons.Transfer
    ActivityKind.CARD -> MyFinHubIcons.Card
    ActivityKind.SAVINGS -> MyFinHubIcons.Savings
    ActivityKind.REFUND -> MyFinHubIcons.Income
    ActivityKind.LENDING -> MyFinHubIcons.Account
    ActivityKind.RECONCILIATION -> MyFinHubIcons.Account
    ActivityKind.SPLIT -> MyFinHubIcons.Expense
}

private fun accountActivityTone(kind: ActivityKind) = when (kind) {
    ActivityKind.INCOME, ActivityKind.REFUND -> FinanceTone.Income
    ActivityKind.EXPENSE, ActivityKind.CARD, ActivityKind.SPLIT -> FinanceTone.Expense
    ActivityKind.TRANSFER -> FinanceTone.Transfer
    ActivityKind.SAVINGS -> FinanceTone.Savings
    ActivityKind.LENDING -> FinanceTone.Attention
    ActivityKind.RECONCILIATION -> FinanceTone.Neutral
}

private fun formatAccountEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedAccountEuro(value: Double): String {
    val sign = if (value > 0.005) "+" else ""
    return sign + formatAccountEuro(value)
}
''')

# 4) Production Home: make accounts and recent transactions actionable.
p = Path('app/src/main/java/app/myfinhub/android/feature/home/ProductionHomeScreen.kt')
t = p.read_text()
old = 'import androidx.compose.foundation.layout.Arrangement\n'
new = 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.Arrangement\n'
assert t.count(old) == 1, 'home clickable import guard failed'
t = t.replace(old, new)
old = '''    onOpenSettings: () -> Unit,
    onOpenQuickEntry: () -> Unit,
) {'''
new = '''    onOpenSettings: () -> Unit,
    onOpenQuickEntry: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenRecent: (String) -> Unit,
) {'''
assert t.count(old) == 1, 'home signature guard failed'
t = t.replace(old, new)
old = '            item { PrimaryAccountsCard(state.accounts.take(3), amountsVisible) }\n            item { RecentActivityCard(state.recentItems, amountsVisible) }'
new = '            item { PrimaryAccountsCard(state.accounts.take(3), amountsVisible, onOpenAccount) }\n            item { RecentActivityCard(state.recentItems, amountsVisible, onOpenRecent) }'
assert t.count(old) == 1, 'home card calls guard failed'
t = t.replace(old, new)
old = 'private fun PrimaryAccountsCard(accounts: List<HomeAccount>, amountsVisible: Boolean) {'
new = 'private fun PrimaryAccountsCard(accounts: List<HomeAccount>, amountsVisible: Boolean, onOpenAccount: (String) -> Unit) {'
assert t.count(old) == 1, 'home account card signature guard failed'
t = t.replace(old, new)
old = '''                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {'''
new = '''                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAccount(account.id) }
                            .semantics(mergeDescendants = true) {'''
assert t.count(old) == 1, 'home account clickable guard failed'
t = t.replace(old, new)
old = 'private fun RecentActivityCard(items: List<HomeRecentItem>, amountsVisible: Boolean) {'
new = 'private fun RecentActivityCard(items: List<HomeRecentItem>, amountsVisible: Boolean, onOpenRecent: (String) -> Unit) {'
assert t.count(old) == 1, 'home recent signature guard failed'
t = t.replace(old, new)
old = '                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},'
new = '                        modifier = Modifier.fillMaxWidth().clickable { onOpenRecent(item.id) }.semantics(mergeDescendants = true) {},'
assert t.count(old) == 1, 'home recent clickable guard failed'
t = t.replace(old, new)
p.write_text(t)

# 5) Money: account rows become actionable.
p = Path('app/src/main/java/app/myfinhub/android/feature/money/CanonicalMoneyScreens.kt')
t = p.read_text()
old = 'import androidx.compose.foundation.layout.Arrangement\n'
new = 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.Arrangement\n'
assert t.count(old) == 1, 'money clickable import guard failed'
t = t.replace(old, new)
old = '''    onOpenCard: (String) -> Unit,
    onOpenSavings: () -> Unit,'''
new = '''    onOpenCard: (String) -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenSavings: () -> Unit,'''
assert t.count(old) == 1, 'money signature guard failed'
t = t.replace(old, new)
old = '                                    modifier = Modifier.fillMaxWidth(),\n                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),'
new = '                                    modifier = Modifier.fillMaxWidth().clickable { onOpenAccount(account.id) },\n                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),'
assert t.count(old) == 1, 'money account clickable guard failed'
t = t.replace(old, new)
p.write_text(t)

# 6) App wiring: account route and generic ActivityDetail back stack.
p = Path('app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt')
t = p.read_text()
old = 'import app.myfinhub.android.feature.money.CanonicalCardDetailScreen\n'
new = 'import app.myfinhub.android.feature.money.CanonicalAccountDetailScreen\nimport app.myfinhub.android.feature.money.CanonicalCardDetailScreen\nimport app.myfinhub.android.feature.money.accountActivityItems\n'
assert t.count(old) == 1, 'app account import guard failed'
t = t.replace(old, new)
old = '''                            onOpenSettings = { homeBackStack.pushIfNew(AppRoute.Settings) },
                            onOpenQuickEntry = { openFastExpense(homeBackStack) },
                        )'''
new = '''                            onOpenSettings = { homeBackStack.pushIfNew(AppRoute.Settings) },
                            onOpenQuickEntry = { openFastExpense(homeBackStack) },
                            onOpenAccount = { accountId -> homeBackStack.pushIfNew(AppRoute.AccountDetail(accountId)) },
                            onOpenRecent = { eventId -> homeBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                        )'''
assert t.count(old) == 1, 'app home wiring guard failed'
t = t.replace(old, new)

# ActivityDetail can now be reached from Activity, Home recent, or Account detail.
t = t.replace('onBack = { activityBackStack.removeLastOrNull() },', 'onBack = { activeBackStack.removeLastOrNull() },', 1)
t = t.replace('                            activityBackStack.removeLastOrNull()\n                        },', '                            activeBackStack.removeLastOrNull()\n                        },', 1)

old = '''                            onDeleteCard = onDeleteCard,
                            onOpenCard = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardDetail(cardId)) },
                            onOpenSavings = { moneyBackStack.pushIfNew(AppRoute.Savings) },'''
new = '''                            onDeleteCard = onDeleteCard,
                            onOpenCard = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardDetail(cardId)) },
                            onOpenAccount = { accountId -> moneyBackStack.pushIfNew(AppRoute.AccountDetail(accountId)) },
                            onOpenSavings = { moneyBackStack.pushIfNew(AppRoute.Savings) },'''
assert t.count(old) == 1, 'app money wiring guard failed'
t = t.replace(old, new)

marker = '                entry<AppRoute.CardDetail> { route ->\n'
assert t.count(marker) == 1, 'account route insertion marker failed'
route = '''                entry<AppRoute.AccountDetail> { route ->
                    val account = moneyState.accounts.firstOrNull { it.id == route.accountId }
                    CanonicalAccountDetailScreen(
                        account = account,
                        activityItems = accountActivityItems(route.accountId, activityState.items),
                        onBack = { activeBackStack.removeLastOrNull() },
                        onOpenActivity = { eventId -> activeBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                    )
                }
'''
t = t.replace(marker, route + marker)
p.write_text(t)

# 7) Focused regression test.
p = Path('app/src/test/java/app/myfinhub/android/feature/money/AccountActivityProjectionTest.kt')
p.parent.mkdir(parents=True, exist_ok=True)
assert not p.exists(), 'account activity test already exists'
p.write_text('''package app.myfinhub.android.feature.money

import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountActivityProjectionTest {
    @Test
    fun filtersDirectFromAndToAccountWithoutChangingCanonicalOrder() {
        val items = listOf(
            ActivityItem("newest", "Σήμερα", ActivityKind.EXPENSE, "A", "n", -10.0, "Main", "Food", rawDate = "2026-09-04", accountId = "acc-main"),
            ActivityItem("transfer", "Σήμερα", ActivityKind.TRANSFER, "B", "n", 20.0, "Main → Save", null, rawDate = "2026-09-04", fromAccountId = "acc-main", toAccountId = "acc-save"),
            ActivityItem("other", "Χθες", ActivityKind.INCOME, "C", "n", 30.0, "Cash", null, rawDate = "2026-09-03", accountId = "acc-cash"),
            ActivityItem("older", "Χθες", ActivityKind.TRANSFER, "D", "n", 40.0, "Cash → Main", null, rawDate = "2026-09-03", fromAccountId = "acc-cash", toAccountId = "acc-main"),
        )

        assertEquals(listOf("newest", "transfer", "older"), accountActivityItems("acc-main", items).map { it.id })
    }
}
''')
