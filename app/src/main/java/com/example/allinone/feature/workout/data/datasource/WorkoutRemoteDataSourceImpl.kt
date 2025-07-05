package com.example.allinone.feature.workout.data.datasource

import android.util.Log
import com.example.allinone.data.Workout
import com.example.allinone.firebase.FirebaseManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-based implementation of WorkoutRemoteDataSource
 * Handles remote synchronization of workouts with Firebase Firestore
 */
@Singleton
class WorkoutRemoteDataSourceImpl @Inject constructor(
    private val firebaseManager: FirebaseManager
) : WorkoutRemoteDataSource {
    
    companion object {
        private const val TAG = "WorkoutRemoteDataSource"
    }
    
    override suspend fun getAll(): List<Workout> {
        return try {
            firebaseManager.getWorkouts()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all workouts from Firebase: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun getById(id: Long): Workout? {
        return try {
            getAll().find { it.id == id }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workout by id $id from Firebase: ${e.message}")
            null
        }
    }
    
    override suspend fun save(item: Workout) {
        try {
            firebaseManager.saveWorkout(item)
            Log.d(TAG, "Workout saved to Firebase with id: ${item.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workout to Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun saveAll(items: List<Workout>) {
        try {
            items.forEach { workout ->
                firebaseManager.saveWorkout(workout)
            }
            Log.d(TAG, "Saved ${items.size} workouts to Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workouts to Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun delete(item: Workout) {
        try {
            firebaseManager.deleteWorkout(item.id)
            Log.d(TAG, "Workout deleted from Firebase with id: ${item.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workout from Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun deleteById(id: Long) {
        try {
            firebaseManager.deleteWorkout(id)
            Log.d(TAG, "Workout deleted from Firebase with id: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workout by id from Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun deleteAll() {
        try {
            val allWorkouts = getAll()
            allWorkouts.forEach { workout ->
                firebaseManager.deleteWorkout(workout.id)
            }
            Log.d(TAG, "All workouts deleted from Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all workouts from Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun getCount(): Int {
        return try {
            getAll().size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workout count from Firebase: ${e.message}")
            0
        }
    }
} 