package com.example.allinone.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.allinone.data.BinanceFutures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class ExternalBinanceRepository {
    private val apiService = ExternalBinanceApiClient.service
    
    companion object {
        private const val TAG = "ExternalBinanceRepository"
    }
    
    // ===============================
    // Position Repository with Polling (as per integration guide)
    // ===============================
    
    private val _positions = MutableLiveData<List<Position>>()
    val positions: LiveData<List<Position>> = _positions
    
    private val _orders = MutableLiveData<List<Order>>()
    val orders: LiveData<List<Order>> = _orders
    
    private val _accountInfo = MutableLiveData<AccountInfo>()
    val accountInfo: LiveData<AccountInfo> = _accountInfo
    
    private var refreshJob: Job? = null
    
    /**
     * Start position updates with polling (as per integration guide)
     * @param intervalMs Polling interval in milliseconds (default: 5000ms = 5 seconds)
     */
    fun startPositionUpdates(intervalMs: Long = 5000) {
        refreshJob?.cancel() // Cancel any existing job
        refreshJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // Fetch USD-M positions
                    val positionsResponse = getFuturesPositions()
                    positionsResponse.fold(
                        onSuccess = { response ->
                            if (response.success && response.data != null) {
                                // Convert to integration guide format
                                val positions = response.data.map { position ->
                                    Position(
                                        symbol = position.symbol,
                                        positionAmount = position.positionAmount,
                                        entryPrice = position.entryPrice,
                                        markPrice = position.markPrice,
                                        unrealizedProfit = position.unrealizedProfit,
                                        positionSide = position.positionSide,
                                        leverage = position.leverage.toInt()
                                    )
                                }
                                _positions.postValue(positions)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to fetch positions: ${error.message}")
                        }
                    )
                    
                    // Fetch orders
                    val ordersResponse = getFuturesOrders()
                    ordersResponse.fold(
                        onSuccess = { response ->
                            if (response.success && response.data != null) {
                                // Convert to integration guide format
                                val orders = response.data.map { order ->
                                    Order(
                                        orderId = order.orderId,
                                        symbol = order.symbol,
                                        status = order.status,
                                        clientOrderId = order.clientOrderId,
                                        price = order.price,
                                        avgPrice = order.avgPrice,
                                        origQty = order.origQty,
                                        executedQty = order.executedQty,
                                        cumQuote = order.cumQuote,
                                        timeInForce = order.timeInForce,
                                        type = order.type,
                                        reduceOnly = order.reduceOnly,
                                        closePosition = order.closePosition,
                                        side = order.side,
                                        positionSide = order.positionSide,
                                        stopPrice = order.stopPrice,
                                        workingType = order.workingType,
                                        priceProtect = order.priceProtect,
                                        origType = order.origType,
                                        time = order.time,
                                        updateTime = order.updateTime
                                    )
                                }
                                _orders.postValue(orders)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to fetch orders: ${error.message}")
                        }
                    )
                    
                    // Fetch account info
                    val accountResponse = getFuturesAccount()
                    accountResponse.fold(
                        onSuccess = { response ->
                            if (response.success && response.data != null) {
                                val accountInfo = AccountInfo(
                                    totalWalletBalance = response.data.totalWalletBalance,
                                    totalUnrealizedProfit = response.data.totalUnrealizedProfit,
                                    totalMarginBalance = response.data.totalMarginBalance,
                                    totalPositionInitialMargin = response.data.totalPositionInitialMargin,
                                    maxWithdrawAmount = response.data.maxWithdrawAmount,
                                    assets = response.data.assets.map { asset ->
                                        AssetBalance(
                                            asset = asset.asset,
                                            walletBalance = asset.walletBalance,
                                            unrealizedProfit = asset.unrealizedProfit,
                                            marginBalance = asset.marginBalance,
                                            maintMargin = asset.maintMargin,
                                            initialMargin = asset.initialMargin,
                                            positionInitialMargin = asset.positionInitialMargin,
                                            openOrderInitialMargin = asset.openOrderInitialMargin,
                                            maxWithdrawAmount = asset.maxWithdrawAmount
                                        )
                                    }
                                )
                                _accountInfo.postValue(accountInfo)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to fetch account info: ${error.message}")
                        }
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in polling update: ${e.message}")
                }
                delay(intervalMs)
            }
        }
    }
    
    /**
     * Stop position updates
     */
    fun stopPositionUpdates() {
        refreshJob?.cancel()
        refreshJob = null
    }
    
    // ===============================
    // Safe API Call Helper (as per integration guide)
    // ===============================
    
    suspend fun <T> safeApiCall(
        apiCall: suspend () -> retrofit2.Response<T>
    ): ApiResult<T> {
        return try {
            val response = apiCall()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error("API call failed: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}")
        }
    }
    
    // ===============================
    // Health Check
    // ===============================
    
    suspend fun getHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching health status")
            val response = apiService.getHealth()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Health check successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "Health check failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health check exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    // ===============================
    // Health Check with ApiResult (Integration Guide Format)
    // ===============================
    
    suspend fun checkServiceHealth(): ApiResult<HealthResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.getHealth() }
    }
    
    // ===============================
    // USD-M Futures API Methods
    // ===============================
    
    suspend fun getFuturesAccount(): Result<AccountResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching USD-M futures account data")
            val response = apiService.getFuturesAccount()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures account fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures account fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures account fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getFuturesPositions(): Result<PositionsResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching USD-M futures positions")
            val response = apiService.getFuturesPositions()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures positions fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures positions fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures positions fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getFuturesOrders(symbol: String? = null, limit: Int? = null, offset: Int? = null): Result<OrdersResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching USD-M futures orders for symbol: $symbol")
            val response = apiService.getFuturesOrders(symbol, limit, offset)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures orders fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures orders fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures orders fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun placeFuturesOrder(orderRequest: OrderRequest): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Placing USD-M futures order: $orderRequest")
            val response = apiService.placeFuturesOrder(orderRequest)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures order placement successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures order placement failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures order placement exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun cancelFuturesOrder(symbol: String, orderId: String): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Cancelling USD-M futures order: $symbol/$orderId")
            val response = apiService.cancelFuturesOrder(symbol, orderId)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures order cancellation successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures order cancellation failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures order cancellation exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun setFuturesTPSL(symbol: String, side: String, takeProfitPrice: Double?, stopLossPrice: Double?, quantity: Double): Result<TPSLResponse> = withContext(Dispatchers.IO) {
        try {
            val tpslRequest = TPSLRequest(
                symbol = symbol,
                side = side,
                takeProfitPrice = takeProfitPrice,
                stopLossPrice = stopLossPrice,
                quantity = quantity
            )
            Log.d(TAG, "Setting USD-M futures TP/SL: $tpslRequest")
            val response = apiService.setFuturesTPSL(tpslRequest)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures TP/SL set successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures TP/SL setting failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures TP/SL setting exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Close position for USD-M futures (as per integration guide)
     * @param symbol The trading symbol (e.g. "LINKUSDT") 
     * @param quantity Optional quantity to close. If null, closes entire position
     */
    suspend fun closeFuturesPosition(symbol: String, quantity: Double? = null): Result<ClosePositionResponse> = withContext(Dispatchers.IO) {
        try {
            val closePositionRequest = ClosePositionRequest(
                symbol = symbol,
                quantity = quantity
            )
            Log.d(TAG, "Closing USD-M futures position: $closePositionRequest")
            val response = apiService.closeFuturesPosition(closePositionRequest)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures position close successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures position close failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures position close exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getFuturesBalance(asset: String = "USDT"): Result<BalanceResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching USD-M futures balance for asset: $asset")
            val response = apiService.getFuturesBalance(asset)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures balance fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures balance fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures balance fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getFuturesPrice(symbol: String): Result<PriceResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching USD-M futures price for symbol: $symbol")
            val response = apiService.getFuturesPrice(symbol)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "USD-M futures price fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "USD-M futures price fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "USD-M futures price fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getAllFuturesPrices(): Result<AllPricesResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching all USD-M futures prices")
            val response = apiService.getAllFuturesPrices()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "All USD-M futures prices fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "All USD-M futures prices fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "All USD-M futures prices fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    // ===============================
    // COIN-M Futures API Methods
    // ===============================
    
    suspend fun getCoinMAccount(): Result<AccountResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching COIN-M futures account data")
            val response = apiService.getCoinMAccount()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures account fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures account fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures account fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getCoinMPositions(): Result<PositionsResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching COIN-M futures positions")
            val response = apiService.getCoinMPositions()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures positions fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures positions fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures positions fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getCoinMOrders(symbol: String? = null, limit: Int? = null, offset: Int? = null): Result<OrdersResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching COIN-M futures orders for symbol: $symbol")
            val response = apiService.getCoinMOrders(symbol, limit, offset)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures orders fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures orders fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures orders fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun placeCoinMOrder(orderRequest: OrderRequest): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Placing COIN-M futures order: $orderRequest")
            val response = apiService.placeCoinMOrder(orderRequest)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures order placement successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures order placement failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures order placement exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun cancelCoinMOrder(symbol: String, orderId: String): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Cancelling COIN-M futures order: $symbol/$orderId")
            val response = apiService.cancelCoinMOrder(symbol, orderId)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures order cancellation successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures order cancellation failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures order cancellation exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun setCoinMTPSL(symbol: String, side: String, takeProfitPrice: Double?, stopLossPrice: Double?, quantity: Double): Result<TPSLResponse> = withContext(Dispatchers.IO) {
        try {
            val tpslRequest = TPSLRequest(
                symbol = symbol,
                side = side,
                takeProfitPrice = takeProfitPrice,
                stopLossPrice = stopLossPrice,
                quantity = quantity
            )
            Log.d(TAG, "Setting COIN-M futures TP/SL: $tpslRequest")
            val response = apiService.setCoinMTPSL(tpslRequest)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures TP/SL set successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures TP/SL setting failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures TP/SL setting exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Close position for COIN-M futures (as per integration guide)
     * @param symbol The trading symbol (e.g. "BTCUSD_PERP")
     * @param quantity Optional quantity to close. If null, closes entire position
     */
    suspend fun closeCoinMPosition(symbol: String, quantity: Double? = null): Result<ClosePositionResponse> = withContext(Dispatchers.IO) {
        try {
            val closePositionRequest = ClosePositionRequest(
                symbol = symbol,
                quantity = quantity
            )
            Log.d(TAG, "Closing COIN-M futures position: $closePositionRequest")
            val response = apiService.closeCoinMPosition(closePositionRequest)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures position close successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures position close failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures position close exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getCoinMBalance(asset: String = "BTC"): Result<BalanceResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching COIN-M futures balance for asset: $asset")
            val response = apiService.getCoinMBalance(asset)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures balance fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures balance fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures balance fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getCoinMPrice(symbol: String): Result<PriceResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching COIN-M futures price for symbol: $symbol")
            val response = apiService.getCoinMPrice(symbol)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "COIN-M futures price fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "COIN-M futures price fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "COIN-M futures price fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getAllCoinMPrices(): Result<AllPricesResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching all COIN-M futures prices")
            val response = apiService.getAllCoinMPrices()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "All COIN-M futures prices fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "All COIN-M futures prices fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "All COIN-M futures prices fetch exception: ${e.message}")
            Result.failure(e)
        }
    }

    // ===============================
    // COIN-M Helper Methods (for fragment compatibility)
    // ===============================
    
    suspend fun placeTakeProfitMarketOrder(
        symbol: String,
        side: String,
        quantity: Double,
        stopPrice: Double
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Placing COIN-M Take Profit order: $symbol, $side, $quantity, $stopPrice")
            val orderRequest = OrderRequest(
                symbol = symbol,
                side = side,
                type = "TAKE_PROFIT_MARKET",
                quantity = quantity,
                stopPrice = stopPrice
            )
            val result = placeCoinMOrder(orderRequest)
            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Log.d(TAG, "COIN-M Take Profit order placed successfully")
                        "{\"success\": true}"
                    } else {
                        Log.e(TAG, "COIN-M Take Profit order failed: ${response.error}")
                        "{\"error\": \"${response.error}\"}"
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "COIN-M Take Profit order exception: ${error.message}")
                    "{\"error\": \"${error.message}\"}"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception placing COIN-M Take Profit order: ${e.message}")
            "{\"error\": \"${e.message}\"}"
        }
    }
    
    suspend fun placeStopLossMarketOrder(
        symbol: String,
        side: String,
        quantity: Double,
        stopPrice: Double
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Placing COIN-M Stop Loss order: $symbol, $side, $quantity, $stopPrice")
            val orderRequest = OrderRequest(
                symbol = symbol,
                side = side,
                type = "STOP_MARKET",
                quantity = quantity,
                stopPrice = stopPrice
            )
            val result = placeCoinMOrder(orderRequest)
            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Log.d(TAG, "COIN-M Stop Loss order placed successfully")
                        "{\"success\": true}"
                    } else {
                        Log.e(TAG, "COIN-M Stop Loss order failed: ${response.error}")
                        "{\"error\": \"${response.error}\"}"
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "COIN-M Stop Loss order exception: ${error.message}")
                    "{\"error\": \"${error.message}\"}"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception placing COIN-M Stop Loss order: ${e.message}")
            "{\"error\": \"${e.message}\"}"
        }
    }
    
    suspend fun cancelOrder(symbol: String, orderId: Long): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Cancelling COIN-M order: $symbol/$orderId")
            val result = cancelCoinMOrder(symbol, orderId.toString())
            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Log.d(TAG, "COIN-M order cancelled successfully")
                        "{\"success\": true}"
                    } else {
                        Log.e(TAG, "COIN-M order cancellation failed: ${response.error}")
                        "{\"error\": \"${response.error}\"}"
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "COIN-M order cancellation exception: ${error.message}")
                    "{\"error\": \"${error.message}\"}"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception cancelling COIN-M order: ${e.message}")
            "{\"error\": \"${e.message}\"}"
        }
    }
    
    suspend fun closePosition(symbol: String, positionAmt: Double): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Closing COIN-M position: $symbol, $positionAmt")
            val side = if (positionAmt > 0) "SELL" else "BUY"
            val quantity = Math.abs(positionAmt)
            
            val orderRequest = OrderRequest(
                symbol = symbol,
                side = side,
                type = "MARKET",
                quantity = quantity
            )
            val result = placeCoinMOrder(orderRequest)
            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Log.d(TAG, "COIN-M position closed successfully")
                        "{\"success\": true}"
                    } else {
                        Log.e(TAG, "COIN-M position close failed: ${response.error}")
                        "{\"error\": \"${response.error}\"}"
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "COIN-M position close exception: ${error.message}")
                    "{\"error\": \"${error.message}\"}"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception closing COIN-M position: ${e.message}")
            "{\"error\": \"${e.message}\"}"
        }
    }
    
    // Legacy compatibility methods for COIN-M operations
    suspend fun getAccountBalance(): List<PositionData> = withContext(Dispatchers.IO) {
        try {
            val result = getCoinMAccount()
            result.fold(
                onSuccess = { _ ->
                    // Account response doesn't have positions, return empty list
                    emptyList()
                },
                onFailure = { 
                    Log.e(TAG, "Failed to get COIN-M account balance")
                    emptyList()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting COIN-M account balance: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getPositionInformation(): List<BinanceFutures> = withContext(Dispatchers.IO) {
        try {
            val result = getCoinMPositions()
            result.fold(
                onSuccess = { response ->
                    response.data?.map { position ->
                        BinanceFutures(
                            symbol = position.symbol,
                            positionAmt = position.positionAmount,
                            entryPrice = position.entryPrice,
                            markPrice = position.markPrice,
                            unRealizedProfit = position.unrealizedProfit,
                            liquidationPrice = 0.0, // Not provided by external API
                            leverage = position.leverage.toInt(),
                            marginType = position.marginType,
                            isolatedMargin = position.isolatedMargin,
                            isAutoAddMargin = false,
                            positionSide = if (position.positionAmount >= 0) "LONG" else "SHORT",
                            futuresType = "COIN-M"
                        )
                    } ?: emptyList()
                },
                onFailure = { 
                    emptyList()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting COIN-M position information: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getLatestPrices(): Map<String, Double> = withContext(Dispatchers.IO) {
        try {
            val result = getAllCoinMPrices()
            result.fold(
                onSuccess = { response ->
                    response.data?.associate { price ->
                        price.symbol to price.price
                    } ?: emptyMap()
                },
                onFailure = { 
                    emptyMap()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting COIN-M latest prices: ${e.message}")
            emptyMap()
        }
    }
    
    suspend fun getOpenOrders(): List<OrderData> = withContext(Dispatchers.IO) {
        try {
            val result = getCoinMOrders()
            result.fold(
                onSuccess = { response ->
                    response.data ?: emptyList()
                },
                onFailure = { 
                    emptyList()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting COIN-M open orders: ${e.message}")
            emptyList()
        }
    }
    
    // ===============================
    // WebSocket Management
    // ===============================
    
    suspend fun getWebSocketStatus(): Result<WebSocketStatusResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching WebSocket status")
            val response = apiService.getWebSocketStatus()
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "WebSocket status fetch successful")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "WebSocket status fetch failed: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket status fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    // ===============================
    // Legacy Methods (backward compatibility)
    // ===============================
    
    @Deprecated("Use getFuturesAccount() instead")
    suspend fun getAccount(): Result<AccountResponse> = getFuturesAccount()
    
    @Deprecated("Use getFuturesPositions() instead")
    suspend fun getPositions(): Result<PositionsResponse> = getFuturesPositions()
    
    @Deprecated("Use getFuturesOrders() instead")
    suspend fun getOpenOrders(symbol: String? = null): Result<OrdersResponse> = getFuturesOrders(symbol)
    
    @Deprecated("Use placeFuturesOrder() instead")
    suspend fun placeOrder(orderRequest: OrderRequest): Result<ApiResponse> = placeFuturesOrder(orderRequest)
    
    @Deprecated("Use cancelFuturesOrder() instead")
    suspend fun cancelOrder(symbol: String, orderId: String): Result<ApiResponse> = cancelFuturesOrder(symbol, orderId)
    
    @Deprecated("Use setFuturesTPSL() instead")
    suspend fun setTPSL(symbol: String, side: String, takeProfitPrice: Double?, stopLossPrice: Double?, quantity: Double): Result<TPSLResponse> = setFuturesTPSL(symbol, side, takeProfitPrice, stopLossPrice, quantity)
    
    @Deprecated("Use getFuturesBalance() instead")
    suspend fun getBalance(asset: String = "USDT"): Result<BalanceResponse> = getFuturesBalance(asset)
    
    @Deprecated("Use getFuturesPrice() instead")
    suspend fun getPrice(symbol: String): Result<PriceResponse> = getFuturesPrice(symbol)
    
    @Deprecated("Use getAllFuturesPrices() instead")
    suspend fun getAllPrices(): Result<AllPricesResponse> = getAllFuturesPrices()
} 