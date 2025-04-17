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
            // Atomic increment of the counter and retrieval of the new value
            countersCollection.document(collectionName)
                .update("count", FieldValue.increment(1))
                .await()

            // Get the updated counter value
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
                Log.e(TAG, "Error getting next ID for $collectionName", e)
                return 0L
            }
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