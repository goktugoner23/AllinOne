package com.example.allinone.feature.instagram.data.model

import com.google.gson.annotations.SerializedName

// Instagram Posts API Response (actual format from backend)
data class InstagramPostsApiResponse(
    val success: Boolean,
    val data: List<InstagramPost> = emptyList(),
    val count: Int,
    val source: String,
    val syncInfo: SyncInfo,
    val timestamp: String
)

// Internal posts data for ViewModel
data class InstagramPostsData(
    val posts: List<InstagramPost>,
    val count: Int,
    val source: String,
    val syncInfo: SyncInfo,
    val timestamp: String
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
    val processingTime: String? = null
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
    val totalWatchTime: String
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
    val avgWatchTime: String
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
    val topPerformingHashtags: List<HashtagPerformance> = emptyList()
)

// New data class for hashtag performance
data class HashtagPerformance(
    val hashtag: String,
    val count: Int,
    val avgEngagementRate: Double? = null,
    val totalPosts: Int? = null
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
    val processingTime: String
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
    val domain: String = "instagram",
    val options: QueryOptions? = null
)

data class QueryOptions(
    val topK: Int = 5,
    val minScore: Double = 0.7
)

data class RAGQueryResponse(
    val answer: String,
    val sources: List<AISource>,
    val confidence: Double,
    val processingTime: Long,
    val metadata: AIMetadata
)

data class AISource(
    val id: String,
    val score: Double,
    val content: String,
    val metadata: AISourceMetadata
)

data class AISourceMetadata(
    val postId: String? = null,
    val likesCount: Int? = null,
    val commentsCount: Int? = null,
    val engagementRate: Double? = null,
    val mediaType: String? = null,
    val timestamp: String? = null,
    val hashtags: List<String>? = null
)

data class AIMetadata(
    val originalQuery: String,
    val totalMatches: Int
)

// Chat functionality (Ask AI tab) - ENHANCED FOR MULTIMODAL
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val sources: List<AISource> = emptyList(),
    val confidence: Double? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    // ✅ NEW: Multimodal support
    val contentType: ContentType = ContentType.TEXT,
    val attachments: List<MessageAttachment> = emptyList(),
    val isTyping: Boolean = false,
    val processingTime: Long? = null
)

// Content types supported by the multimodal AI
enum class ContentType {
    TEXT,       // Regular text messages
    IMAGE,      // Image uploads
    AUDIO,      // Audio recordings or uploads
    PDF,        // PDF document uploads
    URL,        // URL analysis
    MULTIMODAL  // Mixed content (text + attachments)
}

// Message attachments for multimodal content
data class MessageAttachment(
    val id: String,
    val type: AttachmentType,
    val uri: String,                    // Local file URI or remote URL
    val fileName: String? = null,       // Original file name
    val fileSize: Long? = null,         // File size in bytes
    val mimeType: String? = null,       // MIME type
    val duration: Long? = null,         // For audio/video (milliseconds)
    val thumbnailUri: String? = null,   // Thumbnail for images/videos
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val uploadProgress: Int = 0,        // 0-100
    val analysisQuery: String? = null   // Custom analysis query for this attachment
)

// Attachment types matching MULTIMODAL_GUIDE.md
enum class AttachmentType {
    IMAGE,          // Instagram screenshots, competitor analysis, brand logos
    AUDIO,          // Reel audio, voice memos, podcast clips  
    PDF,            // Analytics reports, strategy documents, research papers
    VIDEO,          // Video content for analysis
    VOICE_RECORDING // Live audio recording from device
}

// Upload status tracking
enum class UploadStatus {
    PENDING,        // Not yet uploaded
    UPLOADING,      // Currently uploading
    PROCESSING,     // Being processed by AI
    COMPLETED,      // Successfully processed
    FAILED,         // Upload or processing failed
    CANCELLED       // User cancelled
}

// Multimodal analysis request (matches API_REFERENCE.md)
data class MultimodalAnalysisRequest(
    val query: String,
    val contentType: String,           // 'image', 'audio', 'pdf', 'text'
    val domain: String = "instagram",
    val content: String? = null,       // File content or URL
    val options: QueryOptions? = null
)

// File upload request for external API
data class FileUploadRequest(
    val file: ByteArray,
    val fileName: String,
    val contentType: String,
    val analysisQuery: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileUploadRequest
        if (!file.contentEquals(other.file)) return false
        if (fileName != other.fileName) return false
        if (contentType != other.contentType) return false
        if (analysisQuery != other.analysisQuery) return false
        return true
    }

    override fun hashCode(): Int {
        var result = file.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + (analysisQuery?.hashCode() ?: 0)
        return result
    }
}

// Audio recording state
data class AudioRecordingState(
    val isRecording: Boolean = false,
    val duration: Long = 0,          // Current recording duration in milliseconds
    val amplitude: Float = 0f,       // Current audio amplitude for visualization
    val filePath: String? = null,    // Path to recorded file
    val error: String? = null        // Recording error if any
)

// Suggested actions for multimodal content
data class MultimodalSuggestion(
    val title: String,
    val description: String,
    val contentType: ContentType,
    val icon: String,                // Emoji or icon resource
    val exampleQuery: String,
    val acceptedFileTypes: List<String> = emptyList()  // MIME types
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
    val timestamp: String,
    val processingTime: String? = null
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