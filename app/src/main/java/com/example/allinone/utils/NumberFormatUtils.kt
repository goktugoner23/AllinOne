package com.example.allinone.utils

import java.text.NumberFormat
import java.util.Locale

/**
 * Utility class for consistent number formatting across the app
 * Uses dots as decimal separators instead of commas
 */
object NumberFormatUtils {
    
    // Currency formatter with 2 decimal places (e.g., ₺123.45)
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        currency = java.util.Currency.getInstance("TRY") // Turkish Lira
    }
    
    // Number formatter with 2 decimal places (e.g., 123.45)
    private val decimalFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    
    // Price formatter for prices above 1 (2 digits after decimal)
    private val highPriceFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    
    // Price formatter for prices below 1 (7 digits after decimal)
    private val lowPriceFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 7
        maximumFractionDigits = 7
    }
    
    /**
     * Format amount as currency with ₺ symbol
     */
    fun formatCurrency(amount: Double): String {
        return currencyFormatter.format(amount).replace("$", "₺")
    }
    
    /**
     * Format amount as decimal with 2 decimal places
     */
    fun formatDecimal(amount: Double): String {
        return decimalFormatter.format(amount)
    }
    
    /**
     * Format price with appropriate precision based on value
     * - For prices >= 1.0, show 2 decimal places
     * - For prices < 1.0, show 7 decimal places
     */
    fun formatPrice(price: Double): String {
        return if (price >= 1.0) {
            highPriceFormatter.format(price)
        } else {
            lowPriceFormatter.format(price)
        }
    }
    
    /**
     * Format amount directly with String.format using Locale.US
     * to ensure dot as decimal separator
     */
    fun formatAmount(amount: Double): String {
        return String.format(Locale.US, "₺%.2f", amount)
    }
}
