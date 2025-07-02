package com.example.allinone.ui.instagram

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.databinding.FragmentInstagramAskAiBinding
import com.example.allinone.feature.instagram.data.model.*
import com.example.allinone.feature.instagram.ui.adapter.ChatAdapter
import com.example.allinone.feature.instagram.ui.viewmodel.InstagramAIViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class InstagramAskAIFragment : Fragment() {

    private var _binding: FragmentInstagramAskAiBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstagramAIViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    
    // ✅ NEW: File selection and permissions
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageSelection(uri)
            }
        }
    }
    
    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleAudioSelection(uri)
            }
        }
    }
    
    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handlePDFSelection(uri)
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAudioRecording()
        } else {
            Toast.makeText(context, "Audio permission required for recording", Toast.LENGTH_SHORT).show()
        }
    }

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
        setupAttachmentOptions()
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
            
            // ✅ NEW: Attachment button click
            btnAttachments.setOnClickListener {
                toggleAttachmentOptions()
            }
            
            // ✅ NEW: Text change listener for URL detection
            editTextQuestion.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString() ?: ""
                    // Auto-detect Instagram URLs and show hint
                    if (text.contains("instagram.com")) {
                        editTextQuestion.hint = "Analyzing Instagram URL..."
                    } else {
                        editTextQuestion.hint = "Ask about your Instagram..."
                    }
                }
            })
        }
    }

    private fun setupSuggestedQuestions() {
        binding.apply {
            // ✅ ENHANCED: Dynamic suggested questions based on attachments
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
    
    // ✅ NEW: Setup attachment options
    private fun setupAttachmentOptions() {
        binding.apply {
            // Image upload
            cardImageUpload.setOnClickListener {
                hideAttachmentOptions()
                openImagePicker()
            }
            
            // Voice recording
            cardVoiceRecord.setOnClickListener {
                hideAttachmentOptions()
                requestAudioPermissionAndRecord()
            }
            
            // Audio upload
            cardAudioUpload.setOnClickListener {
                hideAttachmentOptions()
                openAudioPicker()
            }
            
            // PDF upload
            cardPDFUpload.setOnClickListener {
                hideAttachmentOptions()
                openPDFPicker()
            }
            
            // URL analysis
            cardURLAnalysis.setOnClickListener {
                hideAttachmentOptions()
                focusOnURLInput()
            }
            
            // Remove attachment
            btnRemoveAttachment.setOnClickListener {
                viewModel.removeAttachmentPreview()
            }
            
            // Stop recording
            btnStopRecording.setOnClickListener {
                stopAudioRecording()
            }
        }
    }
    
    // ✅ NEW: File picker methods
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }
    
    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        intent.type = "audio/*"
        audioPickerLauncher.launch(intent)
    }
    
    private fun openPDFPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pdfPickerLauncher.launch(intent)
    }
    
    // ✅ NEW: File selection handlers
    private fun handleImageSelection(uri: Uri) {
        val attachment = createAttachment(uri, AttachmentType.IMAGE)
        viewModel.addAttachment(attachment)
        showAttachmentPreview(attachment)
    }
    
    private fun handleAudioSelection(uri: Uri) {
        val attachment = createAttachment(uri, AttachmentType.AUDIO)
        viewModel.addAttachment(attachment)
        showAttachmentPreview(attachment)
    }
    
    private fun handlePDFSelection(uri: Uri) {
        val attachment = createAttachment(uri, AttachmentType.PDF)
        viewModel.addAttachment(attachment)
        showAttachmentPreview(attachment)
    }
    
    // ✅ NEW: Audio recording methods
    private fun requestAudioPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startAudioRecording()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun startAudioRecording() {
        binding.layoutAudioRecording.isVisible = true
        binding.layoutMainInput.isVisible = false
        
        viewModel.startAudioRecording()
        
        // Start duration timer
        startRecordingTimer()
    }
    
    private fun stopAudioRecording() {
        val recordingPath = viewModel.stopAudioRecording()
        
        binding.layoutAudioRecording.isVisible = false
        binding.layoutMainInput.isVisible = true
        
        recordingPath?.let { path ->
            val attachment = MessageAttachment(
                id = UUID.randomUUID().toString(),
                type = AttachmentType.VOICE_RECORDING,
                uri = path,
                fileName = "recording_${System.currentTimeMillis()}.wav",
                duration = viewModel.audioRecordingState.value?.duration ?: 0L
            )
            viewModel.addAttachment(attachment)
            showAttachmentPreview(attachment)
        }
    }
    
    private fun startRecordingTimer() {
        lifecycleScope.launch {
            while (viewModel.audioRecordingState.value?.isRecording == true) {
                val currentDuration = viewModel.audioRecordingState.value?.duration ?: 0L
                val newDuration = currentDuration + 1000L // Add 1 second
                
                // Update duration display
                val minutes = newDuration / 60000
                val seconds = (newDuration % 60000) / 1000
                binding.textRecordingDuration.text = String.format("%02d:%02d", minutes, seconds)
                
                // Update amplitude (mock for now)
                viewModel.updateRecordingState(newDuration, (20..80).random().toFloat())
                
                delay(1000)
            }
        }
    }
    
    // ✅ NEW: Attachment UI methods
    private fun createAttachment(uri: Uri, type: AttachmentType): MessageAttachment {
        val fileName = getFileName(uri)
        return MessageAttachment(
            id = UUID.randomUUID().toString(),
            type = type,
            uri = uri.toString(),
            fileName = fileName,
            mimeType = getMimeType(uri)
        )
    }
    
    private fun showAttachmentPreview(attachment: MessageAttachment) {
        binding.apply {
            layoutAttachmentPreview.isVisible = true
            
            // Set icon based on type
            val iconRes = when (attachment.type) {
                AttachmentType.IMAGE -> android.R.drawable.ic_menu_gallery
                AttachmentType.AUDIO, AttachmentType.VOICE_RECORDING -> android.R.drawable.ic_btn_speak_now
                AttachmentType.PDF -> android.R.drawable.ic_menu_agenda
                AttachmentType.VIDEO -> android.R.drawable.ic_menu_slideshow
            }
            imgAttachmentIcon.setImageResource(iconRes)
            
            textAttachmentName.text = attachment.fileName ?: "Unknown file"
        }
    }
    
    private fun toggleAttachmentOptions() {
        binding.cardAttachmentOptions.isVisible = !binding.cardAttachmentOptions.isVisible
    }
    
    private fun hideAttachmentOptions() {
        binding.cardAttachmentOptions.isVisible = false
    }
    
    private fun focusOnURLInput() {
        binding.editTextQuestion.apply {
            requestFocus()
            hint = "Paste Instagram URL here (profile, post, or reel)..."
        }
    }
    
    // ✅ NEW: Utility methods
    private fun getFileName(uri: Uri): String {
        // In a real implementation, query the content resolver
        return "file_${System.currentTimeMillis()}"
    }
    
    private fun getMimeType(uri: Uri): String {
        return requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
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
        
        // ✅ NEW: Attachment preview state
        viewModel.attachmentPreview.observe(viewLifecycleOwner) { attachment ->
            if (attachment != null) {
                showAttachmentPreview(attachment)
            } else {
                binding.layoutAttachmentPreview.isVisible = false
            }
        }
        
        // ✅ NEW: Audio recording state
        viewModel.audioRecordingState.observe(viewLifecycleOwner) { state ->
            binding.layoutAudioRecording.isVisible = state.isRecording
            binding.layoutMainInput.isVisible = !state.isRecording
            
            if (state.isRecording) {
                // Update progress bar based on amplitude
                binding.progressAudioWave.progress = (state.amplitude.toInt())
            }
        }
        
        // ✅ NEW: Multimodal suggestions (could be used for dynamic suggestions)
        viewModel.multimodalSuggestions.observe(viewLifecycleOwner) { suggestions ->
            // Could implement dynamic suggestion chips based on content type
        }
    }

    private fun sendQuestion() {
        val question = binding.editTextQuestion.text.toString().trim()
        val currentAttachment = viewModel.attachmentPreview.value
        
        if (question.isNotEmpty() || currentAttachment != null) {
            val attachments = if (currentAttachment != null) listOf(currentAttachment) else emptyList()
            viewModel.askQuestion(question, attachments)
            binding.editTextQuestion.text?.clear()
            hideAttachmentOptions()
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