package com.example.allinone.data

import java.util.Date

/**
 * Represents an item in the history list.
 * This is a unified representation of different data types.
 */
data class HistoryItem(
    val id: Long,
    val title: String,
    val description: String,
    val date: Date,
    val amount: Double? = null,
    val type: String,
    val imageUri: String? = null,
    val itemType: ItemType
) {
    enum class ItemType {
        TRANSACTION,
        INVESTMENT,
        NOTE,
        REGISTRATION
    }
} 