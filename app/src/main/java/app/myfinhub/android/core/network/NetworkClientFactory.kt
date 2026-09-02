package app.myfinhub.android.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Shared production HTTP policy.
 *
 * Writes are never retried implicitly by OkHttp. Read retries, where safe, are owned explicitly by
 * the repository so retry behavior is observable, bounded and testable.
 */
object NetworkClientFactory {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}
