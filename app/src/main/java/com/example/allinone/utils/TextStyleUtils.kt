package com.example.allinone.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/**
 * Utility class for consistent text styling across the app
 */
object TextStyleUtils {
    
    /**
     * Creates a bold text span with appropriate color based on the current theme
     * - Black text in light mode
     * - White text in dark mode
     */
    fun createBoldSpan(context: Context, text: String): SpannableString {
        return SpannableString(text).apply {
            // Apply bold styling
            setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Set text color based on night mode
            val isNightMode = context.resources.configuration.uiMode and 
                Configuration.UI_MODE_NIGHT_MASK == 
                Configuration.UI_MODE_NIGHT_YES
                
            // Apply appropriate color based on theme
            val textColor = if (isNightMode) Color.WHITE else Color.BLACK
            setSpan(
                ForegroundColorSpan(textColor),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
} 