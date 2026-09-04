package com.verbigem.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug variant: attest with the debug provider.
 *
 * Play Integrity refuses to vouch for an APK that was not installed by the Play
 * Store, so a sideloaded build has no way to produce a real attestation. The debug
 * provider is the escape hatch: it mints a token that is only accepted after it has
 * been registered by hand in Firebase Console → App Check → Debug tokens.
 *
 * The token is printed to logcat once, tagged `FirebaseAppCheck`, as
 * `Enter this debug secret into the allow list in the Firebase console...`.
 * It is per-install, so it changes when the app is reinstalled.
 */
object AppCheckProvider {
    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
