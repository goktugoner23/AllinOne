package com.example.allinone.data

import java.util.Date

/**
 * Data class representing a student registration/payment record
 */
data class WTRegistration(
    val id: Long = 0,
    val studentId: Long,  // Reference to the WTStudent id
    val amount: Double = 0.0,
    val attachmentUri: String? = null,
    val startDate: Date? = null,
    val endDate: Date? = null,
    val paymentDate: Date = Date(),
    val notes: String? = null,
    val isPaid: Boolean = false  // Default to unpaid
) {
    // Derived property for student name to use in UI (will be supplied by adapter)
    // This isn't stored in the data class but accessed via extension property
    val studentName: String = ""
} 