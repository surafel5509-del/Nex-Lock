package com.nexlock.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.nexlock.common.model.AppCategory
import com.nexlock.common.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val isSystemApp: Boolean,
    val versionName: String?,
)

/**
 * Lists only launchable applications that the user can actually open, which keeps the permission
 * surface small: NexLock declares a `<queries>` intent filter instead of QUERY_ALL_PACKAGES.
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun launcherApps(): List<InstalledApp> = withContext(dispatchers.io) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, 0)
        val ownPackage = context.packageName

        resolved.asSequence()
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val applicationInfo = activityInfo.applicationInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName ?: return@mapNotNull null
                if (packageName == ownPackage) return@mapNotNull null
                if (!applicationInfo.enabled) return@mapNotNull null
                val label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrDefault(packageName)
                InstalledApp(
                    packageName = packageName,
                    label = label.ifBlank { packageName },
                    category = AppCategoryClassifier.classify(packageName, label),
                    isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    versionName = runCatching {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0).versionName
                    }.getOrNull(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Best-effort label lookup for packages that are no longer installed (backup restore). */
    suspend fun labelFor(packageName: String): String? = withContext(dispatchers.io) {
        runCatching {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString()
        }.getOrNull()
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)
}
