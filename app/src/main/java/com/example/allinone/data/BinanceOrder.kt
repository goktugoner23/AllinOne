package com.example.allinone.data

/**
 * Data class representing a Binance order
 */
data class BinanceOrder(
    val symbol: String,
    val orderId: Long,
    val type: String,
    val side: String,
    val price: Double,
    val stopPrice: Double,
    val origQty: Double,
    val positionSide: String = "BOTH" // Default to BOTH for backward compatibility
)
