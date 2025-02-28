package com.example.allinone.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.MainActivity
import com.example.allinone.R
import com.example.allinone.databinding.ActivityBackupBinding
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.utils.BackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBackupBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var backupHelper: BackupHelper
    private lateinit var backupAdapter: BackupAdapter
    
    // File picker for restore
    private val restoreFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                restoreFromBackup(uri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize repository and backup helper
        repository = FirebaseRepository(this)
        backupHelper = BackupHelper(this, repository)
        
        // Set up RecyclerView
        backupAdapter = BackupAdapter(
            onShareClick = { shareBackup(it) },
            onDeleteClick = { deleteBackup(it) },
            onRestoreClick = { restoreFromBackupFile(it) }
        )
        
        binding.backupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@BackupActivity)
            adapter = backupAdapter
        }
        
        // Set up button click listeners
        binding.createBackupButton.setOnClickListener {
            createBackup()
        }
        
        // Load available backups
        loadBackups()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun loadBackups() {
        val backupFiles = backupHelper.getBackupFiles()
        
        if (backupFiles.isEmpty()) {
            binding.noBackupsText.visibility = View.VISIBLE
            binding.backupsRecyclerView.visibility = View.GONE
        } else {
            binding.noBackupsText.visibility = View.GONE
            binding.backupsRecyclerView.visibility = View.VISIBLE
            backupAdapter.submitList(backupFiles)
        }
    }
    
    private fun createBackup() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val backupFile = backupHelper.createBackup()
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    if (backupFile != null) {
                        Toast.makeText(
                            this@BackupActivity,
                            "Backup created successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Reload backups
                        loadBackups()
                    } else {
                        Toast.makeText(
                            this@BackupActivity,
                            "Failed to create backup",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@BackupActivity,
                        "Error creating backup: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip"))
        }
        
        restoreFileLauncher.launch(intent)
    }
    
    private fun restoreFromBackup(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = backupHelper.restoreFromBackup(uri)
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    if (success) {
                        Toast.makeText(
                            this@BackupActivity,
                            "Backup restored successfully. Restarting app...",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Restart the app to refresh all data
                        restartApp()
                    } else {
                        // Show a more detailed error dialog
                        showErrorDialog(
                            "Restore Failed",
                            "The backup could not be restored. This might be due to:\n" +
                            "• Invalid or corrupted backup file\n" +
                            "• Google Play Services authentication issue\n" +
                            "• Network connectivity problems\n\n" +
                            "Please try again or create a new backup."
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    // Show a more detailed error dialog with the exception message
                    showErrorDialog(
                        "Restore Error",
                        "An error occurred while restoring the backup:\n${e.message}\n\n" +
                        "This might be due to:\n" +
                        "• Google Play Services issues\n" +
                        "• Network connectivity problems\n" +
                        "• Insufficient permissions\n\n" +
                        "Please try again later."
                    )
                }
            }
        }
    }
    
    private fun restoreFromBackupFile(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.fromFile(file)
                val success = backupHelper.restoreFromBackup(uri)
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    if (success) {
                        Toast.makeText(
                            this@BackupActivity,
                            "Backup restored successfully. Restarting app...",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Restart the app to refresh all data
                        restartApp()
                    } else {
                        // Show a more detailed error dialog
                        showErrorDialog(
                            "Restore Failed",
                            "The backup could not be restored. This might be due to:\n" +
                            "• Invalid or corrupted backup file\n" +
                            "• Google Play Services authentication issue\n" +
                            "• Network connectivity problems\n\n" +
                            "Please try again or create a new backup."
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    // Show a more detailed error dialog with the exception message
                    showErrorDialog(
                        "Restore Error",
                        "An error occurred while restoring the backup:\n${e.message}\n\n" +
                        "This might be due to:\n" +
                        "• Google Play Services issues\n" +
                        "• Network connectivity problems\n" +
                        "• Insufficient permissions\n\n" +
                        "Please try again later."
                    )
                }
            }
        }
    }
    
    /**
     * Restart the app to refresh all data
     */
    private fun restartApp() {
        // Wait a moment before restarting
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Give time for the toast to show
                kotlinx.coroutines.delay(2000)
                
                withContext(Dispatchers.Main) {
                    try {
                        // Use a safer approach to restart the app
                        val intent = Intent(applicationContext, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) // Clear all existing tasks
                        startActivity(intent)
                        
                        // Finish this activity without using finishAffinity which can cause issues
                        finish()
                        
                        // Optional: Add a slight delay before process exit to ensure clean activity finish
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Force a clean process restart if needed
                            // Process.killProcess(Process.myPid())
                        }, 500)
                    } catch (e: Exception) {
                        // If restarting fails, just finish the activity
                        Toast.makeText(
                            this@BackupActivity,
                            "Backup restored. Please restart the app manually.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
    }
    
    private fun shareBackup(file: File) {
        // Create a content URI using FileProvider instead of direct file URI
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "AllInOne Backup - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        startActivity(Intent.createChooser(intent, "Share Backup"))
    }
    
    private fun deleteBackup(file: File) {
        // Show confirmation dialog before deleting
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Delete Backup")
        builder.setMessage("Are you sure you want to delete this backup? This action cannot be undone.")
        builder.setPositiveButton("Delete") { dialog, _ ->
            if (backupHelper.deleteBackup(file)) {
                Toast.makeText(
                    this,
                    "Backup deleted",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Reload backups
                loadBackups()
            } else {
                Toast.makeText(
                    this,
                    "Failed to delete backup",
                    Toast.LENGTH_SHORT
                ).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
    
    /**
     * Show an error dialog with the given title and message
     */
    private fun showErrorDialog(title: String, message: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }
} 