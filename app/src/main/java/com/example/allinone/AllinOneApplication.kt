package com.example.allinone

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.allinone.cache.CacheManager
import com.example.allinone.utils.GooglePlayServicesHelper
import com.example.allinone.utils.NetworkUtils
import com.google.firebase.FirebaseApp
import androidx.work.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.example.allinone.utils.LogcatHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AllinOneApplication : Application(), Configuration.Provider {
    
    // Lazy initialization of NetworkUtils
    val networkUtils by lazy { NetworkUtils(this) }
    
    // Lazy initialization of CacheManager
    val cacheManager by lazy { CacheManager(this) }
    
    // Initialize the logcat helper
    lateinit var logcatHelper: LogcatHelper
        private set
    
    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_DARK_MODE = "dark_mode_enabled"
        private const val TAG = "AllinOneApplication"
        
        private var instance: AllinOneApplication? = null
        
        fun getInstance(): AllinOneApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Apply the saved theme preference
        applyUserTheme()
        
        // Initialize Google Play Services first
        initGooglePlayServices()
        
        try {
            // Initialize Firebase
            if (!FirebaseApp.getApps(this).isEmpty()) {
                // Firebase already initialized
                Log.d(TAG, "Firebase already initialized")
            } else {
                // Initialize Firebase
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase initialized successfully")
            }
        } catch (e: Exception) {
            // Handle exception - this will prevent crashes if Google Play Services has issues
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }
        
        // Set custom cache expiration times
        // By default, most data expires after 10 minutes
        // For frequently changing data, we use shorter expiration times
        cacheManager.setCacheExpiration("events", 5 * 60 * 1000L) // 5 minutes for events
        
        // Initialize logcat helper
        logcatHelper = LogcatHelper(this)
        
        // Register to capture errors periodically
        CoroutineScope(Dispatchers.IO).launch {
            logcatHelper.captureLogcat()
        }
    }
    
    private fun initGooglePlayServices() {
        try {
            // Check if Google Play Services is available
            if (GooglePlayServicesHelper.isGooglePlayServicesAvailable(this)) {
                Log.d(TAG, "Google Play Services is available")
            } else {
                Log.w(TAG, "Google Play Services is not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Google Play Services: ${e.message}", e)
        }
    }
    
    private fun applyUserTheme() {
        // Get the saved theme preference
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        
        // Apply the appropriate theme mode
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    }
} 