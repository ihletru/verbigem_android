package com.verbigem.app

import android.app.Application
import com.google.firebase.FirebaseApp

class VerbigemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
