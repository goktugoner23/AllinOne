package com.example.allinone.data

import java.util.Date

data class Investment(
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val type: String,
    val description: String?,
    val imageUri: String?,
    val date: Date,
    val profitLoss: Double = 0.0,
    val isPast: Boolean = false
) 