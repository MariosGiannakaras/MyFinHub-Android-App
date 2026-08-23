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
 * Deterministic product host reachable only from benchmark/profile variants.
 *
 * Debug and production release manifests explicitly remove this component. The larger
 * Activity data set keeps profile collection representative without changing production state.
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
                id = "baseline-$index",
                dateLabel = "Δείγμα ${index + 1}",
                subtitle = "${template.subtitle} • profile ${index + 1}",
            ),
        )
    }
}
