package com.verbigem.app.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.UserProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.PhoneVerificationRepository
import com.verbigem.app.notifications.FcmTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        val user = authRepository.currentUser
        if (user != null) {
            viewModelScope.launch {
                authRepository.watchProfile(user.uid).collect { profile ->
                    _userProfile.value = profile
                    if (profile != null && _nicknameInput.value.isBlank()) {
                        _nicknameInput.value = profile.nickname
                    }
                }
            }
        }
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
}
