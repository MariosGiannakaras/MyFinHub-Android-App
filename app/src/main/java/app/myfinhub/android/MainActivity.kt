package app.myfinhub.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import app.myfinhub.android.feature.auth.AuthShellViewModel
import app.myfinhub.android.app.MyFinHubRoot
import app.myfinhub.android.core.security.protectRecentTasksSnapshot

class MainActivity : FragmentActivity() {
    private val authViewModel by lazy { ViewModelProvider(this)[AuthShellViewModel::class.java] }

    override fun onUserInteraction() {
        super.onUserInteraction()
        authViewModel.onUserInteraction()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The task snapshot can be captured before ON_STOP locks the owner session.
        protectRecentTasksSnapshot(this)
        // Edge-to-edge system bar insets/background are owned by the theme-aware Compose root.
        // Applying top padding before MyFinHubRoot left a white strip in manually selected dark mode.
        enableEdgeToEdge()
        setContent { MyFinHubRoot(authViewModel = authViewModel) }
    }
}
