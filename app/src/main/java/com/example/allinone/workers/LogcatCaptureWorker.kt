package com.example.allinone.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.allinone.utils.LogcatHelper

class LogcatCaptureWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        try {
            // Create a new LogcatHelper instance
            val logcatHelper = LogcatHelper(applicationContext)
            
            // Capture logcat
            logcatHelper.captureLogcat()
            
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }
} 