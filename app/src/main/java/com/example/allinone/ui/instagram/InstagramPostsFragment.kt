package com.example.allinone.ui.instagram

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.BuildConfig
import com.example.allinone.R
import com.example.allinone.databinding.FragmentInstagramPostsBinding
import com.example.allinone.databinding.ItemInstagramPostBinding
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.gson.Gson
import java.util.UUID
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import androidx.core.content.res.ResourcesCompat
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import java.util.regex.Pattern
import android.widget.TextView
import android.app.Dialog
import android.view.Window
import android.widget.Button
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager

class InstagramPostsFragment : Fragment() {
    
    private var _binding: FragmentInstagramPostsBinding? = null
    private val binding get() = _binding!!
    
    // Instagram API constants - using the correct environment variable keys
    private val accessToken = BuildConfig.INSTAGRAM_ACCESS_TOKEN
    private val graphToken = BuildConfig.INSTAGRAM_GRAPH_TOKEN // Facebook Graph API token for insights
    private val businessAccountId = BuildConfig.INSTAGRAM_BUSINESS_ACCOUNT_ID
    private var userId: String? = null
    
    // Firebase Firestore reference
    private val db = FirebaseFirestore.getInstance()
    private val instagramCollection = db.collection("instagram_business")
    
    // Post adapter for RecyclerView
    private lateinit var postAdapter: InstagramPostAdapter
    private val postsList = ArrayList<InstagramPost>()
    
    companion object {
        private const val TAG = "InstagramPosts"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstagramPostsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Debug log the token details for troubleshooting
        Log.d(TAG, "Instagram token length: ${accessToken.length}")
        Log.d(TAG, "Graph token length: ${graphToken.length}")
        Log.d(TAG, "Business Account ID: $businessAccountId")
        
        // Set up the RecyclerView
        setupRecyclerView()
        
        // Set up refresh button
        binding.btnRefresh.setOnClickListener {
            refreshData()
        }
        
        if (graphToken.isNotEmpty() && graphToken != "NOT_SET") {
            // Hide both buttons as we'll fetch data automatically
            binding.btnFetchProfileData.visibility = View.GONE
            binding.btnFetchInsights.visibility = View.GONE
            
            // No need for login button since we're using the existing token
            binding.btnInstagramLogin.visibility = View.GONE
            
            // Show refresh button
            binding.btnRefresh.visibility = View.VISIBLE
            
            // First try to load from Firebase, then show data
            loadFromFirebase()
        } else {
            binding.textInstagramBusiness.text = "Instagram API Token not found"
            binding.textInstagramBusiness.visibility = View.VISIBLE
            Log.e(TAG, "Instagram token not configured in .env file")
            Toast.makeText(
                context,
                "Please add a valid INSTAGRAM_GRAPH_TOKEN to your .env file",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun setupRecyclerView() {
        postAdapter = InstagramPostAdapter(postsList) { post ->
            // Show detailed view when post is clicked
            showPostDetails(post)
        }
        
        binding.recyclerInstagramPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
        }
    }
    
    private fun showPostDetails(post: InstagramPost) {
        context?.let { ctx ->
            // Create dialog using the custom layout
            val dialog = Dialog(ctx)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_post_details)
            
            // Get references to views in the dialog
            val captionView = dialog.findViewById<TextView>(R.id.postCaption)
            val dateView = dialog.findViewById<TextView>(R.id.postDate)
            val typeView = dialog.findViewById<TextView>(R.id.postType)
            val linkView = dialog.findViewById<TextView>(R.id.postLink)
            val insightsView = dialog.findViewById<TextView>(R.id.postInsights)
            val closeButton = dialog.findViewById<Button>(R.id.closeButton)
            
            // Set data to views
            captionView.text = post.caption
            dateView.text = post.formattedDate
            typeView.text = post.mediaType
            linkView.text = "Link: ${post.permalink}"
            
            // Build insights text
            val insights = StringBuilder()
            post.metrics.forEach { (key, value) ->
                // Skip metrics with empty or zero values
                if (value.toString().isNotEmpty() && value.toString() != "0") {
                    // Format the key name
                    val formattedKey = when (key) {
                        "ig_reels_avg_watch_time" -> "Average Watch Time"
                        else -> key.replace("_", " ").split(" ").joinToString(" ") { 
                            it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } 
                        }
                    }
                    
                    // Format the value for special cases
                    val formattedValue = when (key) {
                        "ig_reels_avg_watch_time" -> formatWatchTime(value)
                        else -> value.toString()
                    }
                    
                    // Add appropriate emoji based on metric type
                    val emoji = when (key) {
                        "reach" -> "👥"
                        "impressions" -> "👁️"
                        "likes" -> "❤️"
                        "comments" -> "💬"
                        "views" -> "🎬"
                        "video_plays" -> "▶️"
                        "saved" -> "🔖"
                        "shares" -> "🔄"
                        "engagement" -> "✨"
                        "total_interactions" -> "📊"
                        "ig_reels_avg_watch_time" -> "⏱️"
                        else -> "📊"
                    }
                    
                    insights.append("$emoji $formattedKey: $formattedValue\n")
                }
            }
            
            // Apply bold formatting to insights
            insightsView.text = formatMetricsWithBoldTitles(insights.toString())
            
            // Set close button click listener
            closeButton.setOnClickListener {
                dialog.dismiss()
            }
            
            // Set dialog size
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // Show dialog
            dialog.show()
        }
    }
    
    // Helper function to format metrics text with bold titles
    private fun formatMetricsWithBoldTitles(metricsText: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        val lines = metricsText.split("\n")
        
        for (line in lines) {
            if (line.isEmpty()) continue
            
            val colonIndex = line.indexOf(":")
            if (colonIndex > 0) {
                val title = line.substring(0, colonIndex + 1)
                val value = line.substring(colonIndex + 1)
                
                // Add title with bold style
                ssb.append(title, StyleSpan(Typeface.BOLD), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                // Add color to the bold title based on theme
                if (context != null) {
                    ssb.setSpan(
                        ForegroundColorSpan(ResourcesCompat.getColor(resources, R.color.boldTextColor, null)),
                        ssb.length - title.length,
                        ssb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                // Add value with normal style
                ssb.append(value)
                ssb.append("\n")
            } else {
                ssb.append(line)
                ssb.append("\n")
            }
        }
        
        return ssb
    }
    
    private fun openInstagramLink(permalink: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(permalink))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Instagram link", e)
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun buildDetailedPostInfo(post: InstagramPost): String {
        val sb = StringBuilder()
        sb.append("Caption: ${post.caption}\n\n")
        sb.append("Posted on: ${post.formattedDate}\n")
        sb.append("Media Type: ${post.mediaType}\n")
        sb.append("Link: ${post.permalink}\n\n")
        sb.append("📊 INSIGHTS:\n")
        
        // Show key metrics with improved emoji icons
        if (post.metrics.containsKey("reach")) {
            sb.append("👥 Reach: ${post.metrics["reach"]}\n")
        }
        if (post.metrics.containsKey("impressions")) {
            sb.append("👁️ Impressions: ${post.metrics["impressions"]}\n")
        }
        if (post.metrics.containsKey("likes")) {
            sb.append("❤️ Likes: ${post.metrics["likes"]}\n")
        }
        if (post.metrics.containsKey("comments")) {
            sb.append("💬 Comments: ${post.metrics["comments"]}\n")
        }
        if (post.metrics.containsKey("views")) {
            sb.append("🎬 Views: ${post.metrics["views"]}\n")
        }
        if (post.metrics.containsKey("saved")) {
            sb.append("🔖 Saved: ${post.metrics["saved"]}\n")
        }
        if (post.metrics.containsKey("shares")) {
            sb.append("🔄 Shares: ${post.metrics["shares"]}\n")
        }
        if (post.metrics.containsKey("video_plays")) {
            sb.append("▶️ Plays: ${post.metrics["video_plays"]}\n")
        }
        if (post.metrics.containsKey("engagement")) {
            sb.append("✨ Engagement: ${post.metrics["engagement"]}\n")
        }
        
        // Add any other metrics that weren't specifically handled above
        post.metrics.forEach { (key, value) ->
            // Skip metrics we've already added
            if (!key.matches(Regex("reach|impressions|likes|comments|views|saved|shares|video_plays|engagement"))) {
                val formattedKey = key.replace("_", " ").split(" ").joinToString(" ") { 
                    it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } 
                }
                sb.append("$formattedKey: $value\n")
            }
        }
        
        return sb.toString()
    }
    
    private fun refreshData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                fetchInsightsData(true)
                Toast.makeText(context, "Data refreshed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing data", e)
                Toast.makeText(context, "Error refreshing: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun loadFromFirebase() {
        binding.progressBar.visibility = View.VISIBLE
        postsList.clear()
        
        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    instagramCollection
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get()
                        .await()
                }
                
                if (snapshot.isEmpty) {
                    // No data in Firebase, fetch from API
                    Log.d(TAG, "No data in Firebase, fetching from API")
                    fetchInsightsData(false)
                } else {
                    // Convert Firestore documents to InstagramPost objects
                    for (document in snapshot.documents) {
                        try {
                            val post = document.toObject(InstagramPost::class.java)
                            post?.let { postsList.add(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document", e)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (postsList.isEmpty()) {
                            binding.emptyStateView.visibility = View.VISIBLE
                        } else {
                            binding.emptyStateView.visibility = View.GONE
                        }
                        
                        postAdapter.notifyDataSetChanged()
                        binding.progressBar.visibility = View.GONE
                        binding.textInstagramStats.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading from Firebase", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Try fetching from API if Firebase fails
                    fetchInsightsData(false)
                }
            }
        }
    }
    
    private suspend fun fetchInsightsData(isRefresh: Boolean = false) {
        try {
            // Show loading indicator
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.VISIBLE
                if (isRefresh) {
                    // Clear existing data if refreshing
                    postsList.clear()
                    postAdapter.notifyDataSetChanged()
                }
            }
            
            // Fetch media insights - this will be a list of InstagramPost objects
            val posts = fetchMediaInsights()
            
            if (posts.isNotEmpty()) {
                // Save to Firebase
                saveToFirebase(posts)
                
                // Update UI
                withContext(Dispatchers.Main) {
                    postsList.clear()
                    postsList.addAll(posts)
                    postAdapter.notifyDataSetChanged()
                    binding.progressBar.visibility = View.GONE
                    binding.textInstagramStats.visibility = View.GONE
                    binding.emptyStateView.visibility = View.GONE
                    binding.recyclerInstagramPosts.visibility = View.VISIBLE
                }
            } else {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyStateView.visibility = View.VISIBLE
                    binding.emptyStateView.text = "No posts found"
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching insights", e)
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.emptyStateView.visibility = View.VISIBLE
                binding.emptyStateView.text = "Error: ${e.message}"
            }
        }
    }
    
    private suspend fun saveToFirebase(posts: List<InstagramPost>) {
        withContext(Dispatchers.IO) {
            try {
                // Debug log to check posts before saving
                for (post in posts) {
                    if (post.metrics.containsKey("ig_reels_avg_watch_time")) {
                        val watchTime = post.metrics["ig_reels_avg_watch_time"]
                        Log.d(TAG, "Preparing to save to Firebase - Post ${post.id} with watch time: $watchTime")
                    }
                }
                
                // Delete previous data
                val batch = db.batch()
                val existingDocs = instagramCollection.get().await()
                
                for (document in existingDocs) {
                    batch.delete(document.reference)
                }
                
                // Execute the batch delete
                batch.commit().await()
                
                // Add new data - ensure all metrics are serialized properly
                for (post in posts) {
                    // Convert post to a map to ensure all fields are properly serialized
                    val postMap = hashMapOf(
                        "id" to post.id,
                        "caption" to post.caption,
                        "mediaType" to post.mediaType,
                        "timestamp" to post.timestamp,
                        "formattedDate" to post.formattedDate,
                        "permalink" to post.permalink,
                        "metrics" to post.metrics
                    )
                    
                    instagramCollection.document(post.id).set(postMap).await()
                }
                
                Log.d(TAG, "Saved ${posts.size} posts to Firebase")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving to Firebase", e)
            }
        }
    }
    
    private suspend fun fetchMediaInsights(): List<InstagramPost> {
        return withContext(Dispatchers.IO) {
            try {
                val posts = ArrayList<InstagramPost>()
                var nextPageUrl: String? = "https://graph.facebook.com/v22.0/$businessAccountId/media?fields=id,media_type,media_product_type,permalink,caption,timestamp&limit=25&access_token=$graphToken"
                
                // Loop to fetch all pages
                while (nextPageUrl != null) {
                    try {
                        // Fetch current page of media
                        val mediaConnection = URL(nextPageUrl).openConnection() as HttpURLConnection
                        mediaConnection.connectTimeout = 10000 // 10 seconds timeout
                        mediaConnection.readTimeout = 15000 // 15 seconds read timeout
                        
                        Log.d(TAG, "Fetching media page from URL: ${nextPageUrl.substringBefore("access_token")}access_token=HIDDEN")
                        
                        val responseCode = mediaConnection.responseCode
                        Log.d(TAG, "API Response code: $responseCode")
                        
                        val mediaResponse = if (responseCode == HttpURLConnection.HTTP_OK) {
                            mediaConnection.inputStream.bufferedReader().use { it.readText() }
                        } else {
                            // Handle errors
                            val errorText = mediaConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details available"
                            Log.e(TAG, "API Error Response: $errorText")
                            throw Exception("API Error: $responseCode - $errorText")
                        }
                        
                        val mediaJson = JSONObject(mediaResponse)
                        
                        // Process the media items on this page
                        if (mediaJson.has("data")) {
                            val mediaArray = mediaJson.getJSONArray("data")
                            
                            // Process all posts on this page
                            for (i in 0 until mediaArray.length()) {
                                val media = mediaArray.getJSONObject(i)
                                val mediaId = media.getString("id")
                                val mediaType = media.optString("media_type", "UNKNOWN")
                                val mediaProductType = media.optString("media_product_type", "")
                                val caption = media.optString("caption", "No caption")
                                val timestamp = media.optString("timestamp", "")
                                val permalink = media.optString("permalink", "")
                                
                                // Determine media product type for display
                                val displayMediaType = when {
                                    mediaProductType == "REELS" -> "REELS"
                                    mediaProductType == "STORY" -> "STORY"
                                    mediaType == "CAROUSEL_ALBUM" -> "ALBUM"
                                    mediaType == "VIDEO" -> "VIDEO"
                                    else -> "FEED"
                                }
                                
                                // Create a post object with basic info
                                val post = InstagramPost(
                                    id = mediaId,
                                    caption = caption,
                                    mediaType = displayMediaType,
                                    timestamp = timestamp,
                                    formattedDate = formatDate(parseIsoDate(timestamp)),
                                    permalink = permalink,
                                    metrics = HashMap()
                                )
                                
                                // Fetch insights based on media type
                                when (displayMediaType) {
                                    "ALBUM" -> {
                                        // Albums don't have insights, just add placeholder
                                        post.metrics["note"] = "Instagram does not provide insights for albums"
                                    }
                                    "REELS" -> {
                                        fetchInsightsForPost(mediaId, post, "REELS")
                                    }
                                    "STORY" -> {
                                        fetchInsightsForPost(mediaId, post, "STORY")
                                    }
                                    else -> {
                                        fetchInsightsForPost(mediaId, post, "FEED")
                                    }
                                }
                                
                                // Add to our list
                                posts.add(post)
                            }
                            
                            // Check if there's another page of results
                            nextPageUrl = if (mediaJson.has("paging") && mediaJson.getJSONObject("paging").has("next")) {
                                mediaJson.getJSONObject("paging").getString("next")
                            } else {
                                null // No more pages
                            }
                        } else {
                            nextPageUrl = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching media page", e)
                        nextPageUrl = null // Stop pagination on error
                    }
                }
                
                posts
            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchMediaInsights", e)
                throw e
            }
        }
    }
    
    private suspend fun fetchInsightsForPost(mediaId: String, post: InstagramPost, mediaType: String) {
        try {
            // Select metrics based on media type
            val metrics = when (mediaType) {
                "REELS" -> "comments,likes,reach,saved,shares,total_interactions,views,ig_reels_avg_watch_time"
                "STORY" -> "impressions,reach,replies"
                else -> "comments,likes,reach,saved,shares,total_interactions,views"
            }
            
            val insightsUrl = "https://graph.facebook.com/v22.0/$mediaId/insights?metric=$metrics&access_token=$graphToken"
            
            val insightsConnection = URL(insightsUrl).openConnection() as HttpURLConnection
            val responseCode = insightsConnection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val insightsText = insightsConnection.inputStream.bufferedReader().use { it.readText() }
                processInsightsData(insightsText, post)
                
                // Get additional breakdowns if available
                if (mediaType == "FEED") {
                    fetchProfileActivityBreakdown(mediaId, post)
                } else if (mediaType == "STORY") {
                    fetchStoryNavigationBreakdown(mediaId, post)
                }
            } else {
                // Handle error
                val errorText = insightsConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                Log.e(TAG, "Insights error for $mediaId: $errorText")
                post.metrics["error"] = "Unable to retrieve insights"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching insights for $mediaId", e)
            post.metrics["error"] = e.message ?: "Unknown error"
        }
    }
    
    private fun processInsightsData(insightsText: String, post: InstagramPost) {
        try {
            val insightsResponse = JSONObject(insightsText)
            
            if (insightsResponse.has("data") && insightsResponse.getJSONArray("data").length() > 0) {
                val insightsData = insightsResponse.getJSONArray("data")
                
                for (j in 0 until insightsData.length()) {
                    val insight = insightsData.getJSONObject(j)
                    val name = insight.optString("name", "")
                    val values = insight.optJSONArray("values")
                    
                    if (values != null && values.length() > 0) {
                        val valueObj = values.getJSONObject(0)
                        val value = if (valueObj.has("value")) {
                            // Store the value as the appropriate type, especially for average watch time
                            when (name) {
                                "ig_reels_avg_watch_time" -> valueObj.opt("value") ?: 0
                                else -> valueObj.optInt("value", 0)
                            }
                        } else {
                            0
                        }
                        
                        // Ensure value is properly saved to metrics map
                        post.metrics[name] = value
                        
                        // Debug log to verify values
                        if (name == "ig_reels_avg_watch_time") {
                            Log.d(TAG, "Saving avg watch time: $value for post: ${post.id}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing insights response", e)
        }
    }
    
    private suspend fun fetchStoryNavigationBreakdown(mediaId: String, post: InstagramPost) {
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
                                    post.metrics["navigation_$actionType"] = value
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
    
    private suspend fun fetchProfileActivityBreakdown(mediaId: String, post: InstagramPost) {
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
                                    post.metrics["profile_$actionType"] = value
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
    
    private fun formatWatchTime(seconds: Any): String {
        try {
            val totalSeconds = when(seconds) {
                is Int -> seconds
                is String -> seconds.toIntOrNull() ?: 0
                else -> 0
            }
            
            // Calculate hours, minutes, and seconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val secs = totalSeconds % 60
            
            // Format as Xh Ym Zs
            val formattedTime = StringBuilder()
            if (hours > 0) formattedTime.append("${hours}h ")
            if (minutes > 0) formattedTime.append("${minutes}m ")
            if (secs > 0 || formattedTime.isEmpty()) formattedTime.append("${secs}s")
            
            return formattedTime.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting watch time", e)
            return seconds.toString()
        }
    }
    
    // Instagram Post adapter for RecyclerView
    inner class InstagramPostAdapter(
        private val posts: List<InstagramPost>,
        private val onItemClick: (InstagramPost) -> Unit
    ) : RecyclerView.Adapter<InstagramPostAdapter.PostViewHolder>() {
        
        inner class PostViewHolder(private val binding: ItemInstagramPostBinding) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(post: InstagramPost) {
                binding.postCaption.text = post.caption.take(100) + if (post.caption.length > 100) "..." else ""
                binding.postDate.text = post.formattedDate
                binding.postType.text = "Type: ${post.mediaType}"
                
                // Set insights summary with bold titles and improved emoji icons
                val insights = StringBuilder()
                
                // Show key metrics if available with improved emoji icons
                if (post.metrics.containsKey("reach")) {
                    insights.append("👥 Reach: ${post.metrics["reach"]}\n")
                }
                if (post.metrics.containsKey("impressions")) {
                    insights.append("👁️ Impressions: ${post.metrics["impressions"]}\n")
                }
                if (post.metrics.containsKey("likes")) {
                    insights.append("❤️ Likes: ${post.metrics["likes"]}\n")
                }
                if (post.metrics.containsKey("comments")) {
                    insights.append("💬 Comments: ${post.metrics["comments"]}\n")
                }
                if (post.metrics.containsKey("views")) {
                    insights.append("🎬 Views: ${post.metrics["views"]}\n")
                }
                if (post.metrics.containsKey("saved")) {
                    insights.append("🔖 Saved: ${post.metrics["saved"]}\n")
                }
                if (post.metrics.containsKey("shares")) {
                    insights.append("🔄 Shares: ${post.metrics["shares"]}\n")
                }
                if (post.metrics.containsKey("video_plays")) {
                    insights.append("▶️ Plays: ${post.metrics["video_plays"]}\n")
                }
                if (post.metrics.containsKey("total_interactions")) {
                    insights.append("📊 Total Interactions: ${post.metrics["total_interactions"]}\n")
                }
                if (post.metrics.containsKey("ig_reels_avg_watch_time")) {
                    val watchTime = formatWatchTime(post.metrics["ig_reels_avg_watch_time"]!!)
                    insights.append("⏱️ Average Watch Time: $watchTime\n")
                }
                
                // Apply bold formatting to metric titles
                binding.postInsights.text = formatMetricsWithBoldTitles(insights.toString())
                
                // Set background color to white for all cards
                binding.root.setCardBackgroundColor(binding.root.context.getColor(R.color.white))
                
                // Set click listener
                binding.root.setOnClickListener {
                    onItemClick(post)
                }
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
            val binding = ItemInstagramPostBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return PostViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            holder.bind(posts[position])
        }
        
        override fun getItemCount() = posts.size
    }
    
    // Data class for Instagram Post
    data class InstagramPost(
        val id: String = "",
        val caption: String = "",
        val mediaType: String = "",
        val timestamp: String = "",
        val formattedDate: String = "",
        val permalink: String = "",
        val metrics: HashMap<String, Any> = HashMap()
    )
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 