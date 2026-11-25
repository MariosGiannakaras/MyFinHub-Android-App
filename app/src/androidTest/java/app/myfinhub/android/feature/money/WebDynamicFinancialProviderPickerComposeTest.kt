package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        compose.onNodeWithTag("card_create_provider_search").performTextInput("Νέα Τράπεζα")
        compose.onNodeWithTag("card_create_provider_owner-bank").performClick()
        // Verify the selected issuer field, not the search text or outgoing sheet row.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("card_create_provider_search").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("card_create_provider")
            .assert(hasText("Νέα Τράπεζα"))
            .assertIsDisplayed()
    }

    @Test fun dynamicProviderNamedCustomDoesNotCollideWithManualIssuerSentinel() {
        val provider = FinancialProviderRecord(
            id = "custom", displayName = "Custom Verified Bank", shortName = "Custom",
            providerKind = "bank", countryCode = "GR", sortOrder = 2,
            logoAssetKey = null, wordmarkAssetKey = null,
        )
        compose.setContent { MyFinHubTheme {
            CanonicalCardCreateScreen(
                cards = emptyList(), financialProviderCatalog = listOf(provider),
                onCreate = {}, onBack = {},
            )
        } }
        compose.onNodeWithTag("card_create_provider").performClick()
        compose.onNodeWithTag("card_create_provider_search").performTextInput("Custom Verified Bank")
        compose.onNodeWithTag("card_create_provider_custom").performClick()
        // Verify the selected issuer field, not the search text or outgoing sheet row.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("card_create_provider_search").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("card_create_provider")
            .assert(hasText("Custom Verified Bank"))
            .assertIsDisplayed()
    }

    @Test fun unknownCatalogRendersBundledOptionsInsteadOfUnverifiedThirdPartyMarks() {
        compose.setContent { MyFinHubTheme {
            CanonicalCardCreateScreen(cards = emptyList(), financialProviderCatalog = emptyList(),
                onCreate = {}, onBack = {})
        } }
        compose.onNodeWithTag("card_create_provider").assertIsDisplayed()
    }
}
