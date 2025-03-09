package com.example.allinone.data

import java.util.Date

/**
 * Data class representing an event in the calendar.
 * 
 * @property id Unique identifier for the event
 * @property title Title of the event
 * @property description Optional description of the event
 * @property date Date and time of the event
 * @property type Type of event (e.g., "Event", "Lesson")
 */
data class Event(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val date: Date = Date(),
    val type: String = "Event"
) {
    // No-argument constructor required for Firestore
    constructor() : this(
        id = 0,
        title = "",
        description = null,
        date = Date(),
        type = "Event"
    )
} 