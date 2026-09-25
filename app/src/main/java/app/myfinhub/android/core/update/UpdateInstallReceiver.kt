package app.myfinhub.android.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, -1L)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
                UpdateInstallStatusStore.write(context, InstallStatus.PENDING_USER_ACTION, versionCode)
            }
            PackageInstaller.STATUS_SUCCESS ->
                UpdateInstallStatusStore.write(context, InstallStatus.SUCCESS, versionCode)
            else ->
                UpdateInstallStatusStore.write(context, InstallStatus.FAILURE, versionCode)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "app.myfinhub.android.action.UPDATE_INSTALL_STATUS"
        const val EXTRA_VERSION_CODE = "versionCode"
    }
}

enum class InstallStatus { NONE, PENDING_USER_ACTION, SUCCESS, FAILURE }

object UpdateInstallStatusStore {
    private const val PREFS = "myfinhub_update_install"
    private const val KEY_STATUS = "status"
    private const val KEY_VERSION = "version"

    fun write(context: Context, status: InstallStatus, versionCode: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATUS, status.name)
            .putLong(KEY_VERSION, versionCode)
            .apply()
    }

    fun read(context: Context): Pair<InstallStatus, Long> {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val status = runCatching {
            InstallStatus.valueOf(preferences.getString(KEY_STATUS, null).orEmpty())
        }.getOrDefault(InstallStatus.NONE)
        return status to preferences.getLong(KEY_VERSION, -1L)
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
