package com.example.allinone.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the device's network connectivity state.
 * 
 * This class provides a Flow of network connectivity updates and methods
 * to check the current connectivity state.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Returns a Flow that emits the current network connectivity state and subsequent changes.
     * 
     * @return A Flow of Boolean values where true means the device is connected to the internet
     */
    val networkStatus: Flow<Boolean> = callbackFlow {
        // Initial network state
        trySend(isCurrentlyConnected())
        
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("Network available")
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                Timber.d("Network lost")
                trySend(isCurrentlyConnected()) // Check if other networks are available
            }
            
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Timber.d("Network capabilities changed: hasInternet=$hasInternet, isValidated=$isValidated")
                
                if (hasInternet && isValidated) {
                    trySend(true)
                }
            }
        }
        
        // Register callback to observe all network types
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        // Unregister callback when the flow collector is cancelled
        awaitClose {
            Timber.d("Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged() // Only emit when the state changes
    
    /**
     * Checks if the device is currently connected to the internet.
     * 
     * @return true if the device has an active network connection with internet capability
     */
    fun isCurrentlyConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Checks if the device has a metered network connection (like mobile data).
     * 
     * This is useful for restricting large data transfers that might incur costs.
     * 
     * @return true if the current active network is metered
     */
    fun isActiveNetworkMetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        
        // If it has WIFI or ETHERNET capability, it's not metered
        return !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }
} 