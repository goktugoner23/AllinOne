package com.example.allinone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.NoteImageAdapter
import com.example.allinone.adapters.NoteVideoAdapter
import com.example.allinone.adapters.VoiceNoteAdapter
import com.example.allinone.data.Note
import com.example.allinone.data.VoiceNote
import com.example.allinone.databinding.ActivityEditNoteBinding
import com.example.allinone.firebase.FirebaseStorageUtil
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.viewmodels.NotesViewModel
import io.github.mthli.knife.KnifeText
import java.util.Date
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.app.Dialog
import android.view.LayoutInflater
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.allinone.ui.drawing.DrawingActivity
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.provider.MediaStore
import android.text.format.DateUtils
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class EditNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var viewModel: NotesViewModel
    private lateinit var imageAdapter: NoteImageAdapter
    private lateinit var videoAdapter: NoteVideoAdapter
    private lateinit var voiceNoteAdapter: VoiceNoteAdapter

    private val selectedImages = mutableListOf<Uri>()
    private val selectedVideos = mutableListOf<Uri>()
    private val voiceNotes = mutableListOf<VoiceNote>()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0
    private val recordingTimeHandler = Handler(Looper.getMainLooper())
    private val recordingTimeRunnable = object : Runnable {
        override fun run() {
            val elapsedTime = System.currentTimeMillis() - recordingStartTime
            binding.recordingTimeText?.text = DateUtils.formatElapsedTime(elapsedTime / 1000)
            recordingTimeHandler.postDelayed(this, 1000)
        }
    }

    private val storageUtil by lazy { FirebaseStorageUtil(this) }
    private val idManager by lazy { FirebaseIdManager() }

    private var isNewNote = true
    private var noteId: Long? = null

    // Image picker launcher
    private val getImageContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedImages.add(uri)
                updateImageAttachmentSection()
            } catch (e: Exception) {
                Log.e("EditNoteActivity", "Error with image permission: ${e.message}", e)
            }
        }
    }

    // Video picker launcher
    private val getVideoContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedVideos.add(uri)
                updateVideoAttachmentSection()
            } catch (e: Exception) {
                Log.e("EditNoteActivity", "Error with video permission: ${e.message}", e)
            }
        }
    }

    // Drawing activity result
    private val drawingLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(DrawingActivity.RESULT_DRAWING_URI)?.let { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    // No need to call takePersistableUriPermission as we already granted permission
                    selectedImages.add(uri)
                    updateImageAttachmentSection()
                    Toast.makeText(this, getString(R.string.drawing_added), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("EditNoteActivity", "Error with drawing URI: ${e.message}", e)
                    Toast.makeText(this, getString(R.string.error_adding_drawing), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "com.example.allinone.EXTRA_NOTE_ID"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val TAG = "EditNoteActivity"

        fun newIntent(context: Context, noteId: Long? = null): Intent {
            return Intent(context, EditNoteActivity::class.java).apply {
                noteId?.let { putExtra(EXTRA_NOTE_ID, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        viewModel = ViewModelProvider(this)[NotesViewModel::class.java]

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) noteId = null
        isNewNote = noteId == null

        setupToolbar()
        setupImageRecyclerView()
        setupVideoRecyclerView()
        setupRichTextEditor()
        setupButtons()
        setupVoiceRecordingUI()

        // If editing existing note, load its data
        if (!isNewNote) {
            loadNoteData()
        }
    }

    private fun setupToolbar() {
        supportActionBar?.title = if (isNewNote) getString(R.string.create_note) else getString(R.string.edit_note)
    }

    private fun setupImageRecyclerView() {
        imageAdapter = NoteImageAdapter(
            onDeleteClick = { uri ->
                // Show confirmation dialog before deleting
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_image)
                    .setMessage(R.string.delete_image_confirmation)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        // Delete from Firebase if it's a Firebase URL
                        if (uri.toString().contains("firebase")) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val success = storageUtil.deleteFile(uri.toString())
                                    Log.d(TAG, "Image deleted from Firebase: $success")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error deleting image from Firebase: ${e.message}")
                                }
                            }
                        }

                        // Remove from UI list
                        selectedImages.remove(uri)
                        updateImageAttachmentSection()
                        
                        // Update the note's imageUris field
                        noteId?.let { id ->
                            viewModel.allNotes.value?.find { it.id == id }?.let { existingNote ->
                                val currentUris = existingNote.imageUris?.split(",")?.filter {
                                    it.isNotEmpty() && it != uri.toString()
                                } ?: emptyList()

                                // Create updated image URIs string
                                val updatedImageUris = if (currentUris.isEmpty()) null else currentUris.joinToString(",")

                                // Create updated note with the new image URIs
                                val updatedNote = existingNote.copy(
                                    imageUris = updatedImageUris,
                                    lastEdited = Date()
                                )

                                // Update the note in the database
                                viewModel.updateNote(updatedNote)
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            },
            onImageClick = { uri ->
                showFullscreenImage(uri)
            }
        )
        binding.imagesRecyclerView.apply {
            adapter = imageAdapter
            layoutManager = LinearLayoutManager(this@EditNoteActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        binding.imagesRecyclerView.visibility = View.GONE
    }

    private fun setupVideoRecyclerView() {
        videoAdapter = NoteVideoAdapter(
            onDeleteClick = { uri ->
                // Show confirmation dialog before deleting
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_video)
                    .setMessage(R.string.delete_video_confirmation)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        // Delete from Firebase if it's a Firebase URL
                        if (uri.toString().contains("firebase")) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val success = storageUtil.deleteFile(uri.toString())
                                    Log.d(TAG, "Video deleted from Firebase: $success")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error deleting video from Firebase: ${e.message}")
                                }
                            }
                        }

                        // Remove from UI list
                        selectedVideos.remove(uri)
                        updateVideoAttachmentSection()
                        
                        // Update the note's videoUris field
                        noteId?.let { id ->
                            viewModel.allNotes.value?.find { it.id == id }?.let { existingNote ->
                                val currentUris = existingNote.videoUris?.split(",")?.filter {
                                    it.isNotEmpty() && it != uri.toString()
                                } ?: emptyList()

                                // Create updated video URIs string
                                val updatedVideoUris = if (currentUris.isEmpty()) null else currentUris.joinToString(",")

                                // Create updated note with the new video URIs
                                val updatedNote = existingNote.copy(
                                    videoUris = updatedVideoUris,
                                    lastEdited = Date()
                                )

                                // Update the note in the database
                                viewModel.updateNote(updatedNote)
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            },
            onVideoClick = { uri ->
                playVideo(uri)
            }
        )
        binding.videosRecyclerView?.apply {
            adapter = videoAdapter
            layoutManager = LinearLayoutManager(this@EditNoteActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        binding.videosRecyclerView?.visibility = View.GONE
    }

    private fun updateVideoAttachmentSection() {
        // Filter out any invalid URIs
        val validVideos = selectedVideos.filter { uri ->
            val isValid = try {
                // For http/https URIs, we assume they're valid
                if (uri.toString().startsWith("http")) {
                    true
                } else {
                    contentResolver.getType(uri) != null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Invalid video URI: $uri", e)
                false
            }
            isValid
        }.toMutableList()

        // Update the list if needed
        if (validVideos.size != selectedVideos.size) {
            Log.d(TAG, "Filtered out ${selectedVideos.size - validVideos.size} invalid video URIs")
            selectedVideos.clear()
            selectedVideos.addAll(validVideos)
        }

        // Update adapter and visibility
        videoAdapter.submitList(selectedVideos.toList())
        binding.videosRecyclerView?.visibility = if (selectedVideos.isEmpty()) View.GONE else View.VISIBLE
        Log.d(TAG, "Video section updated with ${selectedVideos.size} videos")
    }

    private fun playVideo(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No video player found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: ${e.message}", e)
            Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateImageAttachmentSection() {
        // Filter out any invalid URIs
        val validImages = selectedImages.filter { uri ->
            val isValid = try {
                // For http/https URIs, we assume they're valid
                if (uri.toString().startsWith("http")) {
                    true
                } else {
                    contentResolver.getType(uri) != null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Invalid image URI: $uri", e)
                false
            }
            isValid
        }.toMutableList()

        // Update the list if needed
        if (validImages.size != selectedImages.size) {
            Log.d(TAG, "Filtered out ${selectedImages.size - validImages.size} invalid image URIs")
            selectedImages.clear()
            selectedImages.addAll(validImages)
        }

        // Update adapter and visibility
        imageAdapter.submitList(selectedImages.toList())
        binding.imagesRecyclerView.visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
        Log.d(TAG, "Image section updated with ${selectedImages.size} images")
    }

    private fun setupRichTextEditor() {
        // Setup formatting buttons
        binding.boldButton.setOnClickListener {
            binding.editNoteContent.bold(!binding.editNoteContent.contains(KnifeText.FORMAT_BOLD))
        }

        binding.italicButton.setOnClickListener {
            binding.editNoteContent.italic(!binding.editNoteContent.contains(KnifeText.FORMAT_ITALIC))
        }

        binding.underlineButton.setOnClickListener {
            binding.editNoteContent.underline(!binding.editNoteContent.contains(KnifeText.FORMAT_UNDERLINED))
        }

        binding.bulletListButton.setOnClickListener {
            // Apply custom bullet list formatting
            insertBulletList()
        }

        binding.checkboxListButton.setOnClickListener {
            // For checkbox lists, we'll insert checkboxes
            insertCheckboxList()
        }

        // Setup drawing button
        binding.drawingButton?.setOnClickListener {
            openDrawingActivity()
        }
    }

    private fun insertBulletList() {
        try {
            val editor = binding.editNoteContent
            val start = editor.selectionStart
            val end = editor.selectionEnd

            // If text is selected, apply to each line
            if (start != end) {
                val selectedText = editor.text.toString().substring(start, end)
                val lines = selectedText.split("\n")
                val builder = StringBuilder()

                for (line in lines) {
                    builder.append("• $line\n")
                }

                // Replace the selected text with bulleted lines
                editor.text.replace(start, end, builder.toString())
                editor.setSelection(start + builder.length)
            } else {
                // If no selection, apply to current line
                val text = editor.text.toString()

                // Find the beginning of the current line
                var lineStart = start
                while (lineStart > 0 && text[lineStart - 1] != '\n') {
                    lineStart--
                }

                // Find the end of the current line
                var lineEnd = start
                while (lineEnd < text.length && text[lineEnd] != '\n') {
                    lineEnd++
                }

                // Check if the line already has a bullet
                val line = text.substring(lineStart, lineEnd)
                val bulletPattern = "^•\\s*".toRegex()

                if (bulletPattern.containsMatchIn(line)) {
                    // If already bulleted, remove the bullet
                    val unbulleted = line.replace(bulletPattern, "")
                    editor.text.replace(lineStart, lineEnd, unbulleted)
                    editor.setSelection(lineStart + unbulleted.length)
                } else {
                    // Add a bullet to the line
                    val bulleted = "• $line"
                    editor.text.replace(lineStart, lineEnd, bulleted)
                    editor.setSelection(lineStart + bulleted.length)
                }
            }
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error applying bullet list: ${e.message}", e)
            Toast.makeText(this, "Error formatting text", Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertCheckboxList() {
        try {
            val editor = binding.editNoteContent
            val start = editor.selectionStart
            val end = editor.selectionEnd

            // If text is selected, apply to each line
            if (start != end) {
                val selectedText = editor.text.toString().substring(start, end)
                val lines = selectedText.split("\n")
                val builder = StringBuilder()

                for (line in lines) {
                    if (line.isNotEmpty()) {
                        builder.append("☐ $line\n")
                    }
                }

                // Replace the selected text with checkbox lines
                editor.text.replace(start, end, builder.toString())
                editor.setSelection(start + builder.length)
            } else {
                // If no selection, apply to current line
                val text = editor.text.toString()

                // Find the beginning of the current line
                var lineStart = start
                while (lineStart > 0 && text[lineStart - 1] != '\n') {
                    lineStart--
                }

                // Find the end of the current line
                var lineEnd = start
                while (lineEnd < text.length && text[lineEnd] != '\n') {
                    lineEnd++
                }

                // Check if the line already has a checkbox
                val line = text.substring(lineStart, lineEnd)
                val checkboxPattern = "^[☐☑]\\s".toRegex()

                if (checkboxPattern.containsMatchIn(line)) {
                    // Toggle checkbox state
                    val toggledLine = when {
                        line.startsWith("☐ ") -> line.replace("☐ ", "☑ ")
                        line.startsWith("☑ ") -> line.replace("☑ ", "☐ ")
                        else -> line
                    }
                    editor.text.replace(lineStart, lineEnd, toggledLine)
                    editor.setSelection(lineStart + toggledLine.length)
                } else {
                    // Add a checkbox to the line
                    val checkboxLine = "☐ $line"
                    editor.text.replace(lineStart, lineEnd, checkboxLine)
                    editor.setSelection(lineStart + checkboxLine.length)
                }
            }
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error applying checkbox list: ${e.message}", e)
            Toast.makeText(this, "Error formatting text", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDrawingActivity() {
        // Show drawing options dialog directly
        showDrawingOptions()
    }

    private fun showDrawingOptions() {
        val options = arrayOf(getString(R.string.save_to_note), getString(R.string.save_to_note_and_gallery))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.drawing_options))
            .setItems(options) { _, which ->
                val saveToGallery = which == 1
                val intent = Intent(this, com.example.allinone.ui.drawing.DrawingActivity::class.java).apply {
                    putExtra(com.example.allinone.ui.drawing.DrawingActivity.EXTRA_SAVE_TO_GALLERY, saveToGallery)
                }
                drawingLauncher.launch(intent)
            }
            .show()
    }

    private fun setupButtons() {
        // Setup attachment options (image and video)
        binding.addAttachmentButton.setOnClickListener {
            showAttachmentOptions()
        }

        // Setup save FAB
        binding.saveFab.setOnClickListener {
            saveNote()
        }

        // Check if device is in dark mode
        val isNightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            // In dark mode, use a blue color
            // Using a nice shade of blue that works well in dark mode
            val darkModeBlue = android.graphics.Color.rgb(41, 121, 255) // #2979FF - Blue A400
            binding.saveFab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(darkModeBlue))
            binding.saveFab.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        } else {
            // In light mode, keep it black and white
            binding.saveFab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK))
            binding.saveFab.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
    }

    private fun showAttachmentOptions() {
        val options = arrayOf(
            getString(R.string.add_image),
            getString(R.string.add_video)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_attachment))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> getImageContent.launch("image/*")
                    1 -> getVideoContent.launch("video/*")
                }
            }
            .show()
    }

    private fun setupVoiceRecordingUI() {
        // Setup voice notes recycler view
        voiceNoteAdapter = VoiceNoteAdapter(
            voiceNotes,
            onPlayClick = { voiceNote -> playVoiceNote(voiceNote) },
            onDeleteClick = { position -> deleteVoiceNote(position) }
        )
        binding.voiceNotesRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@EditNoteActivity)
            adapter = voiceNoteAdapter
        }

        // Setup recording button
        binding.recordButton?.setOnClickListener {
            if (checkAudioPermission()) {
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            } else {
                requestAudioPermissions()
            }
        }

        // Initially hide recording time
        binding.recordingTimeText?.visibility = View.GONE
        binding.stopRecordingButton?.visibility = View.GONE

        // Setup stop button
        binding.stopRecordingButton?.setOnClickListener {
            if (isRecording) {
                stopRecording()
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(
                    this,
                    "Permission to record audio denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startRecording() {
        try {
            // Create file to store recording
            val fileName = "voice_note_${System.currentTimeMillis()}.mp3"
            val dir = File(cacheDir, "voice_notes")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            recordingFile = File(dir, fileName)

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
            }

            // Update UI to show recording state
            isRecording = true
            binding.recordButton?.setText(R.string.recording)
            binding.recordButton?.icon = null
            binding.stopRecordingButton?.visibility = View.VISIBLE
            binding.recordingTimeText?.visibility = View.VISIBLE
            recordingStartTime = System.currentTimeMillis()
            recordingTimeHandler.post(recordingTimeRunnable)

            Toast.makeText(this, getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Error starting recording: ${e.message}")
            Toast.makeText(this, getString(R.string.recording_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            // Update UI
            isRecording = false
            binding.recordButton?.setText(R.string.record)
            binding.recordButton?.setIconResource(android.R.drawable.ic_btn_speak_now)
            binding.stopRecordingButton?.visibility = View.GONE
            binding.recordingTimeText?.visibility = View.GONE
            recordingTimeHandler.removeCallbacks(recordingTimeRunnable)

            // Save the recording
            recordingFile?.let { file ->
                // Create voice note object with sequential ID
                CoroutineScope(Dispatchers.Main).launch {
                    val voiceNoteId = idManager.getNextId("voice_notes").toString()

                    val voiceNote = VoiceNote(
                        id = voiceNoteId,
                        fileName = file.name,
                        filePath = file.absolutePath,
                        duration = getDuration(file),
                        timestamp = System.currentTimeMillis(),
                        firebaseUrl = ""  // Will be updated after upload
                    )

                    voiceNotes.add(voiceNote)
                    voiceNoteAdapter.notifyItemInserted(voiceNotes.size - 1)

                    // Upload to Firebase
                    uploadVoiceNoteToFirebase(voiceNote, file)
                }
            }

            Toast.makeText(this, getString(R.string.recording_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            Toast.makeText(this, getString(R.string.error_saving_recording), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            return durationStr?.toLong() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            return 0
        } finally {
            retriever.release()
        }
    }

    private fun uploadVoiceNoteToFirebase(voiceNote: VoiceNote, file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Upload file to Firebase Storage
                val downloadUrl = storageUtil.uploadFile(
                    fileUri = Uri.fromFile(file),
                    folderName = "note-attachments",
                    id = noteId?.toString() ?: "temp" // Use note ID as the subfolder or "temp" for new notes
                )

                if (downloadUrl != null) {
                    Log.d(TAG, "Voice note uploaded successfully")
                    withContext(Dispatchers.Main) {
                        // Update voice note with Firebase URL
                        val index = voiceNotes.indexOf(voiceNote)
                        if (index != -1) {
                            voiceNotes[index] = voiceNote.copy(firebaseUrl = downloadUrl)
                            voiceNoteAdapter.notifyItemChanged(index)
                            Log.d(TAG, "Voice note at index $index updated with Firebase URL")

                            // If this is an existing note, update the note's voiceNoteUris field
                            noteId?.let { id ->
                                viewModel.allNotes.value?.find { it.id == id }?.let { existingNote ->
                                    // Get current voice note URIs
                                    val currentUris = existingNote.voiceNoteUris?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                                    val updatedUris = currentUris.toMutableList()

                                    // Add the new URL if it's not already in the list
                                    if (!updatedUris.contains(downloadUrl)) {
                                        updatedUris.add(downloadUrl)
                                    }

                                    // Create updated note with the new voice note URIs
                                    val updatedVoiceNoteUris = updatedUris.joinToString(",")
                                    val updatedNote = existingNote.copy(
                                        voiceNoteUris = updatedVoiceNoteUris,
                                        lastEdited = Date()
                                    )

                                    // Update the note in the database
                                    viewModel.updateNote(updatedNote)
                                    Log.d(TAG, "Updated note with new voice note URL")

                                    // Force refresh notes to ensure UI consistency
                                    // No need to refresh here as updateNote already triggers a refresh
                                }
                            }

                            // If this is a new note, we need to save the URL for later use when the note is created
                            if (noteId == null) {
                                // Save the URL to be used when the note is created
                                Log.d(TAG, "Preparing to save new note with voice note")
                                // We'll use the voiceNotes list when saving the note

                                // For new notes, we need to immediately save the note to Firestore
                                // to ensure the voice note URL is properly stored
                                val title = binding.editNoteTitle.text.toString().trim()
                                val content = binding.editNoteContent.toHtml()

                                if (title.isNotEmpty()) {
                                    // Get all voice note URLs
                                    val voiceNoteUrls = voiceNotes.mapNotNull { vn ->
                                        if (vn.firebaseUrl.isNotEmpty() && vn.firebaseUrl.startsWith("http")) {
                                            vn.firebaseUrl
                                        } else {
                                            null
                                        }
                                    }

                                    // Create voice note URIs string
                                    val voiceNoteUris = if (voiceNoteUrls.isNotEmpty()) {
                                        voiceNoteUrls.joinToString(",")
                                    } else {
                                        null
                                    }

                                    Log.d(TAG, "Creating new note with voice note")

                                    // Save the note with the current voice note URLs
                                    viewModel.addNote(
                                        title = title,
                                        content = content,
                                        voiceNoteUris = voiceNoteUris
                                    )

                                    // Update the noteId to the newly created note's ID
                                    noteId = viewModel.getLastCreatedNoteId()
                                    isNewNote = false

                                    Log.d(TAG, "Created new note with ID: $noteId")

                                    // Show toast to inform user
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@EditNoteActivity,
                                            "Note saved with voice recording",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Log.d(TAG, "Cannot save note yet - title is empty")
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get download URL for voice note")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading voice note: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditNoteActivity,
                        "Failed to upload voice note",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun playVoiceNote(voiceNote: VoiceNote) {
        try {
            // Stop any currently playing voice note
            voiceNoteAdapter.stopPlayback()

            // Create and configure MediaPlayer
            MediaPlayer().apply {
                setDataSource(voiceNote.filePath)
                setOnPreparedListener {
                    start()
                    voiceNoteAdapter.updatePlaybackState(true, voiceNotes.indexOf(voiceNote), this)
                }
                setOnCompletionListener {
                    voiceNoteAdapter.updatePlaybackState(false, -1)
                    release()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@EditNoteActivity, getString(R.string.error_playing_audio), Toast.LENGTH_SHORT).show()
                    voiceNoteAdapter.updatePlaybackState(false, -1)
                    release()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing voice note: ${e.message}")
            Toast.makeText(this, getString(R.string.error_playing_audio), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopRecording()
        }
        voiceNoteAdapter.releaseMediaPlayer()
    }

    private fun deleteVoiceNote(position: Int) {
        if (position >= 0 && position < voiceNotes.size) {
            val voiceNote = voiceNotes[position]

            // Show confirmation dialog
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_voice_note)
                .setMessage(R.string.delete_voice_note_confirmation)
                .setPositiveButton(R.string.delete) { _, _ ->
                    // Delete from Firebase Storage if URL exists
                    if (voiceNote.firebaseUrl.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val success = storageUtil.deleteFile(voiceNote.firebaseUrl)

                                if (success && !isNewNote && noteId != null) {
                                    // Update the note's voiceNoteUris field to remove this URL
                                    updateNoteAfterVoiceNoteDeletion(voiceNote.firebaseUrl)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting voice note from Firebase: ${e.message}")
                            }
                        }
                    }

                    // Delete local file if it exists and is not a Firebase URL
                    try {
                        if (!voiceNote.filePath.startsWith("http")) {
                            val file = File(voiceNote.filePath)
                            file.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting local voice note file: ${e.message}")
                    }

                    // Remove from list and update adapter
                    voiceNotes.removeAt(position)
                    voiceNoteAdapter.notifyItemRemoved(position)

                    Toast.makeText(this, getString(R.string.voice_note_deleted), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updateNoteAfterVoiceNoteDeletion(deletedUrl: String) {
        noteId?.let { id ->
            viewModel.allNotes.value?.find { it.id == id }?.let { existingNote ->
                // Filter out the deleted URL from voiceNoteUris
                val currentUris = existingNote.voiceNoteUris?.split(",")?.filter {
                    it.isNotEmpty() && it != deletedUrl
                } ?: emptyList()

                // Create updated voice note URIs string
                val updatedVoiceNoteUris = if (currentUris.isEmpty()) null else currentUris.joinToString(",")

                // Create updated note with the new voice note URIs
                val updatedNote = existingNote.copy(
                    voiceNoteUris = updatedVoiceNoteUris,
                    lastEdited = Date()
                )

                // Update the note in the database
                viewModel.updateNote(updatedNote)
            }
        }
    }

    private fun loadNoteData() {
        noteId?.let { id ->
            // Get the note by ID from the view model
            viewModel.allNotes.observe(this) { notes ->
                // Find the specific note with this ID
                val note = notes.find { it.id == id }

                note?.let { foundNote ->
                    // Pre-fill the form
                    binding.editNoteTitle.setText(foundNote.title)
                    binding.editNoteContent.fromHtml(foundNote.content)

                    // Load images
                    selectedImages.clear()
                    foundNote.imageUris?.split(",")?.forEach { uriString ->
                        if (uriString.isNotEmpty()) {
                            try {
                                val uri = Uri.parse(uriString)
                                selectedImages.add(uri)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing image URI: $uriString", e)
                            }
                        }
                    }
                    updateImageAttachmentSection()

                    // Load videos
                    selectedVideos.clear()
                    foundNote.videoUris?.split(",")?.forEach { uriString ->
                        if (uriString.isNotEmpty()) {
                            try {
                                val uri = Uri.parse(uriString)
                                selectedVideos.add(uri)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing video URI: $uriString", e)
                            }
                        }
                    }
                    updateVideoAttachmentSection()

                    // Load voice notes if any exist
                    voiceNotes.clear()

                    if (!foundNote.voiceNoteUris.isNullOrEmpty()) {
                        loadVoiceNotesFromUris(foundNote.voiceNoteUris)
                    } else {
                        binding.voiceNotesRecyclerView?.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun loadVoiceNotesFromUris(voiceNoteUrisString: String) {
        Log.d(TAG, "Loading voice notes from URIs: $voiceNoteUrisString")
        val voiceNoteUrls = voiceNoteUrisString.split(",").filter { it.isNotEmpty() && it.startsWith("http") }

        if (voiceNoteUrls.isNotEmpty()) {
            Log.d(TAG, "Found ${voiceNoteUrls.size} valid voice note URLs")
            // Create voice note objects for each URL
            CoroutineScope(Dispatchers.Main).launch {
                for (url in voiceNoteUrls) {
                    try {
                        val fileName = url.substring(url.lastIndexOf('/') + 1)
                        val voiceNoteId = idManager.getNextId("voice_notes").toString()

                        Log.d(TAG, "Creating voice note object for URL: $url")
                        voiceNotes.add(
                            VoiceNote(
                                id = voiceNoteId,
                                fileName = fileName,
                                filePath = url, // Use remote URL as filePath for playback
                                duration = 0, // Duration unknown until played
                                timestamp = Date().time,
                                firebaseUrl = url
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing voice note URL: $url", e)
                    }
                }

                updateVoiceNotesList()
            }
        } else {
            Log.d(TAG, "No valid voice note URLs found in: $voiceNoteUrisString")
            binding.voiceNotesRecyclerView?.visibility = View.GONE
        }
    }

    private fun updateVoiceNotesList() {
        if (voiceNotes.isNotEmpty()) {
            Log.d(TAG, "Updating voice notes list with ${voiceNotes.size} items")
            voiceNoteAdapter.notifyDataSetChanged()
            binding.voiceNotesRecyclerView?.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "No voice notes to display")
            binding.voiceNotesRecyclerView?.visibility = View.GONE
        }
    }

    private fun saveNote() {
        val title = binding.editNoteTitle.text.toString().trim()
        val content = binding.editNoteContent.toHtml()

        if (title.isBlank()) {
            Toast.makeText(this, getString(R.string.please_enter_title), Toast.LENGTH_SHORT).show()
            return
        }

        // First ensure we only have valid images and videos
        updateImageAttachmentSection()
        updateVideoAttachmentSection()

        // Process and upload new images that need to be uploaded to Firebase
        val processedImageUris = mutableListOf<String>()
        val processedVideoUris = mutableListOf<String>()

        // Process voice notes for saving

        if (selectedImages.isNotEmpty() || selectedVideos.isNotEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                val dialog = MaterialAlertDialogBuilder(this@EditNoteActivity)
                    .setTitle(R.string.saving)
                    .setMessage(R.string.uploading_attachments)
                    .setCancelable(false)
                    .create()
                dialog.show()

                try {
                    val imageUploadJobs = selectedImages.map { uri ->
                        CoroutineScope(Dispatchers.IO).async {
                            // Skip urls that are already uploaded (http/https)
                            if (uri.toString().startsWith("http")) {
                                processedImageUris.add(uri.toString())
                                return@async
                            }

                            // Upload new image to Firebase
                            val downloadUrl = uploadImageToFirebase(uri)
                            if (downloadUrl != null) {
                                processedImageUris.add(downloadUrl)
                            } else {
                                // Keep original URI if upload fails
                                processedImageUris.add(uri.toString())
                            }
                        }
                    }

                    val videoUploadJobs = selectedVideos.map { uri ->
                        CoroutineScope(Dispatchers.IO).async {
                            // Skip urls that are already uploaded (http/https)
                            if (uri.toString().startsWith("http")) {
                                processedVideoUris.add(uri.toString())
                                return@async
                            }

                            // Upload new video to Firebase
                            val downloadUrl = uploadVideoToFirebase(uri)
                            if (downloadUrl != null) {
                                processedVideoUris.add(downloadUrl)
                            } else {
                                // Keep original URI if upload fails
                                processedVideoUris.add(uri.toString())
                            }
                        }
                    }

                    // Wait for all uploads to complete
                    (imageUploadJobs + videoUploadJobs).awaitAll()

                    dialog.dismiss()

                    // Convert processed URIs to comma-separated string
                    val imageUris = if (processedImageUris.isNotEmpty()) {
                        processedImageUris.joinToString(",")
                    } else {
                        null
                    }

                    val videoUris = if (processedVideoUris.isNotEmpty()) {
                        processedVideoUris.joinToString(",")
                    } else {
                        null
                    }

                    // Convert voice note URIs to comma-separated string
                    val voiceNoteUris = if (voiceNotes.isNotEmpty()) {
                        // Only save Firebase URLs for permanent storage
                        val filteredUrls = voiceNotes.mapNotNull {
                            if (it.firebaseUrl.isNotEmpty() && it.firebaseUrl.startsWith("http")) {
                                it.firebaseUrl
                            } else {
                                null
                            }
                        }

                        if (filteredUrls.isEmpty()) {
                            null
                        } else {
                            filteredUrls.joinToString(",")
                        }
                    } else {
                        null
                    }

                    finalizeSaveNote(title, content, imageUris, videoUris, voiceNoteUris)
                } catch (e: Exception) {
                    dialog.dismiss()
                    Toast.makeText(
                        this@EditNoteActivity,
                        "Error uploading images: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e(TAG, "Error uploading images: ${e.message}", e)

                    // Fallback to original URIs if uploads fail
                    val imageUris = if (selectedImages.isNotEmpty()) {
                        selectedImages.joinToString(",") { it.toString() }
                    } else {
                        null
                    }

                    val videoUris = if (selectedVideos.isNotEmpty()) {
                        selectedVideos.joinToString(",") { it.toString() }
                    } else {
                        null
                    }

                    // Convert voice note URIs to comma-separated string
                    val voiceNoteUris = if (voiceNotes.isNotEmpty()) {
                        // Only save Firebase URLs for permanent storage
                        val filteredUrls = voiceNotes.mapNotNull {
                            if (it.firebaseUrl.isNotEmpty() && it.firebaseUrl.startsWith("http")) {
                                it.firebaseUrl
                            } else {
                                null
                            }
                        }

                        if (filteredUrls.isEmpty()) {
                            null
                        } else {
                            filteredUrls.joinToString(",")
                        }
                    } else {
                        null
                    }

                    finalizeSaveNote(title, content, imageUris, videoUris, voiceNoteUris)
                }
            }
        } else {
            // No images to upload
            val voiceNoteUris = if (voiceNotes.isNotEmpty()) {
                // Only save Firebase URLs for permanent storage
                val filteredUrls = voiceNotes.mapNotNull {
                    if (it.firebaseUrl.isNotEmpty() && it.firebaseUrl.startsWith("http")) {
                        it.firebaseUrl
                    } else {
                        null
                    }
                }

                if (filteredUrls.isEmpty()) {
                    null
                } else {
                    filteredUrls.joinToString(",")
                }
            } else {
                null
            }

            finalizeSaveNote(title, content, null, null, voiceNoteUris)
        }
    }

    private fun finalizeSaveNote(title: String, content: String, imageUris: String?, videoUris: String?, voiceNoteUris: String?) {
        if (isNewNote) {

            viewModel.addNote(
                title = title,
                content = content,
                imageUris = imageUris,
                videoUris = videoUris,
                voiceNoteUris = voiceNoteUris
            )
            // Note saved successfully
            Toast.makeText(this, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
        } else {
            noteId?.let { id ->
                // Get the current note to compare image URIs
                viewModel.allNotes.value?.find { it.id == id }?.let { existingNote ->
                    // Check for deleted images
                    val existingImageUris = existingNote.imageUris?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                    val currentImageUris = imageUris?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

                    // Find images that were deleted (in existing note but not in current list)
                    val deletedImages = existingImageUris.filter { uri -> !currentImageUris.contains(uri) }

                    // Delete removed images from Firebase Storage
                    if (deletedImages.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            deletedImages.forEach { imageUrl ->
                                try {
                                    if (imageUrl.contains("firebase")) {
                                        storageUtil.deleteFile(imageUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error deleting image from Firebase: ${e.message}")
                                }
                            }
                        }
                    }

                    // Check existing note data
                }

                // Get the existing note to preserve fields that might not be updated
                val existingNote = viewModel.allNotes.value?.find { it.id == id }

                // Create updated note, preserving voice note URIs if not explicitly set
                val updatedNote = Note(
                    id = id,
                    title = title,
                    content = content,
                    date = existingNote?.date ?: Date(),
                    imageUris = imageUris,
                    videoUris = videoUris ?: existingNote?.videoUris,
                    // If voiceNoteUris is null but existingNote has voice notes, preserve them
                    voiceNoteUris = voiceNoteUris ?: existingNote?.voiceNoteUris,
                    lastEdited = Date(),
                    isRichText = existingNote?.isRichText ?: true
                )
                viewModel.updateNote(updatedNote)
                // Note updated successfully
                Toast.makeText(this, getString(R.string.note_updated), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            }
        }

        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit_note, menu)

        // Hide delete option for new notes
        menu.findItem(R.id.action_delete).isVisible = !isNewNote

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete -> {
                deleteNote()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteNote() {
        noteId?.let { id ->
            viewModel.allNotes.observe(this) { notes ->
                // Find the specific note with this ID
                val note = notes.find { it.id == id }

                note?.let { foundNote ->
                    // Show confirmation dialog
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete_note)
                        .setMessage(R.string.delete_note_confirmation)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            viewModel.deleteNote(foundNote)
                            Toast.makeText(this, getString(R.string.note_deleted), Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun showFullscreenImage(uri: Uri) {
        try {
            // Get all images from the current note
            val allImages = mutableListOf<String>()
            var initialPosition = 0

            // Add all selected images to the list
            if (selectedImages.isNotEmpty()) {
                selectedImages.forEach { imageUri ->
                    allImages.add(imageUri.toString())
                }
                // Find the position of the clicked image
                initialPosition = selectedImages.indexOf(uri).coerceAtLeast(0)
            } else {
                // Just show this single image
                allImages.add(uri.toString())
            }

            // Create and show the fullscreen dialog
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_fullscreen_image, null)
            val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(dialogView as View)

            // Setup ViewPager
            val viewPager = dialogView.findViewById<ViewPager2>(R.id.fullscreenViewPager)
            val imageCounter = dialogView.findViewById<TextView>(R.id.imageCounterText)

            // Setup adapter for the ViewPager
            val adapter = com.example.allinone.adapters.FullscreenImageAdapter(this, allImages)
            viewPager.adapter = adapter

            // Set initial position
            viewPager.setCurrentItem(initialPosition, false)

            // Update counter text
            if (allImages.size > 1) {
                imageCounter.visibility = View.VISIBLE
                imageCounter.text = getString(R.string.image_counter, initialPosition + 1, allImages.size)

                // Add page change listener to update counter
                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        imageCounter.text = getString(R.string.image_counter, position + 1, allImages.size)
                    }
                })
            } else {
                imageCounter.visibility = View.GONE
            }

            dialog.show()

            // Close on tap
            dialogView.setOnClickListener {
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing fullscreen image: ${e.message}", e)
            Toast.makeText(this, "Error showing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImageToFirebase(imageUri: Uri): String? = runBlocking {
        try {
            // Upload file to Firebase Storage using the new folder structure
            val downloadUrl = storageUtil.uploadFile(
                fileUri = imageUri,
                folderName = "note-attachments",
                id = noteId?.toString() ?: "temp" // Use note ID as the subfolder or "temp" for new notes
            )
            return@runBlocking downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image: ${e.message}")
            return@runBlocking null
        }
    }

    private fun uploadVideoToFirebase(videoUri: Uri): String? = runBlocking {
        try {
            // Upload file to Firebase Storage using the new folder structure
            val downloadUrl = storageUtil.uploadFile(
                fileUri = videoUri,
                folderName = "note-attachments",
                id = noteId?.toString() ?: "temp" // Use note ID as the subfolder or "temp" for new notes
            )
            return@runBlocking downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading video: ${e.message}")
            return@runBlocking null
        }
    }
}