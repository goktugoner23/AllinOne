package com.example.allinone.api

import com.google.gson.JsonObject

// Health Response
data class HealthResponse(
    val success: Boolean,
    val data: HealthData,
    val timestamp: Long
)

data class HealthData(
    val status: String,
    val timestamp: String,
    val services: ServiceStatus,
    val uptime: Double,
    val memory: MemoryInfo,
    val version: String
)

data class ServiceStatus(
    val spot: BinanceServiceStatus,
    val usdm: BinanceServiceStatus,
    val coinm: BinanceServiceStatus,
    val isInitialized: Boolean
)

data class BinanceServiceStatus(
    val isConnected: Boolean,
    val clientCount: Int
)

data class MemoryInfo(
    val rss: Long,
    val heapTotal: Long,
    val heapUsed: Long,
    val external: Long,
    val arrayBuffers: Long
)

@Deprecated("Use BinanceServiceStatus instead")
data class BinanceStatus(
    val isConnected: Boolean,
    val clientCount: Int,
    val isInitialized: Boolean
)

// Account Response
data class AccountResponse(
    val success: Boolean,
    val data: AccountData? = null,
    val error: String? = null
)

data class AccountData(
    val totalWalletBalance: Double,
    val totalUnrealizedProfit: Double,
    val totalMarginBalance: Double,
    val totalPositionInitialMargin: Double,
    val totalOpenOrderInitialMargin: Double,
    val maxWithdrawAmount: Double,
    val assets: List<AssetData>
)

data class AssetData(
    val asset: String,
    val walletBalance: Double,
    val unrealizedProfit: Double,
    val marginBalance: Double,
    val maintMargin: Double,
    val initialMargin: Double,
    val positionInitialMargin: Double,
    val openOrderInitialMargin: Double,
    val maxWithdrawAmount: Double
)

// Positions Response
data class PositionsResponse(
    val success: Boolean,
    val data: List<PositionData>? = null,
    val error: String? = null
)

data class PositionData(
    val symbol: String,
    val positionAmount: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val unrealizedProfit: Double,
    val percentage: Double,
    val positionSide: String,
    val leverage: Double,
    val maxNotionalValue: Double,
    val marginType: String,
    val isolatedMargin: Double,
    val isAutoAddMargin: Boolean
)

// Orders Response
data class OrdersResponse(
    val success: Boolean,
    val data: List<OrderData>? = null,
    val error: String? = null
)

data class OrderData(
    val orderId: String,
    val symbol: String,
    val status: String,
    val clientOrderId: String,
    val price: Double,
    val avgPrice: Double,
    val origQty: Double,
    val executedQty: Double,
    val cumQuote: Double,
    val timeInForce: String,
    val type: String,
    val reduceOnly: Boolean,
    val closePosition: Boolean,
    val side: String,
    val positionSide: String,
    val stopPrice: Double,
    val workingType: String,
    val priceProtect: Boolean,
    val origType: String,
    val time: Long,
    val updateTime: Long
)

// Order Request
data class OrderRequest(
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val type: String, // "LIMIT", "MARKET", etc.
    val quantity: Double,
    val price: Double? = null,
    val stopPrice: Double? = null,
    val timeInForce: String? = "GTC",
    val reduceOnly: Boolean? = false,
    val closePosition: Boolean? = false,
    val positionSide: String? = "BOTH"
)

// Balance Response
data class BalanceResponse(
    val success: Boolean,
    val data: List<AssetData>? = null,
    val error: String? = null
)

// Price Response
data class PriceResponse(
    val success: Boolean,
    val data: PriceData? = null,
    val error: String? = null
)

data class PriceData(
    val symbol: String,
    val price: Double
)

// All Prices Response
data class AllPricesResponse(
    val success: Boolean,
    val data: List<PriceData>? = null,
    val error: String? = null
)

// Generic API Response
data class ApiResponse(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null
)

// WebSocket Message
data class WebSocketMessage(
    val type: String,
    val status: String? = null,
    val data: JsonObject? = null,
    val error: String? = null,
    val timestamp: Long? = null
)

// WebSocket Connection Status
data class WebSocketConnectionStatus(
    val connected: Boolean,
    val lastHeartbeat: Long? = null
)

// WebSocket Status Response
data class WebSocketStatusResponse(
    val success: Boolean,
    val data: WebSocketStatusData? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class WebSocketStatusData(
    val spot: WebSocketServiceStatus,
    val usdm: WebSocketServiceStatus,
    val coinm: WebSocketServiceStatus,
    val isInitialized: Boolean
)

data class WebSocketServiceStatus(
    val isConnected: Boolean,
    val clientCount: Int
)

// Additional Response Models for Market Data
data class TickerResponse(
    val success: Boolean,
    val data: TickerData? = null,
    val error: String? = null
)

data class TickerData(
    val symbol: String,
    val priceChange: String,
    val priceChangePercent: String,
    val weightedAvgPrice: String,
    val prevClosePrice: String,
    val lastPrice: String,
    val lastQty: String,
    val bidPrice: String,
    val askPrice: String,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val volume: String,
    val quoteVolume: String,
    val openTime: Long,
    val closeTime: Long,
    val firstId: Long,
    val lastId: Long,
    val count: Int
)

data class DepthResponse(
    val success: Boolean,
    val data: DepthData? = null,
    val error: String? = null
)

data class DepthData(
    val lastUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>
)

data class TradesResponse(
    val success: Boolean,
    val data: List<TradeData>? = null,
    val error: String? = null
)

data class TradeData(
    val id: Long,
    val price: String,
    val qty: String,
    val quoteQty: String,
    val time: Long,
    val isBuyerMaker: Boolean,
    val isBestMatch: Boolean
)

// TP/SL Request and Response Models (as per integration guide)
data class TPSLRequest(
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val takeProfitPrice: Double?,
    val stopLossPrice: Double?,
    val quantity: Double
)

data class TPSLResponse(
    val success: Boolean,
    val data: List<OrderResult>? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class OrderResult(
    val type: String, // "TAKE_PROFIT" or "STOP_LOSS"
    val success: Boolean,
    val data: OrderData? = null,
    val error: String? = null
)

// API Result handling (as per integration guide)
sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val message: String, val code: Int? = null) : ApiResult<T>()
    data class Loading<T>(val message: String = "Loading...") : ApiResult<T>()
}

// Position model aligned with integration guide
data class Position(
    val symbol: String,
    val positionAmount: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val unrealizedProfit: Double,
    val positionSide: String,
    val leverage: Int
)

// Account Info model aligned with integration guide
data class AccountInfo(
    val totalWalletBalance: Double,
    val totalUnrealizedProfit: Double,
    val totalMarginBalance: Double,
    val totalPositionInitialMargin: Double,
    val maxWithdrawAmount: Double,
    val assets: List<AssetBalance>
)

data class AssetBalance(
    val asset: String,
    val walletBalance: Double,
    val unrealizedProfit: Double,
    val marginBalance: Double,
    val maintMargin: Double,
    val initialMargin: Double,
    val positionInitialMargin: Double,
    val openOrderInitialMargin: Double,
    val maxWithdrawAmount: Double
)

// Order model aligned with integration guide
data class Order(
    val orderId: String,
    val symbol: String,
    val status: String,
    val clientOrderId: String,
    val price: Double,
    val avgPrice: Double,
    val origQty: Double,
    val executedQty: Double,
    val cumQuote: Double,
    val timeInForce: String,
    val type: String,
    val reduceOnly: Boolean,
    val closePosition: Boolean,
    val side: String,
    val positionSide: String,
    val stopPrice: Double,
    val workingType: String,
    val priceProtect: Boolean,
    val origType: String,
    val time: Long,
    val updateTime: Long
) 