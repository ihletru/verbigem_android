package com.verbigem.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.verbigem.app.data.model.PublicProfile
import com.verbigem.app.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {

    companion object {
        /**
         * Profile fields mirrored into `usersPublic/{uid}`. Only these trigger a
         * rewrite of the public projection, so an unrelated profile save doesn't
         * cost an extra document read.
         */
        private val PUBLIC_FIELDS = setOf(
            "nickname", "email", "photoURL", "uiLang", "speakLangSource", "speakLangTarget"
        )
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInEmail(email: String, pass: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, pass).await()
        val user = result.user ?: throw IllegalStateException("Nie udało się zalogować")
        ensureProfile(user)
        return user
    }

    suspend fun signUpEmail(email: String, pass: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        val user = result.user ?: throw IllegalStateException("Nie udało się utworzyć konta")
        ensureProfile(user)
        return user
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: throw IllegalStateException("Logowanie Google nieudane")
        ensureProfile(user)
        return user
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun ensureProfile(user: FirebaseUser) {
        val docRef = firestore.collection("users").document(user.uid)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                // Only fields the Firestore security rules allow a client to CREATE:
                // signed-in owner, plan == 'free', and NO wallet/noAdsUntil/usage keys.
                val profile = mapOf(
                    "uid" to user.uid,
                    "nickname" to (user.displayName ?: user.email?.substringBefore("@") ?: "user-${user.uid.take(5)}"),
                    "email" to (user.email ?: ""),
                    "photoURL" to (user.photoUrl?.toString() ?: "🙂"),
                    "uiLang" to "pl",
                    "speakLangSource" to "pl",
                    "speakLangTarget" to "en",
                    "plan" to "free",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(profile).await()
                syncPublicProfile(
                    UserProfile(
                        uid = user.uid,
                        nickname = profile["nickname"] as String,
                        email = profile["email"] as String,
                        photoURL = profile["photoURL"] as String,
                        uiLang = "pl",
                        speakLangSource = "pl",
                        speakLangTarget = "en"
                    )
                )
            } else {
                // Self-healing backfill: accounts created before `usersPublic` existed
                // get their public projection on the next sign-in, no script needed.
                snap.toObject(UserProfile::class.java)?.let { syncPublicProfile(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error ensuring profile", e)
            throw e
        }
    }

    /**
     * Mirrors the searchable part of a profile into `usersPublic/{uid}`.
     *
     * This is the fix for "search for people doesn't work at all": `users/{uid}` is
     * owner-only, so the only way another user can find this account is through a
     * separate, world-readable-to-signed-in document.
     */
    suspend fun syncPublicProfile(profile: UserProfile) {
        val uid = profile.uid
        if (uid.isBlank()) return
        try {
            firestore.collection("usersPublic").document(uid)
                .set(PublicProfile.from(profile), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Non-fatal: a stale public profile only degrades search, it never blocks
            // sign-in or translation. Never let this bubble up into the login flow.
            android.util.Log.w("AuthRepository", "Could not sync usersPublic/$uid", e)
        }
    }

    /** Reads another user's public profile. Null when the document doesn't exist yet. */
    suspend fun getPublicProfile(uid: String): PublicProfile? {
        if (uid.isBlank()) return null
        return try {
            firestore.collection("usersPublic").document(uid).get().await()
                .toObject(PublicProfile::class.java)
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "Could not read usersPublic/$uid", e)
            null
        }
    }

    fun watchProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val sub = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val profile = snapshot?.toObject(UserProfile::class.java)
                trySend(profile)
            }
        awaitClose { sub.remove() }
    }

    suspend fun updateProfile(uid: String, updates: Map<String, Any>) {
        val updatesWithTimestamp = updates.toMutableMap().apply {
            this["updatedAt"] = FieldValue.serverTimestamp()
        }
        val docRef = firestore.collection("users").document(uid)
        docRef.set(updatesWithTimestamp, SetOptions.merge()).await()

        // Re-read instead of trusting `updates` alone: the public projection must be
        // built from the full stored profile, so a partial update (e.g. nickname only)
        // doesn't blank out the fields it didn't mention.
        if (updates.keys.any { it in PUBLIC_FIELDS }) {
            docRef.get().await().toObject(UserProfile::class.java)
                ?.copy(uid = uid)
                ?.let { syncPublicProfile(it) }
        }
    }
}
