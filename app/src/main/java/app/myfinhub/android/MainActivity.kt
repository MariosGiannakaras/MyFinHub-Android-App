package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.app.MyFinHubRoot

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ lays applications out edge-to-edge. Keep the background compatible with that
        // platform contract, but never allow the app's headers/interactive product UI underneath the
        // status bar or display cutout on the supported Galaxy S24 Ultra.
        enableEdgeToEdge()
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                MyFinHubRoot()
            }
        }
    }
}
