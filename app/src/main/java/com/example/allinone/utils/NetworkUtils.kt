package com.example.allinone.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class to monitor network connectivity
 */
class NetworkUtils(private val context: Context) {
    private val TAG = "NetworkUtils"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isNetworkAvailable = MutableLiveData<Boolean>()
    
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable
    
    // Track network state changes to reduce flapping
    private var consecutiveFailures = 0
    private var consecutiveSuccesses = 0
    private val requiredConsecutiveCount = 2 // How many consecutive same results needed before changing state
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Don't immediately set network available, confirm with an actual connection test
            CoroutineScope(Dispatchers.IO).launch {
                if (isActiveNetworkConnected()) {
                    Log.d(TAG, "Network callback - connection available")
                    // Increment success counter
                    consecutiveSuccesses++
                    consecutiveFailures = 0
                    
                    // Only update LiveData after consecutive successes
                    if (consecutiveSuccesses >= requiredConsecutiveCount) {
                        withContext(Dispatchers.Main) {
                            _isNetworkAvailable.value = true
                        }
                    }
                }
            }
        }
        
        override fun onLost(network: Network) {
            CoroutineScope(Dispatchers.IO).launch {
                // Don't immediately report network lost - verify first
                delay(1000) // Brief delay
                
                if (!isActiveNetworkConnected()) {
                    Log.d(TAG, "Network callback - connection lost")
                    consecutiveFailures++
                    consecutiveSuccesses = 0
                    
                    // Only update LiveData after consecutive failures
                    if (consecutiveFailures >= requiredConsecutiveCount) {
                        withContext(Dispatchers.Main) {
                            _isNetworkAvailable.value = false
                        }
                    }
                }
            }
        }
    }
    
    init {
        // Check initial network state with actual connection test
        CoroutineScope(Dispatchers.IO).launch {
            val initialState = isNetworkConnected()
            withContext(Dispatchers.Main) {
                _isNetworkAvailable.value = initialState
            }
            Log.d(TAG, "Initial network state: ${if (initialState) "Connected" else "Disconnected"}")
            
            // Register for network changes
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            try {
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering network callback: ${e.message}")
            }
        }
    }
    
    /**
     * Checks if network is connected by both checking system connectivity status
     * and attempting an actual connection to a reliable server.
     * This is now a suspend function to ensure it's called from a coroutine.
     */
    suspend fun isNetworkConnected(): Boolean = withContext(Dispatchers.IO) {
        // First check system connectivity
        if (!isActiveNetworkConnected()) {
            return@withContext false
        }
        
        // Then try an actual connection to a reliable server
        return@withContext try {
            val connection = URL("https://www.google.com").openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            val connected = connection.responseCode == 200
            connection.disconnect()
            connected
        } catch (e: IOException) {
            Log.d(TAG, "Connection test failed: ${e.message}")
            false
        }
    }
    
    /**
     * Checks if the system reports active network connectivity.
     * This is the non-suspend version which is safer to call from any thread/place.
     * It may still cause NetworkOnMainThreadException in some edge cases - use with caution.
     */
    fun isActiveNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Suspend version of isActiveNetworkConnected that safely runs on a background thread.
     * Use this version when calling from a suspend function or coroutine.
     */
    suspend fun isActiveNetworkConnectedSuspend(): Boolean = withContext(Dispatchers.IO) {
        isActiveNetworkConnected()
    }
    
    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Network callback was not registered or already unregistered
            Log.e(TAG, "Error unregistering network callback: ${e.message}")
        }
    }
} 