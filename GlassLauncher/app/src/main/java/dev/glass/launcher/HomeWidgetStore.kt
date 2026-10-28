package dev.glass.launcher

import android.content.Context

data class HomeWidgetEntry(
    val appWidgetId: Int,
    val page: Int,
    val heightDp: Int,
)

class HomeWidgetStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("launcher_widgets", Context.MODE_PRIVATE)

    fun load(): List<HomeWidgetEntry> =
        preferences.getString(KEY_WIDGETS, null)
            ?.split(",")
            ?.mapNotNull { value ->
                val parts = value.split(":")
                if (parts.size != 3) return@mapNotNull null
                val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                val page = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null
                val height = parts[2].toIntOrNull()?.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                    ?: return@mapNotNull null
                HomeWidgetEntry(id, page, height)
            }
            .orEmpty()

    fun save(entries: List<HomeWidgetEntry>) {
        val encoded = entries.joinToString(",") { "${it.appWidgetId}:${it.page}:${it.heightDp}" }
        preferences.edit().putString(KEY_WIDGETS, encoded).apply()
    }

    companion object {
        const val MIN_HEIGHT_DP = 120
        const val MAX_HEIGHT_DP = 360
        const val HEIGHT_STEP_DP = 40

        private const val KEY_WIDGETS = "home_widgets"
    }
}
