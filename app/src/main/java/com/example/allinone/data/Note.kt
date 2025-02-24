package com.example.allinone.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val date: Date = Date(),
    val imageUri: String? = null,  // Single image URI
    val imageUris: String? = null,  // Comma-separated list of image URIs
    val lastEdited: Date = Date(),
    val isRichText: Boolean = true  // Flag to indicate if content has rich formatting
) 