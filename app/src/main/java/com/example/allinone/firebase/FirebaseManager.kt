package com.example.allinone.firebase

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import com.example.allinone.data.Transaction
import com.example.allinone.data.Investment
import com.example.allinone.data.Note
import com.example.allinone.data.WTStudent
import com.example.allinone.data.Event
import com.example.allinone.data.WTLesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Date
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.util.Log
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Manager class for Firebase operations (storage only)
 */
class FirebaseManager(private val context: Context? = null) {
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    
    // Collection references
    private val transactionsCollection = firestore.collection("transactions")
    private val investmentsCollection = firestore.collection("investments")
    private val notesCollection = firestore.collection("notes")
    private val studentsCollection = firestore.collection("students")
    private val eventsCollection = firestore.collection("events")
    private val wtLessonsCollection = firestore.collection("wtLessons")
    
    // Storage references
    private val imagesRef: StorageReference = storage.reference.child("images")
    private val attachmentsRef: StorageReference = storage.reference.child("attachments")
    
    // Constants
    companion object {
        private const val TAG = "FirebaseManager"
    }
    
    // Device ID for anonymous data storage
    private val deviceId: String by lazy {
        if (context != null) {
            val sharedPrefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            var id = sharedPrefs.getString("device_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                sharedPrefs.edit().putString("device_id", id).apply()
            }
            id
        } else {
            UUID.randomUUID().toString()
        }
    }
    
    // Transactions
    suspend fun saveTransaction(transaction: Transaction): Boolean {
        try {
            Log.d(TAG, "Starting to save transaction with ID: ${transaction.id}, deviceId: $deviceId")
            
            val transactionMap = hashMapOf(
                "id" to transaction.id,
                "amount" to transaction.amount,
                "type" to transaction.type,
                "category" to transaction.category,
                "description" to transaction.description,
                "isIncome" to transaction.isIncome,
                "date" to transaction.date,
                "deviceId" to deviceId
            )
            
            Log.d(TAG, "Setting transaction document with ID: ${transaction.id}")
            
            // Use a task with timeout
            val task = transactionsCollection.document(transaction.id.toString()).set(transactionMap)
            Tasks.await(task, 15, TimeUnit.SECONDS)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transaction: ${e.message}", e)
            return false
        }
    }
    
    suspend fun getTransactions(): List<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = transactionsCollection.whereEqualTo("deviceId", deviceId).get().await()
                snapshot.documents.mapNotNull { doc ->
                    val id = doc.getLong("id") ?: return@mapNotNull null
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: ""
                    val category = doc.getString("category") ?: ""
                    val description = doc.getString("description") ?: ""
                    val isIncome = doc.getBoolean("isIncome") ?: false
                    val date = doc.getDate("date") ?: Date()
                    
                    Transaction(id, amount, type, description, isIncome, date, category)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    // Investments
    suspend fun saveInvestment(investment: Investment) {
        // Upload images first if any
        val imageUri = investment.imageUri
        var uploadedImageUrl = ""
        
        if (!imageUri.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(imageUri)
                val imageRef = imagesRef.child("${UUID.randomUUID()}")
                imageRef.putFile(uri).await()
                uploadedImageUrl = imageRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                // Skip failed upload
            }
        }
        
        val investmentMap = hashMapOf(
            "id" to investment.id,
            "name" to investment.name,
            "type" to investment.type,
            "amount" to investment.amount,
            "description" to investment.description,
            "date" to investment.date,
            "imageUri" to uploadedImageUrl,
            "profitLoss" to investment.profitLoss,
            "deviceId" to deviceId
        )
        
        investmentsCollection.document(investment.id.toString()).set(investmentMap).await()
    }
    
    suspend fun getInvestments(): List<Investment> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = investmentsCollection.whereEqualTo("deviceId", deviceId).get().await()
                snapshot.documents.mapNotNull { doc ->
                    val id = doc.getLong("id") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: ""
                    val type = doc.getString("type") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val description = doc.getString("description")
                    val date = doc.getDate("date") ?: Date()
                    val imageUri = doc.getString("imageUri")
                    val profitLoss = doc.getDouble("profitLoss") ?: 0.0
                    
                    Investment(id, name, amount, type, description, imageUri, date, profitLoss)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    // Notes
    suspend fun saveNote(note: Note) {
        // Upload images first if any
        val imageUris = note.imageUris
        val uploadedImageUrls = mutableListOf<String>()
        
        if (!imageUris.isNullOrEmpty()) {
            val uriList = imageUris.split(",").filter { it.isNotEmpty() }
            for (uriString in uriList) {
                try {
                    val uri = Uri.parse(uriString)
                    val imageRef = imagesRef.child("${UUID.randomUUID()}")
                    imageRef.putFile(uri).await()
                    val downloadUrl = imageRef.downloadUrl.await().toString()
                    uploadedImageUrls.add(downloadUrl)
                } catch (e: Exception) {
                    // Skip failed uploads
                    continue
                }
            }
        }
        
        val noteMap = hashMapOf(
            "id" to note.id,
            "title" to note.title,
            "content" to note.content,
            "date" to note.date,
            "imageUri" to note.imageUri,
            "imageUris" to note.imageUris,
            "lastEdited" to note.lastEdited,
            "isRichText" to note.isRichText,
            "deviceId" to deviceId
        )
        
        notesCollection.document(note.id.toString()).set(noteMap).await()
    }
    
    suspend fun getNotes(): List<Note> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = notesCollection.whereEqualTo("deviceId", deviceId).get().await()
                snapshot.documents.mapNotNull { doc ->
                    val id = doc.getLong("id") ?: return@mapNotNull null
                    val title = doc.getString("title") ?: ""
                    val content = doc.getString("content") ?: ""
                    val date = doc.getDate("date") ?: Date()
                    val imageUri = doc.getString("imageUri")
                    val imageUris = doc.getString("imageUris")
                    val lastEdited = doc.getDate("lastEdited") ?: Date()
                    val isRichText = doc.getBoolean("isRichText") ?: true
                    
                    Note(id, title, content, date, imageUri, imageUris, lastEdited, isRichText)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    // WT Students
    suspend fun saveStudent(student: WTStudent): Boolean {
        try {
            Log.d(TAG, "Starting to save student with ID: ${student.id}, name: ${student.name}, deviceId: $deviceId")
            
            // Upload attachment if any
            var attachmentUrl = ""
            if (student.attachmentUri != null) {
                try {
                    val uri = Uri.parse(student.attachmentUri)
                    val attachmentRef = attachmentsRef.child("${UUID.randomUUID()}")
                    attachmentRef.putFile(uri).await()
                    attachmentUrl = attachmentRef.downloadUrl.await().toString()
                    Log.d(TAG, "Uploaded attachment for student: $attachmentUrl")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload attachment: ${e.message}", e)
                    // Skip failed upload
                }
            }
            
            // Upload profile image if any and it's a local file
            var profileImageUrl = student.profileImageUri
            if (student.profileImageUri != null && student.profileImageUri.startsWith("content://")) {
                try {
                    val uri = Uri.parse(student.profileImageUri)
                    val imageRef = imagesRef.child("profiles/${UUID.randomUUID()}")
                    imageRef.putFile(uri).await()
                    profileImageUrl = imageRef.downloadUrl.await().toString()
                    Log.d(TAG, "Uploaded profile image for student: $profileImageUrl")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload profile image: ${e.message}", e)
                    // Keep the original URI on failure
                }
            }
            
            val studentMap = hashMapOf(
                "id" to student.id,
                "name" to student.name,
                "phoneNumber" to student.phoneNumber,
                "email" to student.email,
                "instagram" to student.instagram,
                "isActive" to student.isActive,
                "profileImageUri" to profileImageUrl,
                "startDate" to student.startDate,
                "endDate" to student.endDate,
                "amount" to student.amount,
                "isPaid" to student.isPaid,
                "paymentDate" to student.paymentDate,
                "attachmentUri" to attachmentUrl,
                "deviceId" to deviceId
            )
            
            Log.d(TAG, "Setting student document with ID: ${student.id}")
            
            // Use a task with timeout
            val task = studentsCollection.document(student.id.toString()).set(studentMap)
            Tasks.await(task, 15, TimeUnit.SECONDS)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving student: ${e.message}", e)
            return false
        }
    }
    
    suspend fun getStudents(): List<WTStudent> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = studentsCollection.whereEqualTo("deviceId", deviceId).get().await()
                snapshot.documents.mapNotNull { doc ->
                    val id = doc.id.hashCode().toLong()
                    val name = doc.getString("name") ?: ""
                    val phoneNumber = doc.getString("phoneNumber") ?: ""
                    val email = doc.getString("email")
                    val instagram = doc.getString("instagram")
                    val isActive = doc.getBoolean("isActive") ?: true
                    val profileImageUri = doc.getString("profileImageUri")
                    val startDate = doc.getDate("startDate")
                    val endDate = doc.getDate("endDate")
                    val amount = doc.getDouble("amount") ?: 0.0
                    val isPaid = doc.getBoolean("isPaid") ?: false
                    val paymentDate = doc.getDate("paymentDate")
                    val attachmentUri = doc.getString("attachmentUri")
                    
                    WTStudent(
                        id = id, 
                        name = name,
                        phoneNumber = phoneNumber,
                        email = email,
                        instagram = instagram,
                        isActive = isActive,
                        profileImageUri = profileImageUri,
                        startDate = startDate,
                        endDate = endDate,
                        amount = amount,
                        isPaid = isPaid,
                        paymentDate = paymentDate,
                        attachmentUri = attachmentUri
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    // Storage
    suspend fun uploadImage(uri: Uri): String? {
        return try {
            val imageRef = imagesRef.child("${UUID.randomUUID()}")
            imageRef.putFile(uri).await()
            imageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun uploadAttachment(uri: Uri): String? {
        return try {
            val attachmentRef = attachmentsRef.child("${UUID.randomUUID()}")
            attachmentRef.putFile(uri).await()
            attachmentRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }
    
    // Delete methods
    suspend fun deleteTransaction(transaction: Transaction) {
        try {
            transactionsCollection.document(transaction.id.toString()).delete().await()
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    suspend fun deleteInvestment(investment: Investment) {
        try {
            investmentsCollection.document(investment.id.toString()).delete().await()
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    suspend fun deleteNote(note: Note) {
        try {
            notesCollection.document(note.id.toString()).delete().await()
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    suspend fun deleteStudent(student: WTStudent) {
        try {
            studentsCollection.document(student.id.toString()).delete().await()
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    // WT Events
    suspend fun saveEvent(event: Event) = withContext(Dispatchers.IO) {
        try {
            val eventMap = hashMapOf(
                "id" to event.id,
                "title" to event.title,
                "description" to event.description,
                "date" to event.date,
                "type" to event.type,
                "deviceId" to deviceId
            )
            
            return@withContext eventsCollection.document(event.id.toString()).set(eventMap)
        } catch (e: Exception) {
            throw e
        }
    }
    
    suspend fun getEvents(): List<Event> = withContext(Dispatchers.IO) {
        try {
            val snapshot = eventsCollection.whereEqualTo("deviceId", deviceId).get().await()
            
            return@withContext snapshot.documents.map { doc ->
                val id = doc.getLong("id") ?: 0L
                val title = doc.getString("title") ?: ""
                val description = doc.getString("description")
                val date = doc.getDate("date") ?: Date()
                val type = doc.getString("type") ?: "Event"
                
                Event(id, title, description, date, type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting events", e)
            return@withContext emptyList<Event>()
        }
    }
    
    suspend fun deleteEvent(eventId: Long) = withContext(Dispatchers.IO) {
        return@withContext eventsCollection.document(eventId.toString()).delete()
    }
    
    // WT Lessons
    suspend fun saveWTLesson(lesson: WTLesson) = withContext(Dispatchers.IO) {
        val lessonMap = mapOf(
            "id" to lesson.id,
            "dayOfWeek" to lesson.dayOfWeek,
            "startHour" to lesson.startHour,
            "startMinute" to lesson.startMinute,
            "endHour" to lesson.endHour,
            "endMinute" to lesson.endMinute,
            "deviceId" to deviceId
        )
        
        wtLessonsCollection.document(lesson.id.toString())
            .set(lessonMap)
    }
    
    suspend fun deleteWTLesson(lessonId: Long) = withContext(Dispatchers.IO) {
        wtLessonsCollection.document(lessonId.toString())
            .delete()
    }
    
    suspend fun getAllWTLessons() = withContext(Dispatchers.IO) {
        val snapshot = wtLessonsCollection
            .whereEqualTo("deviceId", deviceId)
            .get()
            .await()
        
        snapshot.documents.mapNotNull { doc ->
            try {
                val id = doc.getLong("id") ?: return@mapNotNull null
                val dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: return@mapNotNull null
                val startHour = doc.getLong("startHour")?.toInt() ?: return@mapNotNull null
                val startMinute = doc.getLong("startMinute")?.toInt() ?: return@mapNotNull null
                val endHour = doc.getLong("endHour")?.toInt() ?: return@mapNotNull null
                val endMinute = doc.getLong("endMinute")?.toInt() ?: return@mapNotNull null
                
                WTLesson(
                    id = id,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Test Firebase connection and project setup
     */
    suspend fun testConnection(): Boolean {
        Log.d(TAG, "Testing Firebase connection...")
        try {
            // Create a test document with timestamp
            val timestamp = System.currentTimeMillis()
            val testData = hashMapOf(
                "test" to true,
                "timestamp" to timestamp,
                "created" to Date(timestamp),
                "deviceId" to deviceId
            )
            
            // Use a unique document ID to avoid conflicts
            val docRef = firestore.collection("test_connection")
                .document("test_${timestamp}_${deviceId.take(8)}")
            
            Log.d(TAG, "Creating test document...")
            
            // Add the test document with a timeout
            try {
                val task = docRef.set(testData)
                Tasks.await(task, 10, TimeUnit.SECONDS)
                Log.d(TAG, "Test document created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create test document: ${e.message}", e)
                throw e
            }
            
            // Try to read it back
            try {
                Log.d(TAG, "Verifying test document was created...")
                val snapshot = docRef.get().await()
                val exists = snapshot.exists()
                
                if (exists) {
                    Log.d(TAG, "Firebase connection test successful")
                    return true
                } else {
                    Log.w(TAG, "Test document was not found after writing it")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify test document: ${e.message}", e)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase connection test failed: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Check if we have proper Firestore security rules set up
     */
    suspend fun checkSecurityRules(): Boolean {
        return try {
            // Try to read from the transactions collection
            transactionsCollection
                .limit(1)
                .get()
                .await()
            
            // If we reach here, we have proper access to the collection
            true
        } catch (e: Exception) {
            if (e.message?.contains("PERMISSION_DENIED") == true) {
                // Security rules issue
                return false
            }
            // Some other error
            throw e
        }
    }
    
    /**
     * Check if we're using the correct Firebase project
     */
    suspend fun validateFirebaseProject(): Boolean {
        return try {
            // Try to read from test collection (should have open rules)
            firestore.collection("test")
                .document("connectivity_test")
                .get()
                .await()
            
            // If we get here, we have a valid project connection
            true
        } catch (e: Exception) {
            if (e.message?.contains("project") == true && 
                e.message?.contains("placeholder") == true) {
                // We're using a placeholder project
                return false
            }
            // Some other error
            throw e
        }
    }
} 