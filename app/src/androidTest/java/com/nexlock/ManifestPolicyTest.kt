package com.nexlock

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Privacy invariants that must never regress. These assertions run against the real merged
 * manifest, so a future dependency that reintroduces INTERNET (for example an analytics SDK)
 * fails the build immediately.
 */
@RunWith(AndroidJUnit4::class)
class ManifestPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    private fun requestedPermissions(): List<String> {
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        return info.requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun noNetworkPermissionsAreDeclared() {
        val permissions = requestedPermissions()
        assertFalse("INTERNET must never be declared", permissions.contains(Manifest.permission.INTERNET))
        assertFalse(
            "ACCESS_NETWORK_STATE is not needed",
            permissions.contains(Manifest.permission.ACCESS_NETWORK_STATE),
        )
    }

    @Test
    fun noBroadStorageOrPersonalDataPermissions() {
        val forbidden = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
        val declared = requestedPermissions()
        forbidden.forEach { permission ->
            assertFalse("$permission must not be requested", declared.contains(permission))
        }
    }

    @Test
    fun requiredPermissionsAreDeclared() {
        val declared = requestedPermissions()
        assertTrue(declared.contains(Manifest.permission.USE_BIOMETRIC))
        assertTrue(declared.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(declared.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED))
    }

    @Test
    fun backupIsDisabled() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val flags = info.flags
        val allowBackup = (flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        assertFalse("OS backup must stay disabled for the encrypted vault", allowBackup)
    }
}
