package com.example.allinone.data

import java.util.Date

data class WTEvent(
    val id: Long,
    val title: String,
    val description: String,
    val date: Date,
    val type: String
) 