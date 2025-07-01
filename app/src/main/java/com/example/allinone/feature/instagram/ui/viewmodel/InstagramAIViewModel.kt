package com.example.allinone.feature.instagram.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allinone.feature.instagram.data.model.*
import com.example.allinone.feature.instagram.domain.usecase.QueryInstagramAIUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstagramAIViewModel @Inject constructor(
    private val queryInstagramAIUseCase: QueryInstagramAIUseCase
) : ViewModel() {
    
    // Chat messages state
    private val _chatMessages = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatMessages: LiveData<List<ChatMessage>> = _chatMessages
    
    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error state
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    companion object {
        private const val TAG = "InstagramAIViewModel"
    }
    
    /**
     * Ask a question to the Instagram AI
     */
    fun askQuestion(question: String) {
        if (question.isBlank()) return
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Add user message immediately
                val userMessage = ChatMessage(
                    text = question,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
                addMessage(userMessage)
                
                // Add loading AI message
                val loadingMessage = ChatMessage(
                    text = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isLoading = true
                )
                addMessage(loadingMessage)
                
                // Enhance the query to improve RAG matching
                val enhancedQuery = enhanceQueryForInstagram(question)
                
                // Query the AI with optimized parameters for Instagram content
                val result = queryInstagramAIUseCase(
                    query = enhancedQuery,
                    domain = "instagram",
                    topK = 15, // More sources for better coverage
                    minScore = 0.3 // Much lower threshold to catch more content
                )
                
                // Remove loading message
                removeLastMessage()
                
                when (result) {
                    is InstagramResult.Success -> {
                        val aiMessage = ChatMessage(
                            text = result.data.answer,
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            sources = result.data.sources,
                            confidence = result.data.confidence,
                            isLoading = false
                        )
                        addMessage(aiMessage)
                    }
                    
                    is InstagramResult.Error -> {
                        val errorMessage = ChatMessage(
                            text = "Sorry, I couldn't process your question. ${result.message}",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            isError = true
                        )
                        addMessage(errorMessage)
                        _error.value = result.message
                    }
                    
                    is InstagramResult.Loading -> {
                        // This shouldn't happen in our use case, but handle it
                        val processingMessage = ChatMessage(
                            text = "Processing your request...",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            isLoading = true
                        )
                        addMessage(processingMessage)
                    }
                }
                
            } catch (e: Exception) {
                // Remove loading message if there was an error
                removeLastMessage()
                
                val errorMessage = ChatMessage(
                    text = "Sorry, something went wrong. Please try again.",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
                addMessage(errorMessage)
                _error.value = e.message
                
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Enhance queries to better match Instagram content
     * Adds relevant Instagram and martial arts terms to improve RAG matching
     */
    private fun enhanceQueryForInstagram(originalQuery: String): String {
        val lowerQuery = originalQuery.lowercase()
        
        // Add Instagram-specific context terms
        val instagramTerms = mutableListOf<String>()
        
        // Performance-related queries
        when {
            lowerQuery.contains("best") || lowerQuery.contains("top") || lowerQuery.contains("performing") -> {
                instagramTerms.addAll(listOf("engagement", "likes", "comments", "performance", "metrics"))
            }
            lowerQuery.contains("hashtag") -> {
                instagramTerms.addAll(listOf("#wingchun", "#martialarts", "#selfdefense", "#escrima", "#ebmas"))
            }
            lowerQuery.contains("engagement") || lowerQuery.contains("rate") -> {
                instagramTerms.addAll(listOf("engagementRate", "likesCount", "commentsCount"))
            }
            lowerQuery.contains("content") || lowerQuery.contains("post") -> {
                instagramTerms.addAll(listOf("video", "caption", "mediaType", "post"))
            }
            lowerQuery.contains("martial") || lowerQuery.contains("wing") || lowerQuery.contains("defense") -> {
                instagramTerms.addAll(listOf("wingchun", "escrima", "martialarts", "selfdefense", "bıçak", "karşılama"))
            }
        }
        
        // Add relevant context without overwhelming the query
        val contextTerms = instagramTerms.take(3).joinToString(" ")
        
        return if (contextTerms.isNotEmpty()) {
            "$originalQuery $contextTerms Instagram posts metrics"
        } else {
            "$originalQuery Instagram posts engagement"
        }
    }
    
    /**
     * Ask a suggested question
     */
    fun askSuggestedQuestion(question: String) {
        askQuestion(question)
    }
    
    /**
     * Clear chat history
     */
    fun clearChat() {
        _chatMessages.value = emptyList()
        _error.value = null
    }
    
    /**
     * Get suggested questions
     */
    fun getSuggestedQuestions(): List<String> {
        return listOf(
            "What are my best performing martial arts posts?",
            "Which Wing Chun or Escrima posts have highest engagement?",
            "Show me metrics for my self-defense content",
            "What hashtags work best for my martial arts videos?",
            "How do my knife defense posts perform?",
            "What's my average engagement on training videos?",
            "Which posts get the most comments and likes?",
            "Show me my most engaging self-defense content",
            "What type of martial arts content performs best?",
            "How do my Turkish vs English posts compare?"
        )
    }
    
    /**
     * Get current message count
     */
    fun getMessageCount(): Int {
        return _chatMessages.value?.size ?: 0
    }
    
    /**
     * Check if there are any messages
     */
    fun hasMessages(): Boolean {
        return getMessageCount() > 0
    }
    
    private fun addMessage(message: ChatMessage) {
        val currentMessages = _chatMessages.value ?: emptyList()
        _chatMessages.value = currentMessages + message
    }
    
    private fun removeLastMessage() {
        val currentMessages = _chatMessages.value ?: emptyList()
        if (currentMessages.isNotEmpty()) {
            _chatMessages.value = currentMessages.dropLast(1)
        }
    }
} 