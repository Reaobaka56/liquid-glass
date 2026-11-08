package dev.glass.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import org.xmlpull.v1.XmlPullParser

data class IconPackCandidate(val packageName: String, val label: String)

@SuppressLint("DiscouragedApi")
object IconPackRepository {
    fun candidates(context: Context, apps: List<AppEntry>): List<IconPackCandidate> =
        apps.mapNotNull { app ->
            try {
                val resources = context.packageManager.getResourcesForApplication(app.packageName)
                val appFilter = resources.getIdentifier("appfilter", "xml", app.packageName)
                if (appFilter != 0) IconPackCandidate(app.packageName, app.label) else null
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

    @Throws(IOException::class, PackageManager.NameNotFoundException::class)
    fun loadAssignments(context: Context, packageName: String, apps: List<AppEntry>): Map<String, String> {
        val packContext = context.createPackageContext(packageName, 0)
        val resources = packContext.resources
        val resourceId = resources.getIdentifier("appfilter", "xml", packageName)
        if (resourceId == 0) throw IOException("This app does not expose a compatible appfilter.xml.")
        val mapped = mutableMapOf<String, String>()
        val parser = resources.getXml(resourceId)
        parser.use {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    val targetPackage = component
                        ?.substringAfter('{')
                        ?.substringBefore('/')
                        ?.takeIf(String::isNotBlank)
                    if (targetPackage != null && !drawable.isNullOrBlank() &&
                        apps.any { it.packageName == targetPackage }
                    ) {
                        mapped[targetPackage] = drawable
                    }
                }
                event = parser.next()
            }
        }
        if (mapped.isEmpty()) throw IOException("No installed-app mappings were found in this icon pack.")
        return mapped
    }

    @Throws(IOException::class, PackageManager.NameNotFoundException::class)
    fun loadIcon(context: Context, packageName: String, drawableName: String): androidx.compose.ui.graphics.ImageBitmap {
        val packContext = context.createPackageContext(packageName, 0)
        val resources = packContext.resources
        val resourceId = sequenceOf("drawable", "mipmap")
            .map { resources.getIdentifier(drawableName, it, packageName) }
            .firstOrNull { it != 0 }
            ?: throw IOException("Icon pack resource '$drawableName' is missing.")
        val drawable: Drawable = resources.getDrawable(resourceId, packContext.theme)
        return drawable.toBitmap(192, 192).asImageBitmap()
    }
}
