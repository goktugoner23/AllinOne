package com.example.allinone.firebase

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Manages sequential ID generation for Firebase Firestore documents.
 * This class handles retrieving and incrementing ID counters stored in the "counters" collection.
 */
class FirebaseIdManager {
    private val firestore = FirebaseFirestore.getInstance()
    private val countersCollection = firestore.collection("counters")

    /**
     * Gets the next sequential ID for a specific collection.
     * IDs start from 0 and increment sequentially.
     *
     * @param collectionName The name of the collection to get the next ID for
     * @return The next available ID as Long
     */
    suspend fun getNextId(collectionName: String): Long {
        try {
            // Use a standardized collection name for workout-related items
            val standardizedCollectionName = when (collectionName) {
                "programs" -> "workouts_programs"
                "workouts" -> "workouts_sessions"
                "exercises" -> "workouts_exercises"
                else -> collectionName
            }
            
            // Check if document exists
            val docRef = countersCollection.document(standardizedCollectionName)
            val doc = docRef.get().await()

            // If document doesn't exist, create it with initial count of 1
            if (!doc.exists() || doc.getLong("count") == null) {
                val initialData = mapOf("count" to 1L)
                docRef.set(initialData).await()
                return 1L
            }

            // Get the current count and atomically increment it
            docRef.update("count", FieldValue.increment(1)).await()
            
            // Retrieve the updated count
            val updatedDoc = docRef.get().await()
            val newCount = updatedDoc.getLong("count") ?: 1L
            
            return newCount
        } catch (e: Exception) {
            // Log failure and handle gracefully
            Log.e("FirebaseIdManager", "Failed to get next ID: ${e.message}")
            
            // In case of failure, generate a timestamp-based ID as fallback
            return System.currentTimeMillis()
        }
    }

    /**
     * Gets the last used ID for a specific collection without incrementing it.
     * This is useful for retrieving the current counter value without changing it.
     *
     * @param collectionName The name of the collection to get the last ID for
     * @return The last used ID as Long
     */
    suspend fun getLastId(collectionName: String): Long {
        try {
            // Get the current counter value without incrementing
            val counterDoc = countersCollection.document(collectionName).get().await()
            val count = counterDoc.getLong("count")

            return count ?: 0L
        } catch (e: Exception) {
            // If the counter doesn't exist, create it starting at 0
            if (e.message?.contains("NOT_FOUND") == true) {
                try {
                    // Create the counter document with initial value 0
                    val initialData = hashMapOf("count" to 0L)
                    countersCollection.document(collectionName).set(initialData).await()
                    return 0L // Return 0 as the first ID
                } catch (e2: Exception) {
                    Log.e(TAG, "Error creating counter for $collectionName", e2)
                    return 0L
                }
            } else {
                Log.e(TAG, "Error getting last ID for $collectionName", e)
                return 0L
            }
        }
    }

    companion object {
        private const val TAG = "FirebaseIdManager"
    }
}