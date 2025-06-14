package com.example.allinone.data

import java.util.Date

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val date: Date = Date(),
    val imageUris: String? = null,  // Comma-separated list of image URIs
    val videoUris: String? = null,  // Comma-separated list of video URIs
    val voiceNoteUris: String? = null,  // Comma-separated list of voice note URIs
    val lastEdited: Date = Date(),
    val isRichText: Boolean = true  // Flag to indicate if content has rich formatting
) 