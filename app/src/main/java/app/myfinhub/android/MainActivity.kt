package app.myfinhub.android

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import app.myfinhub.android.feature.bankalert.BANK_INBOX_ACTION
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import app.myfinhub.android.feature.auth.AuthShellViewModel
import app.myfinhub.android.app.MyFinHubRoot
import app.myfinhub.android.core.security.protectRecentTasksSnapshot

class MainActivity : FragmentActivity() {
    private val openBankInbox = mutableStateOf(false)
    private val authViewModel by lazy { ViewModelProvider(this)[AuthShellViewModel::class.java] }

    override fun onUserInteraction() {
        super.onUserInteraction()
        authViewModel.onUserInteraction()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == BANK_INBOX_ACTION) openBankInbox.value = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The task snapshot can be captured before ON_STOP locks the owner session.
        protectRecentTasksSnapshot(this)
        // Edge-to-edge system bar insets/background are owned by the theme-aware Compose root.
        // Applying top padding before MyFinHubRoot left a white strip in manually selected dark mode.
        enableEdgeToEdge()
        openBankInbox.value = intent.action == BANK_INBOX_ACTION
        setContent {
            MyFinHubRoot(authViewModel = authViewModel, bankInboxRequested = openBankInbox.value,
                onBankInboxOpened = { openBankInbox.value = false; intent.action = null })
        }
    }
}
