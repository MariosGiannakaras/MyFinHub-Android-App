package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.app.MyFinHubAppContent
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.activity.syntheticActivityItems
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry

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
            var quickEntryState by remember { mutableStateOf(QuickEntryUiState()) }
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    activityState = activityState,
                    quickEntryState = quickEntryState,
                    onQuickEntryAction = { action ->
                        quickEntryState = reduceQuickEntry(quickEntryState, action)
                    },
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
