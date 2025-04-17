package com.example.allinone.config

/**
 * Configuration class for muscle groups
 * Centralizes all muscle groups in one place for easier maintenance
 */
object MuscleGroups {
    // Main muscle groups list used for dropdown selection
    val MUSCLE_GROUPS = arrayOf(
        "Chest",
        "Back",
        "Shoulders",
        "Biceps",
        "Triceps",
        "Legs",
        "Abs",
        "Calves",
        "Forearms",
        "Traps",
        "Glutes",
        "Full Body",
        "Cardio"
    )
    
    // Upper body muscle groups
    val UPPER_BODY = arrayOf(
        "Chest",
        "Back",
        "Shoulders",
        "Biceps",
        "Triceps",
        "Forearms",
        "Traps"
    )
    
    // Lower body muscle groups
    val LOWER_BODY = arrayOf(
        "Legs",
        "Calves",
        "Glutes"
    )
    
    // Core muscle groups
    val CORE = arrayOf(
        "Abs"
    )
} 