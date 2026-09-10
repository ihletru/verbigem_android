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
import com.google.firebase.auth.FirebaseAuthException
import com.verbigem.app.R
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.verbigem.app.util.uiString

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

    /**
     * Zamienia wyjątek na komunikat w języku INTERFEJSU.
     *
     * `e.localizedMessage` zwraca **angielski tekst dla programisty** (np.
     * "The password is invalid or the user does not have a password."), więc
     * użytkownik widział angielski komunikat mimo polskiego UI. Kluczujemy po
     * stabilnym `errorCode`, nie po treści (treść zmienia się między wersjami SDK).
     */
    private fun authErrorMessage(e: Exception, fallbackRes: Int): String {
        val code = (e as? FirebaseAuthException)?.errorCode
        val res = when (code) {
            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_USER_NOT_FOUND" -> R.string.auth_error_wrong_password

            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.auth_error_email_in_use

            "ERROR_NETWORK_REQUEST_FAILED",
            "ERROR_WEB_NETWORK_REQUEST_FAILED" -> R.string.auth_error_network

            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
            "ERROR_CREDENTIAL_ALREADY_IN_USE" -> R.string.auth_error_unsupported_credential

            // Brak internetu potrafi przyjść jako ogólny błąd Credential Managera,
            // a nie jako FirebaseAuthException — łapiemy go po typie.
            null -> if (e is java.io.IOException) R.string.auth_error_network else fallbackRes

            else -> fallbackRes
        }
        return uiString(res)
    }

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
            _errorMessage.value = uiString(R.string.auth_error_password)
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
                _errorMessage.value = authErrorMessage(e, R.string.auth_error_login)
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
                    _errorMessage.value = uiString(R.string.auth_error_unsupported_credential)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google Sign-in failed", e)
                _errorMessage.value = authErrorMessage(e, R.string.auth_error_google)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
