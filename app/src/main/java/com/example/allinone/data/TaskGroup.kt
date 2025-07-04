package com.example.allinone.data

import java.util.Date

data class TaskGroup(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val color: String = "#2196F3", // Default blue color
    val createdAt: Date = Date(),
    val isCompleted: Boolean = false // True when all tasks in group are completed
) 