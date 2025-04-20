package com.example.allinone.api

import android.util.Log
import com.example.allinone.BuildConfig
import com.example.allinone.data.BinanceBalance
import com.example.allinone.data.BinanceFutures
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
}
