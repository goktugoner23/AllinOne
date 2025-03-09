package com.example.allinone.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.allinone.R
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.data.WTStudent
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.Manifest

class ExpirationNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "expiration_notification_worker"
        private const val CHANNEL_ID = "expiration_channel"
        private const val NOTIFICATION_ID = 1
    }

    override suspend fun doWork(): Result {
        val repository = FirebaseRepository(applicationContext)
        
        val today = Calendar.getInstance().time
        // Use first() to get the current value from the StateFlow
        val students = repository.students.first()
        
        // Find students with expiring registrations (within 7 days)
        val expiringStudents = students.filter { student ->
            // Only check students with a valid end date
            student.endDate?.let { endDate ->
                val daysUntilExpiration = (endDate.time - today.time) / TimeUnit.DAYS.toMillis(1)
                daysUntilExpiration in 0..7
            } ?: false // If endDate is null, don't include the student
        }
        
        if (expiringStudents.isNotEmpty()) {
            // Check for notification permission on Android 13+
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Permission not required for Android 12 and below
            }
            
            if (hasNotificationPermission) {
                // Create notification
                val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Create the notification channel (required for Android 8.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Expiration Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notifications for WT student registration expirations"
                    }
                    notificationManager.createNotificationChannel(channel)
                }
                
                // Build the notification
                val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Registration Expiration")
                    .setContentText("${expiringStudents.size} registrations are expiring soon")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                
                // Show the notification
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
        }
        
        return Result.success()
    }
} 