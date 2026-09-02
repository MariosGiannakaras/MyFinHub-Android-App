package app.myfinhub.android.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun canonicalPlanScreenshotState() = PlanUiState(
    items = listOf(
        PlannedItem(
            id = "scheduled-1",
            title = "Ενοίκιο",
            dueLabel = "5 Σεπ",
            amount = 680.0,
            kind = PlannedKind.SCHEDULED,
        ),
        PlannedItem(
            id = "recurring-1",
            title = "Internet",
            dueLabel = "Κάθε μήνα, ημέρα 8",
            amount = 34.90,
            kind = PlannedKind.RECURRING,
        ),
    ),
    budget = BudgetDraft(monthlyLimitText = "800", alertThresholdText = "80"),
    forecastEndBalance = 1_695.0,
)

@PreviewTest
@Preview(
    name = "canonical_plan_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalPlanCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalPlanScreen(
            state = canonicalPlanScreenshotState(),
            onOpenBudget = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "canonical_budget_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalBudgetCompactLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalBudgetScreen(
            state = canonicalPlanScreenshotState(),
            onAction = {},
            onBack = {},
        )
    }
}
