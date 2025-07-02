package com.example.allinone.ui.instagram

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.databinding.FragmentInstagramAskAiBinding
import com.example.allinone.feature.instagram.ui.adapter.ChatAdapter
import com.example.allinone.feature.instagram.ui.viewmodel.InstagramAIViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InstagramAskAIFragment : Fragment() {

    private var _binding: FragmentInstagramAskAiBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstagramAIViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstagramAskAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupInputHandling()
        setupSuggestedQuestions()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerChatMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupInputHandling() {
        binding.apply {
            // Send button click
            btnSendQuestion.setOnClickListener {
                sendQuestion()
            }
            
            // Enter key handling
            editTextQuestion.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendQuestion()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setupSuggestedQuestions() {
        binding.apply {
            // ✅ PERFECT: Proven working suggested questions
            chipBestPosts.setOnClickListener {
                askSuggestedQuestion("Which Wing Chun posts have highest engagement?")
            }
            
            chipHashtags.setOnClickListener {
                askSuggestedQuestion("What martial arts hashtags perform best?")
            }
            
            chipImprove.setOnClickListener {
                askSuggestedQuestion("Compare my sparring vs technique demonstration videos")
            }
            
            chipContent.setOnClickListener {
                askSuggestedQuestion("What content gets the most comments and engagement?")
            }
        }
    }

    private fun observeViewModel() {
        // Chat messages
        viewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.updateMessages(messages)
            updateEmptyState(messages.isEmpty())
            
            // Auto scroll to bottom when new messages arrive
            if (messages.isNotEmpty()) {
                binding.recyclerChatMessages.scrollToPosition(messages.size - 1)
            }
        }
        
        // Loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingOverlay.isVisible = isLoading
            binding.btnSendQuestion.isEnabled = !isLoading
        }
        
        // Error state
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendQuestion() {
        val question = binding.editTextQuestion.text.toString().trim()
        if (question.isNotEmpty()) {
            viewModel.askQuestion(question)
            binding.editTextQuestion.text?.clear()
        }
    }

    private fun askSuggestedQuestion(question: String) {
        viewModel.askSuggestedQuestion(question)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.apply {
            emptyState.isVisible = isEmpty
            cardSuggestedQuestions.isVisible = isEmpty
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 