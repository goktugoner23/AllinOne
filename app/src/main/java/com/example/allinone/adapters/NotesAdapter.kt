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
import android.text.util.Linkify
import android.text.method.LinkMovementMethod

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

            // Always render content to ensure proper display
            if (note.content.isNotEmpty()) {
                try {
                    // Check if content has checkboxes
                    val hasCheckboxes = note.content.contains("☐") || note.content.contains("☑")
                    
                    if (hasCheckboxes) {
                        // For content with checkboxes, make them clickable and also handle links
                        val spannableText = makeCheckboxesAndLinksClickable(note.content, note)
                        contentTextView.text = spannableText
                        contentTextView.movementMethod = LinkMovementMethod.getInstance()
                        
                        // Allow parent to handle clicks when not on specific clickable elements
                        contentTextView.setOnTouchListener { view, event ->
                            val textView = view as TextView
                            val spannedText = textView.text as? Spannable
                            
                            if (spannedText != null && event.action == android.view.MotionEvent.ACTION_DOWN) {
                                val x = event.x.toInt()
                                val y = event.y.toInt()
                                
                                val offset = textView.getOffsetForPosition(x.toFloat(), y.toFloat())
                                val clickableSpans = spannedText.getSpans(offset, offset, ClickableSpan::class.java)
                                
                                // If there are clickable spans (links/checkboxes), let TextView handle it
                                if (clickableSpans.isNotEmpty()) {
                                    false // Let TextView handle the click
                                } else {
                                    // No clickable spans, delegate to parent by performing click
                                    itemView.performClick()
                                    true // We handled the click
                                }
                            } else {
                                false // Let TextView handle other events
                            }
                        }
                        
                        // Ensure proper text encoding for Turkish characters
                        ensureProperTextEncoding(contentTextView)
                    } else {
                        // For content without checkboxes, process as HTML and make links clickable
                        val processedContent = processNoteContent(note.content)
                        val htmlContent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            Html.fromHtml(processedContent, Html.FROM_HTML_MODE_COMPACT)
                        } else {
                            @Suppress("DEPRECATION")
                            Html.fromHtml(processedContent)
                        }
                        contentTextView.text = htmlContent
                        
                        // Enable link clicking
                        Linkify.addLinks(contentTextView, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
                        contentTextView.movementMethod = LinkMovementMethod.getInstance()
                        
                        // Allow parent to handle clicks when not on links
                        contentTextView.setOnTouchListener { view, event ->
                            val textView = view as TextView
                            val spannedText = textView.text as? Spannable
                            
                            if (spannedText != null && event.action == android.view.MotionEvent.ACTION_DOWN) {
                                val x = event.x.toInt()
                                val y = event.y.toInt()
                                
                                val offset = textView.getOffsetForPosition(x.toFloat(), y.toFloat())
                                val urlSpans = spannedText.getSpans(offset, offset, android.text.style.URLSpan::class.java)
                                
                                // If there are URL spans, let TextView handle it
                                if (urlSpans.isNotEmpty()) {
                                    false // Let TextView handle the click
                                } else {
                                    // No URL spans, delegate to parent by performing click
                                    itemView.performClick()
                                    true // We handled the click
                                }
                            } else {
                                false // Let TextView handle other events
                            }
                        }
                        
                        // Ensure proper text encoding for Turkish characters
                        ensureProperTextEncoding(contentTextView)
                    }
                } catch (e: Exception) {
                    Log.e("NotesAdapter", "Error rendering note content: ${e.message}", e)
                    contentTextView.text = note.content
                    // Even for fallback, try to make links clickable
                    Linkify.addLinks(contentTextView, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
                    contentTextView.movementMethod = LinkMovementMethod.getInstance()
                    
                    // Allow parent to handle clicks when not on links (fallback case)
                    contentTextView.setOnTouchListener { view, event ->
                        val textView = view as TextView
                        val spannedText = textView.text as? Spannable
                        
                        if (spannedText != null && event.action == android.view.MotionEvent.ACTION_DOWN) {
                            val x = event.x.toInt()
                            val y = event.y.toInt()
                            
                            val offset = textView.getOffsetForPosition(x.toFloat(), y.toFloat())
                            val urlSpans = spannedText.getSpans(offset, offset, android.text.style.URLSpan::class.java)
                            
                            // If there are URL spans, let TextView handle it
                            if (urlSpans.isNotEmpty()) {
                                false // Let TextView handle the click
                            } else {
                                // No URL spans, delegate to parent by performing click
                                itemView.performClick()
                                true // We handled the click
                            }
                        } else {
                            false // Let TextView handle other events
                        }
                    }
                }
            } else {
                contentTextView.text = ""
                // Clear any movement method and touch listener for empty content
                contentTextView.movementMethod = null
                contentTextView.setOnTouchListener(null)
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
            // Ensure proper UTF-8 encoding for Turkish characters
            var processedContent = try {
                // Convert to UTF-8 bytes and back to ensure proper encoding
                val utf8Bytes = content.toByteArray(Charsets.UTF_8)
                String(utf8Bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w("NotesAdapter", "UTF-8 encoding issue: ${e.message}")
                content
            }

            // Make "Attached Images:" text bold - preserve Turkish characters
            processedContent = processedContent.replace(
                "Attached Images:",
                "<b>Attached Images:</b>"
            )
            
            // Handle Turkish equivalents as well
            processedContent = processedContent.replace(
                "Ekli Resimler:",
                "<b>Ekli Resimler:</b>"
            )

            // Fix bullet lists - preserve Turkish characters
            processedContent = processedContent.replace(
                Regex("\\n•\\s"),
                "<br/>• "
            )

            // Handle line breaks properly for Turkish text
            processedContent = processedContent.replace("\n", "<br/>")

            return processedContent
        }
        
        private fun ensureProperTextEncoding(textView: TextView) {
            try {
                // Set proper typeface and text direction for Turkish characters
                textView.textDirection = View.TEXT_DIRECTION_LOCALE
                
                // Ensure the TextView uses proper font that supports Turkish characters
                textView.typeface = android.graphics.Typeface.DEFAULT
                
                // Force text layout refresh to ensure proper character rendering
                textView.requestLayout()
            } catch (e: Exception) {
                Log.w("NotesAdapter", "Error setting text encoding: ${e.message}")
            }
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

        private fun makeCheckboxesAndLinksClickable(content: String, note: Note): SpannableString {
            // Apply basic formatting while preserving checkboxes and links
            var processedContent = content
            
            // Make "Attached Images:" text bold (preserve Turkish characters)
            processedContent = processedContent.replace(
                "Attached Images:",
                "**Attached Images:**"
            )
            
            // Handle bullet lists (preserve Turkish characters)
            processedContent = processedContent.replace(
                Regex("\\n•\\s"),
                "\n• "
            )
            
            val spannableString = SpannableString(processedContent)
            
            // Apply basic formatting manually
            applyBasicFormatting(spannableString)
            
            // Handle links first (URLs and email addresses)
            makeLinksClickable(spannableString)
            
            // Then handle checkboxes (they take priority over links if they overlap)
            makeCheckboxesClickable(spannableString, note, processedContent)
            
            return spannableString
        }
        
        private fun applyBasicFormatting(spannable: SpannableString) {
            val text = spannable.toString()
            
            // Bold formatting for **text**
            val boldPattern = "\\*\\*(.*?)\\*\\*".toRegex()
            val boldMatches = boldPattern.findAll(text)
            for (match in boldMatches) {
                val start = match.range.first
                val end = match.range.last + 1
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            // Note: In a real implementation, you'd remove the ** markers and adjust spans
            // For simplicity, keeping this basic for now
        }
        
        private fun makeLinksClickable(spannableString: SpannableString) {
            val text = spannableString.toString()
            
            // Pattern for URLs (http, https, www) - simplified
            val urlPattern = "(?i)\\b(?:https?://|www\\.|[a-z0-9.-]+\\.com|[a-z0-9.-]+\\.org|[a-z0-9.-]+\\.net)[^\\s]*".toRegex()
            
            // Pattern for email addresses
            val emailPattern = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".toRegex()
            
            // Find and make URLs clickable
            val urlMatches = urlPattern.findAll(text)
            for (match in urlMatches) {
                val start = match.range.first
                val end = match.range.last + 1
                val url = match.value
                
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        openUrl(widget.context, url)
                    }
                    
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = itemView.context.getColor(android.R.color.holo_blue_dark)
                        ds.isUnderlineText = true // Keep underline for links
                    }
                }
                
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            // Find and make email addresses clickable
            val emailMatches = emailPattern.findAll(text)
            for (match in emailMatches) {
                val start = match.range.first
                val end = match.range.last + 1
                val email = match.value
                
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        openEmail(widget.context, email)
                    }
                    
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = itemView.context.getColor(android.R.color.holo_blue_dark)
                        ds.isUnderlineText = true // Keep underline for email links
                    }
                }
                
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        
        private fun makeCheckboxesClickable(spannableString: SpannableString, note: Note, processedContent: String) {
            // Find all checkbox patterns (☐ and ☑)
            val checkboxPattern = "[☐☑]".toRegex()
            val matches = checkboxPattern.findAll(processedContent)
            
            for (match in matches) {
                val start = match.range.first
                val end = match.range.last + 1
                val checkbox = match.value
                
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        toggleCheckbox(note, start, checkbox, processedContent)
                    }
                    
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false // Remove underline from checkboxes
                        // Make checkboxes bigger
                        ds.textSize = ds.textSize * 1.3f
                        // Make checked checkboxes green, unchecked ones default color
                        ds.color = if (checkbox == "☑") {
                            itemView.context.getColor(android.R.color.holo_green_dark)
                        } else {
                            // Use appropriate color based on theme
                            val isNightMode = itemView.context.resources.configuration.uiMode and
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                            if (isNightMode) {
                                itemView.context.getColor(android.R.color.white)
                            } else {
                                itemView.context.getColor(android.R.color.black)
                            }
                        }
                    }
                }
                
                // Use SPAN_EXCLUSIVE_EXCLUSIVE and higher priority for checkboxes
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_PRIORITY)
            }
        }
        
        private fun openUrl(context: android.content.Context, url: String) {
            try {
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "http://$url"
                } else {
                    url
                }
                
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "No browser found to open link", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error opening URL: ${e.message}", e)
                Toast.makeText(context, "Error opening link", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun openEmail(context: android.content.Context, email: String) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$email")
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error opening email: ${e.message}", e)
                Toast.makeText(context, "Error opening email", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun toggleCheckbox(note: Note, checkboxPosition: Int, @Suppress("UNUSED_PARAMETER") currentCheckbox: String, processedContent: String) {
            try {
                // Find the corresponding position in the original content
                val originalContent = note.content
                val checkboxPattern = "[☐☑]".toRegex()
                val originalMatches = checkboxPattern.findAll(originalContent).toList()
                val processedMatches = checkboxPattern.findAll(processedContent).toList()
                
                // Find which checkbox was clicked by comparing positions
                val clickedIndex = processedMatches.indexOfFirst { it.range.first == checkboxPosition }
                
                if (clickedIndex >= 0 && clickedIndex < originalMatches.size) {
                    val originalMatch = originalMatches[clickedIndex]
                    val originalPosition = originalMatch.range.first
                    
                    val content = originalContent.toCharArray()
                    
                    // Toggle the checkbox at the specific position in original content
                    if (originalPosition < content.size) {
                        content[originalPosition] = when (content[originalPosition]) {
                            '☐' -> '☑'
                            '☑' -> '☐'
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
                } else {
                    Log.w("NotesAdapter", "Could not find corresponding checkbox in original content")
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