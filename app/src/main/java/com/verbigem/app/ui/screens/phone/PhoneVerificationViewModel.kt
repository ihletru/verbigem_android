package com.verbigem.app.ui.screens.phone

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.verbigem.app.R
import com.verbigem.app.VerbigemApplication
import com.verbigem.app.data.PhoneNumbers
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.repository.PhoneCodeRequest
import com.verbigem.app.data.repository.PhoneLinkResult
import com.verbigem.app.data.repository.PhoneVerificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Where the user is in the flow. */
enum class PhoneVerificationStep { NUMBER, CODE, DONE }

/**
 * Drives the phone-verification gate (task 2.6).
 *
 * The number is never sent to our backend: Firebase Phone Auth proves ownership and
 * the `verifyPhone` Cloud Function reads the E.164 number out of the refreshed ID
 * token. The only thing this ViewModel decides is WHICH string to hand to Firebase —
 * and that has to be full E.164, or the hash it produces will not match the hash the
 * server computes for the same person.
 */
class PhoneVerificationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhoneVerificationRepository()
    private val preferences = PreferencesManager(application)

    private val _step = MutableStateFlow(PhoneVerificationStep.NUMBER)
    val step: StateFlow<PhoneVerificationStep> = _step.asStateFlow()

    private val _phoneInput = MutableStateFlow("")
    val phoneInput: StateFlow<String> = _phoneInput.asStateFlow()

    private val _codeInput = MutableStateFlow("")
    val codeInput: StateFlow<String> = _codeInput.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The E.164 number the code was actually sent to — shown back as confirmation. */
    private val _sentTo = MutableStateFlow<String?>(null)
    val sentTo: StateFlow<String?> = _sentTo.asStateFlow()

    /**
     * The country we will assume for a number typed without a country code, spelled
     * out so the user can spot a wrong guess before paying for an SMS.
     */
    val detectedCountry: String = run {
        val iso = PhoneNumbers.defaultCountryIsos(application).firstOrNull()
        if (iso == null) "" else Locale("", iso).getDisplayCountry(Locale.getDefault())
    }

    /** True once the account itself reports a verified number. */
    val phoneVerified: Flow<Boolean> =
        repository.watchPhoneVerified(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())

    fun onPhoneInputChanged(value: String) {
        _phoneInput.value = value
        _error.value = null
    }

    fun onCodeInputChanged(value: String) {
        _codeInput.value = value
        _error.value = null
    }

    fun sendCode(context: Context, resend: Boolean = false) {
        // Firebase Phone Auth needs the Activity itself, and all we get handed is a
        // Context. Unwrapping is the normal path; the application keeps the resumed
        // Activity as a fallback for the day some wrapper breaks the chain again.
        val activity = context.findActivity() ?: VerbigemApplication.foregroundActivity()
        if (activity == null) {
            _error.value = context.getString(R.string.phone_verify_error_send, "no activity")
            return
        }
        val e164 = resolveE164()
        if (e164 == null) {
            _error.value = context.getString(R.string.phone_verify_error_bad_number)
            return
        }
        _isBusy.value = true
        _error.value = null
        repository.sendCodeOrResend(activity, e164, resend) { result ->
            _isBusy.value = false
            when (result) {
                is PhoneCodeRequest.Sent -> {
                    _sentTo.value = e164
                    _step.value = PhoneVerificationStep.CODE
                }
                is PhoneCodeRequest.AutoVerified -> {
                    // Firebase udowodniło numer bez SMS-a. Nie ma na co czekać —
                    // dowiązujemy od razu i idziemy do ekranu „gotowe".
                    _sentTo.value = e164
                    _isBusy.value = true
                    viewModelScope.launch {
                        when (val linked =
                            repository.confirmWithCredential(result.credential)) {
                            is PhoneLinkResult.Linked -> {
                                _isBusy.value = false
                                _step.value = PhoneVerificationStep.DONE
                            }
                            is PhoneLinkResult.Failed -> {
                                _isBusy.value = false
                                _error.value =
                                    messageFor(context, linked.errorCode, linked.message)
                            }
                        }
                    }
                }
                is PhoneCodeRequest.Failed -> {
                    _error.value = messageFor(context, result.errorCode, result.message)
                }
            }
        }
    }

    /**
     * Zamienia surowy błąd Firebase na zdanie, z którego wynika, co zrobić.
     *
     * Teksty Firebase są pisane dla programisty ("This operation is not allowed.")
     * i — co gorsza — jeden kod skrywa kilka różnych usterek: pod
     * `ERROR_OPERATION_NOT_ALLOWED` (status 17006) przychodzi zarówno wyłączony
     * dostawca logowania, jak i **nieodblokowany region SMS**. Ten drugi przypadek
     * wyglądał identycznie jak problem z odciskiem SHA i przez to kosztował cały
     * wieczór. Tam, gdzie jeden kod skrywa dwie przyczyny, patrzymy jeszcze na
     * słowa w komunikacie.
     */
    private fun messageFor(context: Context, errorCode: String?, detail: String): String =
        when (errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" ->
                if (detail.contains("region", ignoreCase = true)) {
                    context.getString(R.string.phone_verify_error_region)
                } else {
                    context.getString(R.string.phone_verify_error_provider_off)
                }
            "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_APP_CREDENTIAL" ->
                context.getString(R.string.phone_verify_error_app_not_authorized)
            "ERROR_QUOTA_EXCEEDED", "ERROR_TOO_MANY_REQUESTS" ->
                context.getString(R.string.phone_verify_error_too_many)
            "ERROR_INVALID_PHONE_NUMBER" ->
                context.getString(R.string.phone_verify_error_bad_number)
            "ERROR_INVALID_VERIFICATION_CODE", "ERROR_SESSION_EXPIRED" ->
                context.getString(R.string.phone_verify_error_code)
            "ERROR_NETWORK_REQUEST_FAILED", "ERROR_WEB_NETWORK_REQUEST_FAILED" ->
                context.getString(R.string.phone_verify_error_network)
            else -> context.getString(R.string.phone_verify_error_send, detail)
        }

    fun confirm(context: Context) {
        val code = _codeInput.value.trim()
        if (code.length < 6) {
            _error.value = context.getString(R.string.phone_verify_error_code)
            return
        }
        _isBusy.value = true
        _error.value = null
        viewModelScope.launch {
            when (val result = repository.confirm(code)) {
                is PhoneLinkResult.Linked -> {
                    _isBusy.value = false
                    _step.value = PhoneVerificationStep.DONE
                }
                is PhoneLinkResult.Failed -> {
                    _isBusy.value = false
                    _error.value = messageFor(context, result.errorCode, result.message)
                }
            }
        }
    }

    /** "Not now" — the chat keeps working, the user is simply not findable. */
    fun skip() {
        viewModelScope.launch {
            preferences.setPhoneGateSkippedAt(System.currentTimeMillis())
        }
    }

    /**
     * Full E.164 for whatever the user typed.
     *
     * Multiple candidates come back when the number was written nationally and we had
     * to guess the country; the first is the best guess.
     */
    private fun resolveE164(): String? {
        val raw = _phoneInput.value
        val isos = PhoneNumbers.defaultCountryIsos(getApplication())
        return PhoneNumbers.e164Candidates(raw, isos).firstOrNull()
            ?: raw.trim().replace("\\s+".toRegex(), "").takeIf { it.startsWith("+") && it.length >= 8 }
    }
}

/**
 * `PhoneAuthProvider.verifyPhoneNumber` insists on a real Activity — it may need to
 * start its own verification flow — and a Composable only hands us a Context.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
