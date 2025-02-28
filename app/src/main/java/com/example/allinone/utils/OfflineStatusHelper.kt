package com.example.allinone.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.LifecycleOwner
import com.example.allinone.R
import com.example.allinone.firebase.FirebaseRepository

/**
 * Helper class to manage the offline status view
 */
class OfflineStatusHelper(
    private val activity: Activity,
    private val repository: FirebaseRepository,
    private val lifecycleOwner: LifecycleOwner
) {
    
    private var offlineStatusCard: CardView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingOperationsCount: TextView? = null
    private var isNotificationVisible = false
    
    // Auto-hide runnable
    private val hideNotificationRunnable = Runnable {
        if (repository.isNetworkAvailable.value == true) {
            offlineStatusCard?.visibility = View.GONE
            isNotificationVisible = false
        }
    }
    
    /**
     * Initialize the offline status view
     * Call this method in the activity's onCreate method
     */
    fun initialize() {
        // Find the offline status card in the activity's layout
        offlineStatusCard = activity.findViewById(R.id.offline_status_card)
        pendingOperationsCount = activity.findViewById(R.id.pending_operations_count)
        
        // If the offline status card is not in the activity's layout, inflate it
        if (offlineStatusCard == null) {
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            val offlineView = activity.layoutInflater.inflate(R.layout.offline_status_view, rootView, false)
            rootView.addView(offlineView)
            
            offlineStatusCard = offlineView.findViewById(R.id.offline_status_card)
            pendingOperationsCount = offlineView.findViewById(R.id.pending_operations_count)
        }
        
        // Observe network status
        repository.isNetworkAvailable.observe(lifecycleOwner) { isAvailable ->
            if (!isAvailable) {
                // When offline, show notification
                offlineStatusCard?.visibility = View.VISIBLE
                isNotificationVisible = true
            } else if (isNotificationVisible) {
                // When back online, auto-hide after 2 seconds
                handler.removeCallbacks(hideNotificationRunnable)
                handler.postDelayed(hideNotificationRunnable, 2000)
            }
        }
        
        // Observe pending operations
        repository.pendingOperations.observe(lifecycleOwner) { count ->
            pendingOperationsCount?.text = activity.getString(R.string.pending_operations, count)
            
            // If there are no pending operations and we're online, hide the card
            if (count == 0 && repository.isNetworkAvailable.value == true) {
                if (isNotificationVisible) {
                    // Auto-hide after 2 seconds
                    handler.removeCallbacks(hideNotificationRunnable)
                    handler.postDelayed(hideNotificationRunnable, 2000)
                }
            } else if (count > 0) {
                // If there are pending operations, show the card
                offlineStatusCard?.visibility = View.VISIBLE
                isNotificationVisible = true
            }
        }
    }
    
    /**
     * Show a custom message in the offline status view
     */
    fun showMessage(message: String) {
        val messageView = offlineStatusCard?.findViewById<TextView>(R.id.offline_status_message)
        messageView?.text = message
        offlineStatusCard?.visibility = View.VISIBLE
        isNotificationVisible = true
        
        // Auto-hide after 2 seconds if online
        if (repository.isNetworkAvailable.value == true) {
            handler.removeCallbacks(hideNotificationRunnable)
            handler.postDelayed(hideNotificationRunnable, 2000)
        }
    }
    
    /**
     * Hide the offline status view
     */
    fun hide() {
        offlineStatusCard?.visibility = View.GONE
        isNotificationVisible = false
        handler.removeCallbacks(hideNotificationRunnable)
    }
} 