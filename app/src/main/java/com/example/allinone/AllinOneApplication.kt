package com.example.allinone

import android.app.Application
import com.example.allinone.utils.NetworkUtils
import com.google.firebase.FirebaseApp

class AllinOneApplication : Application() {
    
    // Lazy initialization of NetworkUtils
    val networkUtils by lazy { NetworkUtils(this) }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
    }
} 