package com.cjajk.phototranslate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val engineType: String = "baidu",
    val appId: String = "",
    val apiKey: String = ""
)

object SettingsStore {

    private val KEY_ENGINE = stringPreferencesKey("translate_engine")
    private val KEY_APP_ID = stringPreferencesKey("translate_app_id")
    private val KEY_API_KEY = stringPreferencesKey("translate_api_key")

    fun settings(context: Context): Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            engineType = prefs[KEY_ENGINE] ?: "baidu",
            appId = prefs[KEY_APP_ID] ?: "",
            apiKey = prefs[KEY_API_KEY] ?: ""
        )
    }

    suspend fun save(context: Context, engineType: String, appId: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENGINE] = engineType
            prefs[KEY_APP_ID] = appId
            prefs[KEY_API_KEY] = apiKey
        }
    }
}
