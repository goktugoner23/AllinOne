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
import java.util.UUID

/**
 * Utility class for handling file uploads to Firebase Storage
 */
class FirebaseStorageUtil(private val context: Context) {
    private val TAG = "FirebaseStorageUtil"
    private val storageRef = FirebaseStorage.getInstance().reference
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

    /**
     * Upload a file to Firebase Storage and return the download URL
     *
     * @param fileUri The URI of the file to upload
     * @param folderName The folder name in Firebase Storage (e.g., "registrations", "notes")
     * @return The download URL of the uploaded file or null if upload failed
     */
    suspend fun uploadFile(fileUri: Uri, folderName: String): String? {
        return try {
            Log.d(TAG, "Starting file upload: $fileUri to folder: $folderName")
            
            // Generate a unique file name
            val fileName = generateFileName(fileUri)
            Log.d(TAG, "Generated file name: $fileName")
            
            // Reference to the file location in Firebase Storage
            val fileRef = storageRef.child("$folderName/$fileName")
            Log.d(TAG, "File reference path: ${fileRef.path}")
            
            // Get content type
            val contentType = getContentType(fileUri)
            Log.d(TAG, "Content type: $contentType")
            
            // Create metadata with content type
            val metadata = StorageMetadata.Builder()
                .setContentType(contentType)
                .build()
            
            // Upload file with metadata and progress tracking
            val uploadTask = fileRef.putFile(fileUri, metadata)
            
            // Add progress listener
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                Log.d(TAG, "Upload progress: $progress%")
            }
            
            // Wait for upload to complete and get download URL
            val taskSnapshot = uploadTask.await()
            val downloadUrl = taskSnapshot.storage.downloadUrl.await().toString()
            
            Log.d(TAG, "File uploaded successfully. Download URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file: ${e.message}", e)
            null
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
     * Generate a unique file name for the upload
     */
    private fun generateFileName(fileUri: Uri): String {
        val originalName = getOriginalFileName(fileUri)
        val extension = getFileExtension(fileUri)
        val uuid = UUID.randomUUID().toString()
        
        return if (originalName != null) {
            // Keep original name but add UUID to ensure uniqueness
            "${originalName.substringBeforeLast(".")}_${uuid}.${extension}"
        } else {
            // Use only UUID with extension
            "$uuid.${extension}"
        }
    }
    
    /**
     * Get the original file name from the URI
     */
    private fun getOriginalFileName(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                try {
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    documentFile?.name
                } catch (e: Exception) {
                    // Fallback to query
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex("_display_name")
                            if (nameIndex != -1) {
                                cursor.getString(nameIndex)
                            } else null
                        } else null
                    }
                }
            }
            "file" -> {
                uri.lastPathSegment
            }
            else -> null
        }
    }
    
    /**
     * Get the file extension from the URI
     */
    private fun getFileExtension(uri: Uri): String {
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(uri))
        
        return extension ?: when {
            uri.toString().contains(".") -> {
                uri.toString().substringAfterLast(".")
            }
            else -> "bin" // Default binary extension
        }
    }
    
    /**
     * Get the content type of the file
     */
    private fun getContentType(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType ?: "application/octet-stream" // Default binary mime type
    }
} 