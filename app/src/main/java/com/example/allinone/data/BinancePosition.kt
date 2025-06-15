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
    val stopLossPrice: Double = 0.0, // 0 means no SL set
    // Additional fields from integration guide
    val positionSide: String = "BOTH", // "BOTH", "LONG", "SHORT"
    val percentage: Double = 0.0, // PnL percentage
    val maxNotionalValue: Double = 0.0,
    val isAutoAddMargin: Boolean = false
) {
    // Helper methods as per integration guide
    fun isLongPosition(): Boolean = positionAmt > 0
    fun isShortPosition(): Boolean = positionAmt < 0
    fun hasPosition(): Boolean = positionAmt != 0.0
    
    /**
     * Get the correct side for closing the position
     * For LONG positions (positionAmt > 0), use "SELL"
     * For SHORT positions (positionAmt < 0), use "BUY"
     */
    fun getCloseSide(): String = if (isLongPosition()) "SELL" else "BUY"
    
    /**
     * Get the absolute quantity for trading operations
     */
    fun getAbsoluteQuantity(): Double = kotlin.math.abs(positionAmt)
}
