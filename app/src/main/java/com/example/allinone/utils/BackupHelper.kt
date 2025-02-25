package com.example.allinone.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.example.allinone.data.Investment
import com.example.allinone.data.Note
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTStudent
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
        try {
            // Create a temporary directory for the extracted files
            val tempDir = File(context.cacheDir, "restore_temp")
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
            }
            
            // Process the extracted files and restore the data
            val transactionsFile = File(tempDir, "transactions.json")
            val investmentsFile = File(tempDir, "investments.json")
            val notesFile = File(tempDir, "notes.json")
            val studentsFile = File(tempDir, "students.json")
            
            // Restore transactions
            if (transactionsFile.exists()) {
                InputStreamReader(FileInputStream(transactionsFile)).use { reader ->
                    val type = object : TypeToken<List<Transaction>>() {}.type
                    val transactions: List<Transaction> = gson.fromJson(reader, type)
                    
                    for (transaction in transactions) {
                        repository.updateTransaction(transaction)
                    }
                }
            }
            
            // Restore investments
            if (investmentsFile.exists()) {
                InputStreamReader(FileInputStream(investmentsFile)).use { reader ->
                    val type = object : TypeToken<List<Investment>>() {}.type
                    val investments: List<Investment> = gson.fromJson(reader, type)
                    
                    for (investment in investments) {
                        repository.updateInvestment(investment)
                    }
                }
            }
            
            // Restore notes
            if (notesFile.exists()) {
                InputStreamReader(FileInputStream(notesFile)).use { reader ->
                    val type = object : TypeToken<List<Note>>() {}.type
                    val notes: List<Note> = gson.fromJson(reader, type)
                    
                    for (note in notes) {
                        repository.updateNote(note)
                    }
                }
            }
            
            // Restore students
            if (studentsFile.exists()) {
                InputStreamReader(FileInputStream(studentsFile)).use { reader ->
                    val type = object : TypeToken<List<WTStudent>>() {}.type
                    val students: List<WTStudent> = gson.fromJson(reader, type)
                    
                    for (student in students) {
                        repository.updateStudent(student)
                    }
                }
            }
            
            // Clean up temporary files
            tempDir.deleteRecursively()
            
            // Force a refresh of all data from Firebase
            repository.refreshAllData()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Get a list of all backup files
     * @return A list of backup files
     */
    fun getBackupFiles(): List<File> {
        return backupDir.listFiles()?.filter { it.name.endsWith(".zip") } ?: emptyList()
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