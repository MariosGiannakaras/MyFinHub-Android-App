package app.myfinhub.android.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.HomeAccount
import app.myfinhub.android.feature.home.HomeAccountGroup
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.syntheticHomeUiState
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "home_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun HomeCompactLightScreenshot() {
    HomeAppScreenshotFixture()
}

@PreviewTest
@Preview(
    name = "home_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun HomeCompactLargeFontScreenshot() {
    HomeAppScreenshotFixture()
}

/** S24-phone edge fixture personally reviewed for long labels and large signed values. */
@PreviewTest
@Preview(
    name = "home_edge_values_phone",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun HomeEdgeValuesPhoneScreenshot() {
    val base = syntheticHomeUiState()
    HomeAppScreenshotFixture(
        state = base.copy(
            amountsVisible = true,
            accounts = listOf(
                HomeAccount(
                    id = "edge-negative",
                    name = "Κύριος λογαριασμός με πολύ μεγάλη περιγραφή για έλεγχο διάταξης",
                    role = "Καθημερινές πληρωμές και πάγιες υποχρεώσεις",
                    balance = -98_765_432.10,
                    group = HomeAccountGroup.LIQUID,
                ),
                HomeAccount(
                    id = "edge-large",
                    name = "Μακροπρόθεσμη αποταμίευση έκτακτης ανάγκης",
                    role = "Αποταμίευση",
                    balance = 987_654_321.99,
                    group = HomeAccountGroup.SAVINGS,
                ),
            ),
            attentionItems = emptyList(),
            upcomingItems = emptyList(),
        ),
    )
}

@Composable
private fun HomeAppScreenshotFixture(
    state: HomeUiState = syntheticHomeUiState(),
) {
    val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner,
    ) {
        MyFinHubTheme(darkTheme = false) {
            MyFinHubAppContent(
                homeState = state,
                onHomeAction = {},
            )
        }
    }
}
