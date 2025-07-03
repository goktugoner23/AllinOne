package com.example.allinone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.example.allinone.backup.BackupActivity
import com.example.allinone.databinding.ActivityMainBinding
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.allinone.firebase.FirebaseManager
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.utils.BackupHelper
import com.example.allinone.utils.OfflineStatusHelper
import com.example.allinone.workers.BackupWorker
import com.example.allinone.workers.ExpirationNotificationWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import android.app.AlertDialog
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.example.allinone.viewmodels.CalendarViewModel
import com.example.allinone.viewmodels.WTLessonsViewModel
import com.example.allinone.viewmodels.LessonChangeEvent
import android.widget.EditText
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.example.allinone.workers.LogcatCaptureWorker
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navController: NavController
    private lateinit var themeSwitch: SwitchCompat
    private val firebaseManager by lazy { FirebaseManager(this) }
    private val firebaseRepository by lazy { FirebaseRepository(this) }
    private val offlineStatusHelper by lazy { OfflineStatusHelper(this, firebaseRepository, this) }
    private val backupHelper by lazy { BackupHelper(this, firebaseRepository) }

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, schedule notifications
            scheduleExpirationNotifications()
        }
    }

    // Multiple permissions launcher
    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Log the permissions status
        permissions.entries.forEach {
            Log.d("MainActivity", "Permission: ${it.key}, granted: ${it.value}")
        }

        // Handle notification permission separately as it's critical for app function
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (permissions[Manifest.permission.POST_NOTIFICATIONS] == true) {
                scheduleExpirationNotifications()
            }
        }
    }

    // ViewModels
    private lateinit var calendarViewModel: CalendarViewModel
    private lateinit var wtLessonsViewModel: WTLessonsViewModel

    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_DARK_MODE = "dark_mode_enabled"
    }

    override fun attachBaseContext(newBase: Context) {
        // Load the saved theme preference
        val prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)

        // Apply the appropriate theme mode
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        val splashScreen = installSplashScreen()

        // Keep splash screen visible only while initializing
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        super.onCreate(savedInstanceState)

        // Set the main activity content view
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the app
        initializeApp()

        // Setup theme toggle switch
        setupThemeToggle()

        // Ensure bottom navigation and toolbar are always visible
        binding.bottomNavigation.visibility = View.VISIBLE
        binding.appBarLayout.visibility = View.VISIBLE

        // Allow the splash screen to dismiss
        keepSplashScreen = false
    }

    private fun setupThemeToggle() {
        // Find the theme switch in the navigation drawer
        themeSwitch = findViewById(R.id.themeSwitch)

        // Load current theme state
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)

        // Set the switch state to match the current theme
        themeSwitch.isChecked = isDarkMode

        // Set up the theme switch listener
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Save the theme preference
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()

            // Apply the theme change
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    private fun initializeApp() {
        // Setup navigation which is critical for the app
        setupNavigation()

        // Initialize ViewModels early
        calendarViewModel = ViewModelProvider(this)[CalendarViewModel::class.java]
        wtLessonsViewModel = ViewModelProvider(this)[WTLessonsViewModel::class.java]

        // Setup view model connections (simple operation)
        setupViewModelConnections()
        
        // Request permissions - needs to be done early
        lifecycleScope.launch { 
            requestAppPermissions()
        }

        // Observe repository error messages - lightweight observers
        firebaseRepository.errorMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                showErrorMessage(message)
                firebaseRepository.clearErrorMessage()
            }
        }

        // Initialize offline status helper directly on the main thread
        runOnUiThread {
            try {
                Log.d("MainActivity", "Initializing OfflineStatusHelper on UI thread: ${Thread.currentThread().name}")
                offlineStatusHelper.initialize()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing OfflineStatusHelper: ${e.message}", e)
            }
        }

        // Use Dispatchers.Default for CPU intensive tasks and background operations
        lifecycleScope.launch(Dispatchers.Default) {
            // Run background operations with a slight delay to prioritize UI
            withContext(Dispatchers.IO) {
                delay(800) // Delay to prioritize UI responsiveness
                
                // Run background operations in parallel but with error handling
                val supervisorJob = SupervisorJob()
                val backgroundScope = CoroutineScope(Dispatchers.IO + supervisorJob)
                
                // Launch tasks in parallel with error handling
                backgroundScope.launch { 
                    try {
                        scheduleBackup() 
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error scheduling backup: ${e.message}", e)
                    }
                }
                
                backgroundScope.launch { 
                    try {
                        scheduleExpirationNotifications() 
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error scheduling notifications: ${e.message}", e)
                    }
                }
                
                backgroundScope.launch { 
                    try {
                        testFirebaseConnection() 
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error testing Firebase connection: ${e.message}", e)
                    }
                }
            }
            
            // Now setup LiveData observers after a delay to ensure UI is responsive
            withContext(Dispatchers.Main) {
                delay(300)
                setupFirebaseObservers()
            }
        }
    }

    /**
     * Check if Google Play Services is available and handle errors
     */
    private fun checkGooglePlayServices(): Boolean {
        try {
            // Verify package name matches what's in google-services.json
            verifyFirebaseConfiguration()
            
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(this)
            
            if (resultCode != ConnectionResult.SUCCESS) {
                if (availability.isUserResolvableError(resultCode)) {
                    // Show dialog to fix the issue
                    availability.getErrorDialog(this, resultCode, 1001)?.show()
                } else {
                    // Non-resolvable error
                    Log.e("MainActivity", "Google Play Services not available, code: $resultCode")
                    showErrorMessage("Some features may not work properly due to Google Play Services issues")
                }
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking Google Play Services: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Verify that the Firebase configuration matches the app's actual configuration
     * This helps diagnose common causes of DEVELOPER_ERROR in Google Play Services
     */
    private fun verifyFirebaseConfiguration() {
        try {
            // Check if package name matches what's in google-services.json
            val actualPackageName = packageName
            val expectedPackageName = "com.example.allinone" // From google-services.json
            
            if (actualPackageName != expectedPackageName) {
                Log.e("MainActivity", "Package name mismatch! Expected: $expectedPackageName, Actual: $actualPackageName")
                showErrorMessage("Firebase configuration error: Package name mismatch")
                return
            }
            
            // Get app signature hash for debug purposes (useful for Firebase setup)
            try {
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = packageManager.getPackageInfo(
                        packageName, 
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    packageInfo.signingInfo.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES
                    )
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }
                
                signatures.forEach { signature ->
                    val md = java.security.MessageDigest.getInstance("SHA-1")
                    md.update(signature.toByteArray())
                    val sha1 = bytesToHex(md.digest())
                    Log.d("MainActivity", "App SHA-1 Fingerprint: $sha1")
                    
                    // For SHA-256 (often required by Firebase)
                    val md256 = java.security.MessageDigest.getInstance("SHA-256")
                    md256.update(signature.toByteArray())
                    val sha256 = bytesToHex(md256.digest())
                    Log.d("MainActivity", "App SHA-256 Fingerprint: $sha256")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting app signature: ${e.message}", e)
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error verifying Firebase configuration: ${e.message}", e)
        }
    }
    
    /**
     * Convert a byte array to a hex string (used for certificate fingerprints)
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return hexChars.joinToString("") { it.toString() }
    }

    /**
     * Test Firebase connection with improved error handling
     */
    private fun testFirebaseConnection() {
        // First check if Google Play Services is available
        val gmsAvailable = checkGooglePlayServices()
        
        try {
            // Proceed with Firebase operations
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Try initializing Firebase with fallback options if needed
                    if (!gmsAvailable) {
                        initializeFirebaseWithFallback()
                    }
                    
                    // Create a test document to verify write access
                    val testData = hashMapOf("timestamp" to System.currentTimeMillis(), "test" to true)
                    val db = FirebaseFirestore.getInstance()
                    
                    // Add a timeout to the operation
                    val task = db.collection("test").document("connection_test").set(testData)
                    try {
                        // Use a simpler approach with just withTimeout
                        kotlinx.coroutines.withTimeout(15000) {
                            task.await()
                            Log.d("FirebaseManager", "Test document created successfully")
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        Log.e("FirebaseManager", "Timeout while creating test document")
                    } catch (e: Exception) {
                        Log.e("FirebaseManager", "Error creating test document: ${e.message}", e)
                    }
                    
                    // Verify the test document was created
                    Log.d("FirebaseManager", "Verifying test document was created...")
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        Log.d("FirebaseManager", "Firebase connection test successful")
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "Firebase connection test failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        showErrorMessage("Firebase connection failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during Firebase connection test setup: ${e.message}", e)
        }
    }

    /**
     * Initialize Firebase with fallback options for devices with Google Play Services issues
     */
    private fun initializeFirebaseWithFallback() {
        try {
            // Check if Firebase is already initialized
            FirebaseApp.getInstance()
            Log.d("MainActivity", "Firebase already initialized")
        } catch (e: IllegalStateException) {
            // Firebase is not initialized, so try to initialize it manually
            try {
                Log.d("MainActivity", "Attempting to initialize Firebase manually")
                
                // Initialize with default settings instead of hardcoded credentials
                FirebaseApp.initializeApp(this)
                Log.d("MainActivity", "Firebase initialized successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize Firebase: ${e.message}", e)
                
                // Use runOnUiThread instead of withContext since this is not a suspend function
                runOnUiThread {
                    showErrorMessage("Unable to initialize Firebase. Please check your internet connection and try again.")
                }
            }
        }
    }

    private fun showGooglePlayServicesError() {
        try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(this)
            
            // Log detailed information about the error
            logGooglePlayServicesErrorDetails(resultCode)
            
            if (resultCode != ConnectionResult.SUCCESS) {
                if (availability.isUserResolvableError(resultCode)) {
                    // Show the built-in error resolution dialog instead of a custom one
                    availability.getErrorDialog(this, resultCode, 1001)?.show()
                    return
                }
            }
            
            // Only show custom dialog if the built-in one couldn't be shown
            AlertDialog.Builder(this)
                .setTitle("Google Play Services Issue")
                .setMessage("There seems to be an issue with Google Play Services on your device (error code: $resultCode). Some features of the app might not work properly.")
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS))
                    } catch (e: Exception) {
                        showErrorMessage("Could not open settings. Please check your device settings manually.")
                    }
                }
                .setNegativeButton("Continue Anyway", null)
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing Google Play Services error dialog: ${e.message}", e)
            // Fallback to simple message if dialog fails
            showErrorMessage("Google Play Services issue detected. Some features may not work properly.")
        }
    }
    
    /**
     * Log detailed information about Google Play Services error
     */
    private fun logGooglePlayServicesErrorDetails(resultCode: Int) {
        val errorMessage = when (resultCode) {
            ConnectionResult.SERVICE_MISSING -> "Google Play services is missing on this device."
            ConnectionResult.SERVICE_UPDATING -> "Google Play services is currently being updated on this device."
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "The installed version of Google Play services is out of date."
            ConnectionResult.SERVICE_DISABLED -> "The installed version of Google Play services has been disabled on this device."
            ConnectionResult.SERVICE_INVALID -> "The version of the Google Play services installed on this device is not authentic."
            ConnectionResult.SIGN_IN_REQUIRED -> "Google Play services is installed but not enabled."
            ConnectionResult.NETWORK_ERROR -> "A network error occurred. Please check your connection."
            ConnectionResult.INTERNAL_ERROR -> "An internal error occurred with Google Play services."
            ConnectionResult.SERVICE_MISSING_PERMISSION -> "Google Play services is missing required permissions."
            ConnectionResult.DEVELOPER_ERROR -> "The application is misconfigured or the SHA1 fingerprint does not match."
            ConnectionResult.API_UNAVAILABLE -> "The API is not available on this device."
            ConnectionResult.RESTRICTED_PROFILE -> "The current user profile is restricted."
            else -> "Unknown Google Play services error code: $resultCode"
        }
        
        Log.e("MainActivity", "Google Play Services Error: $errorMessage (Code: $resultCode)")
        
        // If DEVELOPER_ERROR, add additional diagnostic info
        if (resultCode == ConnectionResult.DEVELOPER_ERROR) {
            Log.e("MainActivity", "DEVELOPER_ERROR typically means the app's package name or signing key doesn't match what's registered in Firebase.")
            Log.e("MainActivity", "Check that the SHA-1 fingerprint in the Firebase console matches your app's signing certificate.")
            
            // Try to get SHA-1 of the app's signing certificate and log it
            verifyFirebaseConfiguration()
            
            Log.e("MainActivity", "Add this SHA-1 fingerprint to the Firebase console for the app.")
            Log.e("MainActivity", "Firebase console: https://console.firebase.google.com/project/allinone-bd6f3/settings/general/")
        }
    }

    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearAppData() {
        // Show confirmation dialog before clearing data
        AlertDialog.Builder(this)
            .setTitle("Clear App Data")
            .setMessage("Are you sure you want to clear all app data? This action cannot be undone.")
            .setPositiveButton("Yes, Clear Data") { _, _ ->
                // Clear shared preferences
                val sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
                sharedPreferences.edit().clear().apply()

                // Restart the app to refresh data
                val packageManager = packageManager
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearFirestoreDatabase() {
        // Show confirmation dialog before clearing Firestore data
        AlertDialog.Builder(this)
            .setTitle("Clear Firestore Database")
            .setMessage("Are you sure you want to delete ALL data from the Firestore database? This action cannot be undone.")
            .setPositiveButton("Continue") { _, _ ->
                // Ask for PIN code
                showPinDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPinDialog() {
        // Create a PIN input dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_input, null)
        val pinEditText = dialogView.findViewById<EditText>(R.id.pinEditText)

        AlertDialog.Builder(this)
            .setTitle("Enter PIN Code")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val enteredPin = pinEditText.text.toString()
                validatePin(enteredPin)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validatePin(enteredPin: String) {
        val correctPin = "1111"

        if (enteredPin == correctPin) {
            // PIN is correct - proceed with clearing Firestore data
            performFirestoreClear()
        } else {
            // PIN is incorrect - show error
            Toast.makeText(this, "Incorrect PIN. Database was not cleared.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performFirestoreClear() {
        // Show loading indicator with progress information
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Clearing Database")
            .setMessage("Please wait while we delete all data from Firestore...\n\nThis may take some time depending on the amount of data.")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        // Use coroutine to perform the clearing operation
        lifecycleScope.launch {
            try {
                // Add Google Play Services check
                try {
                    // Check if Google Play Services is available
                    val availability = GoogleApiAvailability.getInstance()
                    val resultCode = availability.isGooglePlayServicesAvailable(this@MainActivity)

                    if (resultCode != ConnectionResult.SUCCESS) {
                        loadingDialog.dismiss()
                        if (availability.isUserResolvableError(resultCode)) {
                            availability.getErrorDialog(this@MainActivity, resultCode, 1001)?.show()
                        } else {
                            showError("Google Play Services is not available on this device")
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error checking Play Services: ${e.message}", e)
                    // Continue anyway
                }

                // Add a small delay to ensure the dialog is shown
                delay(500)

                val result = firebaseRepository.clearAllFirestoreData()

                // Add a small delay to make the operation feel more substantial
                delay(1000)

                // Dismiss the loading dialog
                loadingDialog.dismiss()

                if (result) {
                    // Success - show confirmation with details
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Database Cleared")
                        .setMessage("All data has been successfully deleted from the Firestore database. The app will now show empty data sets. Note that empty collections will still appear in the Firebase Console. You may need to restart the app to see all changes take effect.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // Failed - show error with more details
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Operation Failed")
                        .setMessage("Failed to clear all database data. This might be due to network issues or insufficient permissions. Please check your internet connection and try again.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                // Handle exception with detailed error
                loadingDialog.dismiss()

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Error")
                    .setMessage("An error occurred while clearing the database: ${e.message}\n\nSome data may have been partially deleted.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showFirebaseProjectError() {
        AlertDialog.Builder(this)
            .setTitle("Firebase Project Error")
            .setMessage("You're using a placeholder Firebase project. Please set up a real Firebase project and update your google-services.json file. See the README in the firebase_rules folder for instructions.")
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

    private fun showFirestoreRulesError() {
        AlertDialog.Builder(this)
            .setTitle("Firestore Rules Error")
            .setMessage("Your Firestore security rules are not properly configured. Please update your Firestore security rules in the Firebase Console. See the README in the firebase_rules folder for instructions.")
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        when (destination.id) {
            R.id.nav_transactions -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            R.id.nav_wt_registry -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            R.id.nav_calendar -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            R.id.nav_notes -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            R.id.nav_instagram_business -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            R.id.nav_workout -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            R.id.nav_history -> {
                binding.bottomNavigation.visibility = View.GONE
            }
            R.id.nav_database_management -> {
                binding.bottomNavigation.visibility = View.GONE
            }
            R.id.nav_backup -> {
                binding.bottomNavigation.visibility = View.GONE
                startActivity(Intent(this, BackupActivity::class.java))
            }
            R.id.nav_error_logs -> {
                binding.bottomNavigation.visibility = View.GONE
            }
            R.id.nav_clear_data -> {
                binding.bottomNavigation.visibility = View.GONE
            }
            R.id.nav_clear_db -> {
                binding.bottomNavigation.visibility = View.GONE
            }
        }

        // Hide bottom navigation for certain screens
        when (destination.id) {
            R.id.homeFragment, R.id.nav_investments, R.id.nav_transaction_report -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            else -> {
                binding.bottomNavigation.visibility = View.GONE
            }
        }

        // Hide/show toolbar for certain screens
        when (destination.id) {
            R.id.nav_history -> {
                binding.toolbar.visibility = View.GONE
            }
            R.id.nav_transaction_report -> {
                binding.toolbar.visibility = View.VISIBLE
                binding.appBarLayout.visibility = View.VISIBLE
            }
            else -> {
                binding.toolbar.visibility = View.VISIBLE
            }
        }

        // Set the title based on destination label or use a specific title
        val title = when (destination.id) {
            R.id.homeFragment -> "Transactions"
            R.id.nav_investments -> "Investments"
            R.id.nav_transaction_report -> "Reports"
            R.id.nav_notes -> "Notes"
            R.id.nav_wt_registry -> "Wing Tzun Registry"
            R.id.nav_calendar -> "Calendar"
            R.id.nav_instagram_business -> "Instagram Business"
            R.id.nav_workout -> "Workout"
            R.id.nav_history -> "History"
            R.id.wtRegisterFragment -> "Wing Tzun Registry"
            R.id.nav_database_management -> "Database Management"
            else -> destination.label?.toString() ?: getString(R.string.app_name)
        }

        // Set the title in the action bar
        supportActionBar?.title = title
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Open the navigation drawer
     */
    fun openDrawer() {
        if (::drawerLayout.isInitialized) {
            drawerLayout.openDrawer(navigationView)
        }
    }

    /**
     * Display an error message to the user
     */
    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Set up connections between ViewModels
     * This allows changes in WTLessonsViewModel to update the CalendarViewModel
     */
    private fun setupViewModelConnections() {
        // Observe lesson changes and update calendar accordingly
        wtLessonsViewModel.lessonChangeEvent.observe(this) { event ->
            when (event) {
                is LessonChangeEvent.LessonsUpdated -> {
                    // When all lessons are updated, update the calendar
                    calendarViewModel.setLessonSchedule(wtLessonsViewModel.lessons.value ?: emptyList())
                }

                is LessonChangeEvent.LessonAdded,
                is LessonChangeEvent.LessonModified,
                is LessonChangeEvent.LessonDeleted -> {
                    // When individual lessons change, update the calendar
                    calendarViewModel.setLessonSchedule(wtLessonsViewModel.lessons.value ?: emptyList())
                }
            }
        }
    }

    private fun setupNavigation() {
        // Initialize drawer layout and navigation view
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView

        // Set the toolbar as the support action bar
        setSupportActionBar(binding.toolbar)

        // Set up the drawer toggle
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            binding.toolbar,
            0,
            0
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Get NavHostFragment and NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Add destination changed listener
        navController.addOnDestinationChangedListener(this)

        // Setup bottom navigation with NavController
        binding.bottomNavigation.setupWithNavController(navController)

        // Add custom navigation for bottom navigation items
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.nav_investments -> {
                    navController.navigate(R.id.nav_investments)
                    true
                }
                R.id.nav_transaction_report -> {
                    navController.navigate(R.id.nav_transaction_report)
                    true
                }
                else -> false
            }
        }

        // Handle navigation item selection
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_transactions -> {
                    navController.navigate(R.id.homeFragment)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_wt_registry -> {
                    navController.navigate(R.id.nav_wt_registry)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_calendar -> {
                    navController.navigate(R.id.nav_calendar)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_notes -> {
                    navController.navigate(R.id.nav_notes)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_tasks -> {
                    navController.navigate(R.id.nav_tasks)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_instagram_business -> {
                    navController.navigate(R.id.nav_instagram_business)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_workout -> {
                    navController.navigate(R.id.nav_workout)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_history -> {
                    navController.navigate(R.id.nav_history)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_database_management -> {
                    navController.navigate(R.id.nav_database_management)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_backup -> {
                    // Navigate to backup activity
                    val intent = Intent(this, BackupActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_error_logs -> {
                    navController.navigate(R.id.nav_error_logs)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_clear_data -> {
                    // Clear app data instead of logging out
                    clearAppData()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_clear_db -> {
                    // Clear Firestore database
                    clearFirestoreDatabase()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }

        // Make sure the hamburger icon is always showing on specific screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_transaction_report) {
                binding.toolbar.visibility = View.VISIBLE
                binding.appBarLayout.visibility = View.VISIBLE
                toggle.isDrawerIndicatorEnabled = true
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                supportActionBar?.setDisplayShowHomeEnabled(true)
                supportActionBar?.setHomeButtonEnabled(true)
                toggle.syncState()
            }
        }
    }

    private fun showAddTransactionDialog() {
        // TODO: Implement transaction dialog
    }

    private fun showAddWTLessonDialog() {
        // TODO: Implement WT lesson dialog
    }

    private fun showAddCalendarEventDialog() {
        // TODO: Implement calendar event dialog
    }

    private fun showAddNoteDialog() {
        // TODO: Implement note dialog
    }

    /**
     * Request necessary app permissions based on Android version
     */
    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses more specific permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below use general storage permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /**
     * Schedule backup worker
     */
    private fun scheduleBackup() {
        // Schedule weekly backups
        val backupWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            7, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            backupWorkRequest
        )
    }

    /**
     * Schedule notification workers for reminders and log capture
     */
    private fun scheduleExpirationNotifications() {
        val expirationWorkRequest = PeriodicWorkRequestBuilder<ExpirationNotificationWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpirationNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            expirationWorkRequest
        )

        // Schedule logcat capture worker to run every 30 minutes
        val logcatWorkRequest = PeriodicWorkRequestBuilder<LogcatCaptureWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "logcat_capture_work",
            ExistingPeriodicWorkPolicy.KEEP,
            logcatWorkRequest
        )
    }
    
    /**
     * Setup Firebase observers for monitoring services
     */
    private fun setupFirebaseObservers() {
        // Observe Google Play Services availability
        firebaseRepository.isGooglePlayServicesAvailable.observe(this) { isAvailable ->
            if (!isAvailable) {
                showGooglePlayServicesError()
            }
        }

        // Observe Firebase project validity
        firebaseRepository.isFirebaseProjectValid.observe(this) { isValid ->
            if (!isValid) {
                showFirebaseProjectError()
            }
        }

        // Observe Firestore security rules validity
        firebaseRepository.areFirestoreRulesValid.observe(this) { areValid ->
            if (!areValid) {
                showFirestoreRulesError()
            }
        }
    }
}