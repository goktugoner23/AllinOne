package com.example.allinone.utils

import com.example.allinone.api.TPSLRequest
import com.example.allinone.api.ClosePositionRequest
import com.example.allinone.api.ExternalBinanceRepository
import com.example.allinone.data.BinancePosition
import android.util.Log
import kotlinx.coroutines.*
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import kotlin.math.abs

/**
 * Utility class for trading operations following the integration guide patterns
 */
object TradingUtils {
    
    private const val TAG = "TradingUtils"
    
    /**
     * Calculate the correct side for TP/SL orders based on position
     * For LONG positions (positionAmount > 0), use "SELL" 
     * For SHORT positions (positionAmount < 0), use "BUY"
     */
    fun getTPSLSide(positionAmount: Double): String {
        return if (positionAmount > 0) "SELL" else "BUY"
    }
    
    /**
     * Get absolute quantity from position amount
     */
    fun getAbsoluteQuantity(positionAmount: Double): Double {
        return abs(positionAmount)
    }
    
    /**
     * Validate TP/SL prices based on position direction
     * @param positionAmount Current position amount
     * @param entryPrice Position entry price  
     * @param takeProfitPrice Proposed take profit price
     * @param stopLossPrice Proposed stop loss price
     * @return Validation result with error message if invalid
     */
    fun validateTPSLPrices(
        positionAmount: Double,
        entryPrice: Double,
        takeProfitPrice: Double?,
        stopLossPrice: Double?
    ): ValidationResult {
        val isLong = positionAmount > 0
        
        takeProfitPrice?.let { tp ->
            if (isLong && tp <= entryPrice) {
                return ValidationResult(false, "Take Profit price must be higher than entry price for LONG positions")
            }
            if (!isLong && tp >= entryPrice) {
                return ValidationResult(false, "Take Profit price must be lower than entry price for SHORT positions")
            }
        }
        
        stopLossPrice?.let { sl ->
            if (isLong && sl >= entryPrice) {
                return ValidationResult(false, "Stop Loss price must be lower than entry price for LONG positions")  
            }
            if (!isLong && sl <= entryPrice) {
                return ValidationResult(false, "Stop Loss price must be higher than entry price for SHORT positions")
            }
        }
        
        return ValidationResult(true, "Valid prices")
    }
    
    /**
     * Enhanced TP/SL setting with validation and error handling (as per integration guide)
     */
    suspend fun setTPSLWithValidation(
        repository: ExternalBinanceRepository,
        position: BinancePosition,
        takeProfitPrice: Double?,
        stopLossPrice: Double?
    ): Result<String> {
        return try {
            // Validate prices
            val validation = validateTPSLPrices(
                position.positionAmt,
                position.entryPrice,
                takeProfitPrice,
                stopLossPrice
            )
            
            if (!validation.isValid) {
                return Result.failure(Exception(validation.message))
            }
            
            // Calculate correct parameters
            val orderSide = getTPSLSide(position.positionAmt)
            val quantity = getAbsoluteQuantity(position.positionAmt)
            
            Log.d(TAG, "Setting TP/SL for ${position.symbol}: side=$orderSide, quantity=$quantity, tp=$takeProfitPrice, sl=$stopLossPrice")
            
            // Make API call
            val result = repository.setFuturesTPSL(
                symbol = position.symbol,
                side = orderSide,
                takeProfitPrice = takeProfitPrice,
                stopLossPrice = stopLossPrice,
                quantity = quantity
            )
            
            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Result.success("TP/SL orders placed successfully")
                    } else {
                        Result.failure(Exception(response.error ?: "Failed to place TP/SL orders"))
                    }
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Close position with proper error handling (as per integration guide)
     */
    suspend fun closePositionSafely(
        repository: ExternalBinanceRepository,
        position: BinancePosition,
        partialQuantity: Double? = null
    ): Result<String> {
        return try {
            val quantityToClose = partialQuantity ?: getAbsoluteQuantity(position.positionAmt)
            
            Log.d(TAG, "Closing position for ${position.symbol}: quantity=$quantityToClose")
            
            val result = repository.closeFuturesPosition(
                symbol = position.symbol,
                quantity = quantityToClose
            )
            
            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Result.success("Position closed successfully")
                    } else {
                        Result.failure(Exception(response.error ?: "Failed to close position"))
                    }
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Format currency for display
     */
    fun formatCurrency(amount: Double): String {
        return "$${String.format("%.2f", amount)}"
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
}

/**
 * Position updater with polling strategy (as per integration guide)
 */
class PositionUpdater(private val repository: ExternalBinanceRepository) {
    private val _positions = MutableLiveData<List<BinancePosition>>()
    val positions: LiveData<List<BinancePosition>> = _positions
    
    private val _isUpdating = MutableLiveData<Boolean>()
    val isUpdating: LiveData<Boolean> = _isUpdating
    
    private var updateJob: Job? = null
    
    /**
     * Start real-time position updates using polling
     * @param intervalMs Update interval in milliseconds (default: 5000ms)
     */
    fun startUpdating(intervalMs: Long = 5000) {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            _isUpdating.postValue(true)
            while (isActive) {
                try {
                    val result = repository.getFuturesPositions()
                    result.fold(
                        onSuccess = { response ->
                            if (response.success && response.data != null) {
                                // Convert API response to BinancePosition
                                val positions = response.data.mapNotNull { positionData ->
                                    if (positionData.positionAmount != 0.0) { // Only include positions with non-zero amount
                                        BinancePosition(
                                            symbol = positionData.symbol,
                                            positionAmt = positionData.positionAmount,
                                            entryPrice = positionData.entryPrice,
                                            markPrice = positionData.markPrice,
                                            unrealizedProfit = positionData.unrealizedProfit,
                                            liquidationPrice = 0.0, // Not provided in API response
                                            leverage = positionData.leverage.toInt(),
                                            marginType = positionData.marginType,
                                            isolatedMargin = positionData.isolatedMargin,
                                            roe = 0.0, // Calculate if needed
                                            positionSide = positionData.positionSide,
                                            percentage = positionData.percentage,
                                            maxNotionalValue = positionData.maxNotionalValue,
                                            isAutoAddMargin = positionData.isAutoAddMargin
                                        )
                                    } else null
                                }
                                _positions.postValue(positions)
                                Log.d("PositionUpdater", "Updated ${positions.size} positions")
                            }
                        },
                        onFailure = { error ->
                            Log.e("PositionUpdater", "Failed to fetch positions: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e("PositionUpdater", "Error in position update: ${e.message}")
                }
                delay(intervalMs)
            }
            _isUpdating.postValue(false)
        }
    }
    
    /**
     * Stop position updates
     */
    fun stopUpdating() {
        updateJob?.cancel()
        updateJob = null
        _isUpdating.postValue(false)
    }
}

/**
 * Validation result helper class
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String
) 