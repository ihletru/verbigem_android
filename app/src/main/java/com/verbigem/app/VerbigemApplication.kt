package com.verbigem.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.verbigem.app.data.repository.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VerbigemApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Startup data sync: profile + history + paid TTS config (Firestore, last-write-wins).
        appScope.launch {
            try {
                SyncManager(this@VerbigemApplication).syncNow()
            } catch (e: Exception) {
                android.util.Log.e("VerbigemApplication", "Startup sync failed", e)
            }
        }
    }
}
