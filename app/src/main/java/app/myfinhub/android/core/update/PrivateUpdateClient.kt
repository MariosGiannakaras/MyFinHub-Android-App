package app.myfinhub.android.core.update

import app.myfinhub.android.BuildConfig
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class PrivateUpdateClient(
    private val configuration: AppConfiguration,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun check(session: AuthSession): UpdateCheckResult = withContext(Dispatchers.IO) {
        val gate = sessionGate(session)
        if (gate != null) return@withContext gate
        val request = Request.Builder()
            .url("${configuration.myFinHubApiBaseUrl}/api/android-update")
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> UpdateCheckResult.Failure(UpdateFailureKind.AUTH_REQUIRED)
                    response.code == 403 -> UpdateCheckResult.Failure(UpdateFailureKind.MFA_REQUIRED)
                    response.code == 429 || response.code in 500..599 ->
                        UpdateCheckResult.Failure(UpdateFailureKind.SERVER, retryable = true)
                    !response.isSuccessful -> UpdateCheckResult.Failure(UpdateFailureKind.SERVER)
                    else -> parseEnvelope(response.body.string())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            UpdateCheckResult.Failure(UpdateFailureKind.NETWORK, retryable = true)
        } catch (_: Exception) {
            UpdateCheckResult.Failure(UpdateFailureKind.SERVER, retryable = true)
        }
    }

    suspend fun download(
        session: AuthSession,
        release: UpdateRelease,
        directory: File,
        onProgress: (Float) -> Unit,
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        val gate = sessionGate(session)
        if (gate != null) {
            return@withContext UpdateDownloadResult.Failure(
                kind = (gate as UpdateCheckResult.Failure).kind,
                retryable = gate.retryable,
            )
        }
        val url = runCatching { java.net.URI(release.downloadUrl) }.getOrNull()
        if (url?.scheme != "https" || url.host.isNullOrBlank()) {
            return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.INSECURE_DOWNLOAD)
        }
        val configuredStorageHost = runCatching { java.net.URI(configuration.supabaseUrl).host }.getOrNull()
        if (url.host != configuredStorageHost || !url.path.startsWith("/storage/v1/object/authenticated/android-releases/")) {
            return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.INSECURE_DOWNLOAD)
        }
        if (release.sizeBytes !in 1..MAX_APK_BYTES || !SHA256_REGEX.matches(release.sha256)) {
            return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.MALFORMED_METADATA)
        }

        directory.mkdirs()
        directory.listFiles()?.filter { it.name.endsWith(".part") || it.name.endsWith(".apk") }?.forEach(File::delete)
        val part = File(directory, "MyFinHub-${release.versionCode}.apk.part")
        val destination = File(directory, "MyFinHub-${release.versionCode}.apk")
        val request = Request.Builder()
            .url(release.downloadUrl)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("apikey", configuration.supabasePublishableKey)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.AUTH_REQUIRED)
                }
                if (!response.isSuccessful) {
                    return@withContext UpdateDownloadResult.Failure(
                        UpdateFailureKind.SERVER,
                        retryable = response.code == 429 || response.code in 500..599,
                    )
                }
                val body = response.body
                val announcedLength = body.contentLength()
                if (announcedLength > 0 && announcedLength != release.sizeBytes) {
                    return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.DOWNLOAD_SIZE_MISMATCH)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                body.byteStream().use { input ->
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > release.sizeBytes || total > MAX_APK_BYTES) {
                                part.delete()
                                return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.DOWNLOAD_SIZE_MISMATCH)
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress((total.toDouble() / release.sizeBytes.toDouble()).toFloat().coerceIn(0f, 1f))
                        }
                    }
                }
                if (total != release.sizeBytes) {
                    part.delete()
                    return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.DOWNLOAD_SIZE_MISMATCH)
                }
                val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualDigest.equals(release.sha256, ignoreCase = true)) {
                    part.delete()
                    return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH)
                }
                if (!part.renameTo(destination)) {
                    part.delete()
                    return@withContext UpdateDownloadResult.Failure(UpdateFailureKind.INSTALL_FAILED)
                }
                UpdateDownloadResult.Success(destination)
            }
        } catch (cancelled: CancellationException) {
            part.delete()
            throw cancelled
        } catch (_: IOException) {
            part.delete()
            UpdateDownloadResult.Failure(UpdateFailureKind.NETWORK, retryable = true)
        } catch (_: Exception) {
            part.delete()
            UpdateDownloadResult.Failure(UpdateFailureKind.SERVER, retryable = true)
        }
    }

    private fun sessionGate(session: AuthSession): UpdateCheckResult.Failure? = when {
        !configuration.isConfigured -> UpdateCheckResult.Failure(UpdateFailureKind.BUILD_NOT_CONFIGURED)
        session.accessToken.isBlank() -> UpdateCheckResult.Failure(UpdateFailureKind.AUTH_REQUIRED)
        session.assuranceLevel != AssuranceLevel.AAL2 -> UpdateCheckResult.Failure(UpdateFailureKind.MFA_REQUIRED)
        else -> null
    }

    private fun parseEnvelope(body: String): UpdateCheckResult = try {
        val envelope = json.decodeFromString<UpdateEnvelope>(body)
        if (!envelope.available) return UpdateCheckResult.UpToDate
        val release = envelope.release ?: return UpdateCheckResult.Failure(UpdateFailureKind.MALFORMED_METADATA)
        if (!validRelease(release)) return UpdateCheckResult.Failure(UpdateFailureKind.MALFORMED_METADATA)
        if (release.versionCode <= BuildConfig.VERSION_CODE.toLong()) UpdateCheckResult.UpToDate
        else UpdateCheckResult.Available(release)
    } catch (_: SerializationException) {
        UpdateCheckResult.Failure(UpdateFailureKind.MALFORMED_METADATA)
    } catch (_: Exception) {
        UpdateCheckResult.Failure(UpdateFailureKind.MALFORMED_METADATA)
    }

    private fun validRelease(release: UpdateRelease): Boolean {
        if (release.versionCode <= 0 || release.versionName.isBlank() || release.versionName.length > 64) return false
        if (release.sizeBytes !in 1..MAX_APK_BYTES || !SHA256_REGEX.matches(release.sha256)) return false
        if (release.notes.length > 8_000 || runCatching { java.time.Instant.parse(release.publishedAt) }.isFailure) return false
        return true
    }

    private companion object {
        const val MAX_APK_BYTES = 300L * 1024L * 1024L
        val SHA256_REGEX = Regex("^[A-Fa-f0-9]{64}$")
    }
}
