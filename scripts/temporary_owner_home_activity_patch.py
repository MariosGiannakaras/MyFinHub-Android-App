from pathlib import Path


def must_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing expected fragment: {label}")
    return text.replace(old, new, 1)


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        raise SystemExit(f"missing start marker: {label}")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise SystemExit(f"missing end marker: {label}")
    return text[:start_index] + replacement + text[end_index:]


# Home state: mark desktop-equivalent primary accounts and carry a canonical per-account mini trend.
path = Path("app/src/main/java/app/myfinhub/android/feature/home/HomeUiState.kt")
text = path.read_text()
text = must_replace(
    text,
    '''data class HomeAccount(\n    val id: String,\n    val name: String,\n    val role: String,\n    val balance: Double,\n    val group: HomeAccountGroup,\n)''',
    '''data class HomeAccount(\n    val id: String,\n    val name: String,\n    val role: String,\n    val balance: Double,\n    val group: HomeAccountGroup,\n    val isPrimary: Boolean = false,\n    val balanceTrend: List<Double> = listOf(balance, balance),\n)''',
    "HomeAccount trend fields",
)
path.write_text(text)


# Activity state: account is the only explicit list filter exposed by the redesigned screen.
path = Path("app/src/main/java/app/myfinhub/android/feature/activity/ActivityUiState.kt")
text = path.read_text()
text = must_replace(
    text,
    '''data class ActivityCategoryOption(\n    val name: String,\n    val subcategories: List<String> = emptyList(),\n)\n''',
    '''data class ActivityCategoryOption(\n    val name: String,\n    val subcategories: List<String> = emptyList(),\n)\n\ndata class ActivityAccountOption(\n    val id: String,\n    val label: String,\n)\n''',
    "ActivityAccountOption",
)
text = must_replace(
    text,
    '''data class ActivityUiState(\n    val query: String = "",\n    val filter: ActivityFilter = ActivityFilter.ALL,\n    val selectedId: String? = null,\n    val items: List<ActivityItem> = syntheticActivityItems(),\n    val expenseCategories: List<ActivityCategoryOption> = emptyList(),\n    val incomeCategories: List<ActivityCategoryOption> = emptyList(),\n) {''',
    '''data class ActivityUiState(\n    val query: String = "",\n    val filter: ActivityFilter = ActivityFilter.ALL,\n    val accountFilterId: String? = null,\n    val selectedId: String? = null,\n    val items: List<ActivityItem> = syntheticActivityItems(),\n    val expenseCategories: List<ActivityCategoryOption> = emptyList(),\n    val incomeCategories: List<ActivityCategoryOption> = emptyList(),\n    val accountOptions: List<ActivityAccountOption> = emptyList(),\n) {''',
    "ActivityUiState account filter fields",
)
text = must_replace(
    text,
    '''            val searchableAmount = item.amount.toString()\n            val matchesQuery = needle.isBlank() ||''',
    '''            val matchesAccount = accountFilterId == null ||\n                item.accountId == accountFilterId ||\n                item.fromAccountId == accountFilterId ||\n                item.toAccountId == accountFilterId\n            val searchableAmount = item.amount.toString()\n            val matchesQuery = needle.isBlank() ||''',
    "Activity account match",
)
text = must_replace(
    text,
    '''            matchesFilter && matchesQuery\n''',
    '''            matchesFilter && matchesAccount && matchesQuery\n''',
    "Activity visible predicate",
)
text = must_replace(
    text,
    '''sealed interface ActivityAction {\n    data class QueryChanged(val value: String) : ActivityAction\n    data class FilterChanged(val value: ActivityFilter) : ActivityAction\n    data class Select(val id: String?) : ActivityAction\n''',
    '''sealed interface ActivityAction {\n    data class QueryChanged(val value: String) : ActivityAction\n    data class FilterChanged(val value: ActivityFilter) : ActivityAction\n    data class AccountFilterChanged(val accountId: String?) : ActivityAction\n    data class Select(val id: String?) : ActivityAction\n''',
    "Activity account action",
)
text = must_replace(
    text,
    '''fun reduceActivity(state: ActivityUiState, action: ActivityAction): ActivityUiState = when (action) {\n    is ActivityAction.QueryChanged -> state.copy(query = action.value)\n    is ActivityAction.FilterChanged -> state.copy(filter = action.value)\n    is ActivityAction.Select -> state.copy(selectedId = action.id)\n''',
    '''fun reduceActivity(state: ActivityUiState, action: ActivityAction): ActivityUiState = when (action) {\n    is ActivityAction.QueryChanged -> state.copy(query = action.value)\n    is ActivityAction.FilterChanged -> state.copy(filter = action.value)\n    is ActivityAction.AccountFilterChanged -> state.copy(accountFilterId = action.accountId)\n    is ActivityAction.Select -> state.copy(selectedId = action.id)\n''',
    "Activity reducer account action",
)
path.write_text(text)


# Canonical projection: mirror the desktop dashboard's three primary account IDs, precompute a
# seven-day account balance sparkline, and project account options for Activity.
path = Path("app/src/main/java/app/myfinhub/android/app/CanonicalProductProjection.kt")
text = path.read_text()
text = must_replace(
    text,
    '''import app.myfinhub.android.feature.activity.ActivityCategoryOption\n''',
    '''import app.myfinhub.android.feature.activity.ActivityAccountOption\nimport app.myfinhub.android.feature.activity.ActivityCategoryOption\n''',
    "ActivityAccountOption import",
)
text = must_replace(
    text,
    '''fun projectCanonicalProduct(\n''',
    '''private val PRIMARY_HOME_ACCOUNT_IDS = listOf("cash", "piraeus-payroll", "piraeus-savings")\n\nfun projectCanonicalProduct(\n''',
    "primary home account constants",
)
old_home = '''    val oldHome = previous?.homeState\n    val homeAccounts = accounts.filter { it.kind != "credit" }.map { account ->\n        HomeAccount(\n            id = account.id,\n            name = account.name,\n            role = account.shortName ?: accountKindLabel(account.kind),\n            balance = balances[account.id] ?: 0.0,\n            group = if (account.kind == "savings" || account.excludeFromAvailable) {\n                HomeAccountGroup.SAVINGS\n            } else {\n                HomeAccountGroup.LIQUID\n            },\n        )\n    }\n'''
new_home = '''    val oldHome = previous?.homeState\n    val primaryHomeAccountIds = PRIMARY_HOME_ACCOUNT_IDS.toSet()\n    val homeTrendBalances = (6L downTo 0L).map { offset ->\n        document.accountBalances(today.minusDays(offset).toString())\n    }\n    val rawHomeAccounts = accounts.filter { it.kind != "credit" }.map { account ->\n        HomeAccount(\n            id = account.id,\n            name = account.name,\n            role = account.shortName ?: accountKindLabel(account.kind),\n            balance = balances[account.id] ?: 0.0,\n            group = if (account.kind == "savings" || account.excludeFromAvailable) {\n                HomeAccountGroup.SAVINGS\n            } else {\n                HomeAccountGroup.LIQUID\n            },\n            isPrimary = account.id in primaryHomeAccountIds,\n            balanceTrend = homeTrendBalances.map { dailyBalances -> dailyBalances[account.id] ?: 0.0 },\n        )\n    }\n    val homeAccountsById = rawHomeAccounts.associateBy(HomeAccount::id)\n    val homeAccounts = PRIMARY_HOME_ACCOUNT_IDS.mapNotNull(homeAccountsById::get) +\n        rawHomeAccounts.filterNot { it.id in primaryHomeAccountIds }\n'''
text = must_replace(text, old_home, new_home, "canonical home accounts")
old_activity = '''    val oldActivity = previous?.activityState\n    val activity = ActivityUiState(\n        query = oldActivity?.query.orEmpty(),\n        filter = oldActivity?.filter ?: ActivityFilter.ALL,\n        selectedId = oldActivity?.selectedId?.takeIf { id -> activityItems.any { it.id == id } },\n        items = activityItems,\n        expenseCategories = quickEntry.expenseCategories.map { ActivityCategoryOption(it.name, it.subcategories) },\n        incomeCategories = quickEntry.incomeCategories.map { ActivityCategoryOption(it.name, it.subcategories) },\n    )\n'''
new_activity = '''    val oldActivity = previous?.activityState\n    val activityAccountOptions = accounts\n        .filter { it.kind != "credit" }\n        .map { account -> ActivityAccountOption(account.id, account.name) }\n    val activity = ActivityUiState(\n        query = oldActivity?.query.orEmpty(),\n        filter = ActivityFilter.ALL,\n        accountFilterId = oldActivity?.accountFilterId?.takeIf { selectedId ->\n            activityAccountOptions.any { it.id == selectedId }\n        },\n        selectedId = oldActivity?.selectedId?.takeIf { id -> activityItems.any { it.id == id } },\n        items = activityItems,\n        expenseCategories = quickEntry.expenseCategories.map { ActivityCategoryOption(it.name, it.subcategories) },\n        incomeCategories = quickEntry.incomeCategories.map { ActivityCategoryOption(it.name, it.subcategories) },\n        accountOptions = activityAccountOptions,\n    )\n'''
text = must_replace(text, old_activity, new_activity, "canonical activity accounts")
path.write_text(text)


# Activity UI: transactions only. Keep search + account filter, remove net/results summary and kind chips.
path = Path("app/src/main/java/app/myfinhub/android/feature/activity/ActivityScreen.kt")
text = path.read_text()
text = text.replace("import androidx.compose.foundation.layout.FlowRow\n", "")
text = must_replace(
    text,
    '''                subtitle = "Η οικονομική σου δραστηριότητα",\n''',
    '''                subtitle = "Όλες οι καταχωρισμένες κινήσεις",\n''',
    "Activity subtitle",
)
text = must_replace(
    text,
    '''        item {\n            ActivityProjectionSummary(state = state)\n        }\n''',
    "",
    "Activity summary item",
)
flow_start = '''        item {\n            FlowRow('''
flow_end = '''        if (state.visibleItems.isEmpty()) {'''
replacement = '''        if (state.accountOptions.isNotEmpty()) {\n            item {\n                ActivityAccountFilterSelector(\n                    selectedId = state.accountFilterId,\n                    options = state.accountOptions,\n                    onSelected = { onAction(ActivityAction.AccountFilterChanged(it)) },\n                )\n            }\n        }\n'''
text = replace_between(text, flow_start, flow_end, replacement, "Activity type filters")
summary_start = '''@Composable\nprivate fun ActivityProjectionSummary'''
summary_end = '''@Composable\nprivate fun ActivityMonthHeader'''
selector = '''@Composable\nprivate fun ActivityAccountFilterSelector(\n    selectedId: String?,\n    options: List<ActivityAccountOption>,\n    onSelected: (String?) -> Unit,\n) {\n    var expanded by rememberSaveable { mutableStateOf(false) }\n    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: "Όλοι οι λογαριασμοί"\n    Box {\n        MyFinHubSelectorButton(\n            label = "Λογαριασμός",\n            onClick = { expanded = true },\n            enabled = options.isNotEmpty(),\n        ) {\n            Text(selectedLabel, modifier = Modifier.weight(1f))\n        }\n        DropdownMenu(\n            expanded = expanded,\n            onDismissRequest = { expanded = false },\n        ) {\n            DropdownMenuItem(\n                text = { Text("Όλοι οι λογαριασμοί") },\n                onClick = {\n                    expanded = false\n                    onSelected(null)\n                },\n            )\n            options.forEach { option ->\n                DropdownMenuItem(\n                    text = { Text(option.label) },\n                    onClick = {\n                        expanded = false\n                        onSelected(option.id)\n                    },\n                )\n            }\n        }\n    }\n}\n\n'''
text = replace_between(text, summary_start, summary_end, selector, "Activity summary functions")
path.write_text(text)


# Home UI: accounts are the content, exactly like the desktop dashboard hierarchy. No total balance,
# month-flow duplicate, or recent-activity duplicate on Home.
path = Path("app/src/main/java/app/myfinhub/android/feature/home/ProductionHomeScreen.kt")
text = path.read_text()
text = must_replace(text, "import androidx.compose.foundation.clickable\n", "import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.clickable\n", "Canvas import")
text = must_replace(text, "import androidx.compose.foundation.layout.fillMaxWidth\n", "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height\n", "height import")
text = must_replace(text, "import androidx.compose.ui.Alignment\n", "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.Path\nimport androidx.compose.ui.graphics.StrokeCap\nimport androidx.compose.ui.graphics.StrokeJoin\nimport androidx.compose.ui.graphics.drawscope.Stroke\n", "graphics imports")
text = must_replace(text, "import androidx.compose.ui.text.font.FontWeight\n", "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.dp\n", "dp import")
text = must_replace(
    text,
    '''    onOpenAccount: (String) -> Unit = {},\n    onOpenRecent: (String) -> Unit = {},\n) {''',
    '''    onOpenAccount: (String) -> Unit = {},\n    @Suppress("UNUSED_PARAMETER") onOpenRecent: (String) -> Unit = {},\n) {''',
    "unused recent parameter",
)
text = must_replace(
    text,
    '''    Scaffold(\n''',
    '''    val explicitlyPrimary = state.accounts.filter(HomeAccount::isPrimary)\n    val primaryAccounts = (if (explicitlyPrimary.isNotEmpty()) explicitlyPrimary else state.accounts.take(3)).take(3)\n    val primaryIds = primaryAccounts.map(HomeAccount::id).toSet()\n    val secondaryAccounts = state.accounts.filterNot { it.id in primaryIds }\n\n    Scaffold(\n''',
    "home account grouping",
)
text = must_replace(
    text,
    '''                subtitle = "Η οικονομική σου εικόνα σήμερα",\n''',
    '''                subtitle = "Οι λογαριασμοί μου",\n''',
    "home subtitle",
)
old_items = '''        ) {\n            item { FinancialSnapshotCard(state = state, amountsVisible = amountsVisible, onOpenQuickEntry = onOpenQuickEntry) }\n            if (state.attentionItems.isNotEmpty()) item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }\n            if (state.upcomingItems.isNotEmpty()) item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }\n            item { MonthContextCard(state = state, amountsVisible = amountsVisible) }\n            item { RecentActivityCard(state.recentItems, amountsVisible, onOpenRecent) }\n            item { PrimaryAccountsCard(state.accounts.take(2), amountsVisible, onOpenAccount) }\n        }\n'''
new_items = '''        ) {\n            item {\n                PrimaryAccountsSection(\n                    accounts = primaryAccounts,\n                    amountsVisible = amountsVisible,\n                    onOpenAccount = onOpenAccount,\n                    onOpenQuickEntry = onOpenQuickEntry,\n                )\n            }\n            if (secondaryAccounts.isNotEmpty()) {\n                item { SecondaryAccountsCard(secondaryAccounts, amountsVisible, onOpenAccount) }\n            }\n            if (state.attentionItems.isNotEmpty()) item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }\n            if (state.upcomingItems.isNotEmpty()) item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }\n        }\n'''
text = must_replace(text, old_items, new_items, "home content order")
home_functions_start = '''@Composable\nprivate fun FinancialSnapshotCard'''
home_functions_end = '''@Composable\nprivate fun ProductionAttentionCard'''
new_home_functions = '''@Composable\nprivate fun PrimaryAccountsSection(\n    accounts: List<HomeAccount>,\n    amountsVisible: Boolean,\n    onOpenAccount: (String) -> Unit,\n    onOpenQuickEntry: () -> Unit,\n) {\n    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {\n        Row(\n            modifier = Modifier.fillMaxWidth(),\n            horizontalArrangement = Arrangement.SpaceBetween,\n            verticalAlignment = Alignment.CenterVertically,\n        ) {\n            Column {\n                Text("Κύριοι λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)\n                Text(\n                    "Οι 3 λογαριασμοί που χρησιμοποιείς περισσότερο",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n                )\n            }\n            TextButton(onClick = onOpenQuickEntry) { Text("Νέα κίνηση") }\n        }\n        if (accounts.isEmpty()) {\n            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {\n                Text("Δεν υπάρχουν διαθέσιμοι λογαριασμοί.", color = MaterialTheme.colorScheme.onSurfaceVariant)\n            }\n        } else {\n            accounts.forEach { account ->\n                PrimaryAccountCard(account, amountsVisible, onOpenAccount)\n            }\n        }\n    }\n}\n\n@Composable\nprivate fun PrimaryAccountCard(\n    account: HomeAccount,\n    amountsVisible: Boolean,\n    onOpenAccount: (String) -> Unit,\n) {\n    val savings = account.group == HomeAccountGroup.SAVINGS\n    val delta = if (account.balanceTrend.size >= 2) {\n        account.balanceTrend.last() - account.balanceTrend.first()\n    } else {\n        0.0\n    }\n    MyFinHubSectionCard(\n        modifier = Modifier\n            .fillMaxWidth()\n            .clickable { onOpenAccount(account.id) }\n            .semantics(mergeDescendants = true) {\n                contentDescription = if (amountsVisible) {\n                    "${account.name}, ${formatHomeEuro(account.balance)}"\n                } else {\n                    "${account.name}, ποσό κρυφό"\n                }\n            },\n    ) {\n        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {\n            Row(\n                modifier = Modifier.fillMaxWidth(),\n                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),\n                verticalAlignment = Alignment.CenterVertically,\n            ) {\n                MyFinHubIconBadge(\n                    icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,\n                    tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,\n                    contentDescription = null,\n                )\n                Column(modifier = Modifier.weight(1f)) {\n                    Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)\n                    Text(account.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                }\n                MyFinHubAmountText(\n                    text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",\n                    tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense,\n                    style = MaterialTheme.typography.titleLarge,\n                )\n            }\n            AccountSparkline(values = account.balanceTrend)\n            Text(\n                text = if (amountsVisible && account.balanceTrend.size >= 2) {\n                    "Τάση 7 ημερών · ${formatSignedHomeEuro(delta)}"\n                } else {\n                    "Τάση 7 ημερών"\n                },\n                style = MaterialTheme.typography.labelMedium,\n                color = MaterialTheme.colorScheme.onSurfaceVariant,\n            )\n        }\n    }\n}\n\n@Composable\nprivate fun AccountSparkline(values: List<Double>) {\n    val lineColor = MaterialTheme.colorScheme.primary\n    val guideColor = MaterialTheme.colorScheme.outlineVariant\n    Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {\n        if (values.size < 2) {\n            drawLine(\n                color = guideColor,\n                start = Offset(0f, size.height / 2f),\n                end = Offset(size.width, size.height / 2f),\n                strokeWidth = 1.dp.toPx(),\n            )\n            return@Canvas\n        }\n        val minimum = values.minOrNull() ?: 0.0\n        val maximum = values.maxOrNull() ?: minimum\n        val range = (maximum - minimum).takeIf { it > 0.005 } ?: 1.0\n        val path = Path()\n        values.forEachIndexed { index, value ->\n            val x = size.width * index / values.lastIndex.toFloat()\n            val normalized = ((value - minimum) / range).toFloat()\n            val y = size.height - normalized * size.height\n            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)\n        }\n        drawLine(\n            color = guideColor,\n            start = Offset(0f, size.height),\n            end = Offset(size.width, size.height),\n            strokeWidth = 1.dp.toPx(),\n        )\n        drawPath(\n            path = path,\n            color = lineColor,\n            style = Stroke(\n                width = 2.dp.toPx(),\n                cap = StrokeCap.Round,\n                join = StrokeJoin.Round,\n            ),\n        )\n    }\n}\n\n@Composable\nprivate fun SecondaryAccountsCard(\n    accounts: List<HomeAccount>,\n    amountsVisible: Boolean,\n    onOpenAccount: (String) -> Unit,\n) {\n    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {\n        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {\n            Text("Δευτερεύοντες λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)\n            accounts.forEachIndexed { index, account ->\n                val savings = account.group == HomeAccountGroup.SAVINGS\n                Row(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .clickable { onOpenAccount(account.id) }\n                        .semantics(mergeDescendants = true) {\n                            contentDescription = if (amountsVisible) {\n                                "${account.name}, ${formatHomeEuro(account.balance)}"\n                            } else {\n                                "${account.name}, ποσό κρυφό"\n                            }\n                        },\n                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),\n                    verticalAlignment = Alignment.CenterVertically,\n                ) {\n                    MyFinHubIconBadge(\n                        icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,\n                        tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,\n                        contentDescription = null,\n                    )\n                    Column(modifier = Modifier.weight(1f)) {\n                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)\n                        Text(account.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                    }\n                    MyFinHubAmountText(\n                        text = if (amountsVisible) formatHomeEuro(account.balance) else "•••• €",\n                        tone = if (account.balance >= 0.0) FinanceTone.Income else FinanceTone.Expense,\n                        style = MaterialTheme.typography.titleMedium,\n                    )\n                }\n                if (index != accounts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)\n            }\n        }\n    }\n}\n\n'''
text = replace_between(text, home_functions_start, home_functions_end, new_home_functions, "home summary functions")
text = must_replace(
    text,
    '''private fun formatHomeEuro(value: Double): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)\n''',
    '''private fun formatHomeEuro(value: Double): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)\n\nprivate fun formatSignedHomeEuro(value: Double): String =\n    (if (value > 0.005) "+" else "") + formatHomeEuro(value)\n''',
    "signed home money",
)
path.write_text(text)


# Focused regression tests for the owner's two requirements.
path = Path("app/src/test/java/app/myfinhub/android/feature/activity/ActivityReducerTest.kt")
text = path.read_text()
insert = '''\n    @Test\n    fun accountFilter_keepsDirectAndTransferAccountActivity() {\n        val items = listOf(\n            ActivityItem(\n                id = "direct",\n                dateLabel = "Σήμερα",\n                kind = ActivityKind.EXPENSE,\n                title = "Άμεση",\n                subtitle = "",\n                amount = -10.0,\n                accountLabel = "Κύριος",\n                category = null,\n                rawDate = "2026-09-07",\n                accountId = "main",\n            ),\n            ActivityItem(\n                id = "transfer",\n                dateLabel = "Σήμερα",\n                kind = ActivityKind.TRANSFER,\n                title = "Μεταφορά",\n                subtitle = "",\n                amount = 20.0,\n                accountLabel = "Κύριος → Αποταμίευση",\n                category = null,\n                rawDate = "2026-09-07",\n                fromAccountId = "main",\n                toAccountId = "savings",\n            ),\n            ActivityItem(\n                id = "other",\n                dateLabel = "Σήμερα",\n                kind = ActivityKind.EXPENSE,\n                title = "Άλλος",\n                subtitle = "",\n                amount = -5.0,\n                accountLabel = "Μετρητά",\n                category = null,\n                rawDate = "2026-09-07",\n                accountId = "cash",\n            ),\n        )\n        val initial = ActivityUiState(\n            items = items,\n            accountOptions = listOf(ActivityAccountOption("main", "Κύριος")),\n        )\n\n        val filtered = reduceActivity(initial, ActivityAction.AccountFilterChanged("main"))\n\n        assertEquals(listOf("direct", "transfer"), filtered.visibleItems.map { it.id })\n    }\n'''
if insert.strip() not in text:
    text = text.replace("\n}", insert + "\n}", 1)
path.write_text(text)

path = Path("app/src/test/java/app/myfinhub/android/app/CanonicalProductProjectionTest.kt")
text = path.read_text()
text = must_replace(
    text,
    '''        assertEquals(1_155.0, projection.homeState.accounts.first { it.id == "acc-main" }.balance, 0.001)\n''',
    '''        assertEquals(1_155.0, projection.homeState.accounts.first { it.id == "acc-main" }.balance, 0.001)\n        assertEquals(7, projection.homeState.accounts.first { it.id == "acc-main" }.balanceTrend.size)\n''',
    "home trend projection test",
)
path.write_text(text)

print("owner Home/Activity redesign patch applied")
