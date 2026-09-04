package com.verbigem.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release variant: attest with Play Integrity.
 *
 * This is the only provider that actually proves the caller is a real install on a
 * real device. It requires the app's SHA-256 signing certificate to be registered in
 * Firebase Console → App Check → Apps, otherwise every request is rejected.
 *
 * The debug provider is deliberately NOT on the release classpath — a release build
 * that can self-attest is no protection at all.
 */
object AppCheckProvider {
    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
