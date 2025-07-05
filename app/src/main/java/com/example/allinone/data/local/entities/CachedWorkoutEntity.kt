package com.example.allinone.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.allinone.data.Workout
import com.example.allinone.data.WorkoutExercise
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

@Entity(tableName = "cached_workouts")
data class CachedWorkoutEntity(
    @PrimaryKey val id: Long,
    val programId: Long?,
    val programName: String?,
    val startTime: Long,
    val endTime: Long?,
    val duration: Long,
    val exercisesJson: String, // Store exercises as JSON for simplicity
    val notes: String?,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toWorkout(): Workout {
        val gson = Gson()
        val exerciseListType = object : TypeToken<List<WorkoutExercise>>() {}.type
        val exercises = try {
            gson.fromJson<List<WorkoutExercise>>(exercisesJson, exerciseListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        return Workout(
            id = id,
            programId = programId,
            programName = programName,
            startTime = Date(startTime),
            endTime = endTime?.let { Date(it) },
            duration = duration,
            exercises = exercises,
            notes = notes
        )
    }
    
    companion object {
        fun fromWorkout(workout: Workout): CachedWorkoutEntity {
            val gson = Gson()
            val exercisesJson = gson.toJson(workout.exercises)
            
            return CachedWorkoutEntity(
                id = workout.id,
                programId = workout.programId,
                programName = workout.programName,
                startTime = workout.startTime.time,
                endTime = workout.endTime?.time,
                duration = workout.duration,
                exercisesJson = exercisesJson,
                notes = workout.notes
            )
        }
    }
} 