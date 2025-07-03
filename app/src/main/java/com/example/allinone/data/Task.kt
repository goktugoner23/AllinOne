package com.example.allinone.data

import java.util.Date

data class Task(
    val id: Long = 0,
    val description: String,
    val completed: Boolean = false,
    val date: Date = Date()  // Creation or due date
) 