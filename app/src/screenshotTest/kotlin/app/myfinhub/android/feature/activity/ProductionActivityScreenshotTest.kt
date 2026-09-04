package app.myfinhub.android.feature.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

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
            state = ActivityUiState(items = pendingActivityItems()),
            onAction = {},
            onOpenDetail = {},
            onOpenQuickEntry = {},
        )
    }
}

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
    ),
    ActivityItem(
        id = "evt-synced-expense",
        dateLabel = "Χθες, 19:10",
        kind = ActivityKind.EXPENSE,
        title = "Μετακίνηση",
        subtitle = "Εισιτήριο",
        amount = -3.60,
        accountLabel = "Πειραιώς Μισθοδοσίας",
        category = "Μεταφορές",
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
    ),
)
