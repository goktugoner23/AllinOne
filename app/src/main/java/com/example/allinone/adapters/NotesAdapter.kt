package com.example.allinone.adapters

import android.content.Intent
import android.net.Uri
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Note
import com.example.allinone.viewmodels.NotesViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import com.bumptech.glide.Glide
import java.io.File
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.widget.Toast

class NotesAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onImageClick: (Uri) -> Unit = { },
    private val viewModel: NotesViewModel? = null
) :
    ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view, onImageClick)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        holder.bind(note)
        holder.itemView.setOnClickListener { onNoteClick(note) }
    }

    inner class NoteViewHolder(
        itemView: View,
        private val onImageClick: (Uri) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.noteTitle)
        private val dateTextView: TextView = itemView.findViewById(R.id.noteDate)
        private val contentTextView: TextView = itemView.findViewById(R.id.noteContent)
        private val shareButton: ImageButton = itemView.findViewById(R.id.shareButton)
        private val voiceNoteIndicator: View = itemView.findViewById(R.id.voiceNoteIndicator)
        private val voiceNoteCountText: TextView = itemView.findViewById(R.id.voiceNoteCountText)
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(note: Note) {
            titleTextView.text = note.title
            dateTextView.text = dateFormat.format(note.lastEdited)

            // Set up share button
            shareButton.setOnClickListener {
                shareNote(note)
            }

            // Always render content as HTML to ensure proper display
            if (note.content.isNotEmpty()) {
                try {
                    val processedContent = processNoteContent(note.content)
                    val spannableText = makeCheckboxesClickable(processedContent, note)
                    contentTextView.text = spannableText
                    contentTextView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                } catch (e: Exception) {
                    Log.e("NotesAdapter", "Error rendering note content: ${e.message}", e)
                    contentTextView.text = note.content
                }
            } else {
                contentTextView.text = ""
            }

            // Handle voice notes if present
            try {
                if (!note.voiceNoteUris.isNullOrEmpty()) {
                    val voiceNoteUris = note.voiceNoteUris.split(",").filter { it.isNotEmpty() }
                    Log.d("NotesAdapter", "Note ${note.id} has voiceNoteUris: ${note.voiceNoteUris}")

                    if (voiceNoteUris.isNotEmpty()) {
                        Log.d("NotesAdapter", "Note ${note.id} has ${voiceNoteUris.size} voice notes: ${voiceNoteUris.joinToString()}")
                        voiceNoteIndicator.visibility = View.VISIBLE
                        val voiceNoteCount = voiceNoteUris.size
                        voiceNoteCountText.text = if (voiceNoteCount == 1) {
                            itemView.context.getString(R.string.voice_note_singular)
                        } else {
                            itemView.context.getString(R.string.voice_note_plural, voiceNoteCount)
                        }

                        // Make sure attachments section is visible
                        val attachmentsSection = itemView.findViewById<ViewGroup>(R.id.attachmentsSection)
                        attachmentsSection.visibility = View.VISIBLE
                    } else {
                        // If no valid URIs in memory, check Firestore directly
                        viewModel?.checkNoteVoiceNotes(note.id) { hasVoiceNotes ->
                            if (hasVoiceNotes) {
                                Log.d("NotesAdapter", "Note ${note.id} has voice notes in Firestore")
                                voiceNoteIndicator.visibility = View.VISIBLE
                                voiceNoteCountText.text = itemView.context.getString(R.string.voice_note_singular)
                                voiceNoteIndicator.requestLayout()
                                val attachmentsSection = itemView.findViewById<ViewGroup>(R.id.attachmentsSection)
                                attachmentsSection.visibility = View.VISIBLE
                            } else {
                                Log.d("NotesAdapter", "Note ${note.id} has no voice notes in Firestore")
                                voiceNoteIndicator.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    // If no voice notes in memory, check Firestore directly
                    viewModel?.checkNoteVoiceNotes(note.id) { hasVoiceNotes ->
                        if (hasVoiceNotes) {
                            Log.d("NotesAdapter", "Note ${note.id} has voice notes in Firestore")
                            voiceNoteIndicator.visibility = View.VISIBLE
                            voiceNoteCountText.text = itemView.context.getString(R.string.voice_note_singular)
                            voiceNoteIndicator.requestLayout()
                            val attachmentsSection = itemView.findViewById<ViewGroup>(R.id.attachmentsSection)
                            attachmentsSection.visibility = View.VISIBLE
                        } else {
                            Log.d("NotesAdapter", "Note ${note.id} has no voice notes in Firestore")
                            voiceNoteIndicator.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error handling voice notes for note ${note.id}: ${e.message}", e)
                // If there's an error, check Firestore directly as a fallback
                viewModel?.checkNoteVoiceNotes(note.id) { hasVoiceNotes ->
                    if (hasVoiceNotes) {
                        Log.d("NotesAdapter", "Note ${note.id} has voice notes in Firestore")
                        voiceNoteIndicator.visibility = View.VISIBLE
                        voiceNoteCountText.text = itemView.context.getString(R.string.voice_note_singular)
                        voiceNoteIndicator.requestLayout()
                        val attachmentsSection = itemView.findViewById<ViewGroup>(R.id.attachmentsSection)
                        attachmentsSection.visibility = View.VISIBLE
                    } else {
                        Log.d("NotesAdapter", "Note ${note.id} has no voice notes in Firestore")
                        voiceNoteIndicator.visibility = View.GONE
                    }
                }
            }

            // Handle images if present
            val imageContainer = itemView.findViewById<ViewGroup>(R.id.imageContainer)
            imageContainer.removeAllViews()

            if (!note.imageUris.isNullOrEmpty()) {
                imageContainer.visibility = View.VISIBLE

                // Split by comma and process each URI
                val imageUris = note.imageUris.split(",").filter { it.isNotEmpty() }

                // For each image URI, create and add an ImageView
                for (uriString in imageUris) {
                    try {
                        val uri = Uri.parse(uriString)
                        // Skip invalid or non-existent URIs
                        if (!isValidImageUri(uri)) {
                            continue
                        }
                        
                        val imageView = ImageView(itemView.context).apply {
                            layoutParams = ViewGroup.LayoutParams(120, 120)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setPadding(4, 4, 4, 4)
                            setOnClickListener { onImageClick(uri) }
                        }

                        Glide.with(itemView.context)
                            .load(uri)
                            .placeholder(R.drawable.ic_image)
                            .error(android.R.drawable.ic_menu_close_clear_cancel)
                            .into(imageView)

                        imageContainer.addView(imageView)
                    } catch (e: Exception) {
                        Log.e("NotesAdapter", "Error adding image preview: ${e.message}", e)
                    }
                }
            } else {
                imageContainer.visibility = View.GONE
            }

            // Handle videos if present  
            if (!note.videoUris.isNullOrEmpty()) {
                val videoUris = note.videoUris.split(",").filter { it.isNotEmpty() }
                
                // For each video URI, create and add a video thumbnail view
                for (uriString in videoUris) {
                    try {
                        val uri = Uri.parse(uriString)
                        // Skip invalid or non-existent URIs
                        if (!isValidVideoUri(uri)) {
                            continue
                        }
                        
                        val videoView = ImageView(itemView.context).apply {
                            layoutParams = ViewGroup.LayoutParams(120, 120)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setPadding(4, 4, 4, 4)
                            setOnClickListener { 
                                // Play video when clicked
                                playVideo(itemView.context, uri)
                            }
                        }

                        // Set placeholder first
                        videoView.setImageResource(R.drawable.ic_video_placeholder)
                        
                        // Try to load video thumbnail with Glide
                        Glide.with(itemView.context)
                            .load(uri)
                            .placeholder(R.drawable.ic_video_placeholder)
                            .error(R.drawable.ic_video_error)
                            .into(videoView)

                        imageContainer.addView(videoView)
                        imageContainer.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        Log.e("NotesAdapter", "Error adding video preview: ${e.message}", e)
                    }
                }
            }
        }

        private fun processNoteContent(content: String): String {
            var processedContent = content

            // Make "Attached Images:" text bold
            processedContent = processedContent.replace(
                "Attached Images:",
                "<b>Attached Images:</b>"
            )

            // Handle checkbox lists - keep as plain text for display
            processedContent = processedContent.replace("☐", "☐")
            processedContent = processedContent.replace("☑", "☑")

            // Fix bullet lists
            processedContent = processedContent.replace(
                Regex("\\n•\\s"),
                "<br/><ul><li>"
            ).replace(
                Regex("(?<=</li>)(?!\\n•\\s)"),
                "</ul>"
            )

            return processedContent
        }

        private fun shareNote(note: Note) {
            val plainText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(note.content).toString()
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, note.title)
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                putExtra(Intent.EXTRA_TEXT, plainText)
                type = "text/plain"
            }

            itemView.context.startActivity(Intent.createChooser(shareIntent, "Share Note"))
        }
        
        private fun playVideo(context: android.content.Context, uri: Uri) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    android.widget.Toast.makeText(context, "No video player found", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error playing video: ${e.message}", e)
                android.widget.Toast.makeText(context, "Error playing video", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        private fun isValidImageUri(uri: Uri): Boolean {
            return when (uri.scheme) {
                "content" -> true  // Content provider URI
                "file" -> File(uri.path ?: "").exists()  // File exists check
                "http", "https" -> true  // Remote URLs
                else -> false  // Invalid scheme
            }
        }

        private fun isValidVideoUri(uri: Uri): Boolean {
            return when (uri.scheme) {
                "content" -> true  // Content provider URI
                "file" -> File(uri.path ?: "").exists()  // File exists check
                "http", "https" -> true  // Remote URLs
                else -> false  // Invalid scheme
            }
        }

        private fun makeCheckboxesClickable(content: String, note: Note): SpannableString {
            val spannableString = SpannableString(content)
            
            // Find all checkbox patterns (☐ and ☑)
            val checkboxPattern = "[☐☑]".toRegex()
            val matches = checkboxPattern.findAll(content)
            
            for (match in matches) {
                val start = match.range.first
                val end = match.range.last + 1
                val checkbox = match.value
                
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        toggleCheckbox(note, start, checkbox)
                    }
                    
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false // Remove underline from clickable text
                    }
                }
                
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            return spannableString
        }
        
        private fun toggleCheckbox(note: Note, checkboxPosition: Int, currentCheckbox: String) {
            try {
                val content = note.content.toCharArray()
                
                // Toggle the checkbox at the specific position
                if (checkboxPosition < content.size) {
                    content[checkboxPosition] = when (currentCheckbox) {
                        "☐" -> '☑'
                        "☑" -> '☐'
                        else -> return
                    }
                    
                    // Update the note content
                    val updatedContent = String(content)
                    val updatedNote = note.copy(
                        content = updatedContent,
                        lastEdited = java.util.Date()
                    )
                    
                    // Update through viewModel if available
                    viewModel?.updateNote(updatedNote)
                    
                    // Show feedback to user
                    Toast.makeText(itemView.context, "Checkbox toggled", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error toggling checkbox: ${e.message}", e)
                Toast.makeText(itemView.context, "Error updating checkbox", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}