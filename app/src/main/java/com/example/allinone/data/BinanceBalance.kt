package com.example.allinone.data

import java.util.Date

/**
 * Data class representing a Binance Futures account balance
 */
data class BinanceBalance(
    val id: Long = 0,
    val asset: String,
    val balance: Double,
    val crossWalletBalance: Double,
    val crossUnPnl: Double,
    val availableBalance: Double,
    val maxWithdrawAmount: Double,
    val marginAvailable: Boolean,
    val updateTime: Date = Date(),
    val deviceId: String? = null,
    val futuresType: String = "USD-M" // Default to USD-M for backward compatibility
)
