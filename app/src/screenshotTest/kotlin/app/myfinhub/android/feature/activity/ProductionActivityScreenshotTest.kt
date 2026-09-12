package app.myfinhub.android.feature.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "category_activity_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CategoryActivityLightScreenshot() { CategoryActivityFixture(false) }

@PreviewTest
@Preview(name = "category_activity_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CategoryActivityDarkScreenshot() { CategoryActivityFixture(true) }

@PreviewTest
@Preview(name = "category_activity_large_font", widthDp = 412, heightDp = 915, fontScale = 1.5f, showBackground = true)
@Composable
fun CategoryActivityLargeFontScreenshot() { CategoryActivityFixture(false) }

@Composable
private fun CategoryActivityFixture(darkTheme: Boolean) {
    val state = ActivityUiState(items = listOf(
        ActivityItem("refund", "10 Σεπ", ActivityKind.INCOME, "Επιστροφή αγοράς", "Μερική επιστροφή", 5.0,
            "Μετρητά", "Τρόφιμα", rawDate = "2026-09-10", categoryContributions = mapOf("Τρόφιμα" to -5.0)),
        ActivityItem("split", "1 Σεπ", ActivityKind.EXPENSE, "Εβδομαδιαίες αγορές", "Μοιρασμένη κίνηση", -100.0,
            "Πειραιώς Μισθοδοσίας", null, rawDate = "2026-09-01", categoryContributions = mapOf("Τρόφιμα" to 20.0)),
    )).forCategory("Τρόφιμα", "2026-09-01", "2026-09-10")
    MyFinHubTheme(darkTheme = darkTheme) {
        ActivityLedgerScreen(state, {}, {}, {}, onBack = {})
    }
}

@PreviewTest
@Preview(name = "production_activity_pending_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityPendingLightScreenshot() { ProductionActivityPendingFixture(false) }

@PreviewTest
@Preview(name = "production_activity_pending_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityPendingDarkScreenshot() { ProductionActivityPendingFixture(true) }

@PreviewTest
@Preview(name = "production_activity_pending_large_font", widthDp = 412, heightDp = 915, fontScale = 1.5f, showBackground = true)
@Composable
fun ProductionActivityPendingLargeFontScreenshot() { ProductionActivityPendingFixture(false) }

@PreviewTest
@Preview(name = "production_activity_account_filter_sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityAccountFilterSheetScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ActivityFilterSheetContent(
            state = activityFixtureState(),
            onApply = { _, _, _, _, _ -> },
            onReset = {},
        )
    }
}

@PreviewTest
@Preview(name = "production_activity_pending_detail", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityPendingDetailScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ActivityReadDetailScreen(
            item = pendingActivityItems().first(),
            accountOptions = activityAccountOptions(),
            mutationBlocked = false,
            onBack = {},
            onEdit = {},
            onDelete = {},
            onDeleted = {},
        )
    }
}

@PreviewTest
@Preview(name = "production_activity_edit", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityEditScreenshot() {
    val item = pendingActivityItems().first { !it.pendingSync && it.kind == ActivityKind.EXPENSE }
    MyFinHubTheme(darkTheme = false) {
        ActivityEditScreen(
            item = item,
            categoryOptions = listOf(
                ActivityCategoryOption("Έξοδος", listOf("Καφές")),
                ActivityCategoryOption("Μεταφορές", listOf("Εισιτήριο")),
            ),
            mutationInFlight = false,
            mutationBlocked = false,
            onBack = {},
            onSave = { _, _, _, _ -> },
            onSaved = {},
        )
    }
}

@Composable
private fun ProductionActivityPendingFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        ActivityLedgerScreen(
            state = activityFixtureState(),
            onAction = {},
            onOpenDetail = {},
            onOpenQuickEntry = {},
        )
    }
}

private fun activityFixtureState() = ActivityUiState(
    items = pendingActivityItems(),
    expenseCategories = listOf(
        ActivityCategoryOption("Έξοδος", listOf("Καφές")),
        ActivityCategoryOption("Τρόφιμα"),
        ActivityCategoryOption("Μεταφορές", listOf("Εισιτήριο")),
    ),
    incomeCategories = listOf(ActivityCategoryOption("Μισθός")),
    accountOptions = activityAccountOptions(),
)

private fun activityAccountOptions(): List<ActivityAccountOption> = listOf(
    ActivityAccountOption("piraeus-payroll", "Πειραιώς Μισθοδοσίας"),
    ActivityAccountOption("cash", "Μετρητά"),
    ActivityAccountOption("piraeus-savings", "Πειραιώς Αποταμίευση"),
)

private fun pendingActivityItems(): List<ActivityItem> = listOf(
    ActivityItem(
        id = "evt-offline-coffee",
        dateLabel = "Σήμερα, 08:45",
        kind = ActivityKind.EXPENSE,
        title = "Καφές",
        subtitle = "Πρωινός καφές",
        amount = -5.00,
        accountLabel = "Πειραιώς Μισθοδοσίας",
        category = "Έξοδος",
        pendingSync = true,
        rawDate = "2026-09-07",
        accountId = "piraeus-payroll",
    ),
    ActivityItem(
        id = "evt-offline-market",
        dateLabel = "Σήμερα, 08:32",
        kind = ActivityKind.EXPENSE,
        title = "Σούπερ μάρκετ",
        subtitle = "Μικρές αγορές",
        amount = -18.40,
        accountLabel = "Πειραιώς Μισθοδοσίας",
        category = "Τρόφιμα",
        pendingSync = true,
        rawDate = "2026-09-07",
        accountId = "piraeus-payroll",
    ),
    ActivityItem(
        id = "evt-transfer",
        dateLabel = "Σήμερα, 08:10",
        kind = ActivityKind.TRANSFER,
        title = "Μεταφορά στην αποταμίευση",
        subtitle = "Εσωτερική μεταφορά",
        amount = 250.00,
        accountLabel = "Πειραιώς Μισθοδοσίας → Πειραιώς Αποταμίευση",
        category = "Αποταμίευση",
        rawDate = "2026-09-07",
        fromAccountId = "piraeus-payroll",
        toAccountId = "piraeus-savings",
    ),
    ActivityItem(
        id = "evt-synced-expense",
        dateLabel = "Χθες, 19:10",
        kind = ActivityKind.EXPENSE,
        title = "Μετακίνηση",
        subtitle = "Εισιτήριο",
        amount = -3.60,
        accountLabel = "Μετρητά",
        category = "Μεταφορές",
        rawDate = "2026-09-06",
        accountId = "cash",
    ),
    ActivityItem(
        id = "evt-synced-income",
        dateLabel = "2 Σεπ, 10:00",
        kind = ActivityKind.INCOME,
        title = "Μισθός",
        subtitle = "Μηνιαία πίστωση",
        amount = 1840.00,
        accountLabel = "Πειραιώς Μισθοδοσίας",
        category = "Μισθός",
        rawDate = "2026-09-02",
        accountId = "piraeus-payroll",
    ),
)
