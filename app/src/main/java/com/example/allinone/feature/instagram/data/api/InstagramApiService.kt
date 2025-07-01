package com.example.allinone.feature.instagram.data.api

import com.example.allinone.feature.instagram.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface InstagramApiService {
    
    // ===============================
    // Posts Tab - Main Display Endpoint
    // ===============================
    
    /**
     * Get Instagram posts with smart caching and auto-sync
     * This is the main endpoint for displaying posts
     * 
     * @param forceSync - Force sync regardless of post count (for pull-to-refresh)
     */
    @GET("api/instagram/firestore/posts")
    suspend fun getInstagramPosts(
        @Query("forceSync") forceSync: Boolean = false
    ): Response<InstagramPostsApiResponse>
    
    // ===============================
    // Insights Tab - Analytics Endpoint
    // ===============================
    
    /**
     * Get comprehensive Instagram analytics and insights
     * This is the main endpoint for the insights/analytics tab
     */
    @GET("api/instagram/analytics")
    suspend fun getInstagramAnalytics(): Response<ApiResponse<InstagramAnalytics>>
    
    // ===============================
    // AI Chat - RAG System
    // ===============================
    
    /**
     * Query the RAG system for Instagram insights
     */
    @POST("api/rag/query")
    suspend fun queryRAG(
        @Body request: RAGQueryRequest
    ): Response<ApiResponse<RAGQueryResponse>>
    
    // ===============================
    // Health Check (Optional)
    // ===============================
    
    /**
     * Check service health
     */
    @GET("api/instagram/status")
    suspend fun checkInstagramHealth(): Response<ApiResponse<HealthStatus>>
} 