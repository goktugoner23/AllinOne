package com.example.allinone.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.allinone.utils.LogcatHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Worker that captures logcat entries periodically
 * Uses timeouts and proper dispatchers for efficient execution
 */
class LogcatCaptureWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    
    companion object {
        private const val TAG = "LogcatCaptureWorker"
        private const val EXECUTION_TIMEOUT_MS = 10_000L // 10 seconds timeout
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Create a new LogcatHelper instance
            val logcatHelper = LogcatHelper(applicationContext)
            
            // Capture logcat with timeout to prevent hanging
            val result = withTimeoutOrNull(EXECUTION_TIMEOUT_MS) {
                logcatHelper.captureLogcat()
                true
            }
            
            // If timeout occurred, log warning and return retry
            if (result == null) {
                Log.w(TAG, "Logcat capture timeout - operation took longer than ${EXECUTION_TIMEOUT_MS}ms")
                return@withContext Result.retry()
            }
            
            // Success
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing logcat: ${e.message}", e)
            // Retry only if it's a temporary issue
            if (isRetryableError(e)) {
                return@withContext Result.retry()
            }
            return@withContext Result.failure()
        }
    }
    
    /**
     * Determines if an error should trigger a retry attempt
     */
    private fun isRetryableError(e: Exception): Boolean {
        // IO errors or runtime exceptions might be temporary
        return e is java.io.IOException || 
               e is SecurityException || 
               e is RuntimeException
    }
} 