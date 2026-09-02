package app.myfinhub.android.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityScreen
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntrySplitPartDraft
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "phase2b_activity_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun Phase2BActivityCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ActivityScreen(
            state = ActivityUiState(),
            onAction = {},
            onOpenDetail = {},
            onOpenQuickEntry = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "phase2b_activity_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun Phase2BActivityCompactLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ActivityScreen(
            state = ActivityUiState(),
            onAction = {},
            onOpenDetail = {},
            onOpenQuickEntry = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "phase2b_quick_entry_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun Phase2BQuickEntryCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        QuickEntryScreen(
            state = QuickEntryUiState(dateText = "2026-09-02"),
            onAction = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "phase2b_quick_entry_split_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun Phase2BQuickEntrySplitCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        QuickEntryScreen(
            state = QuickEntryUiState(
                kind = QuickEntryKind.SPLIT,
                dateText = "2026-09-02",
                note = "Ψώνια και parking",
                accountId = "acc-main",
                splitParts = listOf(
                    QuickEntrySplitPartDraft(
                        id = "part-1",
                        label = "Market",
                        category = "Τρόφιμα",
                        subcategory = "Σούπερ μάρκετ",
                        amountText = "64,20",
                    ),
                    QuickEntrySplitPartDraft(
                        id = "part-2",
                        label = "Parking",
                        category = "Μετακίνηση",
                        amountText = "8,50",
                    ),
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
