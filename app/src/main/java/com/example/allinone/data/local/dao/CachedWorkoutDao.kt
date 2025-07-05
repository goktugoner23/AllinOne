package com.example.allinone.data.local.dao

import androidx.room.*
import com.example.allinone.data.local.entities.CachedWorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedWorkoutDao {
    
    @Query("SELECT * FROM cached_workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<CachedWorkoutEntity>>
    
    @Query("SELECT * FROM cached_workouts WHERE id = :id")
    suspend fun getWorkoutById(id: Long): CachedWorkoutEntity?
    
    @Query("SELECT * FROM cached_workouts WHERE programId = :programId ORDER BY startTime DESC")
    fun getWorkoutsByProgram(programId: Long): Flow<List<CachedWorkoutEntity>>
    
    @Query("SELECT * FROM cached_workouts WHERE programName LIKE '%' || :searchQuery || '%' OR notes LIKE '%' || :searchQuery || '%' ORDER BY startTime DESC")
    fun searchWorkouts(searchQuery: String): Flow<List<CachedWorkoutEntity>>
    
    @Query("SELECT * FROM cached_workouts WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    fun getWorkoutsByDateRange(startTime: Long, endTime: Long): Flow<List<CachedWorkoutEntity>>
    
    @Query("SELECT * FROM cached_workouts WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun getCompletedWorkouts(): Flow<List<CachedWorkoutEntity>>
    
    @Query("SELECT * FROM cached_workouts WHERE endTime IS NULL ORDER BY startTime DESC")
    fun getIncompleteWorkouts(): Flow<List<CachedWorkoutEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: CachedWorkoutEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWorkouts(workouts: List<CachedWorkoutEntity>)
    
    @Update
    suspend fun updateWorkout(workout: CachedWorkoutEntity)
    
    @Delete
    suspend fun deleteWorkout(workout: CachedWorkoutEntity)
    
    @Query("DELETE FROM cached_workouts WHERE id = :id")
    suspend fun deleteWorkoutById(id: Long)
    
    @Query("DELETE FROM cached_workouts")
    suspend fun deleteAllWorkouts()
    
    @Query("SELECT COUNT(*) FROM cached_workouts")
    suspend fun getWorkoutCount(): Int
    
    @Query("SELECT COUNT(*) FROM cached_workouts WHERE endTime IS NOT NULL")
    suspend fun getCompletedWorkoutCount(): Int
    
    @Query("SELECT SUM(duration) FROM cached_workouts WHERE endTime IS NOT NULL")
    suspend fun getTotalWorkoutDuration(): Long?
    
    @Query("DELETE FROM cached_workouts WHERE cachedAt < :expiredTime")
    suspend fun deleteExpiredWorkouts(expiredTime: Long)
    
    // Weekly stats query
    @Query("SELECT * FROM cached_workouts WHERE startTime >= :weekStart AND startTime <= :weekEnd ORDER BY startTime DESC")
    fun getWeeklyWorkouts(weekStart: Long, weekEnd: Long): Flow<List<CachedWorkoutEntity>>
} 