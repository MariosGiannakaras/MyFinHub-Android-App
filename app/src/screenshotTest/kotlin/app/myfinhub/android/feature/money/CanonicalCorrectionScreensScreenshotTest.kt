package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate

private val correctionAccount = MoneyAccount(
    id = "acc-payroll",
    name = "Λογαριασμός μισθοδοσίας",
    balance = 1_155.40,
    kind = "Τράπεζα",
)

private val correctionAccountActivity = listOf(
    ActivityItem(
        id = "evt-groceries",
        dateLabel = "Σήμερα, 18:20",
        kind = ActivityKind.EXPENSE,
        title = "Σούπερ μάρκετ",
        subtitle = "Εβδομαδιαίες αγορές",
        amount = -42.80,
        accountLabel = "Λογαριασμός μισθοδοσίας",
        category = "Τρόφιμα",
        subcategory = "Σούπερ μάρκετ",
        rawDate = "2026-09-04",
        accountId = "acc-payroll",
    ),
    ActivityItem(
        id = "evt-transfer",
        dateLabel = "Χθες, 09:05",
        kind = ActivityKind.TRANSFER,
        title = "Μεταφορά",
        subtitle = "Προς αποταμίευση",
        amount = -120.00,
        accountLabel = "Λογαριασμός μισθοδοσίας → Αποταμίευση",
        category = "Μεταφορά",
        rawDate = "2026-09-03",
        fromAccountId = "acc-payroll",
        toAccountId = "acc-savings",
        pendingSync = true,
    ),
)

@PreviewTest
@Preview(name = "canonical_account_detail_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalAccountDetailLightScreenshot() {
    AccountDetailFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "canonical_account_detail_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalAccountDetailDarkScreenshot() {
    AccountDetailFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "canonical_account_detail_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalAccountDetailLargeFontScreenshot() {
    AccountDetailFixture(darkTheme = false)
}

@Composable
private fun AccountDetailFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalAccountDetailScreen(
            account = correctionAccount,
            activityItems = correctionAccountActivity,
            onBack = {},
            onOpenActivity = {},
            referenceDate = LocalDate.of(2026, 9, 4),
        )
    }
}

@PreviewTest
@Preview(name = "canonical_card_create_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalCardCreateLightScreenshot() {
    CardCreateFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "canonical_card_create_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalCardCreateDarkScreenshot() {
    CardCreateFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "canonical_card_create_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalCardCreateLargeFontScreenshot() {
    CardCreateFixture(darkTheme = false)
}

@Composable
private fun CardCreateFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalCardCreateScreen(
            cards = emptyList(),
            onCreate = {},
            onBack = {},
        )
    }
}
