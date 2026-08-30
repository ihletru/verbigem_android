package com.verbigem.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.verbigem.app.data.ConnectivityObserver
import com.verbigem.app.data.repository.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
    }
}
