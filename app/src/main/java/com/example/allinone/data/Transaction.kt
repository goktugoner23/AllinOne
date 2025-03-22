package com.example.allinone.data

import java.util.Date

data class Transaction(
    val id: Long = 0,
    val amount: Double = 0.0,
    val type: String = "",
    val description: String = "",
    val isIncome: Boolean = false,
    val date: Date = Date(),
    val category: String = "",
    val relatedRegistrationId: Long? = null  // Reference to linked registration if applicable
) 