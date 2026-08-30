package com.verbigem.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.verbigem.app.data.repository.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VerbigemApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncStarted = false

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Firebase Auth restores the signed-in session asynchronously AFTER this call, so reading
        // FirebaseAuth.getInstance().currentUser here is frequently null. Subscribe to auth state
        // changes and trigger the startup sync only once the user is actually available.
        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && !syncStarted) {
                syncStarted = true
                appScope.launch {
                    try {
                        SyncManager(this@VerbigemApplication).syncNow()
                    } catch (e: Exception) {
                        Log.e(TAG, "Startup sync failed", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "VerbigemApplication"
    }
}
