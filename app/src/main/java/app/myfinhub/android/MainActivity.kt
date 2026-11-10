package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.app.MyFinHubRoot

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge system bar insets/background are owned by the theme-aware Compose root.
        // Applying top padding before MyFinHubRoot left a white strip in manually selected dark mode.
        enableEdgeToEdge()
        setContent { MyFinHubRoot() }
    }
}
