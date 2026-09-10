package app.myfinhub.android.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun canonicalPlanScreenshotState() = PlanUiState(
    items = listOf(
        PlannedItem(
            id = "scheduled-rent",
            title = "Ενοίκιο",
            dueLabel = "8 Σεπ",
            dueDateIso = "2026-09-08",
            amount = 680.0,
            kind = PlannedKind.SCHEDULED,
            flow = PlannedFlow.OBLIGATION,
            category = "Στέγαση",
            accountLabel = "Πειραιώς Μισθοδοσίας",
            urgency = PlannedUrgency.OVERDUE,
        ),
        PlannedItem(
            id = "recurring-internet",
            title = "Internet",
            dueLabel = "12 Σεπ",
            dueDateIso = "2026-09-12",
            amount = 34.90,
            kind = PlannedKind.RECURRING,
            flow = PlannedFlow.OBLIGATION,
            category = "Λογαριασμοί",
            accountLabel = "Πειραιώς Μισθοδοσίας",
            urgency = PlannedUrgency.THIS_WEEK,
        ),
        PlannedItem(
            id = "scheduled-loan",
            title = "Δόση δανείου",
            dueLabel = "25 Σεπ",
            dueDateIso = "2026-09-25",
            amount = 185.0,
            kind = PlannedKind.SCHEDULED,
            flow = PlannedFlow.OBLIGATION,
            category = "Δάνειο",
            accountLabel = "Πειραιώς Μισθοδοσίας",
            urgency = PlannedUrgency.LATER,
        ),
        PlannedItem(
            id = "scheduled-income",
            title = "Μισθός",
            dueLabel = "15 Σεπ",
            dueDateIso = "2026-09-15",
            amount = 1_650.0,
            kind = PlannedKind.SCHEDULED,
            flow = PlannedFlow.INCOME,
            category = "Μισθός",
            accountLabel = "Πειραιώς Μισθοδοσίας",
            urgency = PlannedUrgency.THIS_WEEK,
        ),
        PlannedItem(
            id = "scheduled-transfer",
            title = "Μεταφορά στην αποταμίευση",
            dueLabel = "18 Σεπ",
            dueDateIso = "2026-09-18",
            amount = 200.0,
            kind = PlannedKind.SCHEDULED,
            flow = PlannedFlow.TRANSFER,
            accountLabel = "Από Πειραιώς Μισθοδοσίας → Προς Πειραιώς Αποταμίευση",
            urgency = PlannedUrgency.LATER,
        ),
    ),
    budget = BudgetDraft(monthlyLimitText = "800", alertThresholdText = "80"),
    forecastHorizonDays = 30,
    forecastStartBalance = 1_695.0,
    forecastExpectedIncome = 1_650.0,
    forecastObligations = 899.90,
    forecastTransferImpact = 0.0,
    forecastEndBalance = 2_445.10,
    forecastEndDateLabel = "10 Οκτ 2026",
    budgetSpent = 680.0,
    budgetMonthLabel = "Σεπ 2026",
    message = "Αλλαγή budget · Αναμονή επιβεβαίωσης από τον server",
)

private fun canonicalPlanFlowScreenshotState(): PlanUiState {
    val base = canonicalPlanScreenshotState()
    return base.copy(
        items = base.items.filter { it.flow != PlannedFlow.OBLIGATION },
        forecastObligations = 0.0,
        forecastEndBalance = 3_345.0,
    )
}

@PreviewTest
@Preview(
    name = "canonical_plan_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalPlanCompactLightScreenshot() {
    CanonicalPlanScreenshotFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "canonical_plan_compact_dark",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalPlanCompactDarkScreenshot() {
    CanonicalPlanScreenshotFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "canonical_plan_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalPlanCompactLargeFontScreenshot() {
    CanonicalPlanScreenshotFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "canonical_plan_flow_large_font",
    widthDp = 412,
    heightDp = 1600,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalPlanFlowLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalPlanScreen(
            state = canonicalPlanFlowScreenshotState(),
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

@PreviewTest
@Preview(
    name = "canonical_budget_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalBudgetCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalBudgetScreen(
            state = canonicalPlanScreenshotState(),
            onAction = {},
            onBack = {},
        )
    }
}

@Composable
private fun CanonicalPlanScreenshotFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalPlanScreen(
            state = canonicalPlanScreenshotState(),
            onOpenBudget = {},
        )
    }
}
