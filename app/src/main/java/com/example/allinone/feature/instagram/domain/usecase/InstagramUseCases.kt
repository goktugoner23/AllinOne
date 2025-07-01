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
    suspend operator fun invoke(query: String, context: String = "instagram"): InstagramResult<RAGQueryResponse> {
        val request = RAGQueryRequest(query = query, context = context)
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

 