package com.example.allinone.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.allinone.data.Transaction
import java.util.Date

@Entity(tableName = "cached_transactions")
data class CachedTransactionEntity(
    @PrimaryKey val id: Long,
    val amount: Double,
    val type: String,
    val description: String?,
    val isIncome: Boolean,
    val category: String,
    val relatedRegistrationId: Long?,
    val date: Long,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toTransaction(): Transaction {
        return Transaction(
            id = id,
            amount = amount,
            type = type,
            description = description ?: "",
            isIncome = isIncome,
            date = Date(date),
            category = category,
            relatedRegistrationId = relatedRegistrationId
        )
    }
    
    companion object {
        fun fromTransaction(transaction: Transaction): CachedTransactionEntity {
            return CachedTransactionEntity(
                id = transaction.id,
                amount = transaction.amount,
                type = transaction.type,
                description = transaction.description,
                isIncome = transaction.isIncome,
                category = transaction.category,
                relatedRegistrationId = transaction.relatedRegistrationId,
                date = transaction.date.time
            )
        }
    }
} 