package com.example.allinone

import android.Manifest
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
import androidx.core.content.ContextCompat
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import android.app.AlertDialog

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navController: NavController
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set night mode first
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // Initialize binding and set content view ONCE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
}