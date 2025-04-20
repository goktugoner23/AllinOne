package com.example.allinone.data

import java.util.Date

/**
 * Data class representing a Binance Futures position
 */
data class BinanceFutures(
    val id: Long = 0,
    val symbol: String,
    val positionAmt: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val unRealizedProfit: Double,
    val liquidationPrice: Double,
    val leverage: Int,
    val marginType: String,
    val isolatedMargin: Double = 0.0,
    val isAutoAddMargin: Boolean = false,
    val positionSide: String,
    val updateTime: Date = Date(),
    val deviceId: String? = null
)
