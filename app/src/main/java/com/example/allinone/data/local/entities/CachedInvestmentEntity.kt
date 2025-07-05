package com.example.allinone.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.allinone.data.Investment
import java.util.Date

@Entity(tableName = "cached_investments")
data class CachedInvestmentEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val amount: Double,
    val type: String,
    val description: String?,
    val imageUri: String?,
    val date: Long,
    val isPast: Boolean = false,
    val profitLoss: Double = 0.0,
    val currentValue: Double = 0.0,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toInvestment(): Investment {
        return Investment(
            id = id,
            name = name,
            amount = amount,
            type = type,
            description = description,
            imageUri = imageUri,
            date = Date(date),
            isPast = isPast,
            profitLoss = profitLoss,
            currentValue = currentValue
        )
    }
    
    companion object {
        fun fromInvestment(investment: Investment): CachedInvestmentEntity {
            return CachedInvestmentEntity(
                id = investment.id,
                name = investment.name,
                amount = investment.amount,
                type = investment.type,
                description = investment.description,
                imageUri = investment.imageUri,
                date = investment.date.time,
                isPast = investment.isPast,
                profitLoss = investment.profitLoss,
                currentValue = investment.currentValue
            )
        }
    }
} 