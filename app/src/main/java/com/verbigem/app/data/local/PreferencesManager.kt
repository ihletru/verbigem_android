package com.verbigem.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.verbigem.app.data.model.OnlineModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "verbigem_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        private const val TAG = "PreferencesManager"
        private val KEY_THEME = stringPreferencesKey("theme_name")
        private val KEY_MODE = stringPreferencesKey("mode_name")
        private val KEY_UI_LANG = stringPreferencesKey("ui_lang")
        private val KEY_SRC_LANG = stringPreferencesKey("src_lang")
        private val KEY_DST_LANG = stringPreferencesKey("dst_lang")
        private val KEY_ENGINE = stringPreferencesKey("engine_choice")
        private val KEY_PROMPTED_FAST = booleanPreferencesKey("prompted_fast")
        private val KEY_PROMPTED_ACCURATE = booleanPreferencesKey("prompted_accurate")
        /**
         * Sync watermarks are PER ACCOUNT, keyed by uid.
         *
         * A single shared watermark silently breaks a second account: the pull is
         * `whereGreaterThan("updatedAt", lastSync)`, so a watermark left high by
         * account A hides every row of account B's history — B would look empty
         * even though Firestore has the data.
         */
        private fun keyLastSyncHistory(uid: String) = longPreferencesKey("last_sync_history_$uid")
        private fun keyLastSyncOcr(uid: String) = longPreferencesKey("last_sync_ocr_$uid")
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

        /**
         * User's own OpenRouter API key, used only for ":free" models. Empty means
         * "not set" — the three curated (paid) models then go through Verbigem's
         * backend proxy instead. Stored on-device only.
         *
         * **PER ACCOUNT** (v1.0.70): the key is a credential of one specific account.
         * Shared, account B could spend account A's OpenRouter quota without ever
         * entering a key. Same reasoning as the local database — see
         * [AccountScope] and `docs/architektura.md`.
         */
        private fun keyOpenRouterKey(uid: String) = stringPreferencesKey("openrouter_api_key_$uid")

        /**
         * Which online model the user picked (OpenRouter model id). Defaults to the
         * best quality-per-dollar curated model.
         */
        private val KEY_ONLINE_MODEL = stringPreferencesKey("online_model_id")

        /**
         * API wallet balance in cents, mirrored from the Firestore profile so any
         * ViewModel (not just Profile) can gate paid models without re-fetching
         * the user document. Written by [ProfileViewModel] whenever the profile
         * arrives; 0 means "no credits".
         *
         * **PER ACCOUNT** (v1.0.70): it mirrors `users/{uid}.walletCreditsCents`, so
         * sharing one slot meant account B could see account A's balance — and if B's
         * profile fetch ever failed, B kept A's balance and could use paid models for
         * free.
         */
        private fun keyWalletCents(uid: String) = longPreferencesKey("wallet_credits_cents_$uid")

        /**
         * The pre-v1.0.70 slots, without a uid. Kept only so
         * [adoptLegacyAccountPreferences] can hand them to the first account that
         * signs in; never read or written anywhere else.
         */
        private val LEGACY_OPENROUTER_KEY = stringPreferencesKey("openrouter_api_key")
        private val LEGACY_WALLET_CENTS = longPreferencesKey("wallet_credits_cents")

        /**
         * Set once, by whichever account adopts the legacy slots. Without it, a second
         * account signing in later would inherit the first account's OpenRouter key.
         */
        private val KEY_ACCOUNT_PREFS_ADOPTED = booleanPreferencesKey("account_prefs_adopted")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "calm" }
    val modeFlow: Flow<String> = context.dataStore.data.map { it[KEY_MODE] ?: "day" }
    val uiLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_UI_LANG] ?: "pl" }
    val srcLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_SRC_LANG] ?: "pl" }
    val dstLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_DST_LANG] ?: "en" }
    val engineFlow: Flow<String> = context.dataStore.data.map { it[KEY_ENGINE] ?: "localFast" }
    val promptedFastFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROMPTED_FAST] ?: false }
    val promptedAccurateFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROMPTED_ACCURATE] ?: false }
    /** Last successful history sync for [uid] (0 = never). */
    suspend fun lastSyncHistory(uid: String): Long =
        context.dataStore.data.map { it[keyLastSyncHistory(uid)] ?: 0L }.first()

    /** Last successful OCR sync for [uid] (0 = never). */
    suspend fun lastSyncOcr(uid: String): Long =
        context.dataStore.data.map { it[keyLastSyncOcr(uid)] ?: 0L }.first()
    val askedNotifPermFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ASKED_NOTIF_PERM] ?: false }
    val phoneGateSkippedAtFlow: Flow<Long> =
        context.dataStore.data.map { it[KEY_PHONE_GATE_SKIPPED_AT] ?: 0L }
    /**
     * ⚠️ `get()`, not a plain `val`. The preference name depends on the *current*
     * account ([AccountScope]), and this manager is a singleton created before anyone
     * signs in — a `val` would freeze the name at app start, i.e. always `..._anon`.
     * The getter re-reads the account on every emission, so it follows a sign-in.
     */
    val openRouterKeyFlow: Flow<String>
        get() = context.dataStore.data.map { it[keyOpenRouterKey(AccountScope.key())] ?: "" }
    val onlineModelFlow: Flow<String> = context.dataStore.data.map { it[KEY_ONLINE_MODEL] ?: OnlineModels.DEFAULT_ID }
    /** Per account — see [keyWalletCents]. Same `get()` reason as [openRouterKeyFlow]. */
    val walletCentsFlow: Flow<Long>
        get() = context.dataStore.data.map { it[keyWalletCents(AccountScope.key())] ?: 0L }

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
    suspend fun setLastSyncHistory(uid: String, ts: Long) =
        context.dataStore.edit { it[keyLastSyncHistory(uid)] = ts }

    suspend fun setLastSyncOcr(uid: String, ts: Long) =
        context.dataStore.edit { it[keyLastSyncOcr(uid)] = ts }
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

    /** Per account — see [keyOpenRouterKey]. */
    suspend fun setOpenRouterKey(key: String) =
        context.dataStore.edit { it[keyOpenRouterKey(AccountScope.key())] = key.trim() }

    /** Per account — see [keyOpenRouterKey]. */
    suspend fun clearOpenRouterKey() =
        context.dataStore.edit { it.remove(keyOpenRouterKey(AccountScope.key())) }

    suspend fun setOnlineModel(id: String) =
        context.dataStore.edit { it[KEY_ONLINE_MODEL] = id }

    /**
     * Per account — see [keyWalletCents].
     *
     * ⚠️ Takes the uid **explicitly**, unlike [setOpenRouterKey]. Callers here know which
     * account they just read the profile for (sync runs with a uid it was handed), and
     * writing one account's balance into another's slot would be exactly the leak this
     * per-account split exists to prevent.
     */
    suspend fun setWalletCents(uid: String, cents: Long) =
        context.dataStore.edit { it[keyWalletCents(uid)] = cents }

    /**
     * One-shot: hand the pre-v1.0.70 shared slots to the first account that signs in.
     *
     * The same idea as [AccountScope.adoptLegacyDatabase], and needed for the same
     * reason — the OpenRouter key lives on the device only (there is no cloud copy to
     * restore it from), so without this it would simply disappear from Profile after
     * the update. The wallet mirror does have a cloud copy and would re-heal itself,
     * but it is moved along for consistency.
     *
     * Guarded by [KEY_ACCOUNT_PREFS_ADOPTED] so the legacy values can only ever reach
     * ONE account — otherwise a second sign-in would pick up the first one's key.
     */
    suspend fun adoptLegacyAccountPreferences() {
        val uid = AccountScope.key()
        if (uid == AccountScope.ANON) return
        context.dataStore.edit { prefs ->
            if (prefs[KEY_ACCOUNT_PREFS_ADOPTED] == true) return@edit
            prefs[KEY_ACCOUNT_PREFS_ADOPTED] = true
            prefs[LEGACY_OPENROUTER_KEY]?.let {
                prefs[keyOpenRouterKey(uid)] = it
                Log.i(TAG, "legacy OpenRouter key adopted by $uid")
            }
            prefs.remove(LEGACY_OPENROUTER_KEY)
            prefs[LEGACY_WALLET_CENTS]?.let { prefs[keyWalletCents(uid)] = it }
            prefs.remove(LEGACY_WALLET_CENTS)
        }
    }
}
