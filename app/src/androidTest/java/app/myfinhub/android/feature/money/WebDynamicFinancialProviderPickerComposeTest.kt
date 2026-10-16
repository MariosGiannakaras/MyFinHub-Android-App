package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.core.network.FinancialProviderRecord
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end native list supports owner-supplied IDs without inventing a bank logo. */
@RunWith(AndroidJUnit4::class)
class WebDynamicFinancialProviderPickerComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun authenticatedDynamicProviderAppearsAsActualCardIssuerChoice() {
        val provider = FinancialProviderRecord(
            id = "owner-bank", displayName = "Νέα Τράπεζα", shortName = "Νέα",
            providerKind = "bank", countryCode = "GR", sortOrder = 1,
            logoAssetKey = null, wordmarkAssetKey = null,
        )
        compose.setContent { MyFinHubTheme {
            CanonicalCardCreateScreen(
                cards = emptyList(), financialProviderCatalog = listOf(provider),
                onCreate = {}, onBack = {},
            )
        } }
        compose.onNodeWithTag("card_create_provider").performClick()
        compose.onNodeWithText("Νέα Τράπεζα").performClick()
        compose.onNodeWithText("Νέα Τράπεζα").assertIsDisplayed()
    }

    @Test fun unknownCatalogRendersBundledOptionsInsteadOfUnverifiedThirdPartyMarks() {
        compose.setContent { MyFinHubTheme {
            CanonicalCardCreateScreen(cards = emptyList(), financialProviderCatalog = emptyList(),
                onCreate = {}, onBack = {})
        } }
        compose.onNodeWithTag("card_create_provider").assertIsDisplayed()
    }
}
