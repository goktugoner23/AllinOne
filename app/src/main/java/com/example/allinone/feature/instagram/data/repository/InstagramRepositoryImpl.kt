package com.example.allinone.feature.instagram.data.repository

import android.util.Log
import com.example.allinone.feature.instagram.data.api.InstagramApiClient
import com.example.allinone.feature.instagram.data.model.*
import com.example.allinone.feature.instagram.domain.repository.InstagramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstagramRepositoryImpl @Inject constructor() : InstagramRepository {
    
    private val apiService = InstagramApiClient.service
    
    companion object {
        private const val TAG = "InstagramRepository"
    }
    
    /**
     * Get Instagram posts with smart caching and auto-sync
     * Replaces the old Firebase logic with a single API call
     */
    override suspend fun getInstagramPosts(forceSync: Boolean): InstagramResult<InstagramPostsData> {
        return try {
            Log.d(TAG, "Getting Instagram posts (forceSync: $forceSync)")
            
            val response = withContext(Dispatchers.IO) {
                apiService.getInstagramPosts(forceSync)
            }
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true) {
                    Log.d(TAG, "Posts retrieved successfully: ${apiResponse.count} posts from ${apiResponse.source}")
                    if (apiResponse.syncInfo.triggered) {
                        Log.d(TAG, "Sync was triggered: ${apiResponse.syncInfo.reason}")
                    }
                    
                    // Convert API response to internal data format
                    val internalData = InstagramPostsData(
                        posts = apiResponse.data,
                        count = apiResponse.count,
                        source = apiResponse.source,
                        syncInfo = apiResponse.syncInfo,
                        timestamp = apiResponse.timestamp
                    )
                    
                    InstagramResult.Success(internalData)
                } else {
                    val errorMsg = "API returned success=false"
                    Log.e(TAG, "Posts fetch failed: $errorMsg")
                    InstagramResult.Error(errorMsg)
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                Log.e(TAG, "Posts fetch API error: $errorMsg")
                InstagramResult.Error("Failed to get Instagram posts: $errorMsg")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Posts fetch exception", e)
            InstagramResult.Error(
                "Failed to get Instagram posts: ${e.message}",
                e
            )
        }
    }
    
    /**
     * Get comprehensive analytics
     */
    override suspend fun getAnalytics(): InstagramResult<InstagramAnalytics> {
        return try {
            Log.d(TAG, "Fetching Instagram analytics")
            
            val response = withContext(Dispatchers.IO) {
                apiService.getInstagramAnalytics()
            }
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    Log.d(TAG, "Analytics fetch successful")
                    InstagramResult.Success(apiResponse.data)
                } else {
                    val errorMsg = apiResponse?.error ?: "Unknown error fetching analytics"
                    InstagramResult.Error(errorMsg)
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                InstagramResult.Error("Failed to fetch analytics: $errorMsg")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Analytics fetch exception", e)
            InstagramResult.Error("Failed to fetch analytics: ${e.message}", e)
        }
    }
    
    /**
     * Query RAG system for AI insights
     */
    override suspend fun queryRAG(request: RAGQueryRequest): InstagramResult<RAGQueryResponse> {
        return try {
            Log.d(TAG, "Querying RAG system: ${request.query}")
            
            val response = withContext(Dispatchers.IO) {
                apiService.queryRAG(request)
            }
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    Log.d(TAG, "RAG query successful, confidence: ${apiResponse.data.confidence}")
                    InstagramResult.Success(apiResponse.data)
                } else {
                    val errorMsg = apiResponse?.error ?: "Unknown error querying RAG"
                    InstagramResult.Error(errorMsg)
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                InstagramResult.Error("Failed to query RAG: $errorMsg")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "RAG query exception", e)
            InstagramResult.Error("Failed to query RAG: ${e.message}", e)
        }
    }
    
    /**
     * Check Instagram service health
     */
    override suspend fun checkHealth(): InstagramResult<HealthStatus> {
        return try {
            Log.d(TAG, "Checking Instagram service health")
            
            val response = withContext(Dispatchers.IO) {
                apiService.checkInstagramHealth()
            }
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    Log.d(TAG, "Health check successful")
                    InstagramResult.Success(apiResponse.data)
                } else {
                    val errorMsg = apiResponse?.error ?: "Unknown error checking health"
                    InstagramResult.Error(errorMsg)
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                InstagramResult.Error("Failed to check health: $errorMsg")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Health check exception", e)
            InstagramResult.Error("Failed to check health: ${e.message}", e)
        }
    }
} 