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

 