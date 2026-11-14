package app.myfinhub.android.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File


/**
 * Private updates must always be an explicit user decision, including when
 * updating the same package with the enrolled signer. Android >= 12 supports
 * an explicit session policy; earlier releases use the OS confirmation flow.
 */
internal fun userConfirmedUpdateSessionParams(
    packageName: String,
    apkSizeBytes: Long,
): PackageInstaller.SessionParams = PackageInstaller.SessionParams(
    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
).apply {
    setAppPackageName(packageName)
    setSize(apkSizeBytes)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
    }
}

class PackageInstallerUpdateInstaller(private val context: Context) {
    fun canRequestInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(file: File, release: UpdateRelease): UpdateFailureKind? {
        if (!canRequestInstalls()) return UpdateFailureKind.INSTALL_PERMISSION_REQUIRED
        val installer = context.packageManager.packageInstaller
        val params = userConfirmedUpdateSessionParams(context.packageName, file.length())
        val sessionId = try {
            installer.createSession(params)
        } catch (_: Exception) {
            return UpdateFailureKind.INSTALL_BLOCKED
        }
        return try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
                    .putExtra(UpdateInstallReceiver.EXTRA_VERSION_CODE, release.versionCode)
                val statusPendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(statusPendingIntent.intentSender)
            }
            null
        } catch (_: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            UpdateFailureKind.INSTALL_FAILED
        }
    }
}
