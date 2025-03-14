package com.example.allinone.data

import java.util.Date

/**
 * Data class representing a Wing Tzun seminar event
 */
data class WTSeminar(
    val id: Long = System.currentTimeMillis(), // Default to current time as unique ID
    val name: String,
    val date: Date,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val description: String? = null,
    val location: String? = null
) {
    companion object {
        /**
         * Creates a new seminar with end time calculated as 4 hours after the start time
         */
        fun createWithDefaultDuration(
            name: String,
            date: Date,
            startHour: Int,
            startMinute: Int,
            description: String? = null,
            location: String? = null
        ): WTSeminar {
            // Calculate end time (4 hours after start time)
            var endHour = startHour + 4
            var endMinute = startMinute
            
            // Handle overflow (if end hour exceeds 24)
            if (endHour >= 24) {
                endHour -= 24
            }
            
            return WTSeminar(
                name = name,
                date = date,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                description = description,
                location = location
            )
        }
    }
} 