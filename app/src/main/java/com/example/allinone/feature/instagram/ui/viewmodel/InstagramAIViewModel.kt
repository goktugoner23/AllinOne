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
    
    private val _chatMessages = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatMessages: LiveData<List<ChatMessage>> = _chatMessages
    
    private val _isTyping = MutableLiveData(false)
    val isTyping: LiveData<Boolean> = _isTyping
    
    private val _isInitialized = MutableLiveData(false)
    val isInitialized: LiveData<Boolean> = _isInitialized
    
    init {
        initializeChat()
    }
    
    /**
     * Initialize chat with welcome message
     */
    private fun initializeChat() {
        val welcomeMessage = ChatMessage(
            text = "Hi! I'm your Instagram AI assistant. Ask me anything about your Instagram performance, content strategy, or analytics!\n\nHere are some things you can ask:\n• What's my best performing post?\n• How is my engagement trending?\n• Which hashtags work best?\n• What time should I post?\n• Analyze my content strategy",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        
        _chatMessages.value = listOf(welcomeMessage)
        _isInitialized.value = true
    }
    
    /**
     * Send a message to the AI
     */
    fun sendMessage(message: String) {
        if (message.trim().isEmpty()) return
        
        val currentMessages = _chatMessages.value?.toMutableList() ?: mutableListOf()
        
        // Add user message
        val userMessage = ChatMessage(
            text = message.trim(),
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        
        currentMessages.add(userMessage)
        _chatMessages.value = currentMessages
        
        // Start AI processing
        queryAI(message.trim())
    }
    
    /**
     * Query the AI system
     */
    private fun queryAI(query: String) {
        viewModelScope.launch {
            try {
                _isTyping.value = true
                
                // Add typing indicator
                val currentMessages = _chatMessages.value?.toMutableList() ?: mutableListOf()
                val typingMessage = ChatMessage(
                    text = "AI is analyzing your Instagram data...",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isTyping = true
                )
                
                currentMessages.add(typingMessage)
                _chatMessages.value = currentMessages
                
                // Query the AI
                val result = queryInstagramAIUseCase(query)
                
                // Remove typing indicator
                val updatedMessages = _chatMessages.value?.toMutableList() ?: mutableListOf()
                val typingIndex = updatedMessages.indexOfLast { it.isTyping }
                if (typingIndex != -1) {
                    updatedMessages.removeAt(typingIndex)
                }
                
                // Add AI response
                when (result) {
                    is InstagramResult.Success -> {
                        val aiMessage = ChatMessage(
                            text = result.data.answer,
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            confidence = result.data.confidence,
                            sources = result.data.sources
                        )
                        updatedMessages.add(aiMessage)
                    }
                    
                    is InstagramResult.Error -> {
                        val errorMessage = ChatMessage(
                            text = "Sorry, I couldn't process your request right now. Please try again.\n\nError: ${result.message}",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            isError = true
                        )
                        updatedMessages.add(errorMessage)
                    }
                    
                    is InstagramResult.Loading -> {
                        // This shouldn't happen in this flow, but handle it just in case
                        val loadingMessage = ChatMessage(
                            text = "Processing your request...",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            isTyping = true
                        )
                        updatedMessages.add(loadingMessage)
                    }
                }
                
                _chatMessages.value = updatedMessages
                
            } catch (e: Exception) {
                // Handle any unexpected errors
                val currentMessages = _chatMessages.value?.toMutableList() ?: mutableListOf()
                
                // Remove typing indicator if present
                val typingIndex = currentMessages.indexOfLast { it.isTyping }
                if (typingIndex != -1) {
                    currentMessages.removeAt(typingIndex)
                }
                
                val errorMessage = ChatMessage(
                    text = "An unexpected error occurred. Please check your connection and try again.",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
                
                currentMessages.add(errorMessage)
                _chatMessages.value = currentMessages
                
            } finally {
                _isTyping.value = false
            }
        }
    }
    
    /**
     * Clear chat history
     */
    fun clearChat() {
        initializeChat()
    }
    
    /**
     * Send a quick suggestion
     */
    fun sendQuickSuggestion(suggestion: String) {
        sendMessage(suggestion)
    }
    
    /**
     * Get suggested questions for UI
     */
    fun getSuggestedQuestions(): List<String> {
        return listOf(
            "What's my best performing post?",
            "How is my engagement trending?",
            "Which hashtags work best?",
            "What time should I post?",
            "Analyze my content strategy",
            "Show me my reach statistics",
            "What content gets the most saves?",
            "How do my Reels perform vs posts?"
        )
    }
} 