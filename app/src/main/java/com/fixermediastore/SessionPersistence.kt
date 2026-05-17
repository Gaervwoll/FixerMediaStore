package com.fixermediastore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object SessionPersistence {

    private const val FILE_NAME = "scan_session.json"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_APPLY_INDEX = "apply_index"
    private const val KEY_LAST_ERROR = "last_error"

    fun save(
        context: Context,
        entries: List<ScanEntry>,
        applyIndex: Int = 0,
        lastError: String? = null
    ) {
        try {
            val root = JSONObject()
            root.put(KEY_APPLY_INDEX, applyIndex)
            if (lastError != null) root.put(KEY_LAST_ERROR, lastError)

            val array = JSONArray()
            for (entry in entries) {
                array.put(
                    JSONObject().apply {
                        put("name", entry.displayName)
                        put("doc", entry.documentUri.toString())
                        put("media", entry.mediaStoreUri?.toString())
                        entry.relativePathHint?.let { put("relPath", it) }
                        put("status", entry.status.name)
                        put("message", entry.message)
                        put("pattern", entry.matchedPattern)
                        entry.dateSource?.let { put("dateSource", it) }
                        val epoch = entry.parsedDateTime?.atZone(ZoneId.systemDefault())
                            ?.toInstant()?.toEpochMilli()
                        if (epoch != null) put("epoch", epoch)
                    }
                )
            }
            root.put(KEY_ENTRIES, array)

            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { stream ->
                stream.write(root.toString().toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // Не роняем приложение, если запись сессии не удалась
        }
    }

    fun load(context: Context): SavedSession? {
        return try {
            val text = context.openFileInput(FILE_NAME).bufferedReader().readText()
            if (text.isBlank()) return null
            val root = JSONObject(text)
            val array = root.getJSONArray(KEY_ENTRIES)
            val entries = mutableListOf<ScanEntry>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val epoch = if (o.has("epoch")) o.getLong("epoch") else null
                val dateTime = epoch?.let {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                }
                entries += ScanEntry(
                    displayName = o.getString("name"),
                    documentUri = android.net.Uri.parse(o.getString("doc")),
                    parsedDateTime = dateTime,
                    mediaStoreUri = o.optString("media").takeIf { it.isNotBlank() }
                        ?.let { android.net.Uri.parse(it) },
                    relativePathHint = o.optString("relPath").takeIf { it.isNotBlank() },
                    matchedPattern = o.optString("pattern").takeIf { it.isNotBlank() },
                    dateSource = o.optString("dateSource").takeIf { it.isNotBlank() },
                    status = ScanStatus.valueOf(o.getString("status")),
                    message = o.optString("message", "")
                )
            }
            SavedSession(
                entries = entries,
                applyIndex = root.optInt(KEY_APPLY_INDEX, 0),
                lastError = root.optString(KEY_LAST_ERROR).takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.deleteFile(FILE_NAME)
    }

    data class SavedSession(
        val entries: List<ScanEntry>,
        val applyIndex: Int,
        val lastError: String?
    )
}
