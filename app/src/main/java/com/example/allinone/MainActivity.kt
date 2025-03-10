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
        // Initialize offline status helper
        offlineStatusHelper.initialize()
        
        // Setup navigation
        setupNavigation()
        
        // Check for permissions
        checkAndRequestPermissions()
        
        // Schedule workers
        scheduleBackup()
        scheduleExpirationNotifications()
        
        // Test Firebase connection
        testFirebaseConnection()
        
        // Initialize ViewModels
        calendarViewModel = ViewModelProvider(this)[CalendarViewModel::class.java]
        wtLessonsViewModel = ViewModelProvider(this)[WTLessonsViewModel::class.java]
        
        // Connect WTLessonsViewModel to CalendarViewModel
        setupViewModelConnections()
        
        // Observe repository error messages
        firebaseRepository.errorMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                showErrorMessage(message)
                firebaseRepository.clearErrorMessage()
            }
        }
        
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
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
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
                R.id.nav_history -> {
                    navController.navigate(R.id.nav_history)
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
    }
    
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted, schedule notifications
                    scheduleExpirationNotifications()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Explain why the app needs this permission
                    // For simplicity, we're just requesting directly here
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request the permission directly
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android 12 and below, no runtime permission needed
            scheduleExpirationNotifications()
        }
    }
    
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
    
    private fun scheduleExpirationNotifications() {
        val expirationWorkRequest = PeriodicWorkRequestBuilder<ExpirationNotificationWorker>(
            1, TimeUnit.DAYS
        ).build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpirationNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            expirationWorkRequest
        )
    }
    
    private fun testFirebaseConnection() {
        firebaseRepository.checkGooglePlayServicesAvailability()
    }
    
    private fun showGooglePlayServicesError() {
        AlertDialog.Builder(this)
            .setTitle("Google Play Services Error")
            .setMessage("There seems to be an issue with Google Play Services on your device. Some features of the app might not work properly. Would you like to troubleshoot?")
            .setPositiveButton("Troubleshoot") { _, _ ->
                // Open Google Play Services help page or settings
                try {
                    val intent = Intent("com.google.android.gms.settings.GOOGLE_PLAY_SERVICES_SETTINGS")
                    startActivity(intent)
                } catch (e: Exception) {
                    showErrorMessage("Could not open Google Play Services settings. Please check your device settings manually.")
                }
            }
            .setNegativeButton("Continue Anyway", null)
            .setCancelable(true)
            .show()
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
        // Show bottom navigation only for Home and Investments fragments
        when (destination.id) {
            R.id.homeFragment, R.id.nav_investments -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            else -> {
                binding.bottomNavigation.visibility = View.GONE
            }
        }
        
        // Show/hide toolbar based on destination
        when (destination.id) {
            R.id.nav_history -> {
                binding.toolbar.visibility = View.GONE
            }
            else -> {
                binding.toolbar.visibility = View.VISIBLE
            }
        }
        
        // Set the title based on destination label or use a specific title
        val title = when (destination.id) {
            R.id.homeFragment -> "Transactions"
            R.id.nav_investments -> "Investments"
            R.id.nav_notes -> "Notes"
            R.id.nav_wt_registry -> "Wing Tzun Registry"
            R.id.nav_history -> "History"
            R.id.wtRegisterFragment -> "Wing Tzun Registry"
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
}