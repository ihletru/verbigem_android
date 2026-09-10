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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 *
 * ⚠️ Fallback inicjalizacji po 3s: jeśli UMP nadal zwraca `canRequestAds() == false`
 * **mimo że zgoda nie jest wymagana** (użytkownik poza EOG), inicjalizujemy SDK mimo
 * wszystko. Typowa przyczyna: nowe konto AdMob bez skonfigurowanego „Privacy &
 * messaging" — UMP nie ma skąd wziąć formularza i oddaje `false` w każdym kraju, więc
 * baner wisiałby na placeholderze na zawsze.
 *
 * Fallback NIE dotyczy EOG/UK: tam `consentStatus == REQUIRED` oznacza brak zgody i
 * inicjalizacja byłaby złamaniem zasad (→ utrata konta AdMob). Baner zostaje pusty.
 */
object AdsConsent {

    private const val TAG = "AdsConsent"

    /**
     * `true` dopiero gdy (a) UMP pozwala na reklamy i (b) SDK jest zainicjowany.
     * `false` oznacza: nie pokazuj nic, nie miel requestami.
     */
    private val _adsReady = MutableStateFlow(false)
    val adsReady: StateFlow<Boolean> get() = _adsReady

    /**
     * Czy użytkownik MUSI mieć w aplikacji wejście do ustawień prywatności
     * (EOG / UK / CH). Flow, nie zwykła funkcja: Profil czyta to w Compose i musi
     * zareagować, gdy UMP zmieni zdanie po pokazaniu formularza.
     */
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> get() = _privacyOptionsRequired

    private val sdkInitializing = AtomicBoolean(false)
    @Volatile private var sdkInitialized = false

    @Volatile private var consentInfo: ConsentInformation? = null

    /**
     * Własny scope na fallback init (3s po refresh), żeby nie blokować wywołującego
     * i nie trzymać referencji do Activity po jego zniszczeniu.
     */
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        // Szczegółowy dump stanu UMP — ułatwia diagnostykę, kiedy baner nie chce
        // się pokazać. grep `AdsConsent` w logcat wystarczy.
        Log.i(
            TAG,
            "UMP status: canRequestAds=${info.canRequestAds()}, " +
                "consentStatus=${info.consentStatus}, " +
                "privacyOptionsRequirementStatus=${info.privacyOptionsRequirementStatus}, " +
                "isConsentFormAvailable=${info.isConsentFormAvailable}",
        )

        if (info.canRequestAds()) {
            initializeSdk(activity.applicationContext)
            return
        }

        // `canRequestAds() == false` ma dwie zupełnie różne przyczyny i tylko jedną
        // wolno obejść:
        //
        // 1. Użytkownik EOG/UK bez zgody (consentStatus == REQUIRED) — TU NIE WOLNO
        //    inicjalizować SDK. Zostaje placeholder; to jedyny legalny wariant.
        // 2. Nowe konto AdMob bez skonfigurowanego „Privacy & messaging" / Funding
        //    Choices — UMP nie ma skąd wziąć formularza, więc zgłasza błąd i
        //    `canRequestAds()` zostaje false NA ZAWSZE, w każdym kraju. Baner wisiałby
        //    na placeholderze do końca świata. Tu awaryjna inicjalizacja jest właściwa.
        val needsRealConsent =
            info.consentStatus == ConsentInformation.ConsentStatus.REQUIRED ||
                info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        if (needsRealConsent) {
            Log.w(TAG, "Brak zgody w EOG/UK — nie inicjalizuję SDK, baner zostaje pusty.")
            return
        }

        val appCtx = activity.applicationContext
        Log.w(
            TAG,
            "UMP canRequestAds() == false mimo że zgoda nie jest wymagana — " +
                "czekam 3s, potem inicjalizuję SDK awaryjnie (typowa przyczyna: " +
                "brak konfiguracji 'Privacy & messaging' w AdMob dla tej aplikacji).",
        )
        initScope.launch {
            delay(3_000)
            if (!sdkInitialized) {
                Log.w(TAG, "UMP nadal zablokowane po 3s — inicjalizuję SDK mimo wszystko.")
                initializeSdk(appCtx)
            }
        }
    }

    /** Otwiera formularz zmiany zgód (karta „Ustawienia prywatności" w Profilu). */
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

