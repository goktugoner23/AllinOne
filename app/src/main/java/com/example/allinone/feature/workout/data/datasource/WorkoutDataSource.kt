package com.example.allinone.feature.workout.data.datasource

import com.example.allinone.core.data.datasource.LocalDataSource
import com.example.allinone.core.data.datasource.RemoteDataSource
import com.example.allinone.core.data.datasource.SearchableDataSource
import com.example.allinone.data.Workout
import kotlinx.coroutines.flow.Flow

/**
 * Remote DataSource interface for Workout operations via Firebase
 */
interface WorkoutRemoteDataSource : RemoteDataSource<Workout>

/**
 * Local DataSource interface for Workout operations via Room
 */
interface WorkoutLocalDataSource : SearchableDataSource<Workout>, LocalDataSource<Workout> {
    fun getWorkoutsByProgram(programId: Long): Flow<List<Workout>>
    fun getWorkoutsByDateRange(startTime: Long, endTime: Long): Flow<List<Workout>>
    fun getCompletedWorkouts(): Flow<List<Workout>>
    fun getIncompleteWorkouts(): Flow<List<Workout>>
    fun getWeeklyWorkouts(weekStart: Long, weekEnd: Long): Flow<List<Workout>>
    suspend fun getCompletedWorkoutCount(): Int
    suspend fun getTotalWorkoutDuration(): Long
} 