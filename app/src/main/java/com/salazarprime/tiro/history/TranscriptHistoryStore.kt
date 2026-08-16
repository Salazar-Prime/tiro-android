package com.salazarprime.tiro.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class Transcript(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
)

internal class TranscriptHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun all(): List<Transcript> {
        val stored = preferences.getString(HISTORY_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val text = item.optString("text").trim()
                    if (text.isNotEmpty()) {
                        add(
                            Transcript(
                                id = item.optString("id", UUID.randomUUID().toString()),
                                text = text,
                                createdAtMillis = item.optLong("createdAtMillis", 0L),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun latest(): Transcript? = all().firstOrNull()

    fun add(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        val updated = buildList {
            add(
                Transcript(
                    id = UUID.randomUUID().toString(),
                    text = cleaned,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            addAll(all().take(MAX_ITEMS - 1))
        }
        write(updated)
    }

    fun delete(id: String) {
        write(all().filterNot { it.id == id })
    }

    fun clear() {
        preferences.edit().remove(HISTORY_KEY).apply()
    }

    private fun write(items: List<Transcript>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", item.text)
                    .put("createdAtMillis", item.createdAtMillis),
            )
        }
        preferences.edit().putString(HISTORY_KEY, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "tiro_private_history"
        const val HISTORY_KEY = "transcripts"
        const val MAX_ITEMS = 25
    }
}

