package com.example.allinone.data

import java.util.Date

data class WTStudent(
    val id: Long = 0,
    val name: String,
    val startDate: Date,
    val endDate: Date,
    val amount: Double,
    val isPaid: Boolean = false,
    val paymentDate: Date? = null,
    val attachmentUri: String? = null
) 