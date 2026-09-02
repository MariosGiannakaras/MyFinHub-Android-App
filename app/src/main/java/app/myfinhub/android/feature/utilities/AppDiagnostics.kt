package app.myfinhub.android.feature.utilities

/** Safe support metadata only. Never place credentials, tokens, finance payloads or card secrets here. */
data class AppDiagnosticsSnapshot(
    val versionName: String,
    val buildType: String,
    val environment: String,
    val apiHost: String,
    val networkStatus: String,
    val apiStatus: String,
    val sessionStatus: String,
    val lastSuccessfulSync: String?,
    val lastDiagnosticCode: String?,
)
