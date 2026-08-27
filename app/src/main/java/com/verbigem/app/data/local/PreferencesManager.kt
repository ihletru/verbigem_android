package com.verbigem.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "verbigem_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_name")
        private val KEY_MODE = stringPreferencesKey("mode_name")
        private val KEY_UI_LANG = stringPreferencesKey("ui_lang")
        private val KEY_SRC_LANG = stringPreferencesKey("src_lang")
        private val KEY_DST_LANG = stringPreferencesKey("dst_lang")
        private val KEY_ENGINE = stringPreferencesKey("engine_choice")
        private val KEY_PROMPTED_FAST = booleanPreferencesKey("prompted_fast")
        private val KEY_PROMPTED_ACCURATE = booleanPreferencesKey("prompted_accurate")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "calm" }
    val modeFlow: Flow<String> = context.dataStore.data.map { it[KEY_MODE] ?: "day" }
    val uiLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_UI_LANG] ?: "pl" }
    val srcLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_SRC_LANG] ?: "pl" }
    val dstLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_DST_LANG] ?: "en" }
    val engineFlow: Flow<String> = context.dataStore.data.map { it[KEY_ENGINE] ?: "localFast" }
    val promptedFastFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROMPTED_FAST] ?: false }
    val promptedAccurateFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROMPTED_ACCURATE] ?: false }

    suspend fun setTheme(theme: String) = context.dataStore.edit { it[KEY_THEME] = theme }
    suspend fun setMode(mode: String) = context.dataStore.edit { it[KEY_MODE] = mode }
    suspend fun setUiLang(lang: String) = context.dataStore.edit { it[KEY_UI_LANG] = lang }
    suspend fun setPair(src: String, dst: String) = context.dataStore.edit {
        it[KEY_SRC_LANG] = src
        it[KEY_DST_LANG] = dst
    }
    suspend fun setEngine(engine: String) = context.dataStore.edit { it[KEY_ENGINE] = engine }
    suspend fun setPromptedFast(prompted: Boolean) = context.dataStore.edit { it[KEY_PROMPTED_FAST] = prompted }
    suspend fun setPromptedAccurate(prompted: Boolean) = context.dataStore.edit { it[KEY_PROMPTED_ACCURATE] = prompted }
}
