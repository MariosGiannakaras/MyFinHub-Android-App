package app.myfinhub.android.core.config

import app.myfinhub.android.BuildConfig

data class AppConfiguration(
    val myFinHubApiBaseUrl: String,
    val supabaseUrl: String,
    val supabasePublishableKey: String,
) {
    val isConfigured: Boolean
        get() = missingFields.isEmpty()

    val missingFields: List<String>
        get() = buildList {
            if (myFinHubApiBaseUrl.isBlank()) add("MYFINHUB_API_BASE_URL")
            if (supabaseUrl.isBlank()) add("SUPABASE_URL")
            if (supabasePublishableKey.isBlank()) add("SUPABASE_PUBLISHABLE_KEY")
        }

    companion object {
        fun fromBuildConfig(): AppConfiguration = AppConfiguration(
            myFinHubApiBaseUrl = BuildConfig.MYFINHUB_API_BASE_URL.trim().trimEnd('/'),
            supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/'),
            supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim(),
        )
    }
}
