package com.example.allinone.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "wt_students")
data class WTStudent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: Date,
    val endDate: Date,
    val amount: Double,
    val isPaid: Boolean = false,
    val paymentDate: Date? = null
) 