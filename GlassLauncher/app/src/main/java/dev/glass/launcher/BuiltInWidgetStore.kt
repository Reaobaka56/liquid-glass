package dev.glass.launcher

import android.content.Context
import java.util.UUID

enum class BuiltInWidgetType(val label: String) {
    CLOCK("Clock"),
    CALENDAR("Calendar"),
    BATTERY("Battery"),
    PHOTO("Photo"),
    WEATHER("Weather"),
    MUSIC("Music"),
}

enum class BuiltInWidgetSize(val label: String, val heightDp: Int) {
    SMALL("Small", 92),
    MEDIUM("Medium", 148),
    LARGE("Large", 224),
}

data class BuiltInWidgetEntry(
    val id: String,
    val type: BuiltInWidgetType,
    val page: Int,
    val size: BuiltInWidgetSize,
)

class BuiltInWidgetStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("launcher_widgets", Context.MODE_PRIVATE)

    fun load(): List<BuiltInWidgetEntry> =
        preferences.getString(KEY_BUILT_IN_WIDGETS, null)
            ?.split(",")
            ?.mapNotNull { encoded ->
                val parts = encoded.split(":")
                if (parts.size != 4) return@mapNotNull null
                val id = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
                val type = BuiltInWidgetType.entries.firstOrNull { it.name == parts[1] }
                    ?: return@mapNotNull null
                val page = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null
                val size = BuiltInWidgetSize.entries.firstOrNull { it.name == parts[3] }
                    ?: return@mapNotNull null
                BuiltInWidgetEntry(id, type, page, size)
            }
            .orEmpty()

    fun save(entries: List<BuiltInWidgetEntry>) {
        val encoded = entries.joinToString(",") { "${it.id}:${it.type.name}:${it.page}:${it.size.name}" }
        preferences.edit().putString(KEY_BUILT_IN_WIDGETS, encoded).apply()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
        private const val KEY_BUILT_IN_WIDGETS = "built_in_widgets"
    }
}
