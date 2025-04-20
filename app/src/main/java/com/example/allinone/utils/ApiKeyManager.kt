package com.example.allinone.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Utility class to securely store and retrieve API keys
 */
class ApiKeyManager(context: Context) {
    private val TAG = "ApiKeyManager"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val KEY_ALIAS = "BinanceApiKeyAlias"
    private val SHARED_PREFS_NAME = "BinanceApiPrefs"
    private val ENCRYPTED_API_KEY = "encrypted_api_key"
    private val ENCRYPTED_SECRET_KEY = "encrypted_secret_key"
    private val IV_API_KEY = "iv_api_key"
    private val IV_SECRET_KEY = "iv_secret_key"

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        SHARED_PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * Save API key and secret key securely
     */
    fun saveApiKeys(apiKey: String, secretKeyStr: String): Boolean {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Create or get the key
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }

            // Get the key for encryption
            val encryptionKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey

            // Encrypt API key
            val cipherApiKey = Cipher.getInstance("AES/GCM/NoPadding")
            cipherApiKey.init(Cipher.ENCRYPT_MODE, encryptionKey)
            val encryptedApiKey = cipherApiKey.doFinal(apiKey.toByteArray())
            val ivApiKey = cipherApiKey.iv

            // Encrypt Secret key
            val cipherSecretKey = Cipher.getInstance("AES/GCM/NoPadding")
            cipherSecretKey.init(Cipher.ENCRYPT_MODE, encryptionKey)
            val encryptedSecretKey = cipherSecretKey.doFinal(secretKeyStr.toByteArray())
            val ivSecretKey = cipherSecretKey.iv

            // Save encrypted data and IVs
            sharedPreferences.edit()
                .putString(ENCRYPTED_API_KEY, Base64.encodeToString(encryptedApiKey, Base64.DEFAULT))
                .putString(IV_API_KEY, Base64.encodeToString(ivApiKey, Base64.DEFAULT))
                .putString(ENCRYPTED_SECRET_KEY, Base64.encodeToString(encryptedSecretKey, Base64.DEFAULT))
                .putString(IV_SECRET_KEY, Base64.encodeToString(ivSecretKey, Base64.DEFAULT))
                .apply()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API keys", e)
            return false
        }
    }

    /**
     * Get the stored API key
     */
    fun getApiKey(): String? {
        return getDecryptedValue(ENCRYPTED_API_KEY, IV_API_KEY)
    }

    /**
     * Get the stored Secret key
     */
    fun getSecretKey(): String? {
        return getDecryptedValue(ENCRYPTED_SECRET_KEY, IV_SECRET_KEY)
    }

    /**
     * Check if API keys are stored
     */
    fun hasApiKeys(): Boolean {
        return sharedPreferences.contains(ENCRYPTED_API_KEY) &&
               sharedPreferences.contains(ENCRYPTED_SECRET_KEY)
    }

    /**
     * Clear stored API keys
     */
    fun clearApiKeys() {
        sharedPreferences.edit()
            .remove(ENCRYPTED_API_KEY)
            .remove(IV_API_KEY)
            .remove(ENCRYPTED_SECRET_KEY)
            .remove(IV_SECRET_KEY)
            .apply()
    }

    /**
     * Helper method to decrypt a value
     */
    private fun getDecryptedValue(encryptedKey: String, ivKey: String): String? {
        try {
            val encryptedData = Base64.decode(
                sharedPreferences.getString(encryptedKey, null), Base64.DEFAULT
            )
            val iv = Base64.decode(
                sharedPreferences.getString(ivKey, null), Base64.DEFAULT
            )

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val decryptedData = cipher.doFinal(encryptedData)
                return String(decryptedData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving API key", e)
        }
        return null
    }
}
