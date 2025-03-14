package com.example.allinone.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Helper class to properly initialize Google Play Services
 */
class GooglePlayServicesHelper {
    companion object {
        private const val TAG = "GooglePlayServicesHelper"
        private const val REQUEST_CODE_RESOLUTION = 9000

        /**
         * Check if Google Play Services is available
         */
        fun isGooglePlayServicesAvailable(context: Context): Boolean {
            val apiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
            return resultCode == ConnectionResult.SUCCESS
        }

        /**
         * Get a properly initialized GoogleApiClient
         */
        fun getGoogleApiClient(context: Context, connectionCallbacks: GoogleApiClient.ConnectionCallbacks? = null, 
                              onConnectionFailedListener: GoogleApiClient.OnConnectionFailedListener? = null): GoogleApiClient {
            
            // Configure sign-in to request basic profile and email
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
                
            // Build a GoogleApiClient with the proper scopes
            val builder = GoogleApiClient.Builder(context)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                
            connectionCallbacks?.let { builder.addConnectionCallbacks(it) }
            onConnectionFailedListener?.let { builder.addOnConnectionFailedListener(it) }
            
            return builder.build()
        }

        /**
         * Handle resolution if needed
         */
        fun checkAndResolveGooglePlayServices(context: Context, activity: android.app.Activity?): Boolean {
            val apiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
            
            if (resultCode != ConnectionResult.SUCCESS) {
                if (apiAvailability.isUserResolvableError(resultCode) && activity != null) {
                    apiAvailability.getErrorDialog(activity, resultCode, REQUEST_CODE_RESOLUTION)?.show()
                } else {
                    Log.e(TAG, "This device does not support Google Play Services")
                }
                return false
            }
            return true
        }
    }
} 