package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.core.ui.PrivacySafeNoticeRecord
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private val noticeHistoryScreenshotEntries = listOf(
    PrivacySafeNoticeRecord(
        diagnosticCode = "MFH-NET-OFFLINE-PENDING",
        occurredAtEpochMillis = 1_788_534_300_000L,
    ),
    PrivacySafeNoticeRecord(
        diagnosticCode = "MFH-API-SERVER-503",
        occurredAtEpochMillis = 1_788_530_700_000L,
    ),
    PrivacySafeNoticeRecord(
        diagnosticCode = "MFH-AUTH-MFA_REQUIRED-403",
        occurredAtEpochMillis = 1_788_447_600_000L,
    ),
)

@PreviewTest
@Preview(name = "notice_history_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun NoticeHistoryLightScreenshot() {
    NoticeHistoryFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "notice_history_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun NoticeHistoryDarkScreenshot() {
    NoticeHistoryFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "notice_history_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun NoticeHistoryLargeFontScreenshot() {
    NoticeHistoryFixture(darkTheme = false)
}

@Composable
private fun NoticeHistoryFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        NoticeHistoryScreen(
            entries = noticeHistoryScreenshotEntries,
            onBack = {},
        )
    }
}
