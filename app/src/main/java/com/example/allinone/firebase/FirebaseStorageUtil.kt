package com.example.allinone.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Utility class for handling file uploads to Firebase Storage with ID-specific subfolders
 */
class FirebaseStorageUtil(private val context: Context) {
    private val TAG = "FirebaseStorageUtil"
    private val storageRef = FirebaseStorage.getInstance().reference
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    private val idManager = FirebaseIdManager()

    /**
     * Upload a file to Firebase Storage and return the download URL
     * Files are stored in a structure: /{folderName}/{id}/filename
     * 
     * @param fileUri The URI of the file to upload
     * @param folderName The folder name in Firebase Storage (e.g., "registrations", "profile_pictures")
     * @param id Optional ID for creating a subfolder (defaults to sequential ID)
     * @return The download URL of the uploaded file or null if upload failed
     */
    suspend fun uploadFile(fileUri: Uri, folderName: String, id: String? = null): String? {
        try {
            // Generate subfolder ID if not provided
            val folderId = id ?: idManager.getNextId(folderName).toString()
            
            // Get file name
            val fileName = getFileName(fileUri)
            
            // Create reference to the file location
            val fileRef = storageRef.child("$folderName/$folderId/$fileName")
            
            // Get MIME type
            val mimeType = getMimeType(fileUri)
            
            // Create metadata if MIME type is available
            val metadata = if (mimeType != null) {
                StorageMetadata.Builder()
                    .setContentType(mimeType)
                    .build()
            } else null
            
            // Upload file with metadata if available
            if (metadata != null) {
                fileRef.putFile(fileUri, metadata).await()
            } else {
                fileRef.putFile(fileUri).await()
            }
            
            // Return download URL
            return fileRef.downloadUrl.await().toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Delete a file from Firebase Storage
     *
     * @param fileUrl The download URL of the file to delete
     * @return True if the deletion was successful, false otherwise
     */
    suspend fun deleteFile(fileUrl: String): Boolean {
        return try {
            Log.d(TAG, "Deleting file: $fileUrl")
            
            // Get reference from URL
            val fileRef = FirebaseStorage.getInstance().getReferenceFromUrl(fileUrl)
            
            // Delete the file
            fileRef.delete().await()
            
            Log.d(TAG, "File deleted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete all files in a specific folder in Firebase Storage
     *
     * @param folderName The name of the main folder (e.g., "profile_pictures")
     * @param folderId The ID of the subfolder to delete (e.g., student ID)
     * @return True if the deletion was successful, false otherwise
     */
    suspend fun deleteFolder(folderName: String, folderId: String): Boolean {
        return try {
            Log.d(TAG, "Deleting folder: $folderName/$folderId")
            
            // Create reference to the folder
            val folderRef = storageRef.child("$folderName/$folderId")
            
            // List all items in the folder
            val result = folderRef.listAll().await()
            
            // Delete each item
            result.items.forEach { item ->
                item.delete().await()
                Log.d(TAG, "Deleted file: ${item.path}")
            }
            
            // Delete subfolders recursively if any
            result.prefixes.forEach { prefix ->
                deleteFolder(folderName, "$folderId/${prefix.name}")
            }
            
            Log.d(TAG, "Folder deleted successfully: $folderName/$folderId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting folder: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get the file name from a Uri
     */
    private fun getFileName(uri: Uri): String {
        // Try to get the file name from the content resolver
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex("_display_name")
                if (displayNameIndex != -1) {
                    val displayName = cursor.getString(displayNameIndex)
                    if (!displayName.isNullOrEmpty()) {
                        return displayName
                    }
                }
            }
        }
        
        // If that fails, try to get the last path segment
        uri.lastPathSegment?.let { segment ->
            if (segment.isNotEmpty()) {
                return segment
            }
        }
        
        // If all else fails, generate a timestamp-based name
        return "file_${System.currentTimeMillis()}"
    }
    
    /**
     * Get the MIME type of a file from its Uri
     */
    private fun getMimeType(uri: Uri): String? {
        // Check if the uri is a content uri
        if (uri.scheme == "content") {
            return context.contentResolver.getType(uri)
        }
        
        // If not a content uri, try to get the file extension
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        if (fileExtension != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
        }
        
        // Default to octet-stream if unable to determine
        return "application/octet-stream"
    }
} 