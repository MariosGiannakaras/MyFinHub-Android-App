package app.myfinhub.android.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ApkVerifier(private val context: Context) {
    fun verify(file: File, release: UpdateRelease): UpdateFailureKind? {
        // The owner can spend time on the update/permission screen after download.
        // Recheck the private cached bytes before install as well as after download.
        if (file.length() != release.sizeBytes) return UpdateFailureKind.DOWNLOAD_SIZE_MISMATCH
        val digest = try {
            val sha256 = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(bytes)
                    if (count == -1) break
                    sha256.update(bytes, 0, count)
                }
            }
            sha256.digest().joinToString("") { "%02x".format(it) }
        } catch (_: IOException) {
            return UpdateFailureKind.PACKAGE_UNREADABLE
        }
        if (!digest.equals(release.sha256, ignoreCase = true)) {
            return UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH
        }

        val packageManager = context.packageManager
        val archive = archivePackageInfo(packageManager, file) ?: return UpdateFailureKind.PACKAGE_UNREADABLE
        if (archive.packageName != context.packageName) return UpdateFailureKind.WRONG_PACKAGE

        val installed = installedPackageInfo(packageManager) ?: return UpdateFailureKind.PACKAGE_UNREADABLE
        val archiveVersionCode = versionCode(archive)
        val installedVersionCode = versionCode(installed)
        if (archiveVersionCode != release.versionCode || archiveVersionCode <= installedVersionCode) {
            return UpdateFailureKind.WRONG_VERSION
        }

        val archiveSigners = currentSignerDigests(archive)
        val installedSigners = currentSignerDigests(installed)
        if (archiveSigners.isEmpty() || installedSigners.isEmpty() || archiveSigners != installedSigners) {
            return UpdateFailureKind.WRONG_SIGNER
        }
        return null
    }

    private fun archivePackageInfo(packageManager: PackageManager, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        }

    private fun installedPackageInfo(packageManager: PackageManager): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                context.packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun currentSignerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
