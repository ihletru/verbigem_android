package com.verbigem.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.verbigem.app.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {

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
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error ensuring profile", e)
            throw e
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
        firestore.collection("users").document(uid)
            .set(updatesWithTimestamp, SetOptions.merge())
            .await()
    }
}
