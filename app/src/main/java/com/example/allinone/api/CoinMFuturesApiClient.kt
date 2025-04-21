package com.example.allinone.api

import android.util.Log
import com.example.allinone.BuildConfig
import com.example.allinone.data.BinanceBalance
import com.example.allinone.data.BinanceFutures
import com.example.allinone.data.BinanceOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.io.OutputStreamWriter

/**
 * Client for interacting with the Binance COIN-M Futures API
 */
class CoinMFuturesApiClient(
    private val apiKey: String = BuildConfig.BINANCE_API_KEY,
    private val secretKey: String = BuildConfig.BINANCE_SECRET_KEY,
    private val baseUrl: String = "https://dapi.binance.com"
) {
    private val TAG = "CoinMFuturesApiClient"

    /**
     * Get account balance from Binance COIN-M Futures API
     */
    suspend fun getAccountBalance(): List<BinanceBalance> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/balance?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Balance Response: $response")
                Log.d(TAG, "COIN-M Balance Response Length: ${response.length}")
                Log.d(TAG, "COIN-M Balance Response Type: ${if (response.startsWith('[')) "Array" else if (response.startsWith('{')) "Object" else "Unknown"}")
                parseBalanceResponse(response)
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error fetching account balance: $errorResponse")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching account balance", e)
            emptyList()
        }
    }

    /**
     * Get account information from Binance COIN-M Futures API
     * This provides more detailed account information including total assets
     */
    suspend fun getAccountInformation(): JSONObject = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/account?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Account Information Response: $response")
                JSONObject(response)
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error fetching account information: $errorResponse")
                JSONObject()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching account information", e)
            JSONObject()
        }
    }

    /**
     * Get latest prices for all COIN-M Futures trading pairs
     * Returns a map of symbol to price
     */
    suspend fun getLatestPrices(): Map<String, Double> = withContext(Dispatchers.IO) {
        val priceMap = mutableMapOf<String, Double>()
        try {
            // This endpoint doesn't require authentication
            val url = URL("$baseUrl/dapi/v1/ticker/price")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Prices Response: $response")

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val symbol = jsonObject.getString("symbol")
                    val price = jsonObject.getString("price").toDouble()
                    priceMap[symbol] = price

                    // Also add a simplified version of the symbol (without the contract suffix)
                    // For example, for BTCUSD_PERP, also add BTC=price
                    val baseAsset = symbol.split("_").firstOrNull()?.replace("USD", "") ?: continue
                    if (baseAsset.isNotEmpty()) {
                        priceMap[baseAsset] = price
                    }
                }
                Log.d(TAG, "COIN-M Prices parsed: ${priceMap.size} prices")
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error fetching prices: $errorResponse")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching prices", e)
        }
        return@withContext priceMap
    }

    /**
     * Get position information from Binance COIN-M Futures API
     */
    suspend fun getPositionInformation(): List<BinanceFutures> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/positionRisk?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parsePositionResponse(response)
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error fetching position information: $errorResponse")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching position information", e)
            emptyList()
        }
    }

    /**
     * Get all open orders from Binance COIN-M Futures API
     * @return List of open orders
     */
    suspend fun getOpenOrders(): List<BinanceOrder> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/openOrders?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Open Orders Response: $response")
                parseOpenOrdersResponse(response)
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error fetching COIN-M open orders: $errorResponse")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching COIN-M open orders", e)
            emptyList()
        }
    }

    /**
     * Parse the balance response from Binance COIN-M Futures API
     *
     * COIN-M Futures API has a different response format than USD-M Futures API
     * Example response:
     * [
     *   {
     *     "accountAlias": "SgsR",    // unique account code
     *     "asset": "BTC",
     *     "balance": "0.00250000",
     *     "withdrawAvailable": "0.00250000",
     *     "crossWalletBalance": "0.00241969",
     *     "crossUnPnl": "0.00000000",
     *     "availableBalance": "0.00241969",
     *     "updateTime": 1592468353979
     *   }
     * ]
     */
    private fun parseBalanceResponse(response: String): List<BinanceBalance> {
        val balances = mutableListOf<BinanceBalance>()
        try {
            // Log the entire response for debugging
            Log.d(TAG, "COIN-M Balance Response: $response")

            // Check if response is empty or null
            if (response.isNullOrBlank()) {
                Log.e(TAG, "COIN-M Balance Response is empty or null")
                return balances
            }

            // Try to parse as JSON array
            try {
                val jsonArray = JSONArray(response)
                Log.d(TAG, "COIN-M Balance JSON Array Length: ${jsonArray.length()}")

                for (i in 0 until jsonArray.length()) {
                    try {
                        val jsonObject = jsonArray.getJSONObject(i)

                        // Log the individual JSON object for debugging
                        Log.d(TAG, "COIN-M Balance Object $i: $jsonObject")

                        // Check if the object has all required fields
                        if (!jsonObject.has("asset") || !jsonObject.has("balance")) {
                            Log.e(TAG, "COIN-M Balance Object $i missing required fields")
                            continue
                        }

                        val asset = jsonObject.getString("asset")
                        Log.d(TAG, "COIN-M Asset: $asset")

                        val balance = jsonObject.getString("balance").toDouble()
                        Log.d(TAG, "COIN-M Balance: $balance")

                        val crossWalletBalance = jsonObject.optString("crossWalletBalance", "0.0").toDouble()
                        val crossUnPnl = jsonObject.optString("crossUnPnl", "0.0").toDouble()
                        val availableBalance = jsonObject.optString("availableBalance", "0.0").toDouble()

                        // COIN-M API uses withdrawAvailable instead of maxWithdrawAmount
                        val maxWithdrawAmount = if (jsonObject.has("withdrawAvailable")) {
                            jsonObject.getString("withdrawAvailable").toDouble()
                        } else {
                            0.0 // Default value if not present
                        }

                        // COIN-M API doesn't have marginAvailable field
                        val marginAvailable = jsonObject.optBoolean("marginAvailable", true)
                        val updateTime = Date(jsonObject.optLong("updateTime", System.currentTimeMillis()))

                        // Create the balance object
                        val binanceBalance = BinanceBalance(
                            id = i.toLong(),
                            asset = asset,
                            balance = balance,
                            crossWalletBalance = crossWalletBalance,
                            crossUnPnl = crossUnPnl,
                            availableBalance = availableBalance,
                            maxWithdrawAmount = maxWithdrawAmount,
                            marginAvailable = marginAvailable,
                            updateTime = updateTime,
                            futuresType = "COIN-M"
                        )

                        // Log each balance for debugging
                        Log.d(TAG, "COIN-M Balance Added: $asset = $balance")

                        balances.add(binanceBalance)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing balance object at index $i: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing as JSON array: ${e.message}")

                // Try to parse as JSON object (fallback)
                try {
                    Log.d(TAG, "Trying to parse as JSON object instead")
                    val jsonObject = JSONObject(response)
                    Log.d(TAG, "Successfully parsed as JSON object: $jsonObject")

                    // Handle any specific JSON object format here if needed
                } catch (e2: Exception) {
                    Log.e(TAG, "Error parsing as JSON object: ${e2.message}")
                }
            }

            // If we have balances, log them
            if (balances.isNotEmpty()) {
                Log.d(TAG, "COIN-M Balances parsed successfully: ${balances.size} balances found")
            } else {
                Log.w(TAG, "COIN-M No balances found in response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing balance response: ${e.message}", e)
        }
        return balances
    }

    /**
     * Parse the position response from Binance API
     */
    private fun parsePositionResponse(response: String): List<BinanceFutures> {
        val positions = mutableListOf<BinanceFutures>()
        try {
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val symbol = jsonObject.getString("symbol")
                val positionAmt = jsonObject.getString("positionAmt").toDouble()

                // Skip positions with zero amount
                if (positionAmt == 0.0) continue

                val entryPrice = jsonObject.getString("entryPrice").toDouble()
                val markPrice = jsonObject.getString("markPrice").toDouble()
                val unRealizedProfit = jsonObject.getString("unRealizedProfit").toDouble()
                val liquidationPrice = jsonObject.getString("liquidationPrice").toDouble()
                val leverage = jsonObject.getInt("leverage")
                val marginType = jsonObject.getString("marginType")
                val isolatedMargin = if (jsonObject.has("isolatedMargin"))
                    jsonObject.getString("isolatedMargin").toDouble() else 0.0
                val isAutoAddMargin = jsonObject.getBoolean("isAutoAddMargin")
                val positionSide = jsonObject.getString("positionSide")
                val updateTime = Date(jsonObject.getLong("updateTime"))

                positions.add(
                    BinanceFutures(
                        id = i.toLong(),
                        symbol = symbol,
                        positionAmt = positionAmt,
                        entryPrice = entryPrice,
                        markPrice = markPrice,
                        unRealizedProfit = unRealizedProfit,
                        liquidationPrice = liquidationPrice,
                        leverage = leverage,
                        marginType = marginType,
                        isolatedMargin = isolatedMargin,
                        isAutoAddMargin = isAutoAddMargin,
                        positionSide = positionSide,
                        updateTime = updateTime,
                        futuresType = "COIN-M"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing position response", e)
        }
        return positions
    }

    /**
     * Generate HMAC SHA256 signature for API request
     */
    private fun generateSignature(data: String): String {
        try {
            val hmacSha256 = "HmacSHA256"
            val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), hmacSha256)
            val mac = Mac.getInstance(hmacSha256)
            mac.init(secretKeySpec)
            val hash = mac.doFinal(data.toByteArray())
            return bytesToHex(hash)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "No such algorithm", e)
        } catch (e: InvalidKeyException) {
            Log.e(TAG, "Invalid key", e)
        }
        return ""
    }

    /**
     * Convert bytes to hexadecimal string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach {
            val i = it.toInt()
            result.append(hexChars[i shr 4 and 0x0f])
            result.append(hexChars[i and 0x0f])
        }
        return result.toString()
    }

    /**
     * Place a Take Profit Market order for COIN-M Futures
     * @param symbol The trading pair symbol (e.g., BTCUSD_PERP)
     * @param side BUY or SELL
     * @param quantity The quantity to trade
     * @param stopPrice The price at which the order will be triggered
     * @return The response from the API as a JSON string
     */
    suspend fun placeTakeProfitMarketOrder(
        symbol: String,
        side: String,
        quantity: Double,
        stopPrice: Double
    ): String = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "symbol=$symbol&side=$side&type=TAKE_PROFIT_MARKET&quantity=$quantity&stopPrice=$stopPrice&closePosition=true&workingType=CONTRACT_PRICE&timeInForce=GTE_GTC&timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/order?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)
            connection.doOutput = true

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Take Profit Market Order Response: $response")
                response
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error placing COIN-M Take Profit Market order: $errorResponse")
                "{\"error\":\"$errorResponse\"}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception placing COIN-M Take Profit Market order", e)
            "{\"error\":\"${e.message}\"}"
        }
    }

    /**
     * Place a Stop Loss Market order for COIN-M Futures
     * @param symbol The trading pair symbol (e.g., BTCUSD_PERP)
     * @param side BUY or SELL
     * @param quantity The quantity to trade
     * @param stopPrice The price at which the order will be triggered
     * @return The response from the API as a JSON string
     */
    suspend fun placeStopLossMarketOrder(
        symbol: String,
        side: String,
        quantity: Double,
        stopPrice: Double
    ): String = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "symbol=$symbol&side=$side&type=STOP_MARKET&quantity=$quantity&stopPrice=$stopPrice&closePosition=true&workingType=MARK_PRICE&timeInForce=GTE_GTC&timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/order?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)
            connection.doOutput = true

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Stop Loss Market Order Response: $response")
                response
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error placing COIN-M Stop Loss Market order: $errorResponse")
                "{\"error\":\"$errorResponse\"}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception placing COIN-M Stop Loss Market order", e)
            "{\"error\":\"${e.message}\"}"
        }
    }

    /**
     * Parse the open orders response from Binance COIN-M Futures API
     */
    private fun parseOpenOrdersResponse(response: String): List<BinanceOrder> {
        val orders = mutableListOf<BinanceOrder>()
        try {
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val symbol = jsonObject.getString("symbol")
                val orderId = jsonObject.getLong("orderId")
                val type = jsonObject.getString("type")
                val side = jsonObject.getString("side")
                val price = jsonObject.optDouble("price", 0.0)
                val stopPrice = jsonObject.optDouble("stopPrice", 0.0)
                val origQty = jsonObject.optDouble("origQty", 0.0)
                val positionSide = jsonObject.optString("positionSide", "BOTH")

                // Only include TP/SL orders
                if (type == "TAKE_PROFIT_MARKET" || type == "STOP_MARKET") {
                    orders.add(
                        BinanceOrder(
                            symbol = symbol,
                            orderId = orderId,
                            type = type,
                            side = side,
                            price = price,
                            stopPrice = stopPrice,
                            origQty = origQty,
                            positionSide = positionSide
                        )
                    )
                }
            }
            Log.d(TAG, "Parsed ${orders.size} COIN-M TP/SL orders")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing COIN-M open orders response", e)
        }
        return orders
    }

    /**
     * Close a position by placing a market order in the opposite direction for COIN-M Futures
     * @param symbol The trading pair symbol (e.g., BTCUSD_PERP)
     * @param positionAmt The current position amount (positive for long, negative for short)
     * @return The response from the API as a JSON string
     */
    suspend fun closePosition(
        symbol: String,
        positionAmt: Double
    ): String = withContext(Dispatchers.IO) {
        try {
            // Determine the side based on the position amount
            // If positionAmt is positive (long), we need to SELL to close
            // If positionAmt is negative (short), we need to BUY to close
            val side = if (positionAmt > 0) "SELL" else "BUY"
            // Use absolute value of position amount for the quantity
            val quantity = Math.abs(positionAmt)

            val timestamp = System.currentTimeMillis().toString()
            val queryString = "symbol=$symbol&side=$side&type=MARKET&quantity=$quantity&timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/order?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)
            connection.doOutput = true

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Close Position Response: $response")
                response
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error closing COIN-M position: $errorResponse")
                "{\"error\":\"$errorResponse\"}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception closing COIN-M position", e)
            "{\"error\":\"${e.message}\"}"
        }
    }

    /**
     * Cancel an order by its ID for COIN-M Futures
     * @param symbol The trading pair symbol (e.g., BTCUSD_PERP)
     * @param orderId The ID of the order to cancel
     * @return The response from the API as a JSON string
     */
    suspend fun cancelOrder(
        symbol: String,
        orderId: Long
    ): String = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "symbol=$symbol&orderId=$orderId&timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/dapi/v1/order?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "COIN-M Cancel Order Response: $response")
                response
            } else {
                val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "Error canceling COIN-M order: $errorResponse")
                "{\"error\":\"$errorResponse\"}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception canceling COIN-M order", e)
            "{\"error\":\"${e.message}\"}"
        }
    }
}
