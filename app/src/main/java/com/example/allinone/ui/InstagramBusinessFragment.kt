package com.example.allinone.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.allinone.BuildConfig
import com.example.allinone.R
import com.example.allinone.databinding.FragmentInstagramBusinessBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class InstagramBusinessFragment : BaseFragment() {
    
    private var _binding: FragmentInstagramBusinessBinding? = null
    private val binding get() = _binding!!
    
    // Instagram API constants - using the correct environment variable keys
    private val accessToken = BuildConfig.INSTAGRAM_ACCESS_TOKEN
    private val graphToken = BuildConfig.INSTAGRAM_GRAPH_TOKEN // Facebook Graph API token for insights
    private val businessAccountId = BuildConfig.INSTAGRAM_BUSINESS_ACCOUNT_ID
    private var userId: String? = null
    
    companion object {
        private const val TAG = "InstagramBusiness"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstagramBusinessBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Debug log the token details for troubleshooting
        Log.d(TAG, "Instagram token length: ${accessToken.length}")
        Log.d(TAG, "Graph token length: ${graphToken.length}")
        Log.d(TAG, "Business Account ID: $businessAccountId")
        
        if (accessToken.isNotEmpty() && accessToken != "NOT_SET") {
            // We already have a token, show it's ready
            binding.textInstagramBusiness.text = "Instagram Business\nLoading data..."
            
            // Hide both buttons as we'll fetch data automatically
            binding.btnFetchProfileData.visibility = View.GONE
            binding.btnFetchInsights.visibility = View.GONE
            
            // No need for login button since we're using the existing token
            binding.btnInstagramLogin.visibility = View.GONE
            
            // Automatically fetch data when the fragment opens
            fetchDataAutomatically()
        } else {
            binding.textInstagramBusiness.text = "Instagram Business\nAPI Token not found in .env file"
            Log.e(TAG, "Instagram token not configured in .env file")
            Toast.makeText(
                context,
                "Please add a valid INSTAGRAM_ACCESS_TOKEN to your .env file",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun fetchDataAutomatically() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // First fetch profile data
                fetchProfileData()
                
                // Then fetch insights data if we have the graph token
                if (graphToken.isNotEmpty() && graphToken != "NOT_SET") {
                    fetchInsightsData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching data automatically", e)
                binding.textInstagramStats.text = "Error loading data: ${e.message}"
                binding.textInstagramStats.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private suspend fun fetchProfileData() {
        try {
            // Directly construct the URL with the raw token
            val baseUrl = "https://graph.instagram.com/v22.0/me"
            val fields = "id,username,account_type,media_count"
            val urlStr = "$baseUrl?fields=$fields&access_token=$accessToken"
            
            Log.d(TAG, "Making API call to: $baseUrl?fields=$fields&access_token=[TOKEN_HIDDEN]")
            
            // Fetch user profile data
            val response = withContext(Dispatchers.IO) {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                
                val responseCode = connection.responseCode
                Log.d(TAG, "API Response code: $responseCode")
                
                val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details available"
                }
                
                // Log the raw API response
                Log.d(TAG, "Profile endpoint response: $responseText")
                
                responseText
            }
            
            // Parse the response
            val jsonResponse = JSONObject(response)
            
            // Check for error
            if (jsonResponse.has("error")) {
                val error = jsonResponse.getJSONObject("error")
                val message = error.optString("message", "Unknown error")
                val type = error.optString("type", "Unknown type")
                val code = error.optInt("code", -1)
                val traceId = error.optString("fbtrace_id", "None")
                
                Log.e(TAG, "API Error: [$code] $type - $message (Trace: $traceId)")
                
                // Display error to user
                binding.textInstagramStats.text = "Error: $message\nCode: $code\nType: $type"
                binding.textInstagramStats.visibility = View.VISIBLE
            } else {
                // Success! Parse and display profile data
                val profile = jsonResponse
                val statsBuilder = StringBuilder()
                statsBuilder.append("Instagram Business Profile:\n\n")
                
                // Store user ID for insights requests
                userId = profile.optString("id")
                
                if (profile.has("id")) statsBuilder.append("ID: ${profile.getString("id")}\n")
                if (profile.has("username")) statsBuilder.append("Username: ${profile.getString("username")}\n")
                if (profile.has("account_type")) statsBuilder.append("Account Type: ${profile.getString("account_type")}\n")
                if (profile.has("media_count")) statsBuilder.append("Media Count: ${profile.getInt("media_count")}\n")
                
                binding.textInstagramStats.text = statsBuilder.toString()
                binding.textInstagramStats.visibility = View.VISIBLE
                
                // Fetch recent media
                fetchRecentMedia()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile", e)
            binding.textInstagramStats.text = "Error fetching profile: ${e.message}"
            binding.textInstagramStats.visibility = View.VISIBLE
            throw e
        }
    }
    
    private suspend fun fetchInsightsData() {
        try {
            // Only fetch media insights (removed account insights part)
            val mediaInsights = fetchMediaInsights()
            
            // Display only media insights
            val combinedInsights = StringBuilder()
            combinedInsights.append("📊 INSTAGRAM BUSINESS INSIGHTS 📊\n\n")
            
            // Media insights
            combinedInsights.append("RECENT POSTS PERFORMANCE:\n")
            combinedInsights.append(mediaInsights)
            
            // Update UI
            binding.textInstagramStats.text = combinedInsights.toString()
            binding.textInstagramStats.visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching insights", e)
            binding.textInstagramStats.text = "Error fetching insights: ${e.message}"
            binding.textInstagramStats.visibility = View.VISIBLE
            throw e
        }
    }
    
    private suspend fun fetchMediaInsights(): String {
        return withContext(Dispatchers.IO) {
            try {
                // Directly fetch media from the business account using Facebook Graph API
                val mediaUrl = "https://graph.facebook.com/v22.0/$businessAccountId/media?fields=id,media_type,media_product_type,media_url,permalink,thumbnail_url,caption,timestamp,like_count,comments_count&limit=10&access_token=$graphToken"
                val mediaConnection = URL(mediaUrl).openConnection() as HttpURLConnection
                val mediaResponse = mediaConnection.inputStream.bufferedReader().use { it.readText() }
                
                Log.d(TAG, "Facebook Graph Media Response: $mediaResponse")
                
                val mediaJson = JSONObject(mediaResponse)
                val insightsBuilder = StringBuilder()
                
                // Process the media items
                if (mediaJson.has("data")) {
                    val mediaArray = mediaJson.getJSONArray("data")
                    
                    if (mediaArray.length() == 0) {
                        return@withContext "No media found on your business account through Facebook Graph API."
                    }
                    
                    // Display up to 3 most recent posts
                    for (i in 0 until mediaArray.length().coerceAtMost(3)) {
                        val media = mediaArray.getJSONObject(i)
                        val mediaId = media.getString("id")
                        val mediaType = media.optString("media_type", "UNKNOWN")
                        val mediaProductType = media.optString("media_product_type", "")
                        val caption = media.optString("caption", "No caption")
                        val timestamp = media.optString("timestamp", "")
                        val permalink = media.optString("permalink", "")
                        val likesCount = media.optInt("like_count", 0)
                        val commentsCount = media.optInt("comments_count", 0)
                        
                        insightsBuilder.append("Post #${i+1}: ${caption.take(30)}${if (caption.length > 30) "..." else ""}\n")
                        
                        if (timestamp.isNotEmpty()) {
                            try {
                                val date = parseIsoDate(timestamp)
                                insightsBuilder.append("📅 Posted: ${formatDate(date)}\n")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing date: $timestamp", e)
                                insightsBuilder.append("📅 Posted: $timestamp\n")
                            }
                        }
                        
                        insightsBuilder.append("🔗 Link: $permalink\n")
                        
                        // Determine media product type for display and metrics selection
                        val displayMediaType = when {
                            mediaProductType == "REELS" -> "REELS"
                            mediaProductType == "STORY" -> "STORY"
                            mediaType == "CAROUSEL_ALBUM" -> "ALBUM"
                            mediaType == "VIDEO" -> "VIDEO"
                            else -> "FEED"
                        }
                        
                        insightsBuilder.append("📊 Media Type: $displayMediaType\n")
                        insightsBuilder.append("❤️ Likes: $likesCount\n")
                        insightsBuilder.append("💬 Comments: $commentsCount\n")
                        
                        // Handle insights according to media type
                        when (displayMediaType) {
                            "ALBUM" -> {
                                insightsBuilder.append("ℹ️ Note: Instagram does not provide insights for albums\n")
                            }
                            "REELS" -> {
                                fetchReelsInsights(mediaId, insightsBuilder)
                            }
                            "STORY" -> {
                                fetchStoryInsights(mediaId, insightsBuilder)
                            }
                            else -> {
                                fetchFeedInsights(mediaId, insightsBuilder)
                            }
                        }
                        
                        insightsBuilder.append("\n")
                    }
                } else {
                    insightsBuilder.append("No media found on your business account through Facebook Graph API.\n")
                    
                    // Check if there's an error message in the response
                    if (mediaJson.has("error")) {
                        val error = mediaJson.getJSONObject("error")
                        val message = error.optString("message", "Unknown error")
                        val code = error.optInt("code", -1)
                        insightsBuilder.append("Error: $message (Code: $code)\n")
                    }
                }
                
                if (insightsBuilder.isEmpty()) {
                    return@withContext "No media insights data available through Facebook Graph API. Note that this requires a Business or Creator account with Facebook page and proper permissions."
                }
                
                insightsBuilder.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchMediaInsights", e)
                "Error fetching media insights through Facebook Graph API: ${e.message}"
            }
        }
    }
    
    private suspend fun fetchReelsInsights(mediaId: String, insightsBuilder: StringBuilder) {
        try {
            // According to latest docs, these metrics are available for REELS (plays is now deprecated)
            val metrics = "comments,likes,reach,saved,shares,total_interactions,views,ig_reels_avg_watch_time"
            val insightsUrl = "https://graph.facebook.com/v22.0/$mediaId/insights?metric=$metrics&access_token=$graphToken"
            
            Log.d(TAG, "Fetching REELS insights from Facebook Graph API: $mediaId/insights?metric=$metrics")
            
            val insightsConnection = URL(insightsUrl).openConnection() as HttpURLConnection
            val responseCode = insightsConnection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val insightsText = insightsConnection.inputStream.bufferedReader().use { it.readText() }
                processInsightsResponse(insightsText, insightsBuilder, "REELS")
            } else {
                handleInsightsError(insightsConnection, insightsBuilder, mediaId, "REELS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching REELS insights for $mediaId", e)
            insightsBuilder.append("⚠️ Error retrieving REELS insights: ${e.message?.take(50)}\n")
        }
    }
    
    private suspend fun fetchStoryInsights(mediaId: String, insightsBuilder: StringBuilder) {
        try {
            // According to docs, these metrics are available for STORY
            val metrics = "impressions,reach,replies"
            val insightsUrl = "https://graph.facebook.com/v22.0/$mediaId/insights?metric=$metrics&access_token=$graphToken"
            
            Log.d(TAG, "Fetching STORY insights from Facebook Graph API: $mediaId/insights?metric=$metrics")
            
            val insightsConnection = URL(insightsUrl).openConnection() as HttpURLConnection
            val responseCode = insightsConnection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val insightsText = insightsConnection.inputStream.bufferedReader().use { it.readText() }
                processInsightsResponse(insightsText, insightsBuilder, "STORY")
                
                // Try to get navigation metrics with breakdown
                fetchStoryNavigationBreakdown(mediaId, insightsBuilder)
            } else {
                handleInsightsError(insightsConnection, insightsBuilder, mediaId, "STORY")
                insightsBuilder.append("📝 Note: Story metrics are only available for 24 hours\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching STORY insights for $mediaId", e)
            insightsBuilder.append("⚠️ Error retrieving STORY insights: ${e.message?.take(50)}\n")
            insightsBuilder.append("📝 Note: Story metrics are only available for 24 hours\n")
        }
    }
    
    private suspend fun fetchFeedInsights(mediaId: String, insightsBuilder: StringBuilder) {
        try {
            // According to docs, these metrics are available for FEED
            val metrics = "comments,likes,reach,saved,shares,total_interactions,views"
            val insightsUrl = "https://graph.facebook.com/v22.0/$mediaId/insights?metric=$metrics&access_token=$graphToken"
            
            Log.d(TAG, "Fetching FEED insights from Facebook Graph API: $mediaId/insights?metric=$metrics")
            
            val insightsConnection = URL(insightsUrl).openConnection() as HttpURLConnection
            val responseCode = insightsConnection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val insightsText = insightsConnection.inputStream.bufferedReader().use { it.readText() }
                processInsightsResponse(insightsText, insightsBuilder, "FEED")
                
                // Try to get profile_activity breakdown if available
                fetchProfileActivityBreakdown(mediaId, insightsBuilder)
            } else {
                handleInsightsError(insightsConnection, insightsBuilder, mediaId, "FEED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching FEED insights for $mediaId", e)
            insightsBuilder.append("⚠️ Error retrieving FEED insights: ${e.message?.take(50)}\n")
        }
    }
    
    private suspend fun fetchStoryNavigationBreakdown(mediaId: String, insightsBuilder: StringBuilder) {
        try {
            val navigationUrl = "https://graph.facebook.com/v22.0/$mediaId/insights?metric=navigation&breakdown=story_navigation_action_type&access_token=$graphToken"
            val navigationConnection = URL(navigationUrl).openConnection() as HttpURLConnection
            val responseCode = navigationConnection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val navText = navigationConnection.inputStream.bufferedReader().use { it.readText() }
                val navJson = JSONObject(navText)
                
                if (navJson.has("data") && navJson.getJSONArray("data").length() > 0) {
                    val navData = navJson.getJSONArray("data").getJSONObject(0)
                    
                    if (navData.has("total_value") && navData.getJSONObject("total_value").has("breakdowns")) {
                        insightsBuilder.append("🔄 Navigation Actions:\n")
                        
                        val totalValue = navData.getJSONObject("total_value")
                        val breakdowns = totalValue.getJSONArray("breakdowns")
                        
                        if (breakdowns.length() > 0) {
                            val breakdown = breakdowns.getJSONObject(0)
                            val results = breakdown.getJSONArray("results")
                            
                            for (i in 0 until results.length()) {
                                val result = results.getJSONObject(i)
                                val dimensionValues = result.getJSONArray("dimension_values")
                                val value = result.getInt("value")
                                
                                if (dimensionValues.length() > 0) {
                                    val actionType = dimensionValues.getString(0)
                                    val actionDisplay = when (actionType) {
                                        "tap_forward" -> "⏩ Forward Taps"
                                        "tap_back" -> "⏪ Back Taps"
                                        "tap_exit" -> "🚪 Exit Taps"
                                        "swipe_forward" -> "⏭️ Forward Swipes"
                                        else -> "🔄 ${actionType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}"
                                    }
                                    insightsBuilder.append("$actionDisplay: $value\n")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching story navigation breakdown", e)
        }
    }
    
    private suspend fun fetchProfileActivityBreakdown(mediaId: String, insightsBuilder: StringBuilder) {
        try {
            val profileActivityUrl = "https://graph.facebook.com/v22.0/$mediaId/insights?metric=profile_activity&breakdown=action_type&access_token=$graphToken"
            val profileConnection = URL(profileActivityUrl).openConnection() as HttpURLConnection
            val responseCode = profileConnection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val profileText = profileConnection.inputStream.bufferedReader().use { it.readText() }
                val profileJson = JSONObject(profileText)
                
                if (profileJson.has("data") && profileJson.getJSONArray("data").length() > 0) {
                    val profileData = profileJson.getJSONArray("data").getJSONObject(0)
                    
                    if (profileData.has("total_value") && profileData.getJSONObject("total_value").has("breakdowns")) {
                        insightsBuilder.append("👤 Profile Activity:\n")
                        
                        val totalValue = profileData.getJSONObject("total_value")
                        val breakdowns = totalValue.getJSONArray("breakdowns")
                        
                        if (breakdowns.length() > 0) {
                            val breakdown = breakdowns.getJSONObject(0)
                            val results = breakdown.getJSONArray("results")
                            
                            for (i in 0 until results.length()) {
                                val result = results.getJSONObject(i)
                                val dimensionValues = result.getJSONArray("dimension_values")
                                val value = result.getInt("value")
                                
                                if (dimensionValues.length() > 0) {
                                    val actionType = dimensionValues.getString(0)
                                    val actionDisplay = when (actionType) {
                                        "bio_link_clicked" -> "🔗 Bio Link Clicks"
                                        "call" -> "📞 Call Button Clicks"
                                        "email" -> "📧 Email Button Clicks"
                                        "text" -> "💬 Text Button Clicks"
                                        "direction" -> "🧭 Directions Button Clicks"
                                        else -> "🔄 ${actionType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}"
                                    }
                                    insightsBuilder.append("$actionDisplay: $value\n")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile activity breakdown", e)
        }
    }
    
    private fun processInsightsResponse(insightsText: String, insightsBuilder: StringBuilder, mediaType: String) {
        try {
            val insightsResponse = JSONObject(insightsText)
            
            if (insightsResponse.has("data") && insightsResponse.getJSONArray("data").length() > 0) {
                insightsBuilder.append("📈 Insights Metrics:\n")
                
                val insightsData = insightsResponse.getJSONArray("data")
                for (j in 0 until insightsData.length()) {
                    val insight = insightsData.getJSONObject(j)
                    val name = insight.optString("name", "")
                    val values = insight.optJSONArray("values")
                    
                    if (values != null && values.length() > 0) {
                        val valueObj = values.getJSONObject(0)
                        val value = if (valueObj.has("value")) valueObj.optInt("value", 0) else 0
                        
                        when (name) {
                            "comments" -> insightsBuilder.append("💬 Comments: $value\n")
                            "total_interactions" -> insightsBuilder.append("👥 Total Interactions: $value\n")
                            "impressions" -> insightsBuilder.append("👁️ Impressions: $value\n")
                            "reach" -> insightsBuilder.append("🔍 Reach: $value\n")
                            "saved" -> insightsBuilder.append("🔖 Saved: $value\n")
                            "shares" -> insightsBuilder.append("↗️ Shares: $value\n")
                            "likes" -> insightsBuilder.append("❤️ Likes (API): $value\n")
                            "views" -> insightsBuilder.append("👀 Views: $value\n")
                            "replies" -> insightsBuilder.append("↩️ Replies: $value\n")
                            "profile_visits" -> insightsBuilder.append("👤 Profile Visits: $value\n")
                            "follows" -> insightsBuilder.append("➕ Follows: $value\n")
                            "ig_reels_avg_watch_time" -> insightsBuilder.append("⏱️ Avg Watch Time: ${value}ms\n")
                            // Removed "plays" as it's deprecated according to documentation
                            else -> insightsBuilder.append("📊 ${name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}: $value\n")
                        }
                    }
                }
            } else {
                if (mediaType == "STORY") {
                    insightsBuilder.append("⚠️ No insights available. Story insights expire after 24 hours or require 5+ viewers\n")
                } else {
                    insightsBuilder.append("⚠️ No detailed insights available for this post\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing insights response", e)
            insightsBuilder.append("⚠️ Error processing insights data\n")
        }
    }
    
    private fun handleInsightsError(connection: HttpURLConnection, insightsBuilder: StringBuilder, mediaId: String, mediaType: String) {
        try {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            Log.e(TAG, "$mediaType insights error for $mediaId: $errorText")
            
            val errorMessage = try {
                val errorJson = JSONObject(errorText)
                if (errorJson.has("error")) {
                    val error = errorJson.getJSONObject("error")
                    val message = error.optString("message", "Unknown error")
                    val code = error.optInt("code", -1)
                    
                    // Special handling for common error codes
                    when (code) {
                        10 -> "$message (Not enough viewers to show insights)"
                        190 -> "Invalid access token"
                        4 -> "Quota exceeded or rate limited"
                        else -> "$message (Code: $code)"
                    }
                } else {
                    "API error"
                }
            } catch (e: Exception) {
                "API error"
            }
            
            insightsBuilder.append("⚠️ Cannot retrieve $mediaType insights: $errorMessage\n")
            
            if (mediaType == "STORY") {
                insightsBuilder.append("📝 Note: Story metrics are only available for 24 hours and require at least 5 viewers\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling insights error", e)
            insightsBuilder.append("⚠️ Error retrieving insights\n")
        }
    }
    
    private fun parseIsoDate(isoDate: String): Date {
        val possibleFormats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
        )
        
        for (format in possibleFormats) {
            try {
                val dateFormat = SimpleDateFormat(format, Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                return dateFormat.parse(isoDate) ?: continue
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        // If all formats fail, log and return current date
        Log.e(TAG, "Failed to parse date: $isoDate with any format")
        return Date()
    }
    
    private fun formatDate(date: Date): String {
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return format.format(date)
    }
    
    private fun fetchRecentMedia() {
        lifecycleScope.launch {
            try {
                // Directly construct the URL with the raw token
                val baseUrl = "https://graph.instagram.com/v22.0/me/media"
                val fields = "id,caption,media_type,media_url,permalink,thumbnail_url,timestamp,username"
                val urlStr = "$baseUrl?fields=$fields&access_token=$accessToken"
                
                val response = withContext(Dispatchers.IO) {
                    val url = URL(urlStr)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")
                    
                    val responseCode = connection.responseCode
                    Log.d(TAG, "Media API Response code: $responseCode")
                    
                    val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details available"
                    }
                    
                    // Log the raw API response
                    Log.d(TAG, "Media endpoint response: $responseText")
                    
                    responseText
                }
                
                // Parse the response
                val mediaResponse = JSONObject(response)
                
                // Check for error
                if (mediaResponse.has("error")) {
                    val error = mediaResponse.getJSONObject("error")
                    val message = error.optString("message", "Unknown error")
                    binding.textInstagramStats.append("\n\nError fetching media: $message")
                } else if (mediaResponse.has("data")) {
                    val mediaArray = mediaResponse.getJSONArray("data")
                    
                    if (mediaArray.length() > 0) {
                        val mediaBuilder = StringBuilder()
                        mediaBuilder.append("\n\nRecent Media:\n\n")
                        
                        for (i in 0 until mediaArray.length().coerceAtMost(5)) {
                            val media = mediaArray.getJSONObject(i)
                            mediaBuilder.append("Post #${i+1}:\n")
                            if (media.has("caption")) mediaBuilder.append("Caption: ${media.getString("caption")}\n")
                            if (media.has("media_type")) mediaBuilder.append("Type: ${media.getString("media_type")}\n")
                            if (media.has("timestamp")) {
                                try {
                                    val date = parseIsoDate(media.getString("timestamp"))
                                    mediaBuilder.append("Posted: ${formatDate(date)}\n")
                                } catch (e: Exception) {
                                    mediaBuilder.append("Posted: ${media.getString("timestamp")}\n")
                                }
                            }
                            mediaBuilder.append("\n")
                            
                            // Log each media item individually for detailed inspection
                            Log.d(TAG, "Media item #${i+1}: ${media}")
                        }
                        
                        binding.textInstagramStats.append(mediaBuilder.toString())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching media", e)
                binding.textInstagramStats.append("\n\nError fetching media: ${e.message}")
            }
        }
    }
    
    // Helper function to mask token for display
    private fun maskToken(token: String): String {
        return if (token.length > 8) {
            "${token.substring(0, 4)}...${token.substring(token.length - 4)}"
        } else {
            "***"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 