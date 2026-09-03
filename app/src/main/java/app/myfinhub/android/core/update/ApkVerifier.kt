package app.myfinhub.android.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

class ApkVerifier(private val context: Context) {
    fun verify(file: File, release: UpdateRelease): UpdateFailureKind? {
        val packageManager = context.packageManager
        val archive = archivePackageInfo(packageManager, file) ?: return UpdateFailureKind.PACKAGE_UNREADABLE
        if (archive.packageName != context.packageName) return UpdateFailureKind.WRONG_PACKAGE
        if (archive.longVersionCode != release.versionCode || archive.longVersionCode <= installedVersionCode(packageManager)) {
            return UpdateFailureKind.WRONG_VERSION
        }
        val installed = installedPackageInfo(packageManager) ?: return UpdateFailureKind.PACKAGE_UNREADABLE
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
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun installedPackageInfo(packageManager: PackageManager): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun installedVersionCode(packageManager: PackageManager): Long =
        installedPackageInfo(packageManager)?.longVersionCode ?: Long.MAX_VALUE

    private fun currentSignerDigests(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        return signingInfo.apkContentsSigners.orEmpty()
            .mapTo(linkedSetOf()) { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
    }
}
