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
    private val firestore = FirebaseFirestore.getInstance()

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

        // Initialize drawer layout and navigation view
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView

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
                R.id.nav_wt_registers -> {
                    navController.navigate(R.id.nav_wt_registers)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_notes -> {
                    navController.navigate(R.id.nav_notes)
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

        // Check and request notification permission
        checkNotificationPermission()
        
        // Schedule automatic backups
        scheduleAutomaticBackups()
        
        // Test Firebase connection
        testFirebaseConnection()

        // Initialize offline status helper
        offlineStatusHelper.initialize()
        
        // Observe error messages
        firebaseRepository.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                firebaseRepository.clearErrorMessage()
            }
        }
    }
    
    private fun scheduleAutomaticBackups() {
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
    
    private fun testFirebaseConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Write a test document
                val testData = hashMapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "Firebase connection test"
                )
                
                firestore.collection("test_connection")
                    .document("test_document")
                    .set(testData)
                    .await()
                
                // Read the test document
                val snapshot = firestore.collection("test_connection")
                    .document("test_document")
                    .get()
                    .await()
                
                val message = snapshot.getString("message") ?: "No message found"
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Firebase connection successful: $message",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Firebase connection error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkNotificationPermission() {
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

    private fun clearAppData() {
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
}