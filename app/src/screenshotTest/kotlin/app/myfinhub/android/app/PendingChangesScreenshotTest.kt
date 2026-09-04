package app.myfinhub.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private val pendingDelete = PendingChangeUi(
    label = "Διαγραφή κίνησης",
    statusLabel = "Προς συγχρονισμό",
    canUndo = true,
)

private val pendingNeedsReview = PendingChangeUi(
    label = "Διαγραφή κίνησης",
    statusLabel = "Αναμονή επιβεβαίωσης από τον server",
    canUndo = false,
)

@PreviewTest
@Preview(name = "pending_changes_light", widthDp = 412, heightDp = 300, showBackground = true)
@Composable
fun PendingChangesLightScreenshot() {
    PendingChangesEvidence(darkTheme = false, latest = pendingDelete)
}

@PreviewTest
@Preview(name = "pending_changes_dark", widthDp = 412, heightDp = 300, showBackground = true)
@Composable
fun PendingChangesDarkScreenshot() {
    PendingChangesEvidence(darkTheme = true, latest = pendingDelete)
}

@PreviewTest
@Preview(
    name = "pending_changes_font_150",
    widthDp = 412,
    heightDp = 360,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun PendingChangesLargeFontScreenshot() {
    PendingChangesEvidence(darkTheme = false, latest = pendingDelete)
}

@PreviewTest
@Preview(
    name = "pending_changes_needs_review",
    widthDp = 412,
    heightDp = 400,
    showBackground = true,
)
@Composable
fun PendingChangesNeedsReviewScreenshot() {
    PendingChangesEvidence(darkTheme = false, latest = pendingNeedsReview)
}

@Composable
private fun PendingChangesEvidence(darkTheme: Boolean, latest: PendingChangeUi) {
    MyFinHubTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            PendingChangesBanner(
                changeCount = 3,
                latest = latest,
                onUndoLatest = {},
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            )
        }
    }
}
