package com.example.allinone.ui.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Program
import com.example.allinone.data.Workout
import com.example.allinone.data.WorkoutExercise
import com.example.allinone.data.WorkoutSet
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.firebase.FirebaseManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseManager = FirebaseManager(application.applicationContext)
    private val idManager = FirebaseIdManager()
    private val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create()

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
                android.util.Log.d("WorkoutViewModel", "Loaded ${programs.size} programs: ${programs.map { it.name }}")
                _allPrograms.value = programs
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error loading programs", e)
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

    /**
     * Checks if a program with the given ID exists in the current program list
     */
    fun programExists(programId: Long): Boolean {
        return _allPrograms.value?.any { it.id == programId } ?: false
    }

    /**
     * Gets a program by its ID from the current program list
     */
    fun getProgramById(programId: Long): Program? {
        return _allPrograms.value?.firstOrNull { it.id == programId }
    }

    fun deleteProgram(programId: Long) {
        viewModelScope.launch {
            try {
                // Check if any workouts use this program
                val workoutsWithProgram = _allWorkouts.value?.filter { it.programId == programId } ?: emptyList()

                // Log information about the deletion
                android.util.Log.d("WorkoutViewModel", "Deleting program ID: $programId, ${workoutsWithProgram.size} workouts reference this program")

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

                // Notify via log that program was deleted successfully
                android.util.Log.d("WorkoutViewModel", "Program ID: $programId deleted successfully")
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error deleting program: ${e.message}", e)
                // Handle error
            }
        }
    }

    fun saveWorkout(workout: Workout, callback: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                // Log workout details before saving
                android.util.Log.d("WorkoutViewModel", "Saving workout: ${workout.programName} with ${workout.exercises.size} exercises")
                if (workout.exercises.isNotEmpty()) {
                    android.util.Log.d("WorkoutViewModel", "Exercises before ID generation: ${workout.exercises.map { "${it.exerciseName} (${it.sets.size} sets)" }}")
                }

                // Generate ID if not present
                val workoutWithId = if (workout.id == 0L) {
                    // Create a copy with new ID but preserve all other properties including exercises
                    // Make a DEEP COPY of the exercises to prevent reference issues
                    val exercisesCopy: List<WorkoutExercise> = workout.exercises.map { exercise ->
                        val setsCopy: List<WorkoutSet> = exercise.sets.map { set ->
                            WorkoutSet(
                                setNumber = set.setNumber,
                                reps = set.reps,
                                weight = set.weight,
                                completed = set.completed
                            )
                        }

                        WorkoutExercise(
                            exerciseId = exercise.exerciseId,
                            exerciseName = exercise.exerciseName,
                            muscleGroup = exercise.muscleGroup,
                            sets = setsCopy
                        )
                    }

                    // Create a new workout with the copied exercises and the new ID
                    workout.copy(
                        id = idManager.getNextId("workouts"),
                        exercises = exercisesCopy
                    )
                } else {
                    workout
                }

                // Verify exercises after ID generation
                android.util.Log.d("WorkoutViewModel", "After ID generation: ${workoutWithId.exercises.size} exercises")

                // Log each exercise for debugging
                workoutWithId.exercises.forEachIndexed { index, exercise ->
                    android.util.Log.d("WorkoutViewModel", "Exercise ${index + 1}: ${exercise.exerciseName} with ${exercise.sets.size} sets")
                }

                // Save to Firebase
                withContext(Dispatchers.IO) {
                    firebaseManager.saveWorkout(workoutWithId)
                }

                // Update local data immediately after successful save
                val currentWorkouts = _allWorkouts.value?.toMutableList() ?: mutableListOf()
                val index = currentWorkouts.indexOfFirst { it.id == workoutWithId.id }
                if (index >= 0) {
                    currentWorkouts[index] = workoutWithId
                } else {
                    currentWorkouts.add(workoutWithId)
                }
                _allWorkouts.value = currentWorkouts

                android.util.Log.d("WorkoutViewModel", "Workout saved successfully. Total workouts: ${currentWorkouts.size}")

                // Ensure callback is executed on main thread
                withContext(Dispatchers.Main) {
                    callback?.invoke(true)
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error saving workout: ${e.message}", e)

                // Ensure callback is executed on main thread
                withContext(Dispatchers.Main) {
                    callback?.invoke(false)
                }
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

    fun refreshWorkouts() {
        android.util.Log.d("WorkoutViewModel", "Manually refreshing workouts data")
        viewModelScope.launch {
            try {
                val workouts = withContext(Dispatchers.IO) {
                    firebaseManager.getWorkouts()
                }
                android.util.Log.d("WorkoutViewModel", "Successfully loaded ${workouts.size} workouts from Firebase")

                // Log each workout for debugging
                workouts.forEach { workout ->
                    android.util.Log.d("WorkoutViewModel", "Workout: ${workout.id}, ${workout.programName ?: "Unnamed"}, Exercises: ${workout.exercises.size}")
                }

                _allWorkouts.value = workouts
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error refreshing workouts: ${e.message}", e)
                // Keep the existing data if there's an error
            }
        }
    }

    /**
     * Force refresh programs data from Firebase
     */
    fun refreshPrograms() {
        android.util.Log.d("WorkoutViewModel", "Manually refreshing programs data")
        viewModelScope.launch {
            try {
                val programs = withContext(Dispatchers.IO) {
                    firebaseManager.getPrograms()
                }
                android.util.Log.d("WorkoutViewModel", "Successfully loaded ${programs.size} programs from Firebase")

                // Log each program for debugging
                programs.forEach { program ->
                    android.util.Log.d("WorkoutViewModel", "Program: ${program.id}, ${program.name}, Exercises: ${program.exercises.size}")
                }

                _allPrograms.value = programs
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error refreshing programs: ${e.message}", e)
                // Keep the existing data if there's an error
            }
        }
    }

    fun getProgram(programId: Long, callback: (Program?) -> Unit) {
        viewModelScope.launch {
            try {
                android.util.Log.d("WorkoutViewModel", "Getting program details for ID: $programId")

                val program = withContext(Dispatchers.IO) {
                    firebaseManager.getProgramById(programId)
                }

                if (program != null) {
                    android.util.Log.d("WorkoutViewModel", "Retrieved program: ${program.name} with ${program.exercises.size} exercises")
                } else {
                    android.util.Log.d("WorkoutViewModel", "Program with ID $programId not found")
                }

                callback(program)
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error retrieving program: ${e.message}", e)
                callback(null)
            }
        }
    }

    // Add functions for workout serialization/deserialization for passing between fragments
    fun workoutToJson(workout: Workout): String {
        return gson.toJson(workout)
    }

    fun parseWorkoutFromJson(json: String): Workout {
        return gson.fromJson(json, Workout::class.java)
    }
}
