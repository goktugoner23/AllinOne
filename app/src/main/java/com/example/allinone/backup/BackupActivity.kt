package com.example.allinone.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
        
        binding.restoreBackupButton.setOnClickListener {
            openFilePicker()
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
                        Toast.makeText(
                            this@BackupActivity,
                            "Failed to restore backup",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@BackupActivity,
                        "Error restoring backup: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                        Toast.makeText(
                            this@BackupActivity,
                            "Failed to restore backup",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@BackupActivity,
                        "Error restoring backup: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                    // Restart the app
                    val packageManager = packageManager
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finishAffinity()
                }
            }
        }
    }
    
    private fun shareBackup(file: File) {
        val uri = Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AllInOne Backup - ${file.name}")
        }
        
        startActivity(Intent.createChooser(intent, "Share Backup"))
    }
    
    private fun deleteBackup(file: File) {
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
    }
} 