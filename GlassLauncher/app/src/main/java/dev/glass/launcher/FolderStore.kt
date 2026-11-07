package dev.glass.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A home-screen folder: its id, title and the packages inside it, in order. */
data class FolderData(val id: String, val name: String, val packages: List<String>) {
    /** The id used as a home-layout "package name" so folders persist next to normal apps. */
    val key: String get() = FOLDER_PREFIX + id

    companion object {
        const val FOLDER_PREFIX = "folder:"
        fun isFolderKey(key: String) = key.startsWith(FOLDER_PREFIX)
    }
}

class FolderStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("folders", Context.MODE_PRIVATE)

    fun load(): List<FolderData> = try {
        val array = JSONArray(prefs.getString("data", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val pkgs = o.getJSONArray("pkgs")
            FolderData(o.getString("id"), o.getString("name"), (0 until pkgs.length()).map { pkgs.getString(it) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(folders: List<FolderData>) {
        val array = JSONArray()
        folders.forEach { f ->
            array.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("pkgs", JSONArray(f.packages)),
            )
        }
        prefs.edit().putString("data", array.toString()).apply()
    }
}
