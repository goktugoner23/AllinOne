package com.example.allinone.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing an exercise
 */
@Parcelize
data class Exercise(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val youtubeLink: String? = null
) : Parcelable
