package dev.glass.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppIconTheme(val label: String) {
    CLASSIC("iOS Classic"),
    MODERN("iOS Modern"),
    DARK("Midnight Dark"),
    MONOCHROME("Minimal Mono"),
    LIQUID_GLASS("Liquid Glass"),
}

data class IconCollection(
    val name: String,
    val theme: AppIconTheme,
    val assignments: Map<String, String>,
    val customImages: Map<String, String> = emptyMap(),
)

object OriginalIconLibrary {
    val glyphs = listOf(
        "phone" to "Phone",
        "messages" to "Messages",
        "safari" to "Safari",
        "camera" to "Camera",
        "photos" to "Photos",
        "settings" to "Settings",
        "store" to "App Store",
        "music" to "Music",
        "maps" to "Maps",
        "weather" to "Weather",
        "calendar" to "Calendar",
        "clock" to "Clock",
        "mail" to "Mail",
        "files" to "Files",
        "notes" to "Notes",
        "reminders" to "Reminders",
        "facetime" to "FaceTime",
        "calculator" to "Calculator",
        "wallet" to "Wallet",
        "health" to "Health",
        "podcasts" to "Podcasts",
        "translate" to "Translate",
        "shortcuts" to "Shortcuts",
        "contacts" to "Contacts",
        "home" to "Home",
        "voice" to "Voice Memos",
        "whatsapp" to "WhatsApp",
        "instagram" to "Instagram",
        "tiktok" to "TikTok",
        "youtube" to "YouTube",
        "spotify" to "Spotify",
        "chrome" to "Chrome",
        "gmail" to "Gmail",
        "discord" to "Discord",
        "google" to "Google",
        "generic" to "Gradient",
    )

    fun suggestedGlyph(packageName: String, label: String): String {
        val source = "$packageName $label".lowercase()
        val rules = listOf(
            "whatsapp" to listOf("whatsapp"),
            "instagram" to listOf("instagram"),
            "tiktok" to listOf("tiktok", "musically"),
            "youtube" to listOf("youtube"),
            "spotify" to listOf("spotify"),
            "chrome" to listOf("chrome"),
            "gmail" to listOf("gmail"),
            "discord" to listOf("discord"),
            "google" to listOf("google"),
            "phone" to listOf("dialer", "phone", "telecom"),
            "messages" to listOf("messaging", "messages", "sms"),
            "camera" to listOf("camera"),
            "photos" to listOf("photos", "gallery"),
            "settings" to listOf("settings"),
            "maps" to listOf("maps", "navigation"),
            "weather" to listOf("weather"),
            "calendar" to listOf("calendar"),
            "clock" to listOf("clock", "alarm"),
            "mail" to listOf("mail", "email"),
            "files" to listOf("files", "file manager"),
            "notes" to listOf("notes", "memo", "notepad"),
            "music" to listOf("music", "audio"),
            "store" to listOf("play store", "app store"),
            "contacts" to listOf("contacts"),
            "calculator" to listOf("calculator"),
            "home" to listOf("home"),
            "health" to listOf("health", "fit"),
        )
        return rules.firstOrNull { (_, needles) -> needles.any(source::contains) }?.first ?: "generic"
    }
}

class IconAssignmentStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.applicationContext
        .getSharedPreferences("launcher_icons", Context.MODE_PRIVATE)
    private val iconDirectory = File(appContext.filesDir, "custom-icons").apply { mkdirs() }

    var selectedTheme by mutableStateOf(
        AppIconTheme.entries.firstOrNull {
            it.name == preferences.getString(KEY_THEME, null)
        } ?: AppIconTheme.LIQUID_GLASS,
    )
        private set

    fun assignments(): Map<String, String> =
        preferences.getString(KEY_ASSIGNMENTS, null)
            ?.split(",")
            ?.mapNotNull { item ->
                val parts = item.split("=", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else null
            }
            ?.toMap()
            .orEmpty()

    fun setAssignment(packageName: String, glyph: String, theme: AppIconTheme) {
        val map = assignments().toMutableMap()
        map[packageName] = "$glyph|${theme.name}"
        preferences.edit().putString(KEY_ASSIGNMENTS, encode(map)).apply()
        setTheme(theme)
    }

    @Throws(IOException::class, SecurityException::class)
    fun setCustomIcon(packageName: String, uri: Uri): File {
        val destination = iconFile(packageName)
        val staged = File(iconDirectory, "${destination.name}.new")
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected icon image.")
            input.use { source ->
                staged.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_CUSTOM_IMAGE_BYTES) {
                            throw IOException("The selected icon image is larger than 2 MB.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            staged.delete()
            throw e
        } catch (e: SecurityException) {
            staged.delete()
            throw e
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(staged.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            staged.delete()
            throw IOException("The selected file is not a readable image.")
        }
        try {
            decodeIcon(staged).recycle()
        } catch (e: IOException) {
            staged.delete()
            throw e
        }
        if (!staged.renameTo(destination)) {
            staged.delete()
            throw IOException("Could not save the selected icon image.")
        }
        val map = assignments().toMutableMap()
        map[packageName] = "custom|${selectedTheme.name}"
        preferences.edit().putString(KEY_ASSIGNMENTS, encode(map)).apply()
        return destination
    }

    fun customIconFile(packageName: String): File =
        iconFile(packageName).takeIf(File::isFile) ?: throw IOException("Custom icon file is missing.")

    fun loadCustomIcon(packageName: String): Bitmap = decodeIcon(customIconFile(packageName))

    fun applyTheme(apps: List<AppEntry>, theme: AppIconTheme) {
        val map = assignments().toMutableMap()
        apps.forEach { app ->
            map[app.packageName] = "${OriginalIconLibrary.suggestedGlyph(app.packageName, app.label)}|${theme.name}"
        }
        preferences.edit().putString(KEY_ASSIGNMENTS, encode(map)).putString(KEY_THEME, theme.name).apply()
        selectedTheme = theme
    }

    fun applyIconPack(mappedIcons: Map<String, String>, packageName: String) {
        val map = assignments().toMutableMap()
        mappedIcons.forEach { (targetPackage, drawable) ->
            map[targetPackage] = "pack|$packageName|$drawable"
        }
        preferences.edit().putString(KEY_ASSIGNMENTS, encode(map)).apply()
    }

    @Throws(JSONException::class)
    fun collections(): List<IconCollection> {
        val saved = preferences.getString(KEY_COLLECTIONS, null) ?: return emptyList()
        val json = JSONArray(saved)
        return List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val mappings = item.getJSONObject("assignments")
            val assignments = buildMap {
                mappings.keys().forEach { key -> put(key, mappings.getString(key)) }
            }
            val imageJson = item.optJSONObject("customImages") ?: JSONObject()
            val images = buildMap {
                imageJson.keys().forEach { key ->
                    val encoded = imageJson.optString(key)
                    if (encoded.isNotBlank()) put(key, encoded)
                }
            }
            IconCollection(
                name = item.getString("name"),
                theme = AppIconTheme.entries.firstOrNull { it.name == item.optString("theme") }
                    ?: AppIconTheme.LIQUID_GLASS,
                assignments = assignments,
                customImages = images,
            )
        }
    }

    @Throws(JSONException::class, IllegalArgumentException::class, IOException::class)
    fun saveCollection(name: String) {
        val normalizedName = name.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Enter a collection name.")
        val saved = collections().toMutableList()
        if (saved.any { it.name.equals(normalizedName, ignoreCase = true) }) {
            throw IllegalArgumentException("A collection with that name already exists.")
        }
        val currentAssignments = assignments()
        saved += IconCollection(
            normalizedName,
            selectedTheme,
            currentAssignments,
            customImages(currentAssignments),
        )
        persistCollections(saved)
    }

    @Throws(JSONException::class, IllegalArgumentException::class, IOException::class)
    fun duplicateCollection(sourceName: String, newName: String) {
        val normalizedName = newName.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Enter a collection name.")
        val saved = collections().toMutableList()
        if (saved.any { it.name.equals(normalizedName, ignoreCase = true) }) {
            throw IllegalArgumentException("A collection with that name already exists.")
        }
        val source = saved.firstOrNull { it.name == sourceName }
            ?: throw IllegalArgumentException("The selected collection no longer exists.")
        saved += source.copy(name = normalizedName)
        persistCollections(saved)
    }

    @Throws(JSONException::class, IllegalArgumentException::class)
    fun renameCollection(oldName: String, newName: String) {
        val normalizedName = newName.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Enter a collection name.")
        val saved = collections().toMutableList()
        if (saved.any { it.name != oldName && it.name.equals(normalizedName, ignoreCase = true) }) {
            throw IllegalArgumentException("A collection with that name already exists.")
        }
        val index = saved.indexOfFirst { it.name == oldName }
        if (index < 0) throw IllegalArgumentException("The selected collection no longer exists.")
        saved[index] = saved[index].copy(name = normalizedName)
        persistCollections(saved)
    }

    @Throws(JSONException::class)
    fun deleteCollection(name: String) {
        persistCollections(collections().filterNot { it.name == name })
    }

    @Throws(JSONException::class, IllegalArgumentException::class, IOException::class)
    fun applyCollection(name: String) {
        val collection = collections().firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("The selected collection no longer exists.")
        collection.assignments.filterValues { it.startsWith("custom|") }.keys.forEach { packageName ->
            val encoded = collection.customImages[packageName]
                ?: throw IllegalArgumentException("A custom icon image is missing from this collection.")
            val bytes = try {
                Base64.decode(encoded, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("A custom icon image in this collection is invalid.", e)
            }
            validateIconBytes(bytes)
        }
        collection.assignments.filterValues { it.startsWith("custom|") }.keys.forEach { packageName ->
            saveCustomIconBytes(packageName, Base64.decode(collection.customImages.getValue(packageName), Base64.DEFAULT))
        }
        preferences.edit()
            .putString(KEY_ASSIGNMENTS, encode(collection.assignments))
            .putString(KEY_THEME, collection.theme.name)
            .apply()
        selectedTheme = collection.theme
    }

    @Throws(JSONException::class, IllegalArgumentException::class)
    fun exportCollection(name: String): String {
        val collection = collections().firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("The selected collection no longer exists.")
        return collection.toJson().toString(2)
    }

    @Throws(JSONException::class, IllegalArgumentException::class, IOException::class)
    fun importCollection(serialized: String) {
        if (serialized.length > MAX_COLLECTION_CHARS) {
            throw IllegalArgumentException("The selected icon collection is too large.")
        }
        val json = JSONObject(serialized)
        if (json.optInt("version", 0) != 1) {
            throw IllegalArgumentException("This icon collection format is not supported.")
        }
        val name = json.optString("name").trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("The icon collection has no name.")
        val theme = AppIconTheme.entries.firstOrNull { it.name == json.optString("theme") }
            ?: throw IllegalArgumentException("The icon collection has an unknown theme.")
        val mappingJson = json.optJSONObject("assignments")
            ?: throw IllegalArgumentException("The icon collection has no app mappings.")
        val mappings = buildMap {
            mappingJson.keys().forEach { key ->
                val value = mappingJson.optString(key)
                if (key.isNotBlank() && value.isNotBlank()) put(key, value)
            }
        }
        if (mappings.isEmpty()) throw IllegalArgumentException("The icon collection contains no app mappings.")
        val imageJson = json.optJSONObject("customImages") ?: JSONObject()
        val images = buildMap {
            imageJson.keys().forEach { key ->
                val encoded = imageJson.optString(key)
                if (key.isNotBlank() && encoded.isNotBlank()) put(key, encoded)
            }
        }
        var totalImageBytes = 0
        mappings.filterValues { it.startsWith("custom|") }.keys.forEach { packageName ->
            val encoded = images[packageName]
                ?: throw IllegalArgumentException("A custom icon image is missing from the collection.")
            if (encoded.length > MAX_CUSTOM_IMAGE_BASE64) {
                throw IllegalArgumentException("A custom icon image in this collection is too large.")
            }
            val bytes = try {
                Base64.decode(encoded, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("A custom icon image in this collection is invalid.", e)
            }
            totalImageBytes += bytes.size
            if (totalImageBytes > MAX_COLLECTION_IMAGE_BYTES) {
                throw IllegalArgumentException("Custom icons in this collection exceed the 8 MB limit.")
            }
            validateIconBytes(bytes)
        }
        mappings.filterValues { it.startsWith("custom|") }.keys.forEach { packageName ->
            val bytes = Base64.decode(images.getValue(packageName), Base64.DEFAULT)
            saveCustomIconBytes(packageName, bytes)
        }
        val saved = collections().toMutableList()
        val existingIndex = saved.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        val imported = IconCollection(name, theme, mappings, images)
        if (existingIndex >= 0) saved[existingIndex] = imported else saved += imported
        persistCollections(saved)
    }

    fun reset(packageName: String) {
        val map = assignments().toMutableMap()
        map.remove(packageName)
        preferences.edit().putString(KEY_ASSIGNMENTS, encode(map)).apply()
        iconFile(packageName).delete()
    }

    fun resetAll() {
        preferences.edit().remove(KEY_ASSIGNMENTS).apply()
        iconDirectory.listFiles()?.forEach(File::delete)
    }

    private fun iconFile(packageName: String): File =
        File(
            iconDirectory,
            MessageDigest.getInstance("SHA-256")
                .digest(packageName.toByteArray())
                .take(12)
                .joinToString("") { "%02x".format(it) } + ".img",
        )

    private fun decodeIcon(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("The selected file is not a readable image.")
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) sampleSize *= 2
        return BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IOException("The selected image could not be decoded.")
    }

    private fun customImages(assignments: Map<String, String>): Map<String, String> {
        var totalBytes = 0
        return assignments.filterValues { it.startsWith("custom|") }.keys.associateWith { packageName ->
            val bytes = customIconFile(packageName).readBytes()
            if (bytes.size > MAX_CUSTOM_IMAGE_BYTES) {
                throw IOException("The custom icon for $packageName is too large to save in a collection.")
            }
            totalBytes += bytes.size
            if (totalBytes > MAX_COLLECTION_IMAGE_BYTES) {
                throw IOException("Custom icons in this collection exceed the 8 MB export limit.")
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    private fun saveCustomIconBytes(packageName: String, bytes: ByteArray) {
        validateIconBytes(bytes)
        val destination = iconFile(packageName)
        val staged = File(iconDirectory, "${destination.name}.restore")
        try {
            staged.writeBytes(bytes)
            if (!staged.renameTo(destination)) {
                throw IOException("Could not restore a custom app icon.")
            }
        } finally {
            staged.delete()
        }
    }

    private fun validateIconBytes(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_CUSTOM_IMAGE_BYTES) {
            throw IllegalArgumentException("A custom icon image in this collection is invalid or too large.")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IllegalArgumentException("A custom icon image in this collection is not readable.")
        }
    }

    fun setTheme(theme: AppIconTheme) {
        selectedTheme = theme
        preferences.edit().putString(KEY_THEME, theme.name).apply()
    }

    private fun encode(assignments: Map<String, String>): String =
        assignments.entries.joinToString(",") { "${it.key}=${it.value}" }

    private fun persistCollections(collections: List<IconCollection>) {
        val json = JSONArray()
        collections.forEach { json.put(it.toJson()) }
        preferences.edit().putString(KEY_COLLECTIONS, json.toString()).apply()
    }

    private fun IconCollection.toJson(): JSONObject {
        val mappingJson = JSONObject()
        assignments.forEach { (packageName, assignment) -> mappingJson.put(packageName, assignment) }
        val imageJson = JSONObject()
        customImages.forEach { (packageName, encoded) -> imageJson.put(packageName, encoded) }
        return JSONObject()
            .put("version", 1)
            .put("name", name)
            .put("theme", theme.name)
            .put("assignments", mappingJson)
            .put("customImages", imageJson)
    }

    private companion object {
        const val KEY_ASSIGNMENTS = "assignments"
        const val KEY_THEME = "theme"
        const val KEY_COLLECTIONS = "collections"
        const val MAX_CUSTOM_IMAGE_BYTES = 2 * 1024 * 1024
        const val MAX_CUSTOM_IMAGE_BASE64 = MAX_CUSTOM_IMAGE_BYTES * 4 / 3 + 16
        const val MAX_COLLECTION_IMAGE_BYTES = 8 * 1024 * 1024
        const val MAX_COLLECTION_CHARS = 12 * 1024 * 1024
    }
}
