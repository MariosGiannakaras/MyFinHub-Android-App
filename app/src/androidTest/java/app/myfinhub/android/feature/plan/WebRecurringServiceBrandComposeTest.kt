package app.myfinhub.android.feature.plan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import app.myfinhub.android.core.network.RecurringServiceAssetRecord
import app.myfinhub.android.designsystem.LocalRecurringServiceAssets
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Session/catalog backed icon identity; invalid remote images remain neutral. */
class WebRecurringServiceBrandComposeTest {
    @get:Rule val rule=createComposeRule()
    private val asset=RecurringServiceAssetRecord(
        assetKey="service-asset-test",recurringId="internet",
        url="https://untrusted.invalid/icon.png",
        fileName="Internet icon.png",mimeType="image/png",sizeBytes=300,
        updatedAt="2026-10-20T09:00:00Z",
    )

    @Test fun exactBoundServiceAssetShowsSafeFallbackAndMissingAssetsDoNotImpersonate() {
        val state=PlanUiState(recurringManageItems=listOf(
            RecurringManageItem("internet","Internet",35.0,"active",
                logoAssetKey="service-asset-test"),
            RecurringManageItem("other","Other service",15.0,"active",
                logoAssetKey="service-asset-other"),
        ))
        rule.setContent { MyFinHubTheme {
            CompositionLocalProvider(LocalRecurringServiceAssets provides listOf(asset)) {
                CanonicalRecurringManagementScreen(
                    state, onBack={}, onStatusChanged={_,_->Unit},
                )
            }
        } }
        rule.onNodeWithTag("recurring_management_root")
            .performScrollToNode(hasTestTag("recurring_service_mark_internet"))
        rule.onNodeWithTag("recurring_service_mark_internet").assertExists()
        rule.onNodeWithTag("recurring_service_mark_other").assertDoesNotExist()
    }

    @Test fun duplicatedHistoricalIdsNeverGuessWhichLogoBelongsToRow() {
        val state=PlanUiState(recurringManageItems=listOf(
            RecurringManageItem("internet","Old",20.0,"active"),
            RecurringManageItem("internet","New",25.0,"paused"),
        ))
        rule.setContent { MyFinHubTheme {
            CompositionLocalProvider(LocalRecurringServiceAssets provides listOf(asset)) {
                CanonicalRecurringManagementScreen(
                    state,onBack={},onStatusChanged={_,_->Unit},
                )
            }
        } }
        rule.onNodeWithTag("recurring_service_mark_internet").assertDoesNotExist()
    }
}
