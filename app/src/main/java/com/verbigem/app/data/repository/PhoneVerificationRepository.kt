package com.verbigem.app.data.repository

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Co się stało z wysłaniem SMS-a. */
sealed interface PhoneCodeRequest {
    object Sent : PhoneCodeRequest
    data class Failed(val message: String) : PhoneCodeRequest
}

/** Co się stało z potwierdzeniem kodu. */
sealed interface PhoneLinkResult {
    object Linked : PhoneLinkResult
    data class Failed(val message: String) : PhoneLinkResult
}

/**
 * Weryfikacja numeru telefonu na **istniejącym** koncie (zadanie 2.6).
 *
 * Po co to w ogóle: bez zweryfikowanego numeru użytkownik nie trafia do
 * `phoneDirectory`, więc nikt z książki adresowej go nie znajdzie. Sam czat działa
 * bez tego — numer jest wyłącznie po to, żeby być odnajdywalnym.
 *
 * ### Dlaczego `requireSmsValidation(true)`
 *
 * Standardowy `verifyPhoneNumber` potrafi zweryfikować numer „w locie" (instant
 * validation) albo automatycznie przechwycić SMS i **samodzielnie zalogować
 * użytkownika**. W tym miejscu byłoby to katastrofą: Firebase wylogowałoby
 * dotychczasowe konto i zalogowało nowe, oparte tylko na numerze. `requireSmsValidation`
 * wyłącza obie te ścieżki — SMS zawsze przychodzi, a my sami wołamy
 * `linkWithCredential` na koncie, które jest już zalogowane.
 *
 * ### Kto zna numer
 *
 * Nikt w aplikacji nie wysyła numeru do chmury. Firebase Phone Auth wkłada
 * zweryfikowany numer E.164 do tokena, a funkcja `verifyPhone` czyta go z
 * `request.auth.token.phone_number`. Nie ma parametru do sfałszowania.
 * Po `linkWithCredential` **trzeba** odświeżyć token (`getIdToken(true)`) —
 * dotychczasowy powstał przed dodaniem numeru i nadal twierdzi, że go nie ma.
 */
class PhoneVerificationRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    @Volatile
    private var verificationId: String? = null

    @Volatile
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    /** Konto, na którym zaczynaliśmy — patrz komentarz o auto-logowaniu wyżej. */
    @Volatile
    private var startedUid: String? = null

    /**
     * Wysyła SMS z kodem.
     *
     * @param e164 numer w pełnej postaci międzynarodowej (`+595981123456`) —
     *             wynik `PhoneNumbers.e164Candidates`, nigdy to, co wpisał użytkownik
     */
    fun sendCode(
        activity: Activity,
        e164: String,
        onResult: (PhoneCodeRequest) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(PhoneCodeRequest.Failed("not signed in"))
            return
        }
        startedUid = user.uid

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Nie powinno się zdarzyć przy `requireSmsValidation(true)`, ale gdyby
                // jednak — nigdy nie podmieniajmy konta w tle.
                Log.w(TAG, "onVerificationCompleted fired despite requireSmsValidation")
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "Phone verification failed", e)
                onResult(PhoneCodeRequest.Failed(e.localizedMessage ?: e.message ?: ""))
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = id
                resendToken = token
                onResult(PhoneCodeRequest.Sent)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164)
            .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .requireSmsValidation(true)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Jeden punkt wejścia dla ekranu: pierwsza wysyłka albo ponowna. */
    fun sendCodeOrResend(
        activity: Activity,
        e164: String,
        resend: Boolean,
        onResult: (PhoneCodeRequest) -> Unit
    ) {
        if (resend) resendCode(activity, e164, onResult) else sendCode(activity, e164, onResult)
    }

    /** Ponowna wysyłka tego samego kodu — wymaga tokena z pierwszej próby. */
    fun resendCode(activity: Activity, e164: String, onResult: (PhoneCodeRequest) -> Unit) {
        val token = resendToken
        if (token == null) {
            sendCode(activity, e164, onResult)
            return
        }
        startedUid = auth.currentUser?.uid

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = Unit
            override fun onVerificationFailed(e: FirebaseException) {
                onResult(PhoneCodeRequest.Failed(e.localizedMessage ?: e.message ?: ""))
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = id
                resendToken = token
                onResult(PhoneCodeRequest.Sent)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164)
            .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .setForceResendingToken(token)
            .requireSmsValidation(true)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Potwierdza kod, dowiązuje numer do konta i każe chmurze to zapisać.
     *
     * Kolejność ma znaczenie: najpierw `linkWithCredential` (numer staje się faktem
     * w Firebase Auth), potem odświeżenie tokena, potem `verifyPhone` (fakt staje się
     * widoczny w Firestore).
     */
    suspend fun confirm(code: String): PhoneLinkResult {
        val id = verificationId
        val user = auth.currentUser
        if (id == null || user == null) {
            return PhoneLinkResult.Failed("no pending verification")
        }
        if (user.uid != startedUid) {
            // Nie powinno się zdarzyć, ale gdyby konto się zmieniło, dowiązanie
            // numeru do obcego użytkownika byłoby nieodwracalne.
            return PhoneLinkResult.Failed("account changed")
        }

        return try {
            val credential = PhoneAuthProvider.getCredential(id, code)
            user.linkWithCredential(credential).await()

            // Bez tego `verifyPhone` zobaczy token bez `phone_number` — token jest
            // cache'owany i powstał, zanim numer dołączył do konta.
            user.getIdToken(true).await()

            functions.getHttpsCallable(FUNCTION_VERIFY_PHONE).call().await()
            verificationId = null
            resendToken = null
            PhoneLinkResult.Linked
        } catch (e: Exception) {
            Log.w(TAG, "Linking the phone number failed", e)
            PhoneLinkResult.Failed(e.localizedMessage ?: e.message ?: "")
        }
    }

    /**
     * Czy konto ma zweryfikowany numer.
     *
     * Czytamy `users/{uid}` — pole zapisuje wyłącznie funkcja, klient ma do niego
     * odczyt, więc ekran nie musi zgadywać z informacji o dostawcach w Firebase Auth.
     */
    fun watchPhoneVerified(uid: String): Flow<Boolean> = callbackFlow {
        if (uid.isBlank()) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.getBoolean("phoneVerified") == true)
            }
        awaitClose { listener.remove() }
    }

    companion object {
        private const val TAG = "PhoneVerificationRepo"
        private const val FUNCTION_VERIFY_PHONE = "verifyPhone"
        private const val TIMEOUT_SECONDS = 60L
    }
}
