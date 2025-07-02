package com.example.allinone.feature.instagram.domain.usecase

import com.example.allinone.feature.instagram.data.model.*
import com.example.allinone.feature.instagram.domain.repository.InstagramRepository
import javax.inject.Inject

/**
 * Get Instagram posts with smart caching - main use case for Posts tab
 */
class GetInstagramPostsUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(forceSync: Boolean = false): InstagramResult<InstagramPostsData> {
        return repository.getInstagramPosts(forceSync)
    }
}

/**
 * Get Instagram analytics - main use case for Insights tab
 */
class GetInstagramAnalyticsUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(): InstagramResult<InstagramAnalytics> {
        return repository.getAnalytics()
    }
}

/**
 * Query Instagram AI for insights - used for AI chat functionality
 */
class QueryInstagramAIUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(
        query: String, 
        domain: String = "instagram",
        topK: Int = 15, // Increased default for better coverage
        minScore: Double = 0.3 // Lowered default for more matches
    ): InstagramResult<RAGQueryResponse> {
        // Enhanced query options for better RAG results
        val options = QueryOptions(
            topK = topK,
            minScore = minScore
        )
        
        val request = RAGQueryRequest(
            query = query,
            domain = domain,
            options = options
        )
        
        return repository.queryRAG(request)
    }
}

/**
 * Check Instagram service health - optional monitoring
 */
class CheckInstagramHealthUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(): InstagramResult<HealthStatus> {
        return repository.checkHealth()
    }
}

/**
 * Use case for analyzing multimodal content (images, audio, PDFs, URLs)
 * Based on MULTIMODAL_GUIDE.md specifications
 */
class AnalyzeMultimodalContentUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(
        contentType: String,         // 'image', 'audio', 'pdf', 'text'
        content: String,             // File path, URL, or text content
        analysisQuery: String,       // User's analysis question
        domain: String = "instagram"
    ): InstagramResult<RAGQueryResponse> {
        return try {
            val query = buildAnalysisQuery(contentType, content, analysisQuery)
            val request = RAGQueryRequest(
                query = query,
                domain = domain,
                options = QueryOptions(
                    topK = 5,
                    minScore = 0.7
                )
            )
            repository.queryRAG(request)
        } catch (e: Exception) {
            InstagramResult.Error("Failed to analyze $contentType content: ${e.message}", e)
        }
    }
    
    private fun buildAnalysisQuery(contentType: String, content: String, analysisQuery: String): String {
        return when (contentType) {
            "image" -> "Analyze this Instagram-related image and provide strategic insights: $content. Question: $analysisQuery"
            "audio" -> "Analyze this audio content for Instagram strategy insights: $content. Question: $analysisQuery"
            "pdf" -> "Analyze this PDF document for Instagram marketing insights: $content. Question: $analysisQuery"
            "url" -> "Analyze this URL for Instagram strategy lessons: $content. Question: $analysisQuery"
            else -> "Provide Instagram strategy insights for: $analysisQuery. Content: $content"
        }
    }
}

/**
 * Use case for uploading and analyzing files
 */
class UploadFileForAnalysisUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(
        fileUri: String,
        fileName: String,
        mimeType: String,
        analysisQuery: String
    ): InstagramResult<RAGQueryResponse> {
        return try {
            // Determine content type from MIME type
            val contentType = when {
                mimeType.startsWith("image/") -> "image"
                mimeType.startsWith("audio/") -> "audio"
                mimeType == "application/pdf" -> "pdf"
                mimeType.startsWith("video/") -> "image" // Treat videos as images for now
                else -> "text"
            }
            
            // For now, we'll pass the file URI as content
            // In a real implementation, you'd upload the file to your backend first
            val query = "Analyze this $contentType file ($fileName) for Instagram insights: $analysisQuery"
            
            val request = RAGQueryRequest(
                query = query,
                domain = "instagram",
                options = QueryOptions(
                    topK = 5,
                    minScore = 0.7
                )
            )
            repository.queryRAG(request)
        } catch (e: Exception) {
            InstagramResult.Error("Failed to upload and analyze file: ${e.message}", e)
        }
    }
}

/**
 * Use case for analyzing Instagram URLs
 * Based on INSTAGRAM_URL_ANALYSIS.md specifications
 */
class AnalyzeInstagramURLUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(
        url: String,
        customQuery: String? = null
    ): InstagramResult<RAGQueryResponse> {
        return try {
            if (!isValidInstagramURL(url)) {
                return InstagramResult.Error("Please provide a valid Instagram URL")
            }
            
            val analysisType = getInstagramURLType(url)
            val query = customQuery ?: getDefaultAnalysisQuery(url, analysisType)
            
            val request = RAGQueryRequest(
                query = "Analyze this Instagram URL and provide strategic insights: $url. $query",
                domain = "instagram",
                options = QueryOptions(
                    topK = 5,
                    minScore = 0.8  // Higher threshold for URL analysis
                )
            )
            repository.queryRAG(request)
        } catch (e: Exception) {
            InstagramResult.Error("Failed to analyze Instagram URL: ${e.message}", e)
        }
    }
    
    private fun isValidInstagramURL(url: String): Boolean {
        return url.contains("instagram.com") && (
            url.contains("/p/") ||          // Post
            url.contains("/reel/") ||       // Reel
            url.matches(Regex(".*instagram\\.com/[a-zA-Z0-9._]+/?$"))  // Profile
        )
    }
    
    private fun getInstagramURLType(url: String): String {
        return when {
            url.contains("/p/") -> "Post"
            url.contains("/reel/") -> "Reel"
            else -> "Profile"
        }
    }
    
    private fun getDefaultAnalysisQuery(url: String, type: String): String {
        return when (type) {
            "Post" -> "Why did this post perform well/poorly? What can I learn for my content strategy?"
            "Reel" -> "What makes this reel engaging? How can I apply these insights to my content?"
            "Profile" -> "What can I learn from this account's content strategy and posting patterns?"
            else -> "What strategic insights can I get from this Instagram content?"
        }
    }
}

/**
 * Use case for processing audio recordings
 */
class ProcessAudioRecordingUseCase @Inject constructor(
    private val repository: InstagramRepository
) {
    suspend operator fun invoke(
        audioFilePath: String,
        analysisQuery: String,
        duration: Long
    ): InstagramResult<RAGQueryResponse> {
        return try {
            val query = "Analyze this audio recording for Instagram strategy insights. " +
                    "Duration: ${duration/1000}s. Question: $analysisQuery. " +
                    "Audio file: $audioFilePath"
            
            val request = RAGQueryRequest(
                query = query,
                domain = "instagram",
                options = QueryOptions(
                    topK = 5,
                    minScore = 0.7
                )
            )
            repository.queryRAG(request)
        } catch (e: Exception) {
            InstagramResult.Error("Failed to process audio recording: ${e.message}", e)
        }
    }
}

/**
 * Use case for getting multimodal suggestions
 */
class GetMultimodalSuggestionsUseCase {
    operator fun invoke(): List<MultimodalSuggestion> {
        return listOf(
            MultimodalSuggestion(
                title = "Analyze Screenshot",
                description = "Upload Instagram screenshots for analysis",
                contentType = ContentType.IMAGE,
                icon = "📱",
                exampleQuery = "How can I improve my Instagram profile?",
                acceptedFileTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp")
            ),
            MultimodalSuggestion(
                title = "Voice Strategy",
                description = "Record voice memos about your strategy",
                contentType = ContentType.AUDIO,
                icon = "🎤",
                exampleQuery = "Transcribe and analyze my content ideas",
                acceptedFileTypes = listOf("audio/mpeg", "audio/wav", "audio/ogg")
            ),
            MultimodalSuggestion(
                title = "Analytics Report",
                description = "Upload PDF analytics reports",
                contentType = ContentType.PDF,
                icon = "📊",
                exampleQuery = "What insights are in this analytics report?",
                acceptedFileTypes = listOf("application/pdf")
            ),
            MultimodalSuggestion(
                title = "Analyze URL",
                description = "Analyze Instagram profiles or posts",
                contentType = ContentType.URL,
                icon = "🔗",
                exampleQuery = "What can I learn from this account?",
                acceptedFileTypes = emptyList()
            )
        )
    }
}

 