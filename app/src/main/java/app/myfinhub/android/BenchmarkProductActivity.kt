package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.app.MyFinHubApp

/**
 * Deterministic product host used only by benchmark/non-minified profiling manifests.
 *
 * The production release manifest does not declare this Activity, so it is not an auth bypass.
 */
class BenchmarkProductActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyFinHubApp() }
    }
}
