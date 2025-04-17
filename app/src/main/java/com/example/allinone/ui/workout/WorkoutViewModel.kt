package com.example.allinone.ui.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Program
import com.example.allinone.data.Workout
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseManager = FirebaseManager()
    private val idManager = FirebaseIdManager()

    private val _allPrograms = MutableLiveData<List<Program>>(emptyList())
    val allPrograms: LiveData<List<Program>> = _allPrograms

    private val _allWorkouts = MutableLiveData<List<Workout>>(emptyList())
    val allWorkouts: LiveData<List<Workout>> = _allWorkouts

    init {
        // Load initial data
        loadPrograms()
        loadWorkouts()
    }

    private fun loadPrograms() {
        viewModelScope.launch {
            try {
                val programs = withContext(Dispatchers.IO) {
                    firebaseManager.getPrograms()
                }
                _allPrograms.value = programs
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun loadWorkouts() {
        viewModelScope.launch {
            try {
                val workouts = withContext(Dispatchers.IO) {
                    firebaseManager.getWorkouts()
                }
                _allWorkouts.value = workouts
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun saveProgram(program: Program) {
        viewModelScope.launch {
            try {
                // Generate ID if not present
                val programWithId = if (program.id == 0L) {
                    program.copy(id = idManager.getNextId("programs"))
                } else {
                    program
                }

                // Save to Firebase
                withContext(Dispatchers.IO) {
                    firebaseManager.saveProgram(programWithId)
                }

                // Update local data
                val currentPrograms = _allPrograms.value?.toMutableList() ?: mutableListOf()
                val index = currentPrograms.indexOfFirst { it.id == programWithId.id }
                if (index >= 0) {
                    currentPrograms[index] = programWithId
                } else {
                    currentPrograms.add(programWithId)
                }
                _allPrograms.value = currentPrograms
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteProgram(programId: Long) {
        viewModelScope.launch {
            try {
                // Delete from Firebase
                withContext(Dispatchers.IO) {
                    firebaseManager.deleteProgram(programId)
                }

                // Update local data
                val currentPrograms = _allPrograms.value?.toMutableList() ?: mutableListOf()
                val index = currentPrograms.indexOfFirst { it.id == programId }
                if (index >= 0) {
                    currentPrograms.removeAt(index)
                    _allPrograms.value = currentPrograms
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun saveWorkout(workout: Workout) {
        viewModelScope.launch {
            try {
                // Generate ID if not present
                val workoutWithId = if (workout.id == 0L) {
                    workout.copy(id = idManager.getNextId("workouts"))
                } else {
                    workout
                }

                // Save to Firebase
                withContext(Dispatchers.IO) {
                    firebaseManager.saveWorkout(workoutWithId)
                }

                // Update local data
                val currentWorkouts = _allWorkouts.value?.toMutableList() ?: mutableListOf()
                val index = currentWorkouts.indexOfFirst { it.id == workoutWithId.id }
                if (index >= 0) {
                    currentWorkouts[index] = workoutWithId
                } else {
                    currentWorkouts.add(workoutWithId)
                }
                _allWorkouts.value = currentWorkouts
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            try {
                // Delete from Firebase
                withContext(Dispatchers.IO) {
                    firebaseManager.deleteWorkout(workoutId)
                }

                // Update local data
                val currentWorkouts = _allWorkouts.value?.toMutableList() ?: mutableListOf()
                val index = currentWorkouts.indexOfFirst { it.id == workoutId }
                if (index >= 0) {
                    currentWorkouts.removeAt(index)
                    _allWorkouts.value = currentWorkouts
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun getWeeklyWorkouts(workouts: List<Workout>): List<Workout> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val weekStart = calendar.time

        calendar.add(Calendar.DAY_OF_WEEK, 7)
        val weekEnd = calendar.time

        return workouts.filter { workout ->
            workout.startTime.after(weekStart) && workout.startTime.before(weekEnd)
        }
    }
}
