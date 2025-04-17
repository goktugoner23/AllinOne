package com.example.allinone.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Data class representing a completed workout session
 */
@Parcelize
data class Workout(
    val id: Long = 0,
    val programId: Long? = null,
    val programName: String? = null,
    val startTime: Date = Date(),
    val endTime: Date? = null,
    val duration: Long = 0, // Duration in milliseconds
    val exercises: List<WorkoutExercise> = emptyList(),
    val notes: String? = null
) : Parcelable

/**
 * Data class representing an exercise performed during a workout
 */
@Parcelize
data class WorkoutExercise(
    val exerciseId: Long,
    val exerciseName: String,
    val sets: List<WorkoutSet> = emptyList()
) : Parcelable

/**
 * Data class representing a set performed during a workout
 */
@Parcelize
data class WorkoutSet(
    val setNumber: Int,
    val reps: Int,
    val weight: Double,
    val completed: Boolean = false
) : Parcelable
