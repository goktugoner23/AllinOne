package com.example.allinone.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        // Ensure we're on the main thread for all UI operations
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e("OfflineStatusHelper", "initialize called from non-UI thread. Current thread: ${Thread.currentThread().name}")
            handler.post { 
                Log.d("OfflineStatusHelper", "Re-initializing on UI thread: ${Thread.currentThread().name}")
                initialize() 
            }
            return
        }
        
        Log.d("OfflineStatusHelper", "Initializing on thread: ${Thread.currentThread().name}")
        safeInitializeOnMainThread()
    }
    
    /**
     * Safer version of initialization that's resistant to threading issues
     */
    private fun safeInitializeOnMainThread() {
        try {
            // Double-check we're on the main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Log.e("OfflineStatusHelper", "safeInitializeOnMainThread called from non-UI thread: ${Thread.currentThread().name}")
                handler.post { safeInitializeOnMainThread() }
                return
            }
            
            Log.d("OfflineStatusHelper", "Safe initialization running on UI thread: ${Thread.currentThread().name}")
            
            // Find the offline status card in the activity's layout
            offlineStatusCard = activity.findViewById(R.id.offline_status_card)
            pendingOperationsCount = activity.findViewById(R.id.pending_operations_count)
            
            // If the offline status card is not in the activity's layout, inflate it
            if (offlineStatusCard == null) {
                Log.d("OfflineStatusHelper", "Inflating offline status view")
                
                try {
                    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                    
                    // Inflate the view on the UI thread
                    activity.runOnUiThread {
                        try {
                            Log.d("OfflineStatusHelper", "Inflating view on UI thread: ${Thread.currentThread().name}")
                            val offlineView = activity.layoutInflater.inflate(R.layout.offline_status_view, null)
                            
                            // Check if view is already added
                            if (offlineView.parent == null) {
                                rootView.addView(offlineView)
                                Log.d("OfflineStatusHelper", "View successfully added to root")
                            } else {
                                Log.d("OfflineStatusHelper", "View already has a parent, not adding again")
                            }
                            
                            offlineStatusCard = offlineView.findViewById(R.id.offline_status_card)
                            pendingOperationsCount = offlineView.findViewById(R.id.pending_operations_count)
                            
                            // Now setup observers
                            setupObservers()
                        } catch (e: Exception) {
                            Log.e("OfflineStatusHelper", "Error inflating view: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OfflineStatusHelper", "Error adding view: ${e.message}", e)
                }
            } else {
                Log.d("OfflineStatusHelper", "Offline status card already exists, setting up observers")
                // Setup observers directly if view already exists
                setupObservers()
            }
        } catch (e: Exception) {
            Log.e("OfflineStatusHelper", "Error in safeInitializeOnMainThread: ${e.message}", e)
        }
    }
    
    /**
     * Setup LiveData observers for network and pending operations
     */
    private fun setupObservers() {
        // Observe network status
        repository.isNetworkAvailable.observe(lifecycleOwner) { isAvailable ->
            if (!isAvailable) {
                // When offline, show notification
                handler.post {
                    offlineStatusCard?.visibility = View.VISIBLE
                    isNotificationVisible = true
                }
            } else if (isNotificationVisible) {
                // When back online, auto-hide after 2 seconds
                handler.removeCallbacks(hideNotificationRunnable)
                handler.postDelayed(hideNotificationRunnable, 2000)
            }
        }
        
        // Observe pending operations
        repository.pendingOperations.observe(lifecycleOwner) { count ->
            handler.post {
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
    }
    
    /**
     * Show a custom message in the offline status view
     */
    fun showMessage(message: String) {
        // Ensure we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showMessage(message) }
            return
        }
        
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
        // Ensure we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hide() }
            return
        }
        
        offlineStatusCard?.visibility = View.GONE
        isNotificationVisible = false
        handler.removeCallbacks(hideNotificationRunnable)
    }
} 