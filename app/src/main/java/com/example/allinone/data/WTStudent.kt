package com.example.allinone.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Data class representing a Wing Tzun student
 */
@Parcelize
data class WTStudent(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val instagram: String? = null,
    val isActive: Boolean = true,
    val deviceId: String? = null, // For identification purposes
    val notes: String? = null     // Any additional notes
) : Parcelable 