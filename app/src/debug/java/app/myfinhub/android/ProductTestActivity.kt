package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.app.MyFinHubApp

/** Debug-only host used by product UI instrumentation after MainActivity becomes auth-gated. */
class ProductTestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyFinHubApp() }
    }
}
