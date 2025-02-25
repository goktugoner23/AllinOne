package com.example.allinone.data

import java.util.Date

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val type: String,
    val description: String,
    val isIncome: Boolean,
    val date: Date,
    val category: String
) 