package app.myfinhub.android.feature.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

// Canonical Slice C references cover light, dark, 150% font, account-filter sheet and detail states.
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
        ActivityScreen(state, {}, {}, {}, onBack = {})
    }
}

@PreviewTest
@Preview(name = "production_activity_pending_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityPendingLightScreenshot() {
    ProductionActivityPendingFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "production_activity_pending_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityPendingDarkScreenshot() {
    ProductionActivityPendingFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "production_activity_pending_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionActivityPendingLargeFontScreenshot() {
    ProductionActivityPendingFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "production_activity_account_filter_sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityAccountFilterSheetScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ActivityAccountFilterSheetContent(
            selectedId = "piraeus-payroll",
            options = activityAccountOptions(),
            onSelected = {},
        )
    }
}

@PreviewTest
@Preview(name = "production_activity_pending_detail", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionActivityPendingDetailScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ActivityDetailScreen(
            item = pendingActivityItems().first(),
            categoryOptions = listOf(
                ActivityCategoryOption("Έξοδος", listOf("Καφές", "Τρόφιμα")),
                ActivityCategoryOption("Μεταφορές"),
            ),
            onBack = {},
            onSave = { _, _, _, _ -> },
            onDelete = {},
        )
    }
}

@Composable
private fun ProductionActivityPendingFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        ActivityScreen(
            state = ActivityUiState(
                items = pendingActivityItems(),
                accountOptions = activityAccountOptions(),
            ),
            onAction = {},
            onOpenDetail = {},
            onOpenQuickEntry = {},
        )
    }
}

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
        subtitle = "Εκκρεμεί διαγραφή · Πρωινός καφές",
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
