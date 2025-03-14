package com.example.allinone.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.adapters.NoteImageAdapter
import com.example.allinone.adapters.NotesAdapter
import com.example.allinone.data.Note
import com.example.allinone.databinding.DialogEditNoteBinding
import com.example.allinone.databinding.FragmentNotesBinding
import com.example.allinone.viewmodels.NotesViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.mthli.knife.KnifeText
import java.util.Date

// Extension property to get HTML content from KnifeText
val KnifeText.html: String
    get() = this.toHtml()

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotesViewModel by viewModels()
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var imageAdapter: NoteImageAdapter
    private val selectedImages = mutableListOf<Uri>()
    
    private val getContent = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris?.let { selectedUris ->
            selectedUris.forEach { uri ->
                try {
                    // Take persistable permission for the URI
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    selectedImages.add(uri)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to save image permission: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            // Update the recycler view in the dialog
            dialogBinding?.let { binding ->
                val adapter = binding.imagesRecyclerView.adapter as NoteImageAdapter
                adapter.submitList(selectedImages.toList())
                binding.imagesRecyclerView.visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
    
    private var dialogBinding: DialogEditNoteBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupFab()
        observeNotes()
    }
    
    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onNoteClick = { note -> showEditNoteDialog(note) }
        )
        
        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notesAdapter
        }
    }
    
    private fun setupFab() {
        binding.addNoteFab.setOnClickListener {
            showAddNoteDialog()
        }
    }
    
    private fun observeNotes() {
        viewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            // Sort notes by last edited date (newest first)
            val sortedNotes = notes.sortedByDescending { it.lastEdited }
            notesAdapter.submitList(sortedNotes)
            binding.emptyStateText.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }
    
    private fun showAddNoteDialog() {
        // Reset selected images
        selectedImages.clear()
        
        val dialogBinding = DialogEditNoteBinding.inflate(layoutInflater)
        this.dialogBinding = dialogBinding
        
        // Setup image recycler view
        imageAdapter = NoteImageAdapter(
            onDeleteClick = { uri -> 
                selectedImages.remove(uri)
                imageAdapter.submitList(selectedImages.toList())
                dialogBinding.imagesRecyclerView.visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
            }
        )
        dialogBinding.imagesRecyclerView.adapter = imageAdapter
        dialogBinding.imagesRecyclerView.visibility = View.GONE
        
        // Setup rich text editor
        setupRichTextEditor(dialogBinding)
        
        // Setup image attachment
        dialogBinding.addAttachmentButton.setOnClickListener {
            getContent.launch("image/*")
        }
        
        // Setup share button - disabled for new notes
        dialogBinding.shareNoteButton.visibility = View.GONE
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Note")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null) // We'll set this later to prevent auto-dismiss
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            
        // Set the positive button click listener after creating the dialog
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            
            // Set button colors
            positiveButton.setTextColor(requireContext().getColor(R.color.white))
            negativeButton.setTextColor(requireContext().getColor(R.color.white))
            
            positiveButton.setOnClickListener {
                val title = dialogBinding.editNoteTitle.text.toString()
                val content = dialogBinding.editNoteContent.html
                
                if (title.isBlank()) {
                    Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Convert selected images to comma-separated string
                val imageUris = if (selectedImages.isNotEmpty()) {
                    selectedImages.joinToString(",") { it.toString() }
                } else {
                    null
                }
                
                viewModel.addNote(
                    title = title,
                    content = content,
                    imageUris = imageUris
                )
                
                dialog.dismiss()
                Toast.makeText(requireContext(), "Note saved", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    private fun showEditNoteDialog(note: Note) {
        // Reset and populate selected images
        selectedImages.clear()
        note.imageUris?.split(",")?.forEach { uriString ->
            selectedImages.add(Uri.parse(uriString))
        }
        
        val dialogBinding = DialogEditNoteBinding.inflate(layoutInflater)
        this.dialogBinding = dialogBinding
        
        // Pre-fill the form
        dialogBinding.editNoteTitle.setText(note.title)
        dialogBinding.editNoteContent.fromHtml(note.content)
        
        // Setup image recycler view
        imageAdapter = NoteImageAdapter(
            onDeleteClick = { uri -> 
                selectedImages.remove(uri)
                imageAdapter.submitList(selectedImages.toList())
                dialogBinding.imagesRecyclerView.visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
            }
        )
        dialogBinding.imagesRecyclerView.adapter = imageAdapter
        imageAdapter.submitList(selectedImages.toList())
        dialogBinding.imagesRecyclerView.visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
        
        // Setup rich text editor
        setupRichTextEditor(dialogBinding)
        
        // Setup image attachment
        dialogBinding.addAttachmentButton.setOnClickListener {
            getContent.launch("image/*")
        }
        
        // Setup share button
        dialogBinding.shareNoteButton.setOnClickListener {
            shareNote(note, dialogBinding.editNoteTitle.text.toString(), dialogBinding.editNoteContent.html)
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Note")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null) // We'll set this later to prevent auto-dismiss
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Delete") { dialog, _ ->
                showDeleteConfirmation(note)
                dialog.dismiss()
            }
            .create()
            
        // Set the positive button click listener after creating the dialog
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            
            // Set button colors
            positiveButton.setTextColor(requireContext().getColor(R.color.white))
            negativeButton.setTextColor(requireContext().getColor(R.color.white))
            neutralButton.setTextColor(requireContext().getColor(R.color.red))
            
            positiveButton.setOnClickListener {
                val title = dialogBinding.editNoteTitle.text.toString()
                val content = dialogBinding.editNoteContent.html
                
                if (title.isBlank()) {
                    Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Convert selected images to comma-separated string
                val imageUris = if (selectedImages.isNotEmpty()) {
                    selectedImages.joinToString(",") { it.toString() }
                } else {
                    null
                }
                
                val updatedNote = note.copy(
                    title = title,
                    content = content,
                    imageUris = imageUris,
                    lastEdited = Date()
                )
                
                viewModel.updateNote(updatedNote)
                
                dialog.dismiss()
                Toast.makeText(requireContext(), "Note updated", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun shareNote(unused: Note, title: String, content: String) {
        val plainText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(content).toString()
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, plainText)
            type = "text/plain"
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Note"))
    }
    
    private fun setupRichTextEditor(dialogBinding: DialogEditNoteBinding) {
        // Setup formatting buttons
        dialogBinding.boldButton.setOnClickListener {
            dialogBinding.editNoteContent.bold(!dialogBinding.editNoteContent.contains(KnifeText.FORMAT_BOLD))
        }
        
        dialogBinding.italicButton.setOnClickListener {
            dialogBinding.editNoteContent.italic(!dialogBinding.editNoteContent.contains(KnifeText.FORMAT_ITALIC))
        }
        
        dialogBinding.underlineButton.setOnClickListener {
            dialogBinding.editNoteContent.underline(!dialogBinding.editNoteContent.contains(KnifeText.FORMAT_UNDERLINED))
        }
        
        dialogBinding.bulletListButton.setOnClickListener {
            dialogBinding.editNoteContent.bullet(!dialogBinding.editNoteContent.contains(KnifeText.FORMAT_BULLET))
        }
        
        dialogBinding.numberedListButton.setOnClickListener {
            // Apply ordered list formatting
            applyOrderedList(dialogBinding.editNoteContent)
        }
        
        dialogBinding.addImageButton.setOnClickListener {
            // This inserts an image directly into the text content
            // Different from attachments which are shown separately
            getContent.launch("image/*")
        }
    }
    
    private fun applyOrderedList(editor: KnifeText) {
        // Check if there's already an ordered list at the cursor position
        val currentText = editor.html
        val isOrderedList = currentText.contains("<ol>") && currentText.contains("</ol>")
        
        if (isOrderedList) {
            // Remove ordered list formatting
            val processedText = currentText
                .replace("<ol>", "")
                .replace("</ol>", "")
                .replace("<li>", "")
                .replace("</li>", "\n")
            
            editor.fromHtml(processedText)
        } else {
            // Get cursor position to apply ordered list formatting
            val selectionStart = editor.selectionStart
            val selectionEnd = editor.selectionEnd
            val text = editor.text.toString()
            
            // Check if there's text selected
            if (selectionStart != selectionEnd) {
                // Get the selected text lines
                val selectedText = text.substring(selectionStart, selectionEnd)
                val lines = selectedText.split("\n")
                
                // Create ordered list HTML
                val orderedListHtml = StringBuilder("<ol>")
                for (line in lines) {
                    if (line.isNotEmpty()) {
                        orderedListHtml.append("<li>").append(line).append("</li>")
                    }
                }
                orderedListHtml.append("</ol>")
                
                // Replace selected text with ordered list HTML
                editor.fromHtml(
                    text.substring(0, selectionStart) + 
                    orderedListHtml.toString() + 
                    text.substring(selectionEnd)
                )
            } else {
                // If no text is selected, insert an empty ordered list at the current line
                
                // Find the beginning of the current line
                var lineStart = selectionStart
                while (lineStart > 0 && text[lineStart - 1] != '\n') {
                    lineStart--
                }
                
                // Find the end of the current line
                var lineEnd = selectionStart
                while (lineEnd < text.length && text[lineEnd] != '\n') {
                    lineEnd++
                }
                
                // Get the current line text
                val currentLine = text.substring(lineStart, lineEnd).trim()
                
                // Create HTML for the ordered list
                val htmlToInsert = if (currentLine.isEmpty()) {
                    "<ol><li></li></ol>"
                } else {
                    "<ol><li>$currentLine</li></ol>"
                }
                
                // Replace the current line with the ordered list HTML
                editor.fromHtml(
                    text.substring(0, lineStart) + 
                    htmlToInsert + 
                    text.substring(lineEnd)
                )
                
                // Set cursor inside the list item
                editor.setSelection(lineStart + htmlToInsert.length - 5)  // Position inside <li></li>
            }
        }
    }
    
    private fun showDeleteConfirmation(note: Note) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteNote(note)
                Toast.makeText(requireContext(), "Note deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dialogBinding = null
    }
} 