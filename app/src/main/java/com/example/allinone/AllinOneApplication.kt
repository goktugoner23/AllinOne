package com.example.allinone

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import com.example.allinone.cache.CacheManager
import com.example.allinone.utils.GooglePlayServicesHelper
import com.example.allinone.utils.LogcatHelper
import com.example.allinone.utils.NetworkUtils
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AllinOneApplication : Application(), Configuration.Provider {
    
    @Inject lateinit var networkUtils: NetworkUtils
    
    @Inject lateinit var cacheManager: CacheManager
    
    @Inject lateinit var logcatHelper: LogcatHelper
    
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
        
        // Initialize Timber for better logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Plant a crash reporting tree in production
            Timber.plant(CrashReportingTree())
        }
        
        // Apply the saved theme preference
        applyUserTheme()
        
        // Initialize Google Play Services first
        initGooglePlayServices()
        
        try {
            // Initialize Firebase
            if (!FirebaseApp.getApps(this).isEmpty()) {
                // Firebase already initialized
                Timber.d("Firebase already initialized")
            } else {
                // Initialize Firebase
                FirebaseApp.initializeApp(this)
                Timber.d("Firebase initialized successfully")
            }
        } catch (e: Exception) {
            // Handle exception - this will prevent crashes if Google Play Services has issues
            Timber.e(e, "Error initializing Firebase: ${e.message}")
        }
        
        // Set custom cache expiration times
        // By default, most data expires after 10 minutes
        // For frequently changing data, we use shorter expiration times
        cacheManager.setCacheExpiration("events", 5 * 60 * 1000L) // 5 minutes for events
        
        // Register to capture errors periodically
        CoroutineScope(Dispatchers.IO).launch {
            logcatHelper.captureLogcat()
        }
    }
    
    private fun initGooglePlayServices() {
        try {
            // Check if Google Play Services is available
            if (GooglePlayServicesHelper.isGooglePlayServicesAvailable(this)) {
                Timber.d("Google Play Services is available")
            } else {
                Timber.w("Google Play Services is not available on this device")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking Google Play Services: ${e.message}")
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
    
    /**
     * Custom Timber tree for crash reporting in production
     */
    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return // Don't log verbose or debug in production
            }
            
            // Log to Firebase Crashlytics
            if (t != null) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t)
            } else if (priority >= Log.WARN) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log(message)
            }
        }
    }
} 