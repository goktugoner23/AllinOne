package com.example.allinone.api

import retrofit2.Response
import retrofit2.http.*

interface BinanceExternalService {
    
    // ===============================
    // Health Check
    // ===============================
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>
    
    // ===============================
    // USD-M Futures API (/api/binance/futures/)
    // ===============================
    
    // Account & Balance Endpoints
    @GET("api/binance/futures/account")
    suspend fun getFuturesAccount(): Response<AccountResponse>
    
    @GET("api/binance/futures/positions")
    suspend fun getFuturesPositions(): Response<PositionsResponse>
    
    @GET("api/binance/futures/balance/{asset}")
    suspend fun getFuturesBalance(@Path("asset") asset: String = "USDT"): Response<BalanceResponse>
    
    // Order Management Endpoints
    @GET("api/binance/futures/orders")
    suspend fun getFuturesOrders(
        @Query("symbol") symbol: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<OrdersResponse>
    
    @POST("api/binance/futures/orders")
    suspend fun placeFuturesOrder(@Body orderRequest: OrderRequest): Response<ApiResponse>
    
    @DELETE("api/binance/futures/orders/{symbol}/{orderId}")
    suspend fun cancelFuturesOrder(
        @Path("symbol") symbol: String,
        @Path("orderId") orderId: String
    ): Response<ApiResponse>
    
    @DELETE("api/binance/futures/orders/{symbol}")
    suspend fun cancelAllFuturesOrders(@Path("symbol") symbol: String): Response<ApiResponse>
    
    @POST("api/binance/futures/tpsl")
    suspend fun setFuturesTPSL(@Body tpslRequest: Map<String, Any>): Response<ApiResponse>
    
    // Market Data Endpoints
    @GET("api/binance/futures/price/{symbol}")
    suspend fun getFuturesPrice(@Path("symbol") symbol: String): Response<PriceResponse>
    
    @GET("api/binance/futures/price")
    suspend fun getAllFuturesPrices(): Response<AllPricesResponse>
    
    // ===============================
    // COIN-M Futures API (/api/binance/coinm/)
    // ===============================
    
    // Account & Balance Endpoints
    @GET("api/binance/coinm/account")
    suspend fun getCoinMAccount(): Response<AccountResponse>
    
    @GET("api/binance/coinm/positions")
    suspend fun getCoinMPositions(): Response<PositionsResponse>
    
    @GET("api/binance/coinm/balance/{asset}")
    suspend fun getCoinMBalance(@Path("asset") asset: String = "BTC"): Response<BalanceResponse>
    
    // Order Management Endpoints
    @GET("api/binance/coinm/orders")
    suspend fun getCoinMOrders(
        @Query("symbol") symbol: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<OrdersResponse>
    
    @POST("api/binance/coinm/orders")
    suspend fun placeCoinMOrder(@Body orderRequest: OrderRequest): Response<ApiResponse>
    
    @DELETE("api/binance/coinm/orders/{symbol}/{orderId}")
    suspend fun cancelCoinMOrder(
        @Path("symbol") symbol: String,
        @Path("orderId") orderId: String
    ): Response<ApiResponse>
    
    @DELETE("api/binance/coinm/orders/{symbol}")
    suspend fun cancelAllCoinMOrders(@Path("symbol") symbol: String): Response<ApiResponse>
    
    @POST("api/binance/coinm/tpsl")
    suspend fun setCoinMTPSL(@Body tpslRequest: Map<String, Any>): Response<ApiResponse>
    
    // Market Data Endpoints
    @GET("api/binance/coinm/price/{symbol}")
    suspend fun getCoinMPrice(@Path("symbol") symbol: String): Response<PriceResponse>
    
    @GET("api/binance/coinm/price")
    suspend fun getAllCoinMPrices(): Response<AllPricesResponse>
    
    // ===============================
    // WebSocket Management
    // ===============================
    
    @GET("api/binance/websocket/status")
    suspend fun getWebSocketStatus(): Response<WebSocketStatusResponse>
    
    // ===============================
    // Legacy Endpoints (for backward compatibility)
    // ===============================
    
    @GET("api/binance/account")
    @Deprecated("Use getFuturesAccount() instead")
    suspend fun getAccount(): Response<AccountResponse>
    
    @GET("api/binance/positions")
    @Deprecated("Use getFuturesPositions() instead")
    suspend fun getPositions(): Response<PositionsResponse>
    
    @GET("api/binance/orders")
    @Deprecated("Use getFuturesOrders() instead")
    suspend fun getOpenOrders(@Query("symbol") symbol: String? = null): Response<OrdersResponse>
    
    @POST("api/binance/order")
    @Deprecated("Use placeFuturesOrder() instead")
    suspend fun placeOrder(@Body orderRequest: OrderRequest): Response<ApiResponse>
    
    @DELETE("api/binance/order/{symbol}/{orderId}")
    @Deprecated("Use cancelFuturesOrder() instead")
    suspend fun cancelOrder(
        @Path("symbol") symbol: String,
        @Path("orderId") orderId: String
    ): Response<ApiResponse>
    
    @DELETE("api/binance/orders/{symbol}")
    @Deprecated("Use cancelAllFuturesOrders() instead")
    suspend fun cancelAllOrders(@Path("symbol") symbol: String): Response<ApiResponse>
    
    @POST("api/binance/tpsl")
    @Deprecated("Use setFuturesTPSL() instead")
    suspend fun setTPSL(@Body tpslRequest: Map<String, Any>): Response<ApiResponse>
    
    @GET("api/binance/balance")
    @Deprecated("Use getFuturesBalance() instead")
    suspend fun getBalance(@Query("asset") asset: String? = "USDT"): Response<BalanceResponse>
    
    @GET("api/binance/price/{symbol}")
    @Deprecated("Use getFuturesPrice() instead")
    suspend fun getPrice(@Path("symbol") symbol: String): Response<PriceResponse>
    
    @GET("api/binance/prices")
    @Deprecated("Use getAllFuturesPrices() instead")
    suspend fun getAllPrices(): Response<AllPricesResponse>
} 