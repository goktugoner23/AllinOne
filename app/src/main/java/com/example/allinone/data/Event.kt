package com.example.allinone.data

import java.util.Date

/**
 * Data class representing an event in the calendar.
 * 
 * @property id Unique identifier for the event
 * @property title Title of the event
 * @property description Optional description of the event
 * @property date Date and time of the event
 * @property endDate Optional end date and time of the event (null means single-point event)
 * @property type Type of event (e.g., "Event", "Lesson")
 */
data class Event(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val date: Date = Date(),
    val endDate: Date? = null,
    val type: String = "Event"
) {
    // No-argument constructor required for Firestore
    constructor() : this(
        id = 0,
        title = "",
        description = null,
        date = Date(),
        endDate = null,
        type = "Event"
    )
} 