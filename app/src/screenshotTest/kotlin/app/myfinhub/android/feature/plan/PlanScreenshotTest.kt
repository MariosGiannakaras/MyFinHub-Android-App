package app.myfinhub.android.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "plan_overview_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PlanOverviewCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        Plan2026Screen(
            state = PlanUiState(),
            onAction = {},
            onOpenItem = {},
            onOpenBudgets = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "plan_overview_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun PlanOverviewCompactLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        Plan2026Screen(
            state = PlanUiState(),
            onAction = {},
            onOpenItem = {},
            onOpenBudgets = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "plan_item_editor_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PlanItemEditorCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        PlanItemEditor2026Screen(
            item = syntheticPlannedItems().first(),
            onAction = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "plan_budgets_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PlanBudgetsCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        PlanBudgets2026Screen(
            state = PlanUiState(),
            onAction = {},
            onBack = {},
        )
    }
}
