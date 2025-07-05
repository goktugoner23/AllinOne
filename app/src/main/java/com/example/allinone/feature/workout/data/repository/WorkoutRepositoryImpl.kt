package com.example.allinone.feature.workout.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.allinone.data.Workout
import com.example.allinone.feature.workout.data.datasource.WorkoutLocalDataSource
import com.example.allinone.feature.workout.data.datasource.WorkoutRemoteDataSource
import com.example.allinone.feature.workout.domain.repository.WorkoutRepository
import com.example.allinone.firebase.OfflineQueue
import com.example.allinone.utils.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combined repository that implements the DataSource pattern for Workouts.
 * Uses local data source (Room) for caching and remote data source (Firebase) for synchronization.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val localDataSource: WorkoutLocalDataSource,
    private val remoteDataSource: WorkoutRemoteDataSource,
    private val networkUtils: NetworkUtils,
    private val offlineQueue: OfflineQueue
) : WorkoutRepository {
    
    companion object {
        private const val TAG = "WorkoutRepository"
    }
    
    private val gson = Gson()
    
    // State management
    private val _isLoading = MutableLiveData<Boolean>(false)
    override val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    override val errorMessage: LiveData<String?> = _errorMessage
    
    override val workouts: Flow<List<Workout>> = localDataSource.getAllAsFlow()
    
    override suspend fun refreshWorkouts() {
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                
                if (!networkUtils.isActiveNetworkConnected()) {
                    Log.d(TAG, "No network connection, using cached workouts")
                    _isLoading.postValue(false)
                    return@withContext
                }
                
                // Fetch from remote and cache locally
                val remoteWorkouts = remoteDataSource.getAll()
                localDataSource.saveAll(remoteWorkouts)
                
                Log.d(TAG, "Refreshed ${remoteWorkouts.size} workouts from remote")
                _isLoading.postValue(false)
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing workouts: ${e.message}")
                _isLoading.postValue(false)
                Log.e(TAG, "Error refreshing workouts", e)
            }
        }
    }
    
    override suspend fun getWorkouts(): List<Workout> {
        return try {
            val localWorkouts = localDataSource.getAllAsFlow().first()
            if (localWorkouts.isEmpty() && networkUtils.isActiveNetworkConnected()) {
                val remoteWorkouts = remoteDataSource.getAll()
                localDataSource.saveAll(remoteWorkouts)
                remoteWorkouts
            } else {
                localWorkouts
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workouts: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun insertWorkout(workout: Workout) {
        try {
            localDataSource.save(workout)
            
            if (networkUtils.isActiveNetworkConnected()) {
                try {
                    remoteDataSource.save(workout)
                    Log.d(TAG, "Workout saved to remote: ${workout.id}")
                } catch (e: Exception) {
                    queueOfflineOperation(workout, OfflineQueue.Operation.INSERT)
                    _errorMessage.postValue("Workout saved locally. Will sync when network is available.")
                }
            } else {
                queueOfflineOperation(workout, OfflineQueue.Operation.INSERT)
                _errorMessage.postValue("Workout saved locally. Will sync when network is available.")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving workout: ${e.message}")
            Log.e(TAG, "Error inserting workout", e)
        }
    }
    
    override suspend fun updateWorkout(workout: Workout) {
        try {
            localDataSource.save(workout)
            
            if (networkUtils.isActiveNetworkConnected()) {
                try {
                    remoteDataSource.save(workout)
                    Log.d(TAG, "Workout updated in remote: ${workout.id}")
                } catch (e: Exception) {
                    queueOfflineOperation(workout, OfflineQueue.Operation.UPDATE)
                    _errorMessage.postValue("Workout updated locally. Will sync when network is available.")
                }
            } else {
                queueOfflineOperation(workout, OfflineQueue.Operation.UPDATE)
                _errorMessage.postValue("Workout updated locally. Will sync when network is available.")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating workout: ${e.message}")
            Log.e(TAG, "Error updating workout", e)
        }
    }
    
    override suspend fun deleteWorkout(workout: Workout) {
        try {
            localDataSource.delete(workout)
            
            if (networkUtils.isActiveNetworkConnected()) {
                try {
                    remoteDataSource.delete(workout)
                    Log.d(TAG, "Workout deleted from remote: ${workout.id}")
                } catch (e: Exception) {
                    queueOfflineOperation(workout, OfflineQueue.Operation.DELETE)
                    _errorMessage.postValue("Workout deleted locally. Will sync when network is available.")
                }
            } else {
                queueOfflineOperation(workout, OfflineQueue.Operation.DELETE)
                _errorMessage.postValue("Workout deleted locally. Will sync when network is available.")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting workout: ${e.message}")
            Log.e(TAG, "Error deleting workout", e)
        }
    }
    
    override suspend fun getWorkoutById(id: Long): Workout? {
        return try {
            localDataSource.getById(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workout by id: ${e.message}")
            null
        }
    }
    
    override fun searchWorkouts(query: String): Flow<List<Workout>> {
        return localDataSource.search(query)
    }
    
    override fun getWorkoutsByProgram(programId: Long): Flow<List<Workout>> {
        return localDataSource.getWorkoutsByProgram(programId)
    }
    
    override fun getWorkoutsByDateRange(startTime: Long, endTime: Long): Flow<List<Workout>> {
        return localDataSource.getWorkoutsByDateRange(startTime, endTime)
    }
    
    override fun getCompletedWorkouts(): Flow<List<Workout>> {
        return localDataSource.getCompletedWorkouts()
    }
    
    override fun getIncompleteWorkouts(): Flow<List<Workout>> {
        return localDataSource.getIncompleteWorkouts()
    }
    
    override fun getWeeklyWorkouts(weekStart: Long, weekEnd: Long): Flow<List<Workout>> {
        return localDataSource.getWeeklyWorkouts(weekStart, weekEnd)
    }
    
    override suspend fun getCompletedWorkoutCount(): Int {
        return localDataSource.getCompletedWorkoutCount()
    }
    
    override suspend fun getTotalWorkoutDuration(): Long {
        return localDataSource.getTotalWorkoutDuration()
    }
    
    override fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    private fun queueOfflineOperation(workout: Workout, operation: OfflineQueue.Operation) {
        try {
            offlineQueue.enqueue(
                OfflineQueue.DataType.WORKOUT,
                operation,
                gson.toJson(workout)
            )
            Log.d(TAG, "Workout operation queued for offline sync: $operation")
        } catch (e: Exception) {
            Log.e(TAG, "Error queueing offline operation: ${e.message}")
        }
    }
} 