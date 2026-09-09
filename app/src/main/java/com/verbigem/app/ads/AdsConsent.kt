package com.verbigem.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.verbigem.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Zgody reklamowe (UMP) + inicjalizacja AdMoba w jednej kolejności, której nie wolno
 * popsuć: najpierw zgoda, potem SDK, potem pierwsza reklama.
 *
 * Dlaczego w tej kolejności: Google wprost ostrzega, że SDK potrafi wstępnie pobrać
 * reklamy JUŻ W TRAKCIE inicjalizacji. Gdyby `MobileAds.initialize()` poleciało przed
 * uzyskaniem zgody, użytkownik z EOG dostałby request reklamowy bez zgody — a to jest
 * dokładnie ten błąd, za który AdMob potrafi zamknąć konto.
 *
 * Kolejność jest więc wymuszona kodem, nie konwencją: `refresh()` woła formularz,
 * dopiero potem sprawdza `canRequestAds()` i dopiero wtedy odpala SDK. Baner
 * (AdBannerView) czyta [adsReady] i nie zrobi nic, póki to nie nastąpi.
 */
object AdsConsent {

    private const val TAG = "AdsConsent"

    /**
     * `true` dopiero gdy (a) UMP pozwala na reklamy i (b) SDK jest zainicjowany.
     * `false` oznacza: nie pokazuj nic, nie miel requestami.
     */
    private val _adsReady = MutableStateFlow(false)
    val adsReady: StateFlow<Boolean> get() = _adsReady

    private val sdkInitializing = AtomicBoolean(false)
    @Volatile private var sdkInitialized = false

    @Volatile private var consentInfo: ConsentInformation? = null

    /**
     * Odśwież stan zgód — wołaj raz na start aplikacji z aktywnym Activity.
     *
     * Formularz UMP pokazuje się **tylko gdy jest wymagany** (EOG / UK / CH albo brak
     * zapisanej zgody). W Paragwaju, USA czy Chinach to zwykle no-op i przechodzimy
     * od razu do inicjalizacji SDK.
     */
    suspend fun refresh(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo = info

        withContext(Dispatchers.Main) {
            runCatching { updateConsentInfo(activity, info) }
                .onFailure { Log.w(TAG, "Consent info update failed", it) }

            runCatching { showConsentFormIfRequired(activity) }
                .onFailure { Log.w(TAG, "Consent form failed", it) }
        }

        // ⚠️ `canRequestAds()` zwraca false ZAWSZE, dopóki nie wywoła się
        // `requestConsentInfoUpdate()` — nawet gdy zgoda z poprzedniej sesji jest
        // wciąż ważna. Dlatego sprawdzamy je dopiero tutaj, po odświeżeniu.
        // Błąd odświeżenia nie jest blokadą: SDK UMP używa wtedy stanu z poprzedniej
        // sesji i to on decyduje.
        if (info.canRequestAds()) {
            initializeSdk(activity.applicationContext)
        } else {
            Log.i(TAG, "Ads not allowed yet (consent required or missing)")
        }
    }

    /**
     * Czy użytkownik MUSI mieć w aplikacji wejście do ustawień prywatności
     * (EOG/UK/CH). ⚠️ TODO: dodać kartę w Profilu wołającą [showPrivacyOptions] —
     * bez tego nie spełniamy wymogu Google'a dla użytkowników z EOG.
     */
    fun privacyOptionsRequired(): Boolean =
        consentInfo?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Otwiera formularz zmiany zgód (do podpięcia pod Profil — patrz wyżej). */
    suspend fun showPrivacyOptions(activity: Activity) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { err ->
                if (err != null) Log.w(TAG, "Privacy options error: ${err.errorCode} ${err.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private suspend fun updateConsentInfo(
        activity: Activity,
        info: ConsentInformation
    ) = suspendCancellableCoroutine { cont ->
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { if (cont.isActive) cont.resume(Unit) },
            { err ->
                Log.w(TAG, "Consent update error: ${err.errorCode} ${err.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        )
    }

    private suspend fun showConsentFormIfRequired(activity: Activity) =
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                if (err != null) Log.w(TAG, "Consent form error: ${err.errorCode} ${err.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        }

    /**
     * Inicjalizacja AdMoba — na wątku w tle, bo na głównym grozi ANR.
     * Raz na proces: `compareAndSet` odcina współbieżne wywołania, a po błędzie
     * flaga wraca na `false`, żeby kolejny start spróbował ponownie.
     */
    private suspend fun initializeSdk(context: Context) {
        if (sdkInitialized) return
        if (!sdkInitializing.compareAndSet(false, true)) return

        val ok = withContext(Dispatchers.IO) {
            runCatching {
                MobileAds.initialize(
                    context,
                    InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build()
                )
                true
            }.getOrElse {
                Log.e(TAG, "AdMob init failed", it)
                false
            }
        }

        if (ok) {
            sdkInitialized = true
            _adsReady.value = true
            Log.i(TAG, "AdMob ready (v${MobileAds.getVersion()})")
        } else {
            sdkInitializing.set(false)
        }
    }
}
