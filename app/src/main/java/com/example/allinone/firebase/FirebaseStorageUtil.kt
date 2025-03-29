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
 * Utility class for handling file uploads to Firebase Storage with ID-specific subfolders
 */
class FirebaseStorageUtil(private val context: Context) {
    private val TAG = "FirebaseStorageUtil"
    private val storageRef = FirebaseStorage.getInstance().reference
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

    /**
     * Upload a file to Firebase Storage and return the download URL
     * Files are stored in a structure: /{folderName}/{id}/filename
     * 
     * @param fileUri The URI of the file to upload
     * @param folderName The folder name in Firebase Storage (e.g., "registrations", "profile_pictures")
     * @param id Optional ID for creating a subfolder (defaults to random UUID)
     * @return The download URL of the uploaded file or null if upload failed
     */
    suspend fun uploadFile(fileUri: Uri, folderName: String, id: String? = null): String? {
        Log.d(TAG, "********** STORAGE UTIL UPLOAD **********")
        Log.d(TAG, "Starting file upload: fileUri=$fileUri, folderName=$folderName, id=$id")
        
        // Try to get file size
        try {
            val fileSize = context.contentResolver.openFileDescriptor(fileUri, "r")?.statSize ?: -1
            Log.d(TAG, "File size: ${fileSize/1024} KB")
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get file size: ${e.message}")
        }
        
        try {
            // Generate a unique file name
            val fileName = generateFileName(fileUri)
            Log.d(TAG, "Generated file name: $fileName")
            
            // Create subfolder ID based on provided ID or random UUID
            val subfolderId = id ?: UUID.randomUUID().toString()
            Log.d(TAG, "Using subfolder ID: $subfolderId")
            
            // Reference to the file location in Firebase Storage with subfolder structure
            val storagePath = "$folderName/$subfolderId/$fileName"
            Log.d(TAG, "Storage path: $storagePath")
            val fileRef = storageRef.child(storagePath)
            
            // Get content type
            val contentType = getContentType(fileUri)
            Log.d(TAG, "Content type: $contentType")
            
            // Create metadata with content type
            val metadata = StorageMetadata.Builder()
                .setContentType(contentType)
                .build()
            
            Log.d(TAG, "Starting upload task...")
            
            // Upload file with metadata and progress tracking
            val uploadTask = fileRef.putFile(fileUri, metadata)
            
            // Add progress listener
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                Log.d(TAG, "Upload progress: $progress%")
            }
            
            // Wait for upload to complete and get download URL
            val taskSnapshot = uploadTask.await()
            Log.d(TAG, "Upload completed. Awaiting download URL...")
            
            val downloadUrl = taskSnapshot.storage.downloadUrl.await().toString()
            
            Log.d(TAG, "File uploaded successfully!")
            Log.d(TAG, "Download URL: $downloadUrl")
            return downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            return null
        } finally {
            Log.d(TAG, "********** END STORAGE UTIL UPLOAD **********")
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