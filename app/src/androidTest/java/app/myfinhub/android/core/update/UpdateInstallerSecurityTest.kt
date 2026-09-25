package app.myfinhub.android.core.update

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateInstallerSecurityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun unknownSourcePermissionIntent_isScopedToMyFinHubPackage() {
        val intent = PackageInstallerUpdateInstaller(context).permissionIntent()

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package", intent.data?.scheme)
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun installStatusReceiver_isNotExported() {
        val component = ComponentName(context, UpdateInstallReceiver::class.java)
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getReceiverInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getReceiverInfo(component, 0)
        }

        assertFalse(info.exported)
    }

    @Test
    fun installedApkCannotBeAcceptedAsFakeNewerUpdate() {
        val installedApk = File(context.applicationInfo.sourceDir)
        val installedInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val release = UpdateRelease(
            versionCode = installedInfo.longVersionCode + 1,
            versionName = "synthetic-newer",
            downloadUrl = "https://storage.example.test/storage/v1/object/authenticated/android-releases/new/MyFinHub.apk",
            sha256 = "a".repeat(64),
            sizeBytes = installedApk.length(),
            mandatory = false,
            notes = "test",
            publishedAt = "2026-09-03T12:00:00Z",
        )

        assertEquals(UpdateFailureKind.WRONG_VERSION, ApkVerifier(context).verify(installedApk, release))
    }

    @Test
    fun installStatusStore_isAppLocalAndClearable() {
        UpdateInstallStatusStore.clear(context)
        UpdateInstallStatusStore.write(context, InstallStatus.FAILURE, 42L)

        assertEquals(InstallStatus.FAILURE to 42L, UpdateInstallStatusStore.read(context))

        UpdateInstallStatusStore.clear(context)
        assertEquals(InstallStatus.NONE to -1L, UpdateInstallStatusStore.read(context))
    }
}
