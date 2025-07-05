package com.example.allinone.feature.workout.domain.repository

import androidx.lifecycle.LiveData
import com.example.allinone.data.Workout
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for Workout repository operations
 * This interface defines the contract for workout data operations
 * following clean architecture principles
 */
interface WorkoutRepository {
    
    // Reactive data access
    val workouts: Flow<List<Workout>>
    val isLoading: LiveData<Boolean>
    val errorMessage: LiveData<String?>
    
    // Core operations
    suspend fun refreshWorkouts()
    suspend fun getWorkouts(): List<Workout>
    suspend fun insertWorkout(workout: Workout)
    suspend fun updateWorkout(workout: Workout)
    suspend fun deleteWorkout(workout: Workout)
    suspend fun getWorkoutById(id: Long): Workout?
    
    // Filtering and querying
    fun searchWorkouts(query: String): Flow<List<Workout>>
    fun getWorkoutsByProgram(programId: Long): Flow<List<Workout>>
    fun getWorkoutsByDateRange(startTime: Long, endTime: Long): Flow<List<Workout>>
    fun getCompletedWorkouts(): Flow<List<Workout>>
    fun getIncompleteWorkouts(): Flow<List<Workout>>
    fun getWeeklyWorkouts(weekStart: Long, weekEnd: Long): Flow<List<Workout>>
    
    // Stats and analytics
    suspend fun getCompletedWorkoutCount(): Int
    suspend fun getTotalWorkoutDuration(): Long
    
    // Error handling
    fun clearErrorMessage()
} 