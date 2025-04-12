package com.example.allinone.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a voice note recording with its metadata
 */
@Parcelize
data class VoiceNote(
    val id: String, // Unique identifier for the voice note
    val fileName: String,
    val filePath: String,
    val duration: Long,  // Duration in milliseconds
    val timestamp: Long,
    val firebaseUrl: String
) : Parcelable {
    /**
     * Format the duration as MM:SS
     */
    fun getFormattedDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
} 