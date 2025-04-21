package com.example.allinone.config

import com.example.allinone.R

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
        "Shopping",
        "Bills",
        "Food",
        "Transport"
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
        "Shopping",
        "Bills",
        "Food",
        "Transport",
        "Wing Tzun"
    )

    // Special adjustment categories that are shown separately in reports
    val ADJUSTMENT_CATEGORIES = arrayOf(
        "Wing Tzun Adjustment",
        "Salary Adjustment",
        "Investment Adjustment",
        "General Adjustment"
    )

    // Map of category names to their icon resource IDs
    val CATEGORY_ICONS = mapOf(
        "All Categories" to R.drawable.ic_category_all,
        "Salary" to R.drawable.ic_category_salary,
        "Investment" to R.drawable.ic_category_investment,
        "Wing Tzun" to R.drawable.ic_category_wing_tzun,
        "Sports" to R.drawable.ic_category_sports,
        "General" to R.drawable.ic_category_general,
        "Shopping" to R.drawable.ic_category_shopping,
        "Bills" to R.drawable.ic_category_bills,
        "Food" to R.drawable.ic_category_food,
        "Transport" to R.drawable.ic_category_transport
    )
}