package com.example.allinone.data

import java.util.Date

/**
 * Data class representing a schedule template in the app.
 * Schedule templates can be used to create recurring daily activities
 * like "wake up @ 7 am", "work @ 12 pm", etc.
 * 
 * @property id Unique identifier for the schedule
 * @property title Title of the schedule item (e.g., "Wake up", "Work")
 * @property description Optional description of the schedule item
 * @property hour Hour of the day (0-23)
 * @property minute Minute of the hour (0-59)
 * @property isEnabled Whether this schedule item is active
 * @property daysOfWeek Comma-separated string of days (e.g., "1,2,3,4,5" for weekdays)
 * @property color Hex color code for visual identification
 * @property category Category of the schedule (e.g., "Work", "Personal", "Health")
 * @property createdAt When this schedule was created
 */
data class Schedule(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val hour: Int = 0,
    val minute: Int = 0,
    val isEnabled: Boolean = true,
    val daysOfWeek: String = "1,2,3,4,5,6,7", // Default: all days
    val color: String = "#4CAF50", // Default: green
    val category: String = "Personal",
    val createdAt: Date = Date()
) {
    // No-argument constructor required for Firestore
    constructor() : this(
        id = 0,
        title = "",
        description = null,
        hour = 0,
        minute = 0,
        isEnabled = true,
        daysOfWeek = "1,2,3,4,5,6,7",
        color = "#4CAF50",
        category = "Personal",
        createdAt = Date()
    )
    
    /**
     * Get formatted time string (HH:mm)
     */
    fun getFormattedTime(): String {
        return String.format("%02d:%02d", hour, minute)
    }
    
    /**
     * Get list of enabled days as integers (1=Monday, 7=Sunday)
     */
    fun getEnabledDays(): List<Int> {
        return if (daysOfWeek.isBlank()) emptyList()
        else daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
    
    /**
     * Get day names for display
     */
    fun getDayNames(): String {
        val dayMap = mapOf(
            1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
            5 to "Fri", 6 to "Sat", 7 to "Sun"
        )
        val days = getEnabledDays()
        return when {
            days.size == 7 -> "Every day"
            days == listOf(1,2,3,4,5) -> "Weekdays"
            days == listOf(6,7) -> "Weekends"
            else -> days.mapNotNull { dayMap[it] }.joinToString(", ")
        }
    }
} 