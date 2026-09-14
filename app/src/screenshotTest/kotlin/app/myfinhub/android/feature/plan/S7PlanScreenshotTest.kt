package app.myfinhub.android.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun s7PlanState() = PlanUiState(
    items = listOf(
        PlannedItem("rent", "Ενοίκιο", "8 Σεπ", 680.0, PlannedKind.SCHEDULED, category = "Στέγαση", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-08", urgency = PlannedUrgency.OVERDUE),
        PlannedItem("internet", "Internet", "12 Σεπ", 34.90, PlannedKind.RECURRING, category = "Λογαριασμοί", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-12", urgency = PlannedUrgency.THIS_WEEK),
        PlannedItem("salary", "Μισθός", "15 Σεπ", 1_650.0, PlannedKind.SCHEDULED, flow = PlannedFlow.INCOME, category = "Μισθός", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-15", urgency = PlannedUrgency.THIS_WEEK),
        PlannedItem("transfer", "Μεταφορά στην αποταμίευση", "18 Σεπ", 200.0, PlannedKind.SCHEDULED, flow = PlannedFlow.TRANSFER, accountLabel = "Από Πειραιώς Μισθοδοσίας → Προς Πειραιώς Αποταμίευση", dueDateIso = "2026-09-18", urgency = PlannedUrgency.LATER),
        PlannedItem("loan", "Δόση δανείου", "25 Σεπ", 185.0, PlannedKind.SCHEDULED, category = "Δάνειο", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-25", urgency = PlannedUrgency.LATER),
    ),
    budget = BudgetDraft("800", "80"),
    forecastHorizonDays = 30,
    forecastStartDateIso = "2026-09-10",
    forecastEndDateIso = "2026-10-10",
    forecastStartBalance = 1_695.0,
    forecastExpectedIncome = 1_650.0,
    forecastObligations = 899.90,
    forecastTransferImpact = 0.0,
    forecastEndBalance = 2_445.10,
    forecastEndDateLabel = "10 Οκτ 2026",
    budgetSpent = 680.0,
    budgetMonthLabel = "Σεπ 2026",
)

@PreviewTest
@Preview(name = "s7_plan_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7PlanLight() { MyFinHubTheme(false) { CanonicalPlan2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_plan_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7PlanDark() { MyFinHubTheme(true) { CanonicalPlan2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_plan_large", widthDp = 412, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Composable
fun S7PlanLarge() { MyFinHubTheme(false) { CanonicalPlan2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_forecast_light", widthDp = 412, heightDp = 1100, showBackground = true)
@Composable
fun S7ForecastLight() { MyFinHubTheme(false) { CanonicalPlanForecastScreen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_forecast_dark", widthDp = 412, heightDp = 1100, showBackground = true)
@Composable
fun S7ForecastDark() { MyFinHubTheme(true) { CanonicalPlanForecastScreen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_forecast_large", widthDp = 412, heightDp = 1500, fontScale = 1.5f, showBackground = true)
@Composable
fun S7ForecastLarge() { MyFinHubTheme(false) { CanonicalPlanForecastScreen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_budget_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7BudgetLight() { MyFinHubTheme(false) { CanonicalBudget2026Screen(s7PlanState(), { _, _ -> }, {}) } }

@PreviewTest
@Preview(name = "s7_budget_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7BudgetDark() { MyFinHubTheme(true) { CanonicalBudget2026Screen(s7PlanState(), { _, _ -> }, {}) } }

@PreviewTest
@Preview(name = "s7_budget_large", widthDp = 412, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Composable
fun S7BudgetLarge() { MyFinHubTheme(false) { CanonicalBudget2026Screen(s7PlanState(), { _, _ -> }, {}) } }
