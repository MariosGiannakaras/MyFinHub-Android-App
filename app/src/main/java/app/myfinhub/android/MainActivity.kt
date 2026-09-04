package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.app.MyFinHubRoot

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the production product inside Android's system-bar safe area. The previous
        // edge-to-edge opt-in allowed top-level headers to render underneath Samsung's status bar.
        setContent {
            MyFinHubRoot()
        }
    }
}
