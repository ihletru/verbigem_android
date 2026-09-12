package com.verbigem.app.ui.screens.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.verbigem.app.BuildConfig
import com.verbigem.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Napędza bramkę weryfikacji e-maila (zamknięcie dziury "ktoś założy miliard kont").
 *
 * Po założeniu konta e-mail+hasło użytkownik ląduje na tym ekranie i nie wchodzi do
 * aplikacji, póki nie kliknie linku z maila. Google i telefon są już zweryfikowane
 * (ich `isEmailVerified` == true / brak providera "password"), więc omijają bramkę.
 */
class EmailVerificationViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _email = MutableStateFlow(auth.currentUser?.email ?: "")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _status = MutableStateFlow(EmailVerificationStatus.SENT)
    val status: StateFlow<EmailVerificationStatus> = _status.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        _email.value = auth.currentUser?.email ?: ""
    }

    private fun actionCodeSettings() = ActionCodeSettings.newBuilder()
        .setHandleCodeInApp(true)
        .setAndroidPackageName(BuildConfig.APPLICATION_ID, true, null)
        .build()

    /** Ponowne wysłanie linku aktywacyjnego. */
    fun resend(context: Context) {
        val user = auth.currentUser ?: run {
            _error.value = context.getString(R.string.email_verify_error_no_user)
            return
        }
        _isBusy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                user.sendEmailVerification(actionCodeSettings()).await()
                _status.value = EmailVerificationStatus.SENT
                _error.value = null
            } catch (e: Exception) {
                Log.w(TAG, "resend failed", e)
                _error.value = context.getString(R.string.email_verify_error_send, e.localizedMessage ?: "")
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Pobiera świeży stan z Firebase (użytkownik mógł w międzyczasie kliknąć link) i
     * sprawdza, czy e-mail jest już potwierdzony. Przy sukcesie wywołuje `onVerified`.
     */
    fun check(context: Context, onVerified: () -> Unit) {
        val user = auth.currentUser ?: run {
            _error.value = context.getString(R.string.email_verify_error_no_user)
            return
        }
        _isBusy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                user.reload().await()
                if (user.isEmailVerified) {
                    _status.value = EmailVerificationStatus.VERIFIED
                    onVerified()
                } else {
                    _status.value = EmailVerificationStatus.SENT
                    _error.value = context.getString(R.string.email_verify_not_yet)
                }
            } catch (e: Exception) {
                Log.w(TAG, "reload failed", e)
                _error.value = context.getString(R.string.email_verify_error_check, e.localizedMessage ?: "")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private companion object {
        private const val TAG = "EmailVerificationVM"
    }
}

enum class EmailVerificationStatus { SENT, VERIFIED }
