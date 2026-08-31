package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.app.MyFinHubAppContent
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.activity.syntheticActivityItems
import app.myfinhub.android.feature.home.syntheticHomeUiState

/**
 * Deterministic product host used only by the Baseline Profile target variant.
 *
 * The larger Activity data set makes scroll/frame/memory measurements representative
 * without placing synthetic finance data in the production path.
 */
class BenchmarkProductActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityState = ActivityUiState(items = representativeActivityItems())
        setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    activityState = activityState,
                )
            }
        }
    }
}

private fun representativeActivityItems() = buildList {
    val templates = syntheticActivityItems()
    repeat(500) { index ->
        val template = templates[index % templates.size]
        add(
            template.copy(
                id = "benchmark-$index",
                dateLabel = "Δείγμα ${index + 1}",
                subtitle = "${template.subtitle} • benchmark ${index + 1}",
            ),
        )
    }
}
