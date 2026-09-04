package com.verbigem.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        private val KEY_LAST_SYNC_HISTORY = longPreferencesKey("last_sync_history")
        private val KEY_LAST_SYNC_OCR = longPreferencesKey("last_sync_ocr")
        /**
         * Whether the POST_NOTIFICATIONS prompt has already been shown.
         *
         * Android only prompts twice before it stops asking altogether, and the app
         * must not burn those on a user who opens the inbox before ever expecting a
         * message. Asking once and remembering is the whole point of the flag.
         */
        private val KEY_ASKED_NOTIF_PERM = booleanPreferencesKey("asked_notif_perm")

        /**
         * Timestamp of the moment the user dismissed the phone-verification gate.
         *
         * 0 means "never". A boolean would do the same job today, but storing the
         * moment keeps the door open for "ask again after a month" without a migration.
         */
        private val KEY_PHONE_GATE_SKIPPED_AT = longPreferencesKey("phone_gate_skipped_at")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "calm" }
    val modeFlow: Flow<String> = context.dataStore.data.map { it[KEY_MODE] ?: "day" }
    val uiLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_UI_LANG] ?: "pl" }
    val srcLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_SRC_LANG] ?: "pl" }
    val dstLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_DST_LANG] ?: "en" }
    val engineFlow: Flow<String> = context.dataStore.data.map { it[KEY_ENGINE] ?: "localFast" }
    val promptedFastFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROMPTED_FAST] ?: false }
    val promptedAccurateFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROMPTED_ACCURATE] ?: false }
    val lastSyncHistoryFlow: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_SYNC_HISTORY] ?: 0L }
    val lastSyncOcrFlow: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_SYNC_OCR] ?: 0L }
    val askedNotifPermFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ASKED_NOTIF_PERM] ?: false }
    val phoneGateSkippedAtFlow: Flow<Long> =
        context.dataStore.data.map { it[KEY_PHONE_GATE_SKIPPED_AT] ?: 0L }

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
    suspend fun setLastSyncHistory(ts: Long) = context.dataStore.edit { it[KEY_LAST_SYNC_HISTORY] = ts }
    suspend fun setLastSyncOcr(ts: Long) = context.dataStore.edit { it[KEY_LAST_SYNC_OCR] = ts }
    suspend fun setAskedNotifPerm(asked: Boolean) =
        context.dataStore.edit { it[KEY_ASKED_NOTIF_PERM] = asked }

    /**
     * Marks the phone-verification gate as dismissed.
     *
     * "Skip" is permanent on purpose: the chat works fine without a number, and a
     * user who said no once does not want to say it again on every visit. The way
     * back in is Profile → Phone number.
     */
    suspend fun setPhoneGateSkippedAt(ts: Long) =
        context.dataStore.edit { it[KEY_PHONE_GATE_SKIPPED_AT] = ts }
}
