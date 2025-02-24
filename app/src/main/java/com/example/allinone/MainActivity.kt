package com.example.allinone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
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
import com.example.allinone.databinding.ActivityMainBinding
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.allinone.workers.ExpirationNotificationWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navController: NavController

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

        // Set up toolbar using binding
        setSupportActionBar(binding.toolbar)

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
                else -> false
            }
        }

        // Check and request notification permission
        checkNotificationPermission()
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
}