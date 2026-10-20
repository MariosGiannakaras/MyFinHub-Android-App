package app.myfinhub.android.core.network

import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient

/**
 * All trusted public provider/service artwork requests use exactly one independent
 * anonymous transport. Reusing connections avoids one executor/pool per Compose
 * recomposition while keeping finance bearer auth, Web session cookies, disk cache
 * and redirected destinations out of the entire image-loading call path.
 *
 * The caller must validate the exact HTTPS Supabase public-storage URL *before*
 * creating a Request; this client intentionally knows no authenticated API.
 */
internal val certifiedAssetAnonymousClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .cache(null)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}
