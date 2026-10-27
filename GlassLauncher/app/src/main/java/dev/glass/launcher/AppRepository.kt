package dev.glass.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
)

data class AppShortcut(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String?,
    val shortcutInfo: ShortcutInfo,
)

data class HomeItem(
    val packageName: String,
    val page: Int,
    val position: Int,
)

object AppRepository {

    /** Every launchable app except this launcher, sorted by name. Call off the main thread. */
    fun loadApps(context: Context, iconSizePx: Int = 192): List<AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(iconSizePx, iconSizePx).asImageBitmap(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Packages for phone, messages, browser and camera, when a default handler exists. */
    fun dockCandidates(context: Context): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull { resolvePackage(context, it) }.distinct()
    }

    private fun resolvePackage(context: Context, intent: Intent): String? =
        context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeIf { it != "android" } // "android" means the chooser, not a real default

    fun launch(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        recordLocalLaunch(context, packageName)
    }

    fun getShortcuts(context: Context, packageName: String): List<AppShortcut> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        return try {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
                setPackage(packageName)
            }
            val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
            shortcuts.map { shortcut ->
                AppShortcut(
                    id = shortcut.id,
                    packageName = shortcut.`package`,
                    shortLabel = shortcut.shortLabel?.toString() ?: "",
                    longLabel = shortcut.longLabel?.toString(),
                    shortcutInfo = shortcut,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun launchShortcut(context: Context, shortcut: AppShortcut) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return
        try {
            launcherApps.startShortcut(shortcut.shortcutInfo, null, null)
        } catch (e: Exception) {
            // Ignore if missing
        }
    }

    fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun uninstallApp(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun recordLocalLaunch(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("launcher_history", Context.MODE_PRIVATE)
        val history = prefs.getStringSet("launches", emptySet())?.toMutableList() ?: mutableListOf()
        history.remove(packageName)
        history.add(0, packageName)
        val trimmed = history.take(20).toSet()
        prefs.edit().putStringSet("launches", trimmed).apply()
    }

    fun getRecentApps(context: Context, allApps: List<AppEntry>, max: Int = 5): List<AppEntry> {
        val byPkg = allApps.associateBy { it.packageName }
        if (hasUsageStatsPermission(context)) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - 1000 * 60 * 60 * 24 * 7
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                if (!stats.isNullOrEmpty()) {
                    val recentPkgs = stats
                        .filter { it.packageName != context.packageName && byPkg.containsKey(it.packageName) }
                        .sortedByDescending { it.lastTimeUsed }
                        .map { it.packageName }
                        .distinct()
                    return recentPkgs.mapNotNull { byPkg[it] }.take(max)
                }
            }
        }
        val prefs = context.getSharedPreferences("launcher_history", Context.MODE_PRIVATE)
        val history = prefs.getStringSet("launches", emptySet()) ?: emptySet()
        return history.mapNotNull { byPkg[it] }.take(max)
    }

    fun loadHomeLayout(context: Context): List<HomeItem> {
        val prefs = context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
        val raw = prefs.getString("home_grid", null) ?: return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 3) {
                val pkg = parts[0]
                val page = parts[1].toIntOrNull() ?: 0
                val pos = parts[2].toIntOrNull() ?: 0
                HomeItem(pkg, page, pos)
            } else null
        }
    }

    fun saveHomeLayout(context: Context, items: List<HomeItem>) {
        val prefs = context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
        val raw = items.joinToString(",") { "${it.packageName}:${it.page}:${it.position}" }
        prefs.edit().putString("home_grid", raw).apply()
    }

    fun loadDockPackages(context: Context): List<String>? {
        val prefs = context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
        val raw = prefs.getString("dock_packages", null) ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun saveDockPackages(context: Context, packages: List<String>) {
        val prefs = context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
        prefs.edit().putString("dock_packages", packages.joinToString(",")).apply()
    }
}
