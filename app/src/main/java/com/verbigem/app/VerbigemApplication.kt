package com.verbigem.app

import android.app.Activity
import android.app.Application
import android.util.Log
import java.lang.ref.WeakReference
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.verbigem.app.data.ConnectivityObserver
import com.verbigem.app.data.repository.SyncManager
import com.verbigem.app.notifications.FcmTokenManager
import com.verbigem.app.notifications.VerbigemNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.verbigem.app.data.local.AccountScope
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.util.UiLangState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class VerbigemApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncStarted = false

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // App Check attaches an attestation to every callable this app makes. The
        // provider is chosen by variant — see src/debug vs src/release.
        //
        // NOTE ON ENFORCEMENT: `matchContacts` still runs with `enforceAppCheck: false`.
        // The APK we ship through auto-update is a DEBUG build, and Play Integrity will
        // not vouch for an app Play did not install, so enforcing today would reject
        // every real user. Flip it on together with the first Play release — the TODO
        // in functions/src/contacts.ts says exactly where.
        AppCheckProvider.install(FirebaseAppCheck.getInstance())

        // The local database is per account (see AccountScope). Install the context
        // here and seed the uid if Firebase already restored the session; the
        // auth-state listener below covers the case where it has not yet.
        AccountScope.install(this)
        AccountScope.bind(FirebaseAuth.getInstance().currentUser?.uid)

        // Język interfejsu musi być znany, ZANIM cokolwiek rozwiąże tekst. Ten kod
        // biegnie bez kompozycji i bez LocalContext (usługa FCM też), więc bez tego
        // kanał powiadomień i etykiety akcji były w języku TELEFONU, nie w wybranym.
        // DataStore czyta jeden mały plik — blokada jest krótka i ograniczona do 2 s.
        // Efekt uboczny na plus: znika mignięcie polskiego w pierwszej klatce UI.
        runBlocking {
            UiLangState.code = withTimeoutOrNull(2_000) {
                PreferencesManager(this@VerbigemApplication).uiLangFlow.first()
            } ?: UiLangState.code
        }

        // Create the channel at startup, not on the first push: a channel only appears
        // in the system notification settings once it exists, and a user who goes
        // looking for "how do I turn these off" before the first message should find it.
        VerbigemNotifications.ensureChannel(this)

        // Firebase Auth restores the signed-in session asynchronously AFTER this call, so reading
        // FirebaseAuth.getInstance().currentUser here is frequently null. Subscribe to auth state
        // changes and trigger the startup sync only once the user is actually available.
        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            // Fires on sign-in AND sign-out: keeps the local database pointed at the
            // account that is actually signed in, so a shared device never shows one
            // user the other's local history.
            AccountScope.bind(user?.uid)
            if (user != null) {
                // One-shot: the first account to sign in inherits the pre-v1.0.70
                // device-wide OpenRouter key and wallet mirror (see
                // PreferencesManager.adoptLegacyAccountPreferences). A flag inside
                // guarantees no later account can pick them up.
                appScope.launch {
                    try {
                        PreferencesManager(this@VerbigemApplication).adoptLegacyAccountPreferences()
                    } catch (e: Exception) {
                        Log.e(TAG, "Legacy preference adoption failed", e)
                    }
                }
            }
            if (user != null && !syncStarted) {
                syncStarted = true
                appScope.launch {
                    try {
                        SyncManager(this@VerbigemApplication).syncNow()
                    } catch (e: Exception) {
                        Log.e(TAG, "Startup sync failed", e)
                    }
                    // Push token registration belongs here, not in the messaging
                    // service: on a cold start `onNewToken` fires before FirebaseAuth
                    // has restored the session, so there would be no uid to attach it
                    // to. `onNewToken` still covers later rotations.
                    FcmTokenManager.registerCurrentToken(user.uid)
                }
            }
        }

        // Reactive sync: whenever the device (re)gains internet, push/pull immediately so offline
        // edits (additions/deletions queued in pending_deletes) propagate without an app restart.
        val connectivity = ConnectivityObserver(this)
        appScope.launch {
            connectivity.isOnline
                .distinctUntilChanged()
                .filter { it } // only act when we go online
                .collect {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        try {
                            SyncManager(this@VerbigemApplication).syncNow(uid)
                        } catch (e: Exception) {
                            Log.e(TAG, "Connectivity sync failed", e)
                        }
                    }
                }
        }
    }

    companion object {
        private const val TAG = "VerbigemApplication"

        /**
         * The Activity the user is currently looking at, held weakly.
         *
         * A fallback, not the main road: a few APIs insist on a real Activity (Firebase
         * Phone Auth does) while the code that needs them sits in a ViewModel and only
         * has a `Context`. Normally that Context unwraps back to the Activity, but any
         * future wrapper that forgets `ContextWrapper` breaks the chain again — and the
         * symptom is a terse "no activity" in the UI, with nothing in the logs.
         * MainActivity refreshes this on every resume.
         */
        @Volatile
        private var foreground: WeakReference<Activity>? = null

        fun noteForegroundActivity(activity: Activity) {
            foreground = WeakReference(activity)
        }

        fun foregroundActivity(): Activity? = foreground?.get()
    }
}
