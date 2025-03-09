package com.example.allinone.data

import java.util.Date

data class WTStudent(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String = "",
    val email: String? = null,
    val instagram: String? = null,
    val isActive: Boolean = true,
    val profileImageUri: String? = null,
    // The following fields are used for course registration
    val startDate: Date? = null,
    val endDate: Date? = null,
    val amount: Double = 0.0,
    val isPaid: Boolean = false,
    val paymentDate: Date? = null,
    val attachmentUri: String? = null
) 