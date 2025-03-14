package com.example.allinone.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.example.allinone.data.Investment
import com.example.allinone.data.Note
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTStudent
import com.example.allinone.data.WTLesson
import com.example.allinone.data.Event
import com.example.allinone.firebase.FirebaseRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Helper class for backup and restore operations
 */
class BackupHelper(private val context: Context, private val repository: FirebaseRepository) {
    
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val backupDir = File(context.getExternalFilesDir(null), "backups")
    
    init {
        // Create backup directory if it doesn't exist
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
    }
    
    /**
     * Create a backup of all data
     * @return The backup file
     */
    suspend fun createBackup(): File? = withContext(Dispatchers.IO) {
        try {
            // Create a timestamp for the backup file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "allinone_backup_$timestamp.zip")
            
            // Create a temporary directory for the backup files
            val tempDir = File(context.cacheDir, "backup_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
            
            // Create JSON files for each data type
            val transactionsFile = File(tempDir, "transactions.json")
            val investmentsFile = File(tempDir, "investments.json")
            val notesFile = File(tempDir, "notes.json")
            val studentsFile = File(tempDir, "students.json")
            val lessonsFile = File(tempDir, "lessons.json")
            val eventsFile = File(tempDir, "events.json")
            
            // Write data to JSON files
            OutputStreamWriter(FileOutputStream(transactionsFile)).use { writer ->
                writer.write(gson.toJson(repository.transactions.value))
            }
            
            OutputStreamWriter(FileOutputStream(investmentsFile)).use { writer ->
                writer.write(gson.toJson(repository.investments.value))
            }
            
            OutputStreamWriter(FileOutputStream(notesFile)).use { writer ->
                writer.write(gson.toJson(repository.notes.value))
            }
            
            OutputStreamWriter(FileOutputStream(studentsFile)).use { writer ->
                writer.write(gson.toJson(repository.students.value))
            }
            
            // Add lesson schedule data
            OutputStreamWriter(FileOutputStream(lessonsFile)).use { writer ->
                writer.write(gson.toJson(repository.wtLessons.value))
            }
            
            // Add calendar events data
            OutputStreamWriter(FileOutputStream(eventsFile)).use { writer ->
                writer.write(gson.toJson(repository.events.value))
            }
            
            // Create a ZIP file with all the JSON files
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                // Add each file to the ZIP
                for (file in tempDir.listFiles() ?: emptyArray()) {
                    val zipEntry = ZipEntry(file.name)
                    zipOut.putNextEntry(zipEntry)
                    
                    FileInputStream(file).use { fileIn ->
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (fileIn.read(buffer).also { len = it } > 0) {
                            zipOut.write(buffer, 0, len)
                        }
                    }
                    
                    zipOut.closeEntry()
                }
            }
            
            // Clean up temporary files
            tempDir.deleteRecursively()
            
            backupFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Restore data from a backup file
     * @param backupUri The URI of the backup file
     * @return True if the restore was successful, false otherwise
     */
    suspend fun restoreFromBackup(backupUri: Uri): Boolean = withContext(Dispatchers.IO) {
        var errorMessage: String? = null
        var tempDir: File? = null
        
        try {
            // Create a temporary directory for the extracted files
            tempDir = File(context.cacheDir, "restore_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
            
            // Extract the ZIP file
            context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var zipEntry = zipIn.nextEntry
                    while (zipEntry != null) {
                        val newFile = File(tempDir, zipEntry.name)
                        
                        // Create directories if needed
                        if (zipEntry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            // Create parent directories if needed
                            newFile.parentFile?.mkdirs()
                            
                            // Extract the file
                            FileOutputStream(newFile).use { fileOut ->
                                val buffer = ByteArray(1024)
                                var len: Int
                                while (zipIn.read(buffer).also { len = it } > 0) {
                                    fileOut.write(buffer, 0, len)
                                }
                            }
                        }
                        
                        zipIn.closeEntry()
                        zipEntry = zipIn.nextEntry
                    }
                }
            } ?: run {
                errorMessage = "Could not open backup file"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: Could not open backup file", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }
            
            // Process the extracted files and restore the data
            val transactionsFile = File(tempDir, "transactions.json")
            val investmentsFile = File(tempDir, "investments.json")
            val notesFile = File(tempDir, "notes.json")
            val studentsFile = File(tempDir, "students.json")
            val lessonsFile = File(tempDir, "lessons.json")
            val eventsFile = File(tempDir, "events.json")
            
            // Check if at least one file exists
            if (!transactionsFile.exists() && !investmentsFile.exists() && 
                !notesFile.exists() && !studentsFile.exists() &&
                !lessonsFile.exists() && !eventsFile.exists()) {
                errorMessage = "No valid data found in backup"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: No valid data found in backup", Toast.LENGTH_LONG).show()
                }
                tempDir.deleteRecursively()
                return@withContext false
            }
            
            // Try to restore data locally first without Firebase
            val transactions = if (transactionsFile.exists()) {
                try {
                    InputStreamReader(FileInputStream(transactionsFile)).use { reader ->
                        val type = object : TypeToken<List<Transaction>>() {}.type
                        gson.fromJson<List<Transaction>>(reader, type)
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing transactions: ${e.message}"
                    null
                }
            } else null
            
            val investments = if (investmentsFile.exists()) {
                try {
                    InputStreamReader(FileInputStream(investmentsFile)).use { reader ->
                        val type = object : TypeToken<List<Investment>>() {}.type
                        gson.fromJson<List<Investment>>(reader, type)
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing investments: ${e.message}"
                    null
                }
            } else null
            
            val notes = if (notesFile.exists()) {
                try {
                    InputStreamReader(FileInputStream(notesFile)).use { reader ->
                        val type = object : TypeToken<List<Note>>() {}.type
                        gson.fromJson<List<Note>>(reader, type)
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing notes: ${e.message}"
                    null
                }
            } else null
            
            val students = if (studentsFile.exists()) {
                try {
                    InputStreamReader(FileInputStream(studentsFile)).use { reader ->
                        val type = object : TypeToken<List<WTStudent>>() {}.type
                        gson.fromJson<List<WTStudent>>(reader, type)
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing students: ${e.message}"
                    null
                }
            } else null
            
            // Parse lesson schedule data
            val lessons = if (lessonsFile.exists()) {
                try {
                    InputStreamReader(FileInputStream(lessonsFile)).use { reader ->
                        val type = object : TypeToken<List<WTLesson>>() {}.type
                        gson.fromJson<List<WTLesson>>(reader, type)
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing lessons: ${e.message}"
                    null
                }
            } else null
            
            // Parse calendar events data
            val events = if (eventsFile.exists()) {
                try {
                    InputStreamReader(FileInputStream(eventsFile)).use { reader ->
                        val type = object : TypeToken<List<Event>>() {}.type
                        gson.fromJson<List<Event>>(reader, type)
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing events: ${e.message}"
                    null
                }
            } else null
            
            // Now try to update Firebase with the parsed data
            // We'll do this in a separate try-catch block to ensure we don't lose the parsed data
            var firebaseUpdateSuccess = false
            
            try {
                // Update transactions in batches to avoid overwhelming Firebase
                transactions?.chunked(10)?.forEach { batch ->
                    batch.forEach { transaction ->
                        try {
                            repository.updateTransaction(transaction)
                            // Small delay to avoid overwhelming Firebase
                            kotlinx.coroutines.delay(50)
                        } catch (e: Exception) {
                            // Log error but continue with other items
                            e.printStackTrace()
                        }
                    }
                }
                
                // Update investments in batches
                investments?.chunked(10)?.forEach { batch ->
                    batch.forEach { investment ->
                        try {
                            repository.updateInvestment(investment)
                            // Small delay to avoid overwhelming Firebase
                            kotlinx.coroutines.delay(50)
                        } catch (e: Exception) {
                            // Log error but continue with other items
                            e.printStackTrace()
                        }
                    }
                }
                
                // Update notes in batches
                notes?.chunked(10)?.forEach { batch ->
                    batch.forEach { note ->
                        try {
                            repository.updateNote(note)
                            // Small delay to avoid overwhelming Firebase
                            kotlinx.coroutines.delay(50)
                        } catch (e: Exception) {
                            // Log error but continue with other items
                            e.printStackTrace()
                        }
                    }
                }
                
                // Update students in batches
                students?.chunked(10)?.forEach { batch ->
                    batch.forEach { student ->
                        try {
                            repository.updateStudent(student)
                            // Small delay to avoid overwhelming Firebase
                            kotlinx.coroutines.delay(50)
                        } catch (e: Exception) {
                            // Log error but continue with other items
                            e.printStackTrace()
                        }
                    }
                }
                
                // Update lesson schedule in batches
                lessons?.chunked(10)?.forEach { batch ->
                    batch.forEach { lesson ->
                        try {
                            repository.insertWTLesson(lesson)
                            // Small delay to avoid overwhelming Firebase
                            kotlinx.coroutines.delay(50)
                        } catch (e: Exception) {
                            // Log error but continue with other items
                            e.printStackTrace()
                        }
                    }
                }
                
                // Update events in batches
                events?.chunked(10)?.forEach { batch ->
                    batch.forEach { event ->
                        try {
                            repository.insertEvent(event)
                            // Small delay to avoid overwhelming Firebase
                            kotlinx.coroutines.delay(50)
                        } catch (e: Exception) {
                            // Log error but continue with other items
                            e.printStackTrace()
                        }
                    }
                }
                
                // Try to refresh data, but don't fail if it doesn't work
                try {
                    // Use a timeout to prevent hanging
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeout(10000) {
                            repository.refreshAllData()
                        }
                    }
                    firebaseUpdateSuccess = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Don't fail the whole restore process if refresh fails
                }
                
            } catch (e: SecurityException) {
                // Handle Google Play Services security exception
                errorMessage = "Google Play Services authentication error: ${e.message}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Warning: Data restored locally but couldn't sync with Firebase. " +
                        "Please check your Google Play Services.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // We still consider this a partial success
            } catch (e: Exception) {
                errorMessage = "Error updating Firebase: ${e.message}"
                e.printStackTrace()
                // We still consider this a partial success if we parsed the data
            }
            
            // Clean up temporary files
            try {
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            
            // If we got here with parsed data, consider it at least a partial success
            val success = transactions != null || investments != null || 
                         notes != null || students != null ||
                         lessons != null || events != null
            
            if (success) {
                if (firebaseUpdateSuccess) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Backup restored successfully.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else if (errorMessage != null) {
                    // We had some issues but restored some data
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Backup partially restored. Some errors occurred.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            
            return@withContext success
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Restore failed: ${e.message}"
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Error restoring backup: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            // Clean up temporary files if they exist
            tempDir?.let {
                try {
                    it.deleteRecursively()
                } catch (cleanupException: Exception) {
                    // Ignore cleanup errors
                }
            }
            
            return@withContext false
        } finally {
            if (errorMessage != null) {
                println("Backup restore error: $errorMessage")
            }
        }
    }
    
    /**
     * Get a list of all backup files, sorted by newest first
     * @return A list of backup files
     */
    fun getBackupFiles(): List<File> {
        return backupDir.listFiles()
            ?.filter { it.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }  // Sort by last modified time (newest first)
            ?: emptyList()
    }
    
    /**
     * Delete a backup file
     * @param backupFile The backup file to delete
     * @return True if the file was deleted, false otherwise
     */
    fun deleteBackup(backupFile: File): Boolean {
        return backupFile.delete()
    }
} 