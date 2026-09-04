package com.verbigem.app.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Keeps `users/{uid}/fcmTokens/{token}` in sync with the device's current FCM token.
 *
 * The document id IS the token. That is deliberate: the Cloud Function deletes
 * `users/{uid}/fcmTokens/${token}` by id when FCM reports the token as dead, so a
 * token that can only be looked up by query would have to be deleted with a
 * read-modify-write cycle instead of a single delete.
 *
 * One document per device means a user signed in on a phone and a tablet gets a push
 * on both — which is what you want — and signing out on one does not silence the other.
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    private val messaging = FirebaseMessaging.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** Reads the current token from the local FCM cache and stores it under [uid]. */
    suspend fun registerCurrentToken(uid: String) {
        if (uid.isBlank()) return
        val token = try {
            messaging.token.await()
        } catch (e: Exception) {
            // Offline or Play services missing: `onNewToken` will fire later and retry.
            Log.w(TAG, "Could not read FCM token", e)
            return
        }
        saveToken(uid, token)
    }

    suspend fun saveToken(uid: String, token: String) {
        if (uid.isBlank() || token.isBlank()) return
        try {
            firestore.collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
        } catch (e: Exception) {
            // Never fatal: a missing token degrades to "no push", it must not break
            // login or the chat itself.
            Log.w(TAG, "Could not save FCM token", e)
        }
    }

    /**
     * Removes this device's token, so logging out actually stops the pushes.
     *
     * Best-effort by design — if it fails the token simply goes stale and the Cloud
     * Function prunes it on the first failed send.
     */
    suspend fun unregisterCurrentToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return
        val token = try {
            messaging.token.await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read FCM token for removal", e)
            return
        }
        try {
            firestore.collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete FCM token", e)
        }
    }
}
