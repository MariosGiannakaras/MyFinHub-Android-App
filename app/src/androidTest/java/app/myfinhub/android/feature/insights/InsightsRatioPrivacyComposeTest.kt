package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InsightsRatioPrivacyComposeTest {
    @get:Rule val rule=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun hide() { AmountVisibilityPreference.write(context,false) }
    @After fun reset() { AmountVisibilityPreference.write(context,false) }

    private val scope=InsightPeriodScope(
        id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
        startDate="2026-10-01",endDate="2026-10-20",contextLabel="Μερικός μήνας",
        comparison=InsightsComparison("Οκτ","Σεπ",1000.0,200.0,1000.0,100.0),
        categories=listOf(InsightCategory("Τρόφιμα",80.0,0.4f)),
        categoryMomentum=listOf(InsightCategoryMomentum("Τρόφιμα",200.0,100.0,1.0)),
        creditPortfolio=InsightsCreditPortfolio(
            1,1,350.0,700.0,350.0,0.5,
            listOf(InsightsCreditCardSnapshot("visa","Visa",true,350.0,700.0,350.0,0.5)),
        ),
    )

    @Test fun hiddenCategoryRatioIsNotInTalkBackDescription() {
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(scope)),onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_category_Τρόφιμα").performScrollTo()
            .assertContentDescriptionContains("••••")
    }

    @Test fun chartGraphIsSuppressedWhileAmountsHidden() {
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(scope)),onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_details_toggle").performScrollTo().performClick()
        rule.onNodeWithTag("insights_chart_hidden").performScrollTo().assertIsDisplayed()
    }

    @Test fun visibleCategoryRatioIsPreserved() {
        AmountVisibilityPreference.write(context,true)
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(scope)),onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_category_Τρόφιμα").performScrollTo()
            .assertContentDescriptionContains("40%")
    }
}
