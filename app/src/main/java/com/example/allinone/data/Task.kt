package com.example.allinone.data

import java.util.Date

data class Task(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val completed: Boolean = false,
    val date: Date = Date(),  // Creation or due date
    val dueDate: Date? = null, // Optional due date
    val groupId: Long? = null // Optional group ID for task grouping
) 