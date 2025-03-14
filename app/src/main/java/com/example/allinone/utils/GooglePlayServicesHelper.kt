package com.example.allinone.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Helper class to check Google Play Services availability
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