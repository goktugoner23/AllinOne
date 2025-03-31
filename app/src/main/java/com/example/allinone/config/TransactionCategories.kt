package com.example.allinone.config

/**
 * Configuration class for transaction categories
 * Centralizes all transaction categories in one place for easier maintenance
 */
object TransactionCategories {
    // Main categories list used for dropdown selection
    val CATEGORIES = arrayOf(
        "Salary",
        "Investment",
        "Wing Tzun",
        "Sports",
        "General",
        "Bills", 
        "Food", 
        "Transport",
        "Pınar ❤️"
    )
    
    // Income-specific categories (can expand in the future if needed)
    val INCOME_CATEGORIES = arrayOf(
        "Salary",
        "Investment",
        "Wing Tzun"
    )
    
    // Expense-specific categories (can expand in the future if needed)
    val EXPENSE_CATEGORIES = arrayOf(
        "Sports",
        "General",
        "Bills", 
        "Food", 
        "Transport", 
        "Wing Tzun",
        "Pınar ❤️"
    )
    
    // Special adjustment categories that are shown separately in reports
    val ADJUSTMENT_CATEGORIES = arrayOf(
        "Wing Tzun Adjustment",
        "Salary Adjustment",
        "Investment Adjustment",
        "General Adjustment"
    )
} 