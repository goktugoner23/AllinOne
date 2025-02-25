package com.example.allinone.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.utils.BackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Worker class for creating automatic backups
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "automatic_backup_worker"
        private const val TAG = "BackupWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting automatic backup...")
            
            // Create repository and backup helper
            val repository = FirebaseRepository(applicationContext)
            val backupHelper = BackupHelper(applicationContext, repository)
            
            // Wait for data to be loaded
            repository.refreshAllData()
            
            // Create the backup
            val backupFile = backupHelper.createBackup()
            
            if (backupFile != null && backupFile.exists()) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
                
                Log.d(TAG, "Automatic backup created successfully at $timestamp: ${backupFile.absolutePath}")
                
                // Clean up old backups (keep only the last 5)
                val backupFiles = backupHelper.getBackupFiles()
                if (backupFiles.size > 5) {
                    // Sort by last modified date (oldest first)
                    val filesToDelete = backupFiles.sortedBy { it.lastModified() }
                        .take(backupFiles.size - 5)
                    
                    for (file in filesToDelete) {
                        if (backupHelper.deleteBackup(file)) {
                            Log.d(TAG, "Deleted old backup: ${file.name}")
                        }
                    }
                }
                
                Result.success()
            } else {
                Log.e(TAG, "Failed to create automatic backup")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during automatic backup", e)
            Result.failure()
        }
    }
} 