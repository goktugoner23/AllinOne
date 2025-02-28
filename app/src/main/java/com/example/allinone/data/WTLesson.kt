package com.example.allinone.data

data class WTLesson(
    val id: Long = 0,
    val dayOfWeek: Int,  // Calendar.MONDAY, Calendar.TUESDAY, etc.
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) 