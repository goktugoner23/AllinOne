package com.example.allinone.feature.instagram.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allinone.feature.instagram.data.model.*
import com.example.allinone.feature.instagram.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstagramViewModel @Inject constructor(
    private val getInstagramPostsUseCase: GetInstagramPostsUseCase,
    private val getInstagramAnalyticsUseCase: GetInstagramAnalyticsUseCase,
    private val checkInstagramHealthUseCase: CheckInstagramHealthUseCase
) : ViewModel() {
    
    // Posts data state (main content)
    private val _postsData = MutableLiveData<InstagramResult<InstagramPostsData>>()
    val postsData: LiveData<InstagramResult<InstagramPostsData>> = _postsData
    
    // Analytics state
    private val _analytics = MutableLiveData<InstagramResult<InstagramAnalytics>>()
    val analytics: LiveData<InstagramResult<InstagramAnalytics>> = _analytics
    
    // Health status
    private val _healthStatus = MutableLiveData<InstagramResult<HealthStatus>>()
    val healthStatus: LiveData<InstagramResult<HealthStatus>> = _healthStatus
    
    // Loading states
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing
    
    // Last sync info for UI display
    private val _lastSyncInfo = MutableLiveData<SyncInfo?>()
    val lastSyncInfo: LiveData<SyncInfo?> = _lastSyncInfo
    
    /**
     * Load Instagram posts - main function for Posts tab
     * Uses smart caching with automatic sync detection
     */
    fun loadPosts(forceSync: Boolean = false) {
        viewModelScope.launch {
            try {
                if (forceSync) {
                    _isRefreshing.value = true
                } else if (_postsData.value == null) {
                    _isLoading.value = true
                    _postsData.value = InstagramResult.Loading("Loading Instagram posts...")
                }
                
                val result = getInstagramPostsUseCase(forceSync)
                _postsData.value = result
                
                // Update sync info for UI display
                if (result is InstagramResult.Success) {
                    _lastSyncInfo.value = result.data.syncInfo
                }
                
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * Load Instagram analytics - for Insights tab
     */
    fun loadAnalytics() {
        viewModelScope.launch {
            if (_analytics.value == null) {
                _analytics.value = InstagramResult.Loading("Loading analytics...")
            }
            val result = getInstagramAnalyticsUseCase()
            _analytics.value = result
        }
    }
    
    /**
     * Check service health
     */
    fun checkHealth() {
        viewModelScope.launch {
            val result = checkInstagramHealthUseCase()
            _healthStatus.value = result
        }
    }
    
    /**
     * Initialize - load data when ViewModel is created
     */
    fun initialize() {
        if (_postsData.value == null) {
            checkHealth()
            loadPosts()
        }
    }
    
    /**
     * Refresh all data (pull-to-refresh)
     */
    fun refreshAll() {
        loadPosts(forceSync = true)
        loadAnalytics()
        checkHealth()
    }
    
    /**
     * Get posts list for UI
     */
    fun getCurrentPosts(): List<InstagramPost> {
        return when (val current = _postsData.value) {
            is InstagramResult.Success -> current.data.posts
            else -> emptyList()
        }
    }
    
    /**
     * Get posts count for UI
     */
    fun getPostsCount(): Int {
        return when (val current = _postsData.value) {
            is InstagramResult.Success -> current.data.count
            else -> 0
        }
    }
} 