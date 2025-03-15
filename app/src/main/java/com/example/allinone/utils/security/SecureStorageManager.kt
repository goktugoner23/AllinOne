package com.example.allinone.utils.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure storage of sensitive data using encryption.
 * 
 * This class uses Android's Jetpack Security library to encrypt data
 * stored in SharedPreferences.
 */
@Singleton
class SecureStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey
    private val encryptedSharedPreferences: EncryptedSharedPreferences
    
    init {
        try {
            // Create or retrieve the master key for encryption
            masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            // Create the encrypted SharedPreferences instance
            encryptedSharedPreferences = EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
            
            Timber.d("SecureStorageManager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing SecureStorageManager")
            throw e
        }
    }
    
    /**
     * Stores a string value securely
     */
    fun storeString(key: String, value: String) {
        try {
            encryptedSharedPreferences.edit().putString(key, value).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error storing secure string for key: $key")
            throw e
        }
    }
    
    /**
     * Retrieves a stored string value
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return try {
            encryptedSharedPreferences.getString(key, defaultValue)
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving secure string for key: $key")
            defaultValue
        }
    }
    
    /**
     * Stores a boolean value securely
     */
    fun storeBoolean(key: String, value: Boolean) {
        try {
            encryptedSharedPreferences.edit().putBoolean(key, value).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error storing secure boolean for key: $key")
            throw e
        }
    }
    
    /**
     * Retrieves a stored boolean value
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            encryptedSharedPreferences.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving secure boolean for key: $key")
            defaultValue
        }
    }
    
    /**
     * Removes a stored value
     */
    fun remove(key: String) {
        try {
            encryptedSharedPreferences.edit().remove(key).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error removing secure value for key: $key")
            throw e
        }
    }
    
    /**
     * Clears all stored values
     */
    fun clearAll() {
        try {
            encryptedSharedPreferences.edit().clear().apply()
        } catch (e: Exception) {
            Timber.e(e, "Error clearing secure storage")
            throw e
        }
    }
    
    companion object {
        // Common keys for secure storage
        const val KEY_USER_ID = "user_id"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_PIN = "user_pin"
    }
} 