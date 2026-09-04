package app.myfinhub.android.core.config

import app.myfinhub.android.BuildConfig

data class AppConfiguration(
    val myFinHubApiBaseUrl: String,
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    val androidUpdateChannel: String = "production",
) {
    val isConfigured: Boolean
        get() = missingFields.isEmpty()

    val missingFields: List<String>
        get() = buildList {
            if (myFinHubApiBaseUrl.isBlank()) add("MYFINHUB_API_BASE_URL")
            if (supabaseUrl.isBlank()) add("SUPABASE_URL")
            if (supabasePublishableKey.isBlank()) add("SUPABASE_PUBLISHABLE_KEY")
            if (androidUpdateChannel !in ALLOWED_ANDROID_UPDATE_CHANNELS) add("ANDROID_UPDATE_CHANNEL")
        }

    companion object {
        private val ALLOWED_ANDROID_UPDATE_CHANNELS = setOf("production", "phase6-test")

        fun fromBuildConfig(): AppConfiguration = AppConfiguration(
            myFinHubApiBaseUrl = BuildConfig.MYFINHUB_API_BASE_URL.trim().trimEnd('/'),
            supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/'),
            supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim(),
            androidUpdateChannel = BuildConfig.ANDROID_UPDATE_CHANNEL.trim(),
        )
    }
}
