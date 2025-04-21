package com.example.allinone.data

data class BinancePosition(
    val symbol: String,
    val positionAmt: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val unrealizedProfit: Double,
    val liquidationPrice: Double,
    val leverage: Int,
    val marginType: String, // "Cross" or "Isolated"
    val isolatedMargin: Double,
    val roe: Double, // Return on equity (as a decimal, e.g., 0.05 for 5%)
    val takeProfitPrice: Double = 0.0, // 0 means no TP set
    val stopLossPrice: Double = 0.0 // 0 means no SL set
)
