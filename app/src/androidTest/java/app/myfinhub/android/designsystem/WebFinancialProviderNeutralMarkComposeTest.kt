package app.myfinhub.android.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import app.myfinhub.android.core.ui.FinancialProvider
import org.junit.Rule
import org.junit.Test

/** Real shared UI must never display a different bank logo for an unbound Web provider. */
class WebFinancialProviderNeutralMarkComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun missingProviderArtworkUsesVisibleAccessibleNeutralMark() {
        rule.setContent {
            MyFinHubTheme {
                MyFinHubProviderMark(FinancialProvider.NATIONAL, Modifier.size(44.dp))
            }
        }
        rule.onNodeWithContentDescription("Εθνική Τράπεζα").assertIsDisplayed()
    }

    @Test fun existingVerifiedBundledProviderAssetPreservesIdentity() {
        rule.setContent {
            MyFinHubTheme {
                MyFinHubProviderMark(FinancialProvider.PIRAEUS, Modifier.size(44.dp))
            }
        }
        rule.onNodeWithContentDescription("Τράπεζα Πειραιώς").assertIsDisplayed()
    }
}
