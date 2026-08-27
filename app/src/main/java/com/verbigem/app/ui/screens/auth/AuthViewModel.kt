package com.verbigem.app.ui.screens.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.verbigem.app.R
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val preferencesManager = PreferencesManager(application)
    private val credentialManager = CredentialManager.create(application)

    val currentUiLang = preferencesManager.uiLangFlow

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isSignUp = MutableStateFlow(false)
    val isSignUp: StateFlow<Boolean> = _isSignUp.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onEmailChanged(text: String) { _email.value = text }
    fun onPasswordChanged(text: String) { _password.value = text }
    fun toggleAuthMode() { _isSignUp.value = !_isSignUp.value }
    fun setUiLang(lang: String) {
        viewModelScope.launch { preferencesManager.setUiLang(lang) }
    }

    fun submit(onSuccess: () -> Unit) {
        val em = _email.value.trim()
        val pass = _password.value.trim()
        if (em.isBlank() || pass.length < 6) {
            _errorMessage.value = "Hasło musi mieć co najmniej 6 znaków"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                if (_isSignUp.value) {
                    authRepository.signUpEmail(em, pass)
                } else {
                    authRepository.signInEmail(em, pass)
                }
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Błąd logowania"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // default_web_client_id from google-services.json is the Web OAuth client
                // (client_type 3, "Web client (auto created by Google Service)").
                // Credential Manager requires a Web client ID for Firebase id-token verification.
                val webClientId = context.getString(R.string.default_web_client_id)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    authRepository.signInWithGoogle(googleIdTokenCredential.idToken)
                    onSuccess()
                } else {
                    _errorMessage.value = "Nieobsługiwany typ poświadczenia"
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google Sign-in failed", e)
                _errorMessage.value = e.localizedMessage ?: "Błąd logowania przez Google"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
