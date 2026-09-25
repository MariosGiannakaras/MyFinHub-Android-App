package app.myfinhub.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import app.myfinhub.android.core.ui.UserNotice
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import com.android.tools.screenshot.PreviewTest

private val screenshotNotice = UserNotice(
    message = "Το MyFinHub δεν είναι προσωρινά διαθέσιμο.",
    details = "Ενέργεια: Αποθήκευση οικονομικών δεδομένων\nΚατηγορία: SERVER\nHTTP: 503\nΕπανάληψη: επιτρέπεται\nΔεν εμφανίζονται ευαίσθητα δεδομένα ή περιεχόμενο απάντησης για λόγους ασφαλείας.",
    diagnosticCode = "MFH-API-SERVER-503",
)

private val screenshotSnackbarData = object : SnackbarData {
    override val visuals: SnackbarVisuals = object : SnackbarVisuals {
        override val message: String = screenshotNotice.message
        override val actionLabel: String = "Λεπτομέρειες"
        override val withDismissAction: Boolean = true
        override val duration: SnackbarDuration = SnackbarDuration.Indefinite
    }

    override fun performAction() = Unit

    override fun dismiss() = Unit
}

@PreviewTest
@Preview(
    name = "user_notice_snackbar_phone",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun UserNoticeSnackbarScreenshot() {
    val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner,
    ) {
        MyFinHubTheme(darkTheme = false) {
            Box(modifier = Modifier.fillMaxSize()) {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                )
                Snackbar(
                    snackbarData = screenshotSnackbarData,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 152.dp),
                )
            }
        }
    }
}

@PreviewTest
@Preview(
    name = "user_notice_details_phone",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun UserNoticeDetailsScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        UserNoticeDetailsDialog(
            notice = screenshotNotice,
            onDismiss = {},
        )
    }
}
