package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test

class WebCategoryMomentumComposeTest {
    @get:Rule val rule=createComposeRule()

    @After fun resetAmountVisibility() {
        AmountVisibilityPreference.write(InstrumentationRegistry.getInstrumentation().targetContext, false)
    }

    @Test fun monthOnlyMomentumShowsPreviousBaseAndNewCategoriesReadOnly() {
        AmountVisibilityPreference.write(InstrumentationRegistry.getInstrumentation().targetContext, true)
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-19",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Οκτ","Σεπ",900.0,200.0,900.0,100.0),
            categories=emptyList(),
            categoryMomentum=listOf(
                InsightCategoryMomentum("Τρόφιμα › Σούπερ",120.0,60.0,1.0),
                InsightCategoryMomentum("Αγορές",20.0,0.0,null),
            ),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(
                state=InsightsUiState(periods=listOf(scope)),
                onOpenSupportingActivity={},
            )
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_category_momentum"))
        rule.onNodeWithTag("insights_category_momentum").assertIsDisplayed()
        rule.onNodeWithText("Τρόφιμα › Σούπερ").assertIsDisplayed()
        rule.onNodeWithText("Νέα βάση").assertIsDisplayed()
    }
}
