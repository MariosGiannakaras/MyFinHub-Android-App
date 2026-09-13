package app.myfinhub.android.feature.quickentry

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "production_quick_entry_compact_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionQuickEntryCompactLightScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "production_quick_entry_compact_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionQuickEntryCompactDarkScreenshot() {
    ProductionQuickEntryFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "production_quick_entry_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionQuickEntryCompactLargeFontScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false)
}


@PreviewTest
@Preview(name = "production_quick_entry_split_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionQuickEntrySplitLightScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false, kind = QuickEntryKind.SPLIT)
}

@PreviewTest
@Preview(name = "production_quick_entry_split_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionQuickEntrySplitDarkScreenshot() {
    ProductionQuickEntryFixture(darkTheme = true, kind = QuickEntryKind.SPLIT)
}

@PreviewTest
@Preview(
    name = "production_quick_entry_split_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionQuickEntrySplitLargeFontScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false, kind = QuickEntryKind.SPLIT)
}

@PreviewTest
@Preview(name = "production_quick_entry_card_payment", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionQuickEntryCardPaymentScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false, kind = QuickEntryKind.CARD_PAYMENT)
}

@PreviewTest
@Preview(
    name = "production_quick_entry_reconciliation_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionQuickEntryReconciliationLargeFontScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false, kind = QuickEntryKind.RECONCILIATION)
}

@Composable
private fun ProductionQuickEntryFixture(
    darkTheme: Boolean,
    kind: QuickEntryKind = QuickEntryKind.EXPENSE,
) {
    MyFinHubTheme(darkTheme = darkTheme) {
        ProductionQuickEntryScreen(
            state = QuickEntryUiState(
                kind = kind,
                amountText = if (kind == QuickEntryKind.RECONCILIATION) "" else "42.60",
                actualBalanceText = if (kind == QuickEntryKind.RECONCILIATION) "1240.50" else "",
                splitParts = if (kind == QuickEntryKind.SPLIT) {
                    listOf(
                        QuickEntrySplitPartDraft("part-1", category = "Τρόφιμα", amountText = "30.10"),
                        QuickEntrySplitPartDraft("part-2", category = "Μετακίνηση", amountText = "12.50"),
                    )
                } else {
                    QuickEntryUiState().splitParts
                },
                dateText = "2026-09-04",
                dirty = true,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
