package com.example.allinone.feature.instagram.data.model

import com.google.gson.annotations.SerializedName

// Instagram Posts API Response (actual format from backend)
data class InstagramPostsApiResponse(
    val success: Boolean,
    val data: List<InstagramPost> = emptyList(),
    val count: Int,
    val source: String,
    val syncInfo: SyncInfo,
    val timestamp: Long
)

// Internal posts data for ViewModel
data class InstagramPostsData(
    val posts: List<InstagramPost>,
    val count: Int,
    val source: String,
    val syncInfo: SyncInfo,
    val timestamp: Long
)

// Sync information (actual API response format)
data class SyncInfo(
    val triggered: Boolean,
    val success: Boolean? = null,
    val error: String? = null,
    val reason: String,
    val previousCount: Int? = null,
    val currentCount: Int? = null,
    val newPosts: Int? = null,
    val processingTime: Long? = null
)

// Instagram Post (standardized format)
data class InstagramPost(
    val id: String,
    val shortcode: String,
    val caption: String,
    val mediaType: String, // "IMAGE", "VIDEO", "CAROUSEL_ALBUM", "REELS"
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val permalink: String,
    val timestamp: String,
    val formattedDate: String? = null,
    val username: String? = null,
    val metrics: InstagramMetrics,
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList()
)

// Instagram Metrics
data class InstagramMetrics(
    val likesCount: Int,
    val commentsCount: Int,
    val sharesCount: Int? = null,
    val savesCount: Int? = null,
    val reachCount: Int? = null,
    val impressionsCount: Int? = null,
    val videoViewsCount: Int? = null,
    val engagementRate: Double,
    val totalInteractions: Int? = null
)

// Account Information
data class InstagramAccount(
    val id: String,
    val username: String,
    val name: String? = null,
    val biography: String? = null,
    val website: String? = null,
    val profilePictureUrl: String? = null,
    val followersCount: Int,
    val followsCount: Int,
    val mediaCount: Int,
    val accountType: String
)

// Analytics Response (from GET /api/instagram/analytics)
data class InstagramAnalytics(
    val account: InstagramAccount,
    val posts: List<InstagramPost>,
    val summary: AnalyticsSummary
)

data class AnalyticsSummary(
    val totalPosts: Int,
    val totalEngagement: Int,
    val avgEngagementRate: Double,
    val topPerformingPost: TopPerformingPost? = null,
    val recentGrowth: RecentGrowth? = null,
    val detailedMetrics: DetailedMetrics? = null
)

data class TopPerformingPost(
    val id: String,
    val caption: String,
    val metrics: PostMetricsSummary
)

data class PostMetricsSummary(
    val engagementRate: Double,
    val totalInteractions: Int
)

data class RecentGrowth(
    val engagement: Double,
    val reach: Double
)

data class DetailedMetrics(
    val totals: TotalMetrics,
    val averages: AverageMetrics,
    val topPerformers: TopPerformers? = null,
    val contentAnalysis: ContentAnalysis? = null,
    val engagementQuality: EngagementQuality? = null,
    val trends: Trends? = null,
    val performance: PerformanceMetrics? = null
)

data class TotalMetrics(
    val totalPosts: Int,
    val totalLikes: Int,
    val totalComments: Int,
    val totalShares: Int,
    val totalSaves: Int,
    val totalReach: Int,
    val totalVideoViews: Int,
    val totalEngagement: Int,
    val totalWatchTime: Long
)

data class AverageMetrics(
    val avgLikes: Int,
    val avgComments: Int,
    val avgShares: Int,
    val avgSaves: Int,
    val avgReach: Int,
    val avgVideoViews: Int,
    val avgEngagement: Int,
    val avgEngagementRate: Double,
    val avgWatchTime: Int
)

data class TopPerformers(
    val topByEngagement: InstagramPost? = null,
    val topByLikes: InstagramPost? = null,
    val topByComments: InstagramPost? = null,
    val topByReach: InstagramPost? = null,
    val topByShares: InstagramPost? = null,
    val topBySaves: InstagramPost? = null
)

data class ContentAnalysis(
    val mediaTypeBreakdown: MediaTypeBreakdown? = null,
    val postingFrequency: PostingFrequency? = null,
    val hashtagAnalysis: HashtagAnalysis? = null
)

data class MediaTypeBreakdown(
    val videos: MediaTypeStats? = null,
    val images: MediaTypeStats? = null
)

data class MediaTypeStats(
    val count: Int,
    val percentage: Double,
    val avgEngagementRate: Double
)

data class PostingFrequency(
    val avgDaysBetweenPosts: Double,
    val postsPerWeek: Double,
    val postsPerMonth: Double
)

data class HashtagAnalysis(
    val totalUniqueHashtags: Int,
    val avgHashtagsPerPost: Double,
    val topPerformingHashtags: List<String> = emptyList()
)

data class EngagementQuality(
    val commentsToLikesRatio: Double,
    val savesToReachRatio: Double,
    val sharesToReachRatio: Double,
    val engagementScore: Double,
    val viralityScore: Double
)

data class Trends(
    val recentEngagementTrend: Double,
    val recentReachTrend: Double,
    val trendDirection: String
)

data class PerformanceMetrics(
    val highPerformingPosts: Int,
    val lowPerformingPosts: Int,
    val consistencyScore: Double,
    val growthPotential: Double
)

// Health Check
data class HealthStatus(
    val instagram: Boolean,
    val firestore: Boolean,
    val rag: Boolean,
    val cache: Boolean,
    val overall: Boolean
)

// Metrics sync responses
data class MetricsSyncResponse(
    val updatedPosts: Int,
    val totalPosts: Int,
    val lastSync: String,
    val processingTime: Long
)

data class MetricsUpdateResponse(
    val updatedPosts: List<String>,
    val failedPosts: List<String>,
    val totalRequested: Int,
    val totalUpdated: Int
)

// RAG Query Models
data class RAGQueryRequest(
    val query: String,
    val context: String = "instagram"
)

data class RAGQueryResponse(
    val answer: String,
    val confidence: Double,
    val sources: List<String>,
    val processingTime: Long
)

// Chat Message for AI functionality
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val confidence: Double? = null,
    val sources: List<String>? = null,
    val isTyping: Boolean = false,
    val isError: Boolean = false
)

// Raw Instagram Data (for backward compatibility)
data class RawInstagramData(
    val posts: List<InstagramPost>,
    val account: InstagramAccount? = null,
    val exportedAt: String,
    val totalPosts: Int
)

// API Response wrapper
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Long,
    val processingTime: Long? = null
)

// Result handling
sealed class InstagramResult<T> {
    data class Success<T>(val data: T) : InstagramResult<T>()
    data class Error<T>(val message: String, val cause: Throwable? = null) : InstagramResult<T>()
    data class Loading<T>(val message: String = "Loading...") : InstagramResult<T>()
}

// Firestore compatibility models (for transition period)
data class FirestorePost(
    val id: String = "",
    val caption: String = "",
    val mediaType: String = "",
    val timestamp: String = "",
    val formattedDate: String = "",
    val permalink: String = "",
    val metrics: Map<String, Any> = emptyMap()
) 