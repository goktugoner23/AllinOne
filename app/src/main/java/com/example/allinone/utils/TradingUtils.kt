package com.example.allinone.utils

import com.example.allinone.api.ExternalBinanceRepository
import com.example.allinone.api.TPSLRequest
import com.example.allinone.api.ApiResult
import android.util.Log

/**
 * Utility class for trading operations following the integration guide patterns
 */
class TradingUtils {
    
    companion object {
        private const val TAG = "TradingUtils"
        
        /**
         * Set TP/SL for a position following integration guide format
         */
        suspend fun setTPSL(
            repository: ExternalBinanceRepository,
            symbol: String,
            positionSide: String, // "LONG" or "SHORT"
            currentPrice: Double,
            quantity: Double,
            takeProfitPrice: Double? = null,
            stopLossPrice: Double? = null,
            isUSDM: Boolean = true
        ): ApiResult<String> {
            
            // Validate TP/SL prices
            val validationResult = validateTPSLPrices(positionSide, currentPrice, takeProfitPrice, stopLossPrice)
            if (validationResult != null) {
                return ApiResult.Error(validationResult)
            }
            
            // Determine order side (opposite of position side)
            val orderSide = if (positionSide == "LONG") "SELL" else "BUY"
            
            val tpslRequest = TPSLRequest(
                symbol = symbol,
                side = orderSide,
                takeProfitPrice = takeProfitPrice,
                stopLossPrice = stopLossPrice,
                quantity = quantity
            )
            
            Log.d(TAG, "Setting TP/SL: $tpslRequest")
            
            return try {
                val result = if (isUSDM) {
                    repository.setFuturesTPSL(symbol, orderSide, takeProfitPrice, stopLossPrice, quantity)
                } else {
                    repository.setCoinMTPSL(symbol, orderSide, takeProfitPrice, stopLossPrice, quantity)
                }
                
                result.fold(
                    onSuccess = { response ->
                        if (response.success) {
                            val successMessage = buildSuccessMessage(takeProfitPrice, stopLossPrice)
                            ApiResult.Success(successMessage)
                        } else {
                            ApiResult.Error(response.error ?: "Failed to set TP/SL")
                        }
                    },
                    onFailure = { error ->
                        ApiResult.Error("Network error: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                ApiResult.Error("Error setting TP/SL: ${e.message}")
            }
        }
        
        /**
         * Validate TP/SL prices according to position side
         */
        private fun validateTPSLPrices(
            positionSide: String,
            currentPrice: Double,
            takeProfitPrice: Double?,
            stopLossPrice: Double?
        ): String? {
            
            // At least one price must be provided
            if (takeProfitPrice == null && stopLossPrice == null) {
                return "At least one TP or SL price must be provided"
            }
            
            val isLong = positionSide == "LONG"
            
            // Validate Take Profit
            takeProfitPrice?.let { tp ->
                if (tp <= 0) return "Take Profit price must be positive"
                
                if (isLong && tp <= currentPrice) {
                    return "Take Profit must be above current price for LONG positions"
                } else if (!isLong && tp >= currentPrice) {
                    return "Take Profit must be below current price for SHORT positions"
                }
            }
            
            // Validate Stop Loss
            stopLossPrice?.let { sl ->
                if (sl <= 0) return "Stop Loss price must be positive"
                
                if (isLong && sl >= currentPrice) {
                    return "Stop Loss must be below current price for LONG positions"
                } else if (!isLong && sl <= currentPrice) {
                    return "Stop Loss must be above current price for SHORT positions"
                }
            }
            
            return null // All validations passed
        }
        
        /**
         * Build success message based on what was set
         */
        private fun buildSuccessMessage(
            takeProfitPrice: Double?,
            stopLossPrice: Double?
        ): String {
            return when {
                takeProfitPrice != null && stopLossPrice != null -> "TP/SL orders placed successfully"
                takeProfitPrice != null -> "Take Profit order placed successfully"
                stopLossPrice != null -> "Stop Loss order placed successfully"
                else -> "No changes made"
            }
        }
        
        /**
         * Check service health before making trading requests
         */
        suspend fun checkServiceHealth(repository: ExternalBinanceRepository): Boolean {
            return try {
                val healthResult = repository.getHealth()
                healthResult.fold(
                    onSuccess = { response ->
                        response.success && response.data.services.isInitialized
                    },
                    onFailure = { false }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Health check failed: ${e.message}")
                false
            }
        }
        
        /**
         * Format price for display
         */
        fun formatPrice(price: Double, maxDecimals: Int = 8): String {
            return if (price > 1.0) {
                String.format("%.2f", price)
            } else {
                String.format("%.${maxDecimals}f", price).trimEnd('0').trimEnd('.')
            }
        }
        
        /**
         * Format currency for display
         */
        fun formatCurrency(amount: Double): String {
            return "$${String.format("%.2f", amount)}"
        }
    }
} 