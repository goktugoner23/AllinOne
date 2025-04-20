package com.example.allinone.api

import android.util.Log
import com.example.allinone.data.BinanceBalance
import com.example.allinone.BuildConfig
import com.example.allinone.data.BinanceFutures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.URL
import java.net.HttpURLConnection
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/**
 * Client for interacting with the Binance Futures API
 */
class BinanceApiClient(
    private val apiKey: String = BuildConfig.BINANCE_API_KEY,
    private val secretKey: String = BuildConfig.BINANCE_SECRET_KEY,
    private val baseUrl: String = "https://fapi.binance.com"
) {
    private val TAG = "BinanceApiClient"

    /**
     * Get account balance from Binance Futures API
     */
    suspend fun getAccountBalance(): List<BinanceBalance> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/fapi/v2/balance?$queryString&signature=$signature")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-MBX-APIKEY", apiKey)

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
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
     * Get position information from Binance Futures API
     */
    suspend fun getPositionInformation(): List<BinanceFutures> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val queryString = "timestamp=$timestamp"
            val signature = generateSignature(queryString)

            val url = URL("$baseUrl/fapi/v2/positionRisk?$queryString&signature=$signature")
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
     * Parse the balance response from Binance API
     */
    private fun parseBalanceResponse(response: String): List<BinanceBalance> {
        val balances = mutableListOf<BinanceBalance>()
        try {
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val asset = jsonObject.getString("asset")
                val balance = jsonObject.getString("balance").toDouble()
                val crossWalletBalance = jsonObject.getString("crossWalletBalance").toDouble()
                val crossUnPnl = jsonObject.getString("crossUnPnl").toDouble()
                val availableBalance = jsonObject.getString("availableBalance").toDouble()
                val maxWithdrawAmount = jsonObject.getString("maxWithdrawAmount").toDouble()
                val marginAvailable = jsonObject.getBoolean("marginAvailable")
                val updateTime = Date(jsonObject.getLong("updateTime"))

                balances.add(
                    BinanceBalance(
                        id = i.toLong(),
                        asset = asset,
                        balance = balance,
                        crossWalletBalance = crossWalletBalance,
                        crossUnPnl = crossUnPnl,
                        availableBalance = availableBalance,
                        maxWithdrawAmount = maxWithdrawAmount,
                        marginAvailable = marginAvailable,
                        updateTime = updateTime
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing balance response", e)
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
                        updateTime = updateTime
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
        for (byte in bytes) {
            val i = byte.toInt() and 0xff
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0f])
        }
        return result.toString()
    }
}
