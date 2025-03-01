package com.example.allinone.data

data class WTLesson(
    val id: Long = 0,
    val dayOfWeek: Int = 0,  // Calendar.MONDAY, Calendar.TUESDAY, etc.
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 0,
    val endMinute: Int = 0
) {
    // No-argument constructor required for Firestore
    constructor() : this(
        id = 0,
        dayOfWeek = 0,
        startHour = 0,
        startMinute = 0,
        endHour = 0,
        endMinute = 0
    )
} 