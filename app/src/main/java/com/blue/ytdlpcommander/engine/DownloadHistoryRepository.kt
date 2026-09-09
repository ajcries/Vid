package com.blue.ytdlpcommander.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String,
    val url: String,
    val title: String,
    val outputUri: String?,
    val success: Boolean,
    val timestamp: Long,
    val mode: DownloadMode = DownloadMode.VIDEO
)

/**
 * Persists finished downloads (success or failure) so the History screen
 * survives app restarts. The original source [HistoryEntry.url] is always
 * kept, even on success, specifically so the user can re-trigger a download
 * later if the saved file gets deleted.
 */
object DownloadHistoryRepository {
    private const val PREFS_NAME = "download_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 300

    fun add(context: Context, entry: HistoryEntry) {
        val current = getAll(context).toMutableList()
        current.add(0, entry)
        while (current.size > MAX_ENTRIES) current.removeAt(current.size - 1)
        save(context, current)
    }

    fun remove(context: Context, id: String) {
        val current = getAll(context).filterNot { it.id == id }
        save(context, current)
    }

    fun updateOutputUri(context: Context, id: String, newUri: String?) {
        val current = getAll(context).map { if (it.id == id) it.copy(outputUri = newUri, success = newUri != null) else it }
        save(context, current)
    }

    fun getAll(context: Context): List<HistoryEntry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val modeName = o.optString("mode", DownloadMode.VIDEO.name)
                HistoryEntry(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    title = o.optString("title", ""),
                    outputUri = o.optString("outputUri", null).takeIf { !o.isNull("outputUri") },
                    success = o.optBoolean("success", false),
                    timestamp = o.optLong("timestamp", 0L),
                    mode = try { DownloadMode.valueOf(modeName) } catch (_: Exception) { DownloadMode.VIDEO }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { entry ->
            val o = JSONObject()
            o.put("id", entry.id)
            o.put("url", entry.url)
            o.put("title", entry.title)
            o.put("outputUri", entry.outputUri)
            o.put("success", entry.success)
            o.put("timestamp", entry.timestamp)
            o.put("mode", entry.mode.name)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
