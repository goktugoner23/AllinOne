package com.example.allinone

import android.app.Application
import android.util.Log
import com.example.allinone.cache.CacheManager
import com.example.allinone.utils.NetworkUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import androidx.work.Configuration

class AllinOneApplication : Application(), Configuration.Provider {
    
    // Lazy initialization of NetworkUtils
    val networkUtils by lazy { NetworkUtils(this) }
    
    // Lazy initialization of CacheManager
    val cacheManager by lazy { CacheManager(this) }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize Firebase
            if (!FirebaseApp.getApps(this).isEmpty()) {
                // Firebase already initialized
                Log.d("AllinOneApplication", "Firebase already initialized")
            } else {
                // Initialize Firebase
                FirebaseApp.initializeApp(this)
                Log.d("AllinOneApplication", "Firebase initialized successfully")
            }
        } catch (e: Exception) {
            // Handle exception - this will prevent crashes if Google Play Services has issues
            Log.e("AllinOneApplication", "Error initializing Firebase: ${e.message}", e)
        }
        
        // Set custom cache expiration times
        // By default, most data expires after 10 minutes
        // For frequently changing data, we use shorter expiration times
        cacheManager.setCacheExpiration("events", 5 * 60 * 1000L) // 5 minutes for events
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    }
} 