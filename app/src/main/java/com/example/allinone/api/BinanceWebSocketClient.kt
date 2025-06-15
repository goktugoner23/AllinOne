package com.example.allinone.api

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import com.google.gson.Gson
import com.google.gson.JsonObject
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class BinanceWebSocketClient(
    private val onMessage: (String, JsonObject) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit
) {
    private var webSocket: WebSocketClient? = null
    private val gson = Gson()
    private val isConnected = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(true)
    private val reconnectScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val TAG = "BinanceWebSocketClient"
        private const val WEBSOCKET_URL = "ws://localhost:3000"
        private const val PRODUCTION_WEBSOCKET_URL = "wss://allinone-app-5t9md.ondigitalocean.app"
        private const val HEARTBEAT_INTERVAL = 30000L // 30 seconds
        private const val RECONNECT_DELAY = 5000L // 5 seconds
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }
    
    private var reconnectAttempts = 0
    
    fun connect() {
        try {
            val uri = URI(PRODUCTION_WEBSOCKET_URL)
            Log.d(TAG, "Connecting to WebSocket: $PRODUCTION_WEBSOCKET_URL")
            
            webSocket = object : WebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    isConnected.set(true)
                    reconnectAttempts = 0
                    onConnectionChange(true)
                    Log.d(TAG, "WebSocket connected successfully")
                    
                    // Send initial connection message if needed
                    sendConnectionMessage()
                }
                
                override fun onMessage(message: String?) {
                    message?.let {
                        try {
                            Log.d(TAG, "WebSocket message received: $it")
                            val jsonObject = gson.fromJson(it, JsonObject::class.java)
                            val type = jsonObject.get("type")?.asString ?: "unknown"
                            
                            // Handle different message types
                            when (type) {
                                "welcome" -> {
                                    Log.d(TAG, "Welcome message received")
                                    handleWelcomeMessage(jsonObject)
                                }
                                "ticker" -> {
                                    Log.d(TAG, "Ticker update received")
                                    onMessage(type, jsonObject)
                                }
                                "depth" -> {
                                    Log.d(TAG, "Depth update received")
                                    onMessage(type, jsonObject)
                                }
                                "trade" -> {
                                    Log.d(TAG, "Trade update received")
                                    onMessage(type, jsonObject)
                                }
                                "positions_update" -> {
                                    Log.d(TAG, "Positions update received")
                                    onMessage(type, jsonObject)
                                }
                                "order_update" -> {
                                    Log.d(TAG, "Order update received")
                                    onMessage(type, jsonObject)
                                }
                                "balance_update" -> {
                                    Log.d(TAG, "Balance update received")
                                    onMessage(type, jsonObject)
                                }
                                "pong" -> {
                                    Log.d(TAG, "Pong received")
                                    onMessage(type, jsonObject)
                                }
                                "error" -> {
                                    val error = jsonObject.get("error")?.asString ?: "Unknown error"
                                    Log.e(TAG, "WebSocket error message: $error")
                                    onMessage(type, jsonObject)
                                }
                                else -> {
                                    Log.d(TAG, "Unknown message type: $type")
                                    onMessage(type, jsonObject)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing WebSocket message: ${e.message}")
                        }
                    }
                }
                
                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    isConnected.set(false)
                    onConnectionChange(false)
                    Log.d(TAG, "WebSocket closed: $reason (code: $code, remote: $remote)")
                    
                    // Attempt reconnection if it was not intentional
                    if (shouldReconnect.get() && remote) {
                        attemptReconnect()
                    }
                }
                
                override fun onError(ex: Exception?) {
                    isConnected.set(false)
                    onConnectionChange(false)
                    Log.e(TAG, "WebSocket error: ${ex?.message}")
                    
                    // Attempt reconnection on error
                    if (shouldReconnect.get()) {
                        attemptReconnect()
                    }
                }
            }
            
            webSocket?.connect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect WebSocket: ${e.message}")
            isConnected.set(false)
            onConnectionChange(false)
            
            if (shouldReconnect.get()) {
                attemptReconnect()
            }
        }
    }
    
    private fun handleWelcomeMessage(message: JsonObject) {
        Log.d(TAG, "Processing welcome message")
        val welcomeMsg = message.get("message")?.asString
        if (welcomeMsg != null) {
            Log.d(TAG, "Welcome: $welcomeMsg")
        }
    }
    
    private fun sendConnectionMessage() {
        try {
            val connectionMsg = JsonObject().apply {
                addProperty("type", "connection")
                addProperty("source", "android_app")
                addProperty("timestamp", System.currentTimeMillis())
            }
            send(connectionMsg.toString())
            Log.d(TAG, "Connection message sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send connection message: ${e.message}")
        }
    }
    
    private fun attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached. Stopping reconnection.")
            shouldReconnect.set(false)
            return
        }
        
        reconnectAttempts++
        Log.d(TAG, "Attempting reconnection #$reconnectAttempts")
        
        reconnectScope.launch {
            delay(RECONNECT_DELAY)
            if (shouldReconnect.get() && !isConnected.get()) {
                connect()
            }
        }
    }
    
    fun disconnect() {
        shouldReconnect.set(false)
        webSocket?.close()
        isConnected.set(false)
        Log.d(TAG, "WebSocket disconnected")
    }
    
    fun isConnected(): Boolean = isConnected.get()
    
    fun sendHeartbeat() {
        try {
            if (isConnected.get()) {
                val heartbeat = JsonObject().apply {
                    addProperty("type", "ping")
                    addProperty("timestamp", System.currentTimeMillis())
                }
                send(heartbeat.toString())
                Log.d(TAG, "Heartbeat sent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send heartbeat: ${e.message}")
        }
    }
    
    fun send(message: String) {
        try {
            if (isConnected.get()) {
                webSocket?.send(message)
                Log.d(TAG, "Message sent: $message")
            } else {
                Log.w(TAG, "Cannot send message: WebSocket not connected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
        }
    }
    
    // Subscription methods for futures data
    fun subscribeToPositionUpdates() {
        try {
            val subscribeMsg = JsonObject().apply {
                addProperty("type", "subscribe")
                addProperty("channel", "positions")
                addProperty("timestamp", System.currentTimeMillis())
            }
            send(subscribeMsg.toString())
            Log.d(TAG, "Subscribed to position updates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to position updates: ${e.message}")
        }
    }
    
    fun subscribeToOrderUpdates() {
        try {
            val subscribeMsg = JsonObject().apply {
                addProperty("type", "subscribe")
                addProperty("channel", "orders")
                addProperty("timestamp", System.currentTimeMillis())
            }
            send(subscribeMsg.toString())
            Log.d(TAG, "Subscribed to order updates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to order updates: ${e.message}")
        }
    }
    
    fun subscribeToBalanceUpdates() {
        try {
            val subscribeMsg = JsonObject().apply {
                addProperty("type", "subscribe")
                addProperty("channel", "balance")
                addProperty("timestamp", System.currentTimeMillis())
            }
            send(subscribeMsg.toString())
            Log.d(TAG, "Subscribed to balance updates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to balance updates: ${e.message}")
        }
    }
    
    fun subscribeToTickerUpdates(symbol: String) {
        try {
            val subscribeMsg = JsonObject().apply {
                addProperty("type", "subscribe")
                addProperty("channel", "ticker")
                addProperty("symbol", symbol)
                addProperty("timestamp", System.currentTimeMillis())
            }
            send(subscribeMsg.toString())
            Log.d(TAG, "Subscribed to ticker updates for $symbol")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to ticker updates: ${e.message}")
        }
    }
    
    fun unsubscribeFromChannel(channel: String) {
        try {
            val unsubscribeMsg = JsonObject().apply {
                addProperty("type", "unsubscribe")
                addProperty("channel", channel)
                addProperty("timestamp", System.currentTimeMillis())
            }
            send(unsubscribeMsg.toString())
            Log.d(TAG, "Unsubscribed from $channel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from $channel: ${e.message}")
        }
    }
    
    fun resetConnection() {
        Log.d(TAG, "Resetting WebSocket connection")
        shouldReconnect.set(true)
        reconnectAttempts = 0
        disconnect()
        
        // Wait a moment before reconnecting
        reconnectScope.launch {
            delay(1000)
            connect()
        }
    }
} 