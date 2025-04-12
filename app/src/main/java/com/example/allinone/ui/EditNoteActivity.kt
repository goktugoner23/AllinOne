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
import com.example.allinone.data.Note
import com.example.allinone.databinding.ActivityEditNoteBinding
import com.example.allinone.viewmodels.NotesViewModel
import io.github.mthli.knife.KnifeText
import java.util.Date
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.app.Dialog
import com.github.chrisbanes.photoview.PhotoView
import com.bumptech.glide.Glide
import com.example.allinone.ui.drawing.DrawingActivity

class EditNoteActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var viewModel: NotesViewModel
    private lateinit var imageAdapter: NoteImageAdapter
    
    private val selectedImages = mutableListOf<Uri>()
    private var isNewNote = true
    private var noteId: Long? = null
    
    // Image picker launcher
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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
        
        viewModel = ViewModelProvider(this)[NotesViewModel::class.java]
        
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) noteId = null
        isNewNote = noteId == null
        
        setupToolbar()
        setupImageRecyclerView()
        setupRichTextEditor()
        setupButtons()
        
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
                selectedImages.remove(uri)
                updateImageAttachmentSection()
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
    
    private fun updateImageAttachmentSection() {
        // Filter out any invalid URIs
        val validImages = selectedImages.filter { uri ->
            val isValid = try {
                contentResolver.getType(uri) != null
            } catch (e: Exception) {
                false
            }
            isValid
        }.toMutableList()
        
        // Update the list if needed
        if (validImages.size != selectedImages.size) {
            selectedImages.clear()
            selectedImages.addAll(validImages)
        }
        
        // Update adapter and visibility
        imageAdapter.submitList(selectedImages.toList())
        binding.imagesRecyclerView.visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
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
        
        binding.numberedListButton.setOnClickListener {
            // For numbered lists, we'll insert actual numbers
            insertNumberedList()
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
    
    private fun insertNumberedList() {
        try {
            val editor = binding.editNoteContent
            val start = editor.selectionStart
            val end = editor.selectionEnd
            
            // If text is selected, apply to each line
            if (start != end) {
                val selectedText = editor.text.toString().substring(start, end)
                val lines = selectedText.split("\n")
                val builder = StringBuilder()
                
                for (i in lines.indices) {
                    builder.append("${i+1}. ${lines[i]}\n")
                }
                
                // Replace the selected text with numbered lines
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
                
                // Check if the line already has a number
                val line = text.substring(lineStart, lineEnd)
                val numberPattern = "^\\d+\\.\\s*".toRegex()
                
                if (numberPattern.containsMatchIn(line)) {
                    // If already numbered, remove the numbering
                    val unnumbered = line.replace(numberPattern, "")
                    editor.text.replace(lineStart, lineEnd, unnumbered)
                    editor.setSelection(lineStart + unnumbered.length)
                } else {
                    // Add a number to the line
                    val numbered = "1. $line"
                    editor.text.replace(lineStart, lineEnd, numbered)
                    editor.setSelection(lineStart + numbered.length)
                }
            }
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error applying numbered list: ${e.message}", e)
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
        // Setup image attachment
        binding.addAttachmentButton.setOnClickListener {
            getContent.launch("image/*")
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
                            selectedImages.add(Uri.parse(uriString))
                        }
                    }
                    updateImageAttachmentSection()
                }
            }
        }
    }
    
    private fun saveNote() {
        val title = binding.editNoteTitle.text.toString()
        val content = binding.editNoteContent.toHtml()
        
        if (title.isBlank()) {
            Toast.makeText(this, getString(R.string.please_enter_title), Toast.LENGTH_SHORT).show()
            return
        }
        
        // First ensure we only have valid images
        updateImageAttachmentSection()
        
        // Convert selected images to comma-separated string
        val imageUris = if (selectedImages.isNotEmpty()) {
            selectedImages.joinToString(",") { it.toString() }
        } else {
            null
        }
        
        if (isNewNote) {
            viewModel.addNote(
                title = title,
                content = content,
                imageUris = imageUris
            )
            Toast.makeText(this, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
        } else {
            noteId?.let { id ->
                val updatedNote = Note(
                    id = id,
                    title = title,
                    content = content,
                    imageUris = imageUris,
                    lastEdited = Date()
                )
                viewModel.updateNote(updatedNote)
                Toast.makeText(this, getString(R.string.note_updated), Toast.LENGTH_SHORT).show()
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
            val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val photoView = PhotoView(this).apply {
                try {
                    // Use Glide to load the image
                    Glide.with(this@EditNoteActivity)
                        .load(uri)
                        .placeholder(R.drawable.ic_image)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(this)
                    
                    // Set layout parameters
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                } catch (e: Exception) {
                    Log.e("EditNoteActivity", "Error loading image: ${e.message}", e)
                    Toast.makeText(this@EditNoteActivity, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Set content view and show dialog
            dialog.setContentView(photoView)
            dialog.show()

            // Add click listener to dismiss on tap
            photoView.setOnClickListener {
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error showing fullscreen image: ${e.message}", e)
            Toast.makeText(this, "Error showing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 