package com.example.allinone.utils

import android.content.Context
import com.example.allinone.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A centralized error handler for the application.
 * 
 * This class provides consistent error handling and user-friendly error messages
 * throughout the application. It handles common errors like network issues,
 * Firebase exceptions, and general application errors.
 */
@Singleton
class ErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Handles an error and returns a user-friendly error message.
     * 
     * @param throwable The error to handle
     * @param fallbackMessage Optional custom fallback message if no specific handling exists
     * @param logError Whether to log the error (defaults to true)
     * @return A user-friendly error message
     */
    fun handle(
        throwable: Throwable, 
        fallbackMessage: String? = null,
        logError: Boolean = true
    ): String {
        // Log the error if requested
        if (logError) {
            Timber.e(throwable, "Error occurred: ${throwable.message}")
        }
        
        // Report to Crashlytics for non-network errors in production
        if (throwable !is IOException && throwable !is SocketTimeoutException && throwable !is UnknownHostException) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(throwable)
        }
        
        // Return a user-friendly error message based on the type of error
        return when (throwable) {
            // Network errors
            is UnknownHostException, is SocketTimeoutException ->
                context.getString(R.string.error_network_unavailable)
            is IOException -> 
                context.getString(R.string.error_network)
                
            // Firebase Auth errors
            is FirebaseAuthException -> handleFirebaseAuthError(throwable)
            
            // Firebase Firestore errors
            is FirebaseFirestoreException -> handleFirestoreError(throwable)
            
            // Firebase Storage errors
            is StorageException -> handleStorageError(throwable)
            
            // Firebase Network errors
            is FirebaseNetworkException ->
                context.getString(R.string.error_firebase_network)
                
            // Default fallback
            else -> fallbackMessage ?: context.getString(R.string.error_unknown)
        }
    }
    
    /**
     * Handles Firebase Authentication errors.
     */
    private fun handleFirebaseAuthError(error: FirebaseAuthException): String {
        return when (error.errorCode) {
            "ERROR_USER_NOT_FOUND" -> context.getString(R.string.error_user_not_found)
            "ERROR_WRONG_PASSWORD" -> context.getString(R.string.error_wrong_password)
            "ERROR_USER_DISABLED" -> context.getString(R.string.error_user_disabled)
            "ERROR_TOO_MANY_REQUESTS" -> context.getString(R.string.error_too_many_requests)
            "ERROR_REQUIRES_RECENT_LOGIN" -> context.getString(R.string.error_requires_recent_login)
            else -> context.getString(R.string.error_authentication)
        }
    }
    
    /**
     * Handles Firestore database errors.
     */
    private fun handleFirestoreError(error: FirebaseFirestoreException): String {
        return when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                context.getString(R.string.error_permission_denied)
            FirebaseFirestoreException.Code.UNAVAILABLE -> 
                context.getString(R.string.error_service_unavailable)
            FirebaseFirestoreException.Code.ALREADY_EXISTS -> 
                context.getString(R.string.error_document_already_exists)
            FirebaseFirestoreException.Code.NOT_FOUND -> 
                context.getString(R.string.error_document_not_found)
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> 
                context.getString(R.string.error_quota_exceeded)
            else -> context.getString(R.string.error_database)
        }
    }
    
    /**
     * Handles Firebase Storage errors.
     */
    private fun handleStorageError(error: StorageException): String {
        return when (error.errorCode) {
            StorageException.ERROR_OBJECT_NOT_FOUND -> 
                context.getString(R.string.error_file_not_found)
            StorageException.ERROR_QUOTA_EXCEEDED -> 
                context.getString(R.string.error_storage_quota_exceeded)
            StorageException.ERROR_NOT_AUTHENTICATED -> 
                context.getString(R.string.error_not_authenticated)
            StorageException.ERROR_NOT_AUTHORIZED -> 
                context.getString(R.string.error_not_authorized)
            else -> context.getString(R.string.error_storage)
        }
    }
} 