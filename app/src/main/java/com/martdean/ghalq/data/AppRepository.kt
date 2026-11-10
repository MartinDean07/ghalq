package com.martdean.ghalq.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

data class AppInfo(
    val packageName: String,
    val label: String,
    /** False when the app never asked for INTERNET, so blocking it changes nothing. */
    val usesInternet: Boolean,
)

class AppRepository(context: Context) {

    private val pm: PackageManager = context.packageManager
    private val selfPackage = context.packageName
    private val icons = LruCache<String, ImageBitmap>(256)

    /** Every app with a launcher icon, except Ghalq itself, sorted by name. */
    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance()
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != selfPackage }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    usesInternet = requestsInternet(info.packageName),
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    suspend fun icon(packageName: String, sizePx: Int): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            }.getOrNull()?.also { icons.put(packageName, it) }
        }
    }

    private fun requestsInternet(packageName: String): Boolean = runCatching {
        pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(Manifest.permission.INTERNET) == true
    }.getOrDefault(true)
}
