package com.example.allinone.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Data class representing a workout program
 */
@Parcelize
data class Program(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val exercises: List<ProgramExercise> = emptyList(),
    val createdDate: Date = Date(),
    val lastModifiedDate: Date = Date()
) : Parcelable

/**
 * Data class representing an exercise within a program
 */
@Parcelize
data class ProgramExercise(
    val exerciseId: Long,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val notes: String? = null
) : Parcelable
