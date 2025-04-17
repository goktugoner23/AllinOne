package com.example.allinone.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.allinone.data.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Manager class for Firebase operations (storage only)
 */
class FirebaseManager(private val context: Context? = null) {
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    val idManager = FirebaseIdManager()

    // Collection references
    private val transactionsCollection = firestore.collection("transactions")
    private val investmentsCollection = firestore.collection("investments")
    private val notesCollection = firestore.collection("notes")
    private val studentsCollection = firestore.collection("students")
    private val eventsCollection = firestore.collection("events")
    private val wtLessonsCollection = firestore.collection("wtLessons")
    private val registrationsCollection = firestore.collection("registrations")
    private val programsCollection = firestore.collection("programs")
    private val workoutsCollection = firestore.collection("workouts")
    private val exercisesCollection = firestore.collection("exercises")

    // Storage references
    private val imagesRef: StorageReference = storage.reference.child("images")
    private val attachmentsRef: StorageReference = storage.reference.child("attachments")
    private val storageRef: StorageReference = storage.reference

    // Constants
    companion object {
        private const val TAG = "FirebaseManager"
        private const val REGISTRATIONS_COLLECTION = "registrations"
    }

    // Device ID for anonymous data storage
    private val deviceId: String by lazy {
        if (context != null) {
            val sharedPrefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            var id = sharedPrefs.getString("device_id", null)
            if (id == null) {
                // Use a more consistent ID format with device details and timestamp
                id = "device_${System.currentTimeMillis()}_${Math.abs(android.os.Build.MODEL.hashCode())}"
                sharedPrefs.edit().putString("device_id", id).apply()
            }
            id
        } else {
            // Fallback for cases where context is null, still use a timestamp rather than random UUID
            "device_${System.currentTimeMillis()}"
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
                "deviceId" to deviceId,
                "relatedRegistrationId" to transaction.relatedRegistrationId
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
                    val relatedRegistrationId = doc.getLong("relatedRegistrationId")

                    Transaction(
                        id = id,
                        amount = amount,
                        type = type,
                        description = description,
                        isIncome = isIncome,
                        date = date,
                        category = category,
                        relatedRegistrationId = relatedRegistrationId
                    )
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
                // Only upload if it's a content URI
                if (imageUri.startsWith("content://")) {
                    val uri = Uri.parse(imageUri)
                    // Use sequential ID instead of UUID for image name
                    val imageId = idManager.getNextId("investment_images")
                    val imageRef = imagesRef.child("investment_$imageId")
                    imageRef.putFile(uri).await()
                    uploadedImageUrl = imageRef.downloadUrl.await().toString()
                } else if (imageUri.startsWith("http")) {
                    // If it's already a remote URL, just use it
                    uploadedImageUrl = imageUri
                } else {
                    Log.w(TAG, "Skipping investment image upload - invalid URI format: ${imageUri.take(10)}...")
                }
            } catch (e: Exception) {
                // Skip failed upload
                Log.e(TAG, "Failed to upload investment image: ${e.message}", e)
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
            "isPast" to investment.isPast,
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
                    val isPast = doc.getBoolean("isPast") ?: false

                    Investment(id, name, amount, type, description, imageUri, date, isPast)
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
                    // Skip urls that are already uploaded (http/https)
                    if (uriString.startsWith("http")) {
                        uploadedImageUrls.add(uriString)
                        continue
                    }

                    // Only process valid content URIs
                    if (uriString.startsWith("content://")) {
                        val uri = Uri.parse(uriString)

                        // Use the note-attachments folder and note ID subfolder
                        val noteAttachmentsRef = storageRef.child("note-attachments/${note.id}")

                        // Generate a unique filename for the image
                        val imageFileName = "img_${System.currentTimeMillis()}_${idManager.getNextId("note_images")}"
                        val imageRef = noteAttachmentsRef.child(imageFileName)

                        imageRef.putFile(uri).await()
                        val downloadUrl = imageRef.downloadUrl.await().toString()
                        uploadedImageUrls.add(downloadUrl)
                    } else {
                        Log.w(TAG, "Skipping image upload - invalid URI format: ${uriString.take(10)}...")
                    }
                } catch (e: Exception) {
                    // Skip failed uploads
                    Log.e(TAG, "Failed to upload image: ${e.message}", e)
                    continue
                }
            }
        }

        // Prepare to save note to Firestore

        val noteMap = hashMapOf(
            "id" to note.id,
            "title" to note.title,
            "content" to note.content,
            "date" to note.date,
            "imageUris" to uploadedImageUrls.joinToString(","),
            "voiceNoteUris" to note.voiceNoteUris,
            "lastEdited" to note.lastEdited,
            "isRichText" to note.isRichText,
            "deviceId" to deviceId
        )

        // Save the note to Firestore
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
                    val imageUris = doc.getString("imageUris") ?: doc.getString("imageUri") // Migrate old data
                    val voiceNoteUris = doc.getString("voiceNoteUris")
                    val lastEdited = doc.getDate("lastEdited") ?: Date()
                    val isRichText = doc.getBoolean("isRichText") ?: true

                    Note(
                        id = id,
                        title = title,
                        content = content,
                        date = date,
                        imageUris = imageUris,
                        voiceNoteUris = voiceNoteUris,
                        lastEdited = lastEdited,
                        isRichText = isRichText
                    )
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

            val studentWithId = if (student.id <= 0) {
                // Generate sequential ID
                val nextId = idManager.getNextId("students")
                student.copy(id = nextId)
            } else {
                student
            }

            // Convert to map for Firestore
            val studentMap = mapOf(
                "id" to studentWithId.id,
                "name" to studentWithId.name,
                "phoneNumber" to studentWithId.phoneNumber,
                "email" to (studentWithId.email ?: ""),
                "instagram" to (studentWithId.instagram ?: ""),
                "isActive" to studentWithId.isActive,
                "notes" to (studentWithId.notes ?: ""),
                "photoUri" to (studentWithId.photoUri ?: "")
            )

            Log.d(TAG, "Setting student document with ID: ${studentWithId.id}")

            // Always use the student ID directly as a string for the document ID
            // This ensures consistency between saving and loading
            val task = studentsCollection.document(studentWithId.id.toString()).set(studentMap)
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
                val snapshot = studentsCollection.get().await()
                snapshot.documents.mapNotNull { doc ->
                    // Parse the document ID as a Long directly instead of using hashCode
                    val id = try {
                        doc.id.toLong()
                    } catch (e: NumberFormatException) {
                        // Fallback to hashCode if the ID cannot be parsed as a Long
                        Log.w(TAG, "Failed to parse student ID as Long: ${doc.id}, using hashCode instead")
                        doc.id.hashCode().toLong()
                    }

                    val name = doc.getString("name") ?: ""
                    val phoneNumber = doc.getString("phoneNumber")
                    val email = doc.getString("email")
                    val instagram = doc.getString("instagram")
                    val isActive = doc.getBoolean("isActive") ?: true
                    val deviceId = doc.getString("deviceId") // Keep for backward compatibility
                    val notes = doc.getString("notes")
                    val photoUri = doc.getString("photoUri")

                    Log.d(TAG, "Loaded student: ID=$id, Name=$name")

                    WTStudent(
                        id = id,
                        name = name,
                        phoneNumber = phoneNumber,
                        email = email,
                        instagram = instagram,
                        isActive = isActive,
                        deviceId = deviceId,
                        notes = notes,
                        photoUri = photoUri
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching students: ${e.message}", e)
                emptyList()
            }
        }
    }

    // Storage
    suspend fun uploadImage(uri: Uri): String? {
        return try {
            // Verify the URI is a content URI
            if (!uri.toString().startsWith("content://")) {
                Log.w(TAG, "Invalid image URI format: ${uri.toString().take(10)}...")
                return null
            }

            val imageRef = imagesRef.child("${UUID.randomUUID()}")
            imageRef.putFile(uri).await()
            imageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image: ${e.message}", e)
            null
        }
    }

    suspend fun uploadAttachment(uri: Uri): String? {
        return try {
            // Verify the URI is a content URI
            if (!uri.toString().startsWith("content://")) {
                Log.w(TAG, "Invalid attachment URI format: ${uri.toString().take(10)}...")
                return null
            }

            val attachmentRef = attachmentsRef.child("${UUID.randomUUID()}")
            attachmentRef.putFile(uri).await()
            attachmentRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload attachment: ${e.message}", e)
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
            Log.d(TAG, "Starting investment deletion in FirebaseManager. ID: ${investment.id}, Name: ${investment.name}")

            // Be explicit about which document to delete by using the exact ID
            val docId = investment.id.toString()
            Log.d(TAG, "Deleting investment document with ID: $docId")

            // Use await with timeout to ensure operation completes
            val task = investmentsCollection.document(docId).delete()
            Tasks.await(task, 10, TimeUnit.SECONDS)

            Log.d(TAG, "Successfully deleted investment with ID: ${investment.id}")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting investment: ${e.message}", e)
            throw e
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
            Log.d(TAG, "Deleting student with ID: ${student.id}, name: ${student.name}")

            // Use string ID for document reference
            val documentId = student.id.toString()

            // Delete from Firestore with proper await
            studentsCollection.document(documentId).delete().await()

            Log.d(TAG, "Successfully deleted student with ID: ${student.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting student: ${e.message}", e)
            throw e
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
                "endDate" to event.endDate,
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
                val endDate = doc.getDate("endDate")
                val type = doc.getString("type") ?: "Event"

                Event(id, title, description, date, endDate, type)
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
     * Delete all data in Firestore for this device
     */
    suspend fun clearAllFirestoreData() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to delete all Firestore data for device: $deviceId")

            // Delete all transactions (including those without deviceId)
            deleteAllDocumentsInCollection(transactionsCollection)

            // Delete all investments (including those without deviceId)
            deleteAllDocumentsInCollection(investmentsCollection)

            // Delete all notes (including those without deviceId)
            deleteAllDocumentsInCollection(notesCollection)

            // Delete all students (including those without deviceId)
            deleteAllDocumentsInCollection(studentsCollection)

            // Delete all events (including those without deviceId)
            deleteAllDocumentsInCollection(eventsCollection)

            // Delete all WT lessons (including those without deviceId)
            deleteAllDocumentsInCollection(wtLessonsCollection)

            // Also clear test_connection collection
            deleteAllDocumentsInCollection(firestore.collection("test_connection"))

            Log.d(TAG, "Successfully deleted all Firestore data")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Firestore data: ${e.message}", e)
            throw e
        }
    }

    /**
     * Helper method to delete all documents in a collection
     */
    private suspend fun deleteAllDocumentsInCollection(collection: com.google.firebase.firestore.CollectionReference) {
        try {
            // Get all documents (not just those with matching deviceId)
            val allDocs = collection.get().await()
            Log.d(TAG, "Deleting ${allDocs.size()} documents from ${collection.path}")

            for (doc in allDocs) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing collection ${collection.path}: ${e.message}", e)
            // Continue with other collections rather than failing completely
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

    // Get all registrations
    suspend fun getRegistrations(): List<WTRegistration> {
        Log.d(TAG, "Getting registrations for device $deviceId")

        return try {
            val snapshot = firestore.collection(REGISTRATIONS_COLLECTION)
                .whereEqualTo("deviceId", deviceId)
                .get()
                .await()

            Log.d(TAG, "Received ${snapshot.documents.size} registration documents from Firestore")

            val registrations = snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id") ?: doc.id.hashCode().toLong()
                    val studentId = doc.getLong("studentId") ?: return@mapNotNull null
                    val amount = doc.getDouble("amount") ?: 0.0
                    val attachmentUri = doc.getString("attachmentUri")
                    val startDate = doc.getDate("startDate")
                    val endDate = doc.getDate("endDate")
                    val paymentDate = doc.getDate("paymentDate") ?: Date()
                    val notes = doc.getString("notes")
                    val isPaid = doc.getBoolean("isPaid") ?: true // Default to true if field is missing

                    WTRegistration(
                        id = id,
                        studentId = studentId,
                        amount = amount,
                        attachmentUri = attachmentUri,
                        startDate = startDate,
                        endDate = endDate,
                        paymentDate = paymentDate,
                        notes = notes,
                        isPaid = isPaid
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing registration document: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "Successfully parsed ${registrations.size} registrations")
            registrations
        } catch (e: Exception) {
            Log.e(TAG, "Error getting registrations: ${e.message}")
            throw e
        }
    }

    // Save registration
    suspend fun saveRegistration(registration: WTRegistration): Task<Void> {
        Log.d(TAG, "Saving registration with ID: ${registration.id}, studentId: ${registration.studentId}")

        val registrationMap = hashMapOf(
            "id" to registration.id,
            "studentId" to registration.studentId,
            "amount" to registration.amount,
            "attachmentUri" to registration.attachmentUri,
            "startDate" to registration.startDate,
            "endDate" to registration.endDate,
            "paymentDate" to registration.paymentDate,
            "notes" to registration.notes,
            "isPaid" to registration.isPaid,
            "deviceId" to deviceId
        )

        return firestore.collection(REGISTRATIONS_COLLECTION)
            .document(registration.id.toString())
            .set(registrationMap)
    }

    // Delete registration
    suspend fun deleteRegistration(registrationId: Long): Task<Void> {
        Log.d(TAG, "Deleting registration with ID: $registrationId")

        return firestore.collection(REGISTRATIONS_COLLECTION)
            .document(registrationId.toString())
            .delete()
    }

    // Get registrations for a specific student
    suspend fun getRegistrationsForStudent(studentId: Long): List<WTRegistration> {
        Log.d(TAG, "Getting registrations for student ID: $studentId")

        return try {
            val snapshot = firestore.collection(REGISTRATIONS_COLLECTION)
                .whereEqualTo("deviceId", deviceId)
                .whereEqualTo("studentId", studentId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id") ?: doc.id.hashCode().toLong()
                    val amount = doc.getDouble("amount") ?: 0.0
                    val attachmentUri = doc.getString("attachmentUri")
                    val startDate = doc.getDate("startDate")
                    val endDate = doc.getDate("endDate")
                    val paymentDate = doc.getDate("paymentDate") ?: Date()
                    val notes = doc.getString("notes")
                    val isPaid = doc.getBoolean("isPaid") ?: true // Default to true if field is missing

                    WTRegistration(
                        id = id,
                        studentId = studentId,
                        amount = amount,
                        attachmentUri = attachmentUri,
                        startDate = startDate,
                        endDate = endDate,
                        paymentDate = paymentDate,
                        notes = notes,
                        isPaid = isPaid
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing registration document: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting registrations for student: ${e.message}")
            emptyList()
        }
    }

    /**
     * Save a student to Firestore and return its generated ID
     */
    suspend fun saveStudentAndGetId(student: WTStudent): Long = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Saving student to get ID: ${student.name}")

            // Generate a new ID if not provided
            val studentId = if (student.id <= 0) {
                idManager.getNextId("students")
            } else {
                student.id
            }

            // Use the generated ID
            val studentWithId = student.copy(id = studentId)

            // Convert to map for Firestore
            val studentMap = mapOf(
                "id" to studentWithId.id,
                "name" to studentWithId.name,
                "phoneNumber" to studentWithId.phoneNumber,
                "email" to (studentWithId.email ?: ""),
                "instagram" to (studentWithId.instagram ?: ""),
                "isActive" to studentWithId.isActive,
                "notes" to (studentWithId.notes ?: ""),
                "photoUri" to (studentWithId.photoUri ?: "")
            )

            // Save to Firestore
            firestore.collection("students")
                .document(studentId.toString())
                .set(studentMap)
                .await()

            Log.d(TAG, "Student saved with ID: $studentId")
            studentId
        } catch (e: Exception) {
            Log.e(TAG, "Error saving student: ${e.message}", e)
            throw e
        }
    }

    /**
     * Save an investment to Firestore and return its generated ID
     */
    suspend fun saveInvestmentAndGetId(investment: Investment): Long = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Saving investment to get ID: ${investment.name}")

            // Generate a new ID if not provided
            val investmentId = if (investment.id <= 0) {
                idManager.getNextId("investments")
            } else {
                investment.id
            }

            // Use the generated ID
            val investmentWithId = investment.copy(id = investmentId)

            // Convert to map for Firestore
            val investmentMap = mapOf(
                "id" to investmentWithId.id,
                "name" to investmentWithId.name,
                "amount" to investmentWithId.amount,
                "type" to investmentWithId.type,
                "description" to (investmentWithId.description ?: ""),
                "imageUri" to (investmentWithId.imageUri ?: ""),
                "date" to investmentWithId.date,
                "isPast" to investmentWithId.isPast
            )

            // Save to Firestore
            firestore.collection("investments")
                .document(investmentId.toString())
                .set(investmentMap)
                .await()

            Log.d(TAG, "Investment saved with ID: $investmentId")
            investmentId
        } catch (e: Exception) {
            Log.e(TAG, "Error saving investment: ${e.message}", e)
            throw e
        }
    }

    // WT Events
    suspend fun uploadEventAttachment(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val attachmentId = idManager.getNextId("event_attachments")
            val attachmentRef = attachmentsRef.child("event_$attachmentId")
            attachmentRef.putFile(uri).await()
            return@withContext attachmentRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading event attachment: ${e.message}", e)
            return@withContext null
        }
    }

    // Workout Programs
    suspend fun saveProgram(program: Program) = withContext(Dispatchers.IO) {
        try {
            // Generate ID if not present
            val programWithId = if (program.id <= 0) {
                program.copy(id = idManager.getNextId("programs"))
            } else {
                program
            }

            // Convert program exercises to maps
            val exerciseMaps = programWithId.exercises.map { exercise ->
                mapOf(
                    "exerciseId" to exercise.exerciseId,
                    "exerciseName" to exercise.exerciseName,
                    "sets" to exercise.sets,
                    "reps" to exercise.reps,
                    "weight" to exercise.weight,
                    "notes" to exercise.notes
                )
            }

            val programMap = hashMapOf(
                "id" to programWithId.id,
                "name" to programWithId.name,
                "description" to programWithId.description,
                "exercises" to exerciseMaps,
                "createdDate" to programWithId.createdDate,
                "lastModifiedDate" to programWithId.lastModifiedDate,
                "deviceId" to deviceId
            )

            programsCollection.document(programWithId.id.toString()).set(programMap).await()
            return@withContext programWithId.id
        } catch (e: Exception) {
            Log.e(TAG, "Error saving program: ${e.message}", e)
            throw e
        }
    }

    suspend fun getPrograms(): List<Program> = withContext(Dispatchers.IO) {
        try {
            val snapshot = programsCollection.whereEqualTo("deviceId", deviceId).get().await()

            return@withContext snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: ""
                    val description = doc.getString("description")
                    val createdDate = doc.getDate("createdDate") ?: Date()
                    val lastModifiedDate = doc.getDate("lastModifiedDate") ?: Date()

                    // Parse exercises
                    @Suppress("UNCHECKED_CAST")
                    val exercisesList = doc.get("exercises") as? List<Map<String, Any>> ?: emptyList()
                    val exercises = exercisesList.map { exerciseMap ->
                        ProgramExercise(
                            exerciseId = (exerciseMap["exerciseId"] as? Number)?.toLong() ?: 0L,
                            exerciseName = exerciseMap["exerciseName"] as? String ?: "",
                            sets = (exerciseMap["sets"] as? Number)?.toInt() ?: 0,
                            reps = (exerciseMap["reps"] as? Number)?.toInt() ?: 0,
                            weight = (exerciseMap["weight"] as? Number)?.toDouble() ?: 0.0,
                            notes = exerciseMap["notes"] as? String
                        )
                    }

                    Program(
                        id = id,
                        name = name,
                        description = description,
                        exercises = exercises,
                        createdDate = createdDate,
                        lastModifiedDate = lastModifiedDate
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing program: ${e.message}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting programs: ${e.message}", e)
            return@withContext emptyList<Program>()
        }
    }

    suspend fun deleteProgram(programId: Long) = withContext(Dispatchers.IO) {
        return@withContext programsCollection.document(programId.toString()).delete()
    }

    // Workouts
    suspend fun saveWorkout(workout: Workout) = withContext(Dispatchers.IO) {
        try {
            // Generate ID if not present
            val workoutWithId = if (workout.id <= 0) {
                workout.copy(id = idManager.getNextId("workouts"))
            } else {
                workout
            }

            // Convert workout exercises to maps
            val exerciseMaps = workoutWithId.exercises.map { exercise ->
                val setMaps = exercise.sets.map { set ->
                    mapOf(
                        "setNumber" to set.setNumber,
                        "reps" to set.reps,
                        "weight" to set.weight,
                        "completed" to set.completed
                    )
                }

                mapOf(
                    "exerciseId" to exercise.exerciseId,
                    "exerciseName" to exercise.exerciseName,
                    "sets" to setMaps
                )
            }

            val workoutMap = hashMapOf(
                "id" to workoutWithId.id,
                "programId" to workoutWithId.programId,
                "programName" to workoutWithId.programName,
                "startTime" to workoutWithId.startTime,
                "endTime" to workoutWithId.endTime,
                "duration" to workoutWithId.duration,
                "exercises" to exerciseMaps,
                "notes" to workoutWithId.notes,
                "deviceId" to deviceId
            )

            workoutsCollection.document(workoutWithId.id.toString()).set(workoutMap).await()
            return@withContext workoutWithId.id
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workout: ${e.message}", e)
            throw e
        }
    }

    suspend fun getWorkouts(): List<Workout> = withContext(Dispatchers.IO) {
        try {
            val snapshot = workoutsCollection.whereEqualTo("deviceId", deviceId).get().await()

            return@withContext snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id") ?: return@mapNotNull null
                    val programId = doc.getLong("programId")
                    val programName = doc.getString("programName")
                    val startTime = doc.getDate("startTime") ?: Date()
                    val endTime = doc.getDate("endTime")
                    val duration = doc.getLong("duration") ?: 0L
                    val notes = doc.getString("notes")

                    // Parse exercises
                    @Suppress("UNCHECKED_CAST")
                    val exercisesList = doc.get("exercises") as? List<Map<String, Any>> ?: emptyList()
                    val exercises = exercisesList.map { exerciseMap ->
                        @Suppress("UNCHECKED_CAST")
                        val setsList = exerciseMap["sets"] as? List<Map<String, Any>> ?: emptyList()
                        val sets = setsList.map { setMap ->
                            WorkoutSet(
                                setNumber = (setMap["setNumber"] as? Number)?.toInt() ?: 0,
                                reps = (setMap["reps"] as? Number)?.toInt() ?: 0,
                                weight = (setMap["weight"] as? Number)?.toDouble() ?: 0.0,
                                completed = setMap["completed"] as? Boolean ?: false
                            )
                        }

                        WorkoutExercise(
                            exerciseId = (exerciseMap["exerciseId"] as? Number)?.toLong() ?: 0L,
                            exerciseName = exerciseMap["exerciseName"] as? String ?: "",
                            sets = sets
                        )
                    }

                    Workout(
                        id = id,
                        programId = programId,
                        programName = programName,
                        startTime = startTime,
                        endTime = endTime,
                        duration = duration,
                        exercises = exercises,
                        notes = notes
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing workout: ${e.message}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workouts: ${e.message}", e)
            return@withContext emptyList<Workout>()
        }
    }

    suspend fun deleteWorkout(workoutId: Long) = withContext(Dispatchers.IO) {
        return@withContext workoutsCollection.document(workoutId.toString()).delete()
    }
}