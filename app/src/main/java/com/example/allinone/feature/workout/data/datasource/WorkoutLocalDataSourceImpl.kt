package com.example.allinone.feature.workout.data.datasource

import android.util.Log
import com.example.allinone.data.Workout
import com.example.allinone.data.local.dao.CachedWorkoutDao
import com.example.allinone.data.local.entities.CachedWorkoutEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-based implementation of WorkoutLocalDataSource
 * Handles local caching of workouts with efficient querying
 */
@Singleton
class WorkoutLocalDataSourceImpl @Inject constructor(
    private val workoutDao: CachedWorkoutDao
) : WorkoutLocalDataSource {
    
    companion object {
        private const val TAG = "WorkoutLocalDataSource"
    }
    
    override suspend fun getAll(): List<Workout> {
        return try {
            emptyList() // Use Flow for reactive data access
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all workouts: ${e.message}")
            emptyList()
        }
    }
    
    override fun getAllAsFlow(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts().map { entities ->
            entities.map { it.toWorkout() }
        }
    }
    
    override suspend fun getById(id: Long): Workout? {
        return try {
            workoutDao.getWorkoutById(id)?.toWorkout()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workout by id $id: ${e.message}")
            null
        }
    }
    
    override fun getByIdAsFlow(id: Long): Flow<Workout?> {
        return getAllAsFlow().map { workouts ->
            workouts.find { it.id == id }
        }
    }
    
    override suspend fun save(item: Workout) {
        try {
            val entity = CachedWorkoutEntity.fromWorkout(item)
            workoutDao.insertWorkout(entity)
            Log.d(TAG, "Workout saved with id: ${item.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workout: ${e.message}")
        }
    }
    
    override suspend fun saveAll(items: List<Workout>) {
        try {
            val entities = items.map { CachedWorkoutEntity.fromWorkout(it) }
            workoutDao.insertAllWorkouts(entities)
            Log.d(TAG, "Saved ${items.size} workouts")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workouts: ${e.message}")
        }
    }
    
    override suspend fun delete(item: Workout) {
        try {
            workoutDao.deleteWorkoutById(item.id)
            Log.d(TAG, "Workout deleted with id: ${item.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workout: ${e.message}")
        }
    }
    
    override suspend fun deleteById(id: Long) {
        try {
            workoutDao.deleteWorkoutById(id)
            Log.d(TAG, "Workout deleted with id: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workout by id: ${e.message}")
        }
    }
    
    override suspend fun deleteAll() {
        try {
            workoutDao.deleteAllWorkouts()
            Log.d(TAG, "All workouts deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all workouts: ${e.message}")
        }
    }
    
    override suspend fun getCount(): Int {
        return try {
            workoutDao.getWorkoutCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workout count: ${e.message}")
            0
        }
    }
    
    override fun search(query: String): Flow<List<Workout>> {
        return workoutDao.searchWorkouts(query).map { entities ->
            entities.map { it.toWorkout() }
        }
    }
    
    override fun getWorkoutsByProgram(programId: Long): Flow<List<Workout>> {
        return workoutDao.getWorkoutsByProgram(programId).map { entities ->
            entities.map { it.toWorkout() }
        }
    }
    
    override fun getWorkoutsByDateRange(startTime: Long, endTime: Long): Flow<List<Workout>> {
        return workoutDao.getWorkoutsByDateRange(startTime, endTime).map { entities ->
            entities.map { it.toWorkout() }
        }
    }
    
    override fun getCompletedWorkouts(): Flow<List<Workout>> {
        return workoutDao.getCompletedWorkouts().map { entities ->
            entities.map { it.toWorkout() }
        }
    }
    
    override fun getIncompleteWorkouts(): Flow<List<Workout>> {
        return workoutDao.getIncompleteWorkouts().map { entities ->
            entities.map { it.toWorkout() }
        }
    }
    
    override fun getWeeklyWorkouts(weekStart: Long, weekEnd: Long): Flow<List<Workout>> {
        return workoutDao.getWeeklyWorkouts(weekStart, weekEnd).map { entities ->
            entities.map { it.toWorkout() }
        }
    }
    
    override suspend fun getCompletedWorkoutCount(): Int {
        return try {
            workoutDao.getCompletedWorkoutCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting completed workout count: ${e.message}")
            0
        }
    }
    
    override suspend fun getTotalWorkoutDuration(): Long {
        return try {
            workoutDao.getTotalWorkoutDuration() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total workout duration: ${e.message}")
            0L
        }
    }
    
    override suspend fun clearExpiredCache(expiredTime: Long) {
        try {
            workoutDao.deleteExpiredWorkouts(expiredTime)
            Log.d(TAG, "Expired workouts cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing expired cache: ${e.message}")
        }
    }
    
    override suspend fun isHealthy(): Boolean {
        return try {
            workoutDao.getWorkoutCount() >= 0
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed: ${e.message}")
            false
        }
    }
} 