package com.cjajk.phototranslate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cjajk.phototranslate.model.FavoriteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "favorites")

object FavoritesStore {

    private val KEY_FAVORITES = stringPreferencesKey("favorites_json")
    private const val MAX_ITEMS = 500

    fun favorites(context: Context): Flow<List<FavoriteItem>> =
        context.dataStore.data.map { prefs -> parse(prefs[KEY_FAVORITES] ?: "[]") }

    suspend fun add(context: Context, item: FavoriteItem) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY_FAVORITES] ?: "[]").toMutableList()
            list.removeAll { it.text == item.text }
            list.add(0, item)
            prefs[KEY_FAVORITES] = toJson(list.take(MAX_ITEMS))
        }
    }

    suspend fun remove(context: Context, text: String) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY_FAVORITES] ?: "[]").filterNot { it.text == text }
            prefs[KEY_FAVORITES] = toJson(list)
        }
    }

    private fun parse(json: String): List<FavoriteItem> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FavoriteItem(
                text = o.getString("text"),
                type = o.getString("type"),
                createdAt = o.getLong("createdAt")
            )
        }
    }.getOrDefault(emptyList())

    private fun toJson(items: List<FavoriteItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("text", item.text)
                    .put("type", item.type)
                    .put("createdAt", item.createdAt)
            )
        }
        return arr.toString()
    }
}
