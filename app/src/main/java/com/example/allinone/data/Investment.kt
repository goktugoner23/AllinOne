package com.example.allinone.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "investments")
data class Investment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val type: String,
    val date: Date,
    val description: String?,
    val imageUri: String?,
    val currentValue: Double,
    val profitLoss: Double = currentValue - amount
) 