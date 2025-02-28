package com.example.allinone.data

import java.util.Date

/**
 * Data class representing an event in the Wing Tzun calendar.
 * 
 * @property id Unique identifier for the event
 * @property title Title of the event
 * @property description Optional description of the event
 * @property date Date and time of the event
 * @property type Type of event (e.g., "Event", "Lesson")
 */
data class WTEvent(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val date: Date,
    val type: String = "Event"
) 