package com.verbigem.app.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.model.EngineChoice
import com.verbigem.app.data.model.FreeModelInfo
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.ModelTier
import com.verbigem.app.data.model.OnlineModels
import com.verbigem.app.data.model.UserProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.PhoneVerificationRepository
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.ModelDownloader
import com.verbigem.app.engine.OnlineApiEngine
import com.verbigem.app.notifications.FcmTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val preferencesManager = PreferencesManager(application)
    private val phoneVerificationRepository = PhoneVerificationRepository()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _nicknameInput = MutableStateFlow("")
    val nicknameInput: StateFlow<String> = _nicknameInput.asStateFlow()

    val currentTheme = preferencesManager.themeFlow
    val currentMode = preferencesManager.modeFlow
    val currentUiLang = preferencesManager.uiLangFlow

    /**
     * Whether this account can be found by people who have its number in their
     * address book. Read from Firestore rather than from Firebase Auth's provider
     * list, because "verified" is a fact the Cloud Function records — and it is the
     * same field the entry screen's gate reads.
     */
    val phoneVerified: StateFlow<Boolean> =
        phoneVerificationRepository.watchPhoneVerified(authRepository.currentUser?.uid.orEmpty())
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ---- Online translation model selection (OpenRouter) ----

    val onlineModelFlow: StateFlow<String> =
        preferencesManager.onlineModelFlow.stateIn(viewModelScope, SharingStarted.Eagerly, OnlineModels.DEFAULT_ID)

    val hasOwnKeyFlow: StateFlow<Boolean> =
        preferencesManager.openRouterKeyFlow.map { it.isNotBlank() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val openRouterKeyFlow: StateFlow<String> =
        preferencesManager.openRouterKeyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** API wallet balance in cents, mirrored from the Firestore profile. */
    val walletCents: StateFlow<Long> =
        preferencesManager.walletCentsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** Live list of OpenRouter ":free" models — only meaningful when [hasOwnKeyFlow] is true. */
    private val _freeModels = MutableStateFlow<List<FreeModelInfo>>(emptyList())
    val freeModels: StateFlow<List<FreeModelInfo>> = _freeModels.asStateFlow()

    private val _freeModelsLoading = MutableStateFlow(false)
    val freeModelsLoading: StateFlow<Boolean> = _freeModelsLoading.asStateFlow()

    /** Tiers the user has actually downloaded (FAST and/or ACCURATE). */
    private val _downloadedModels = MutableStateFlow<List<ModelTier>>(emptyList())
    val downloadedModels: StateFlow<List<ModelTier>> = _downloadedModels.asStateFlow()

    private val onlineEngine = OnlineApiEngine()

    init {
        val user = authRepository.currentUser
        if (user != null) {
            viewModelScope.launch {
                authRepository.watchProfile(user.uid).collect { profile ->
                    _userProfile.value = profile
                    if (profile != null && _nicknameInput.value.isBlank()) {
                        _nicknameInput.value = profile.nickname
                    }
                    // Mirror the wallet into DataStore so other screens can gate
                    // paid models without re-fetching the user document.
                    preferencesManager.setWalletCents(profile?.walletCreditsCents ?: 0L)
                }
            }
        }
        refreshDownloadedModels()
    }

    fun onNicknameChanged(nick: String) {
        _nicknameInput.value = nick
    }

    fun saveNickname() {
        val user = authRepository.currentUser ?: return
        val newNick = _nicknameInput.value.trim()
        if (newNick.isNotBlank()) {
            viewModelScope.launch {
                authRepository.updateProfile(user.uid, mapOf("nickname" to newNick))
            }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { preferencesManager.setTheme(theme) }
    }

    fun setMode(mode: String) {
        viewModelScope.launch { preferencesManager.setMode(mode) }
    }

    fun setUiLang(lang: String) {
        viewModelScope.launch {
            preferencesManager.setUiLang(lang)
            val user = authRepository.currentUser
            if (user != null) {
                authRepository.updateProfile(user.uid, mapOf("uiLang" to lang))
            }
        }
    }

    fun setSpeakLangs(src: LangCode, dst: LangCode) {
        viewModelScope.launch {
            preferencesManager.setPair(src.code, dst.code)
            val user = authRepository.currentUser
            if (user != null) {
                authRepository.updateProfile(
                    user.uid,
                    mapOf("speakLangSource" to src.code, "speakLangTarget" to dst.code)
                )
            }
        }
    }

    fun signOut() {
        // Remove this device's push token BEFORE the session is dropped — afterwards
        // `currentUser` is null and the token would be orphaned until the Cloud
        // Function notices a failed send and prunes it.
        viewModelScope.launch {
            FcmTokenManager.unregisterCurrentToken()
            authRepository.signOut()
        }
    }

    // ---- Online model selection ----

    fun setOnlineModel(id: String) {
        viewModelScope.launch { preferencesManager.setOnlineModel(id) }
    }

    fun saveOpenRouterKey(key: String) {
        val trimmed = key.trim()
        viewModelScope.launch {
            preferencesManager.setOpenRouterKey(trimmed)
            if (trimmed.isNotBlank()) refreshFreeModels(trimmed)
        }
    }

    fun clearOpenRouterKey() {
        viewModelScope.launch {
            preferencesManager.clearOpenRouterKey()
            _freeModels.value = emptyList()
        }
    }

    /** Pulls the current ":free" model list from OpenRouter (only with a key). */
    fun refreshFreeModels(key: String? = null) {
        viewModelScope.launch {
            val apiKey = key ?: preferencesManager.openRouterKeyFlow.first()
            if (apiKey.isBlank()) {
                _freeModels.value = emptyList()
                return@launch
            }
            _freeModelsLoading.value = true
            try {
                _freeModels.value = onlineEngine.fetchFreeModels(apiKey)
            } catch (e: Exception) {
                _freeModels.value = emptyList()
            } finally {
                _freeModelsLoading.value = false
            }
        }
    }

    // ---- Downloaded local models ----

    private fun refreshDownloadedModels() {
        val ctx: Application = getApplication()
        _downloadedModels.value = ModelTier.entries.filter {
            it != ModelTier.PRO_7B && HyMt2NativeEngine.isModelDownloaded(ctx, it)
        }
    }

    /** Removes the downloaded weights for [tier] and fixes the engine if needed. */
    fun deleteModel(tier: ModelTier) {
        ModelDownloader(getApplication()).deleteModel(tier)
        refreshDownloadedModels()
        // If the deleted model was the active engine, fall back to a usable one.
        viewModelScope.launch {
            val current = EngineChoice.fromId(preferencesManager.engineFlow.first())
            val fastOk = HyMt2NativeEngine.isModelDownloaded(getApplication(), ModelTier.FAST)
            val accOk = HyMt2NativeEngine.isModelDownloaded(getApplication(), ModelTier.ACCURATE)
            val newEngine = when {
                current.modelTier == tier ->
                    if (tier == ModelTier.FAST) {
                        if (accOk) EngineChoice.LOCAL_ACCURATE else EngineChoice.LOCAL_FAST
                    } else {
                        if (fastOk) EngineChoice.LOCAL_FAST else EngineChoice.LOCAL_ACCURATE
                    }
                current == EngineChoice.BOTH && (!fastOk || !accOk) ->
                    if (fastOk) EngineChoice.LOCAL_FAST else if (accOk) EngineChoice.LOCAL_ACCURATE else EngineChoice.LOCAL_FAST
                else -> null
            }
            newEngine?.let { preferencesManager.setEngine(it.id) }
        }
    }
}
