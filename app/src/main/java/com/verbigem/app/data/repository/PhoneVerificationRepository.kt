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
    /** SMS wysłany — kod wpisuje użytkownik. */
    object Sent : PhoneCodeRequest

    /**
     * Firebase potwierdziło numer **bez** SMS-a (instant verification) i od razu
     * dało credential. Brak SMS-a w tym wariancie to nie błąd, tylko oszczędzona
     * minuta użytkownika — trzeba go obsłużyć, bo inaczej ekran czeka na kod,
     * który nigdy nie przyjdzie.
     */
    data class AutoVerified(val credential: PhoneAuthCredential) : PhoneCodeRequest

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
 * ### ☠️ `requireSmsValidation(true)` — NIE WOLNO go tu użyć (crash v37)
 *
 * Ta flaga istnieje **wyłącznie dla MFA** i `PhoneAuthOptions.Builder.build()`
 * odrzuca ją bez sesji wieloskładnikowej:
 * `IllegalArgumentException: You cannot require sms validation without setting a
 * multi-factor session.` (`firebase-auth` 23.0.0). Przez rok nie wyszło to na jaw,
 * bo `sendCode` kończył się wcześniej na `findActivity() == null` (patrz
 * `LocalizedContext`) i `build()` nigdy się nie wykonywał.
 *
 * Obawa, którą flaga miała gasić, była zresztą nieprawdziwa: Firebase **samo nigdy
 * nie loguje użytkownika**. `onVerificationCompleted` dostarcza credential i nic
 * więcej — konto podmienia dopiero `signInWithCredential`, którego tu nie ma. My
 * wołamy wyłącznie `linkWithCredential`, czyli dopisujemy numer do konta, które
 * już jest zalogowane.
 *
 * Instant verification (bez SMS-a) trzeba za to **obsłużyć**: inaczej ekran czeka
 * na kod, który nigdy nie przyjdzie. Robi to `PhoneCodeRequest.AutoVerified`.
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
                // Instant verification: Firebase udowodniło własność numeru bez
                // wysyłania SMS-a. To NIE jest automatyczne logowanie — Firebase
                // samo nigdy nie podmienia zalogowanego konta, dopóki aplikacja nie
                // wywoła `signInWithCredential`. My wołamy wyłącznie
                // `linkWithCredential`, więc numer trafia do konta, które już jest.
                Log.i(TAG, "Phone verified instantly, no SMS needed")
                onResult(PhoneCodeRequest.AutoVerified(credential))
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
            .build()

        try {
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            Log.w(TAG, "verifyPhoneNumber threw immediately", e)
            onResult(PhoneCodeRequest.Failed(e.localizedMessage ?: e.message ?: "unknown"))
        }
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
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                onResult(PhoneCodeRequest.AutoVerified(credential))
            }
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
            .build()

        try {
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            Log.w(TAG, "verifyPhoneNumber (resend) threw immediately", e)
            onResult(PhoneCodeRequest.Failed(e.localizedMessage ?: e.message ?: "unknown"))
        }
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
        return link(PhoneAuthProvider.getCredential(id, code))
    }

    /** Wariant bez SMS-a: Firebase samo skleiło credential (instant verification). */
    suspend fun confirmWithCredential(credential: PhoneAuthCredential): PhoneLinkResult {
        val user = auth.currentUser
        if (user == null) {
            return PhoneLinkResult.Failed("not signed in")
        }
        if (user.uid != startedUid) {
            return PhoneLinkResult.Failed("account changed")
        }
        return link(credential)
    }

    private suspend fun link(credential: PhoneAuthCredential): PhoneLinkResult {
        val user = auth.currentUser
            ?: return PhoneLinkResult.Failed("not signed in")

        return try {
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
