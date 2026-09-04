package com.verbigem.app.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.ProfileLinks
import com.verbigem.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * „Mój kod QR" (Faza 4.1).
 *
 * Kod zawiera link do własnego profilu (`ProfileLinks.forUser`). Nickname ładujemy
 * leniwie, bo profil z Firestore może nie być gotowy w momencie tworzenia VM —
 * UI reaguje na `nickname` tak samo jak na wynik ładowania w innych ekranach.
 *
 * `uid`/`url` liczone raz (są stałe dla sesji), `nickname` to flow.
 */
class MyQrViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()

    val uid: String = authRepository.currentUser?.uid ?: ""
    val url: String = ProfileLinks.forUser(uid)

    private val _nickname = MutableStateFlow<String?>(null)
    val nickname: StateFlow<String?> = _nickname.asStateFlow()

    init {
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                _nickname.value = authRepository.getPublicProfile(uid)?.nickname
            }
        }
    }
}
