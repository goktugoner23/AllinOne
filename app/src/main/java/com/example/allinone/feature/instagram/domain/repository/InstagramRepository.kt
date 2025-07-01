package com.example.allinone.feature.instagram.domain.repository

import com.example.allinone.feature.instagram.data.model.*

interface InstagramRepository {
    
    /**
     * Get Instagram posts with smart caching and auto-sync
     * Main endpoint for Posts tab
     */
    suspend fun getInstagramPosts(forceSync: Boolean = false): InstagramResult<InstagramPostsData>
    
    /**
     * Get comprehensive Instagram analytics
     * Main endpoint for Insights tab
     */
    suspend fun getAnalytics(): InstagramResult<InstagramAnalytics>
    
    /**
     * Query RAG system for AI insights
     * Used for AI chat functionality
     */
    suspend fun queryRAG(request: RAGQueryRequest): InstagramResult<RAGQueryResponse>
    
    /**
     * Check Instagram service health
     * Optional health monitoring
     */
    suspend fun checkHealth(): InstagramResult<HealthStatus>
} 