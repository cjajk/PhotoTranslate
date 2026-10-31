package com.cjajk.phototranslate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "search_history")

object SearchHistoryStore {

    private val KEY_HISTORY = stringPreferencesKey("search_history_json")
    private const val MAX_ITEMS = 20

    fun history(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs -> parse(prefs[KEY_HISTORY] ?: "[]") }

    suspend fun add(context: Context, query: String) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY_HISTORY] ?: "[]").toMutableList()
            list.remove(query)
            list.add(0, query)
            prefs[KEY_HISTORY] = toJson(list.take(MAX_ITEMS))
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs -> prefs[KEY_HISTORY] = "[]" }
    }

    private fun parse(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())

    private fun toJson(items: List<String>): String = JSONArray(items).toString()
}
