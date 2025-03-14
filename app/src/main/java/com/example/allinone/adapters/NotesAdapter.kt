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
import java.text.SimpleDateFormat
import java.util.Locale

class NotesAdapter(private val onNoteClick: (Note) -> Unit) : 
    ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        holder.bind(note)
        holder.itemView.setOnClickListener { onNoteClick(note) }
    }

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.noteTitle)
        private val dateTextView: TextView = itemView.findViewById(R.id.noteDate)
        private val contentTextView: TextView = itemView.findViewById(R.id.noteContent)
        private val imageView: ImageView = itemView.findViewById(R.id.noteImage)
        private val shareButton: ImageButton = itemView.findViewById(R.id.shareButton)
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        contentTextView.text = Html.fromHtml(processedContent, Html.FROM_HTML_MODE_COMPACT)
                    } else {
                        @Suppress("DEPRECATION")
                        contentTextView.text = Html.fromHtml(processedContent)
                    }
                } catch (e: Exception) {
                    Log.e("NotesAdapter", "Error rendering note content: ${e.message}", e)
                    contentTextView.text = note.content
                }
            } else {
                contentTextView.text = ""
            }

            // Handle image if present
            if (!note.imageUris.isNullOrEmpty()) {
                // Use the first image from the list for preview
                val firstImageUri = note.imageUris.split(",").firstOrNull()
                if (firstImageUri != null) {
                    imageView.visibility = View.VISIBLE
                    try {
                        imageView.setImageURI(Uri.parse(firstImageUri))
                    } catch (e: Exception) {
                        Log.e("NotesAdapter", "Error loading image: ${e.message}", e)
                        imageView.visibility = View.GONE
                    }
                } else {
                    imageView.visibility = View.GONE
                }
            } else {
                imageView.visibility = View.GONE
            }
        }

        private fun processNoteContent(content: String): String {
            var processedContent = content

            // Make "Attached Images:" text bold
            processedContent = processedContent.replace(
                "Attached Images:",
                "<b>Attached Images:</b>"
            )

            // Fix numbered lists by ensuring proper HTML format
            processedContent = processedContent.replace(
                Regex("\\n\\d+\\.\\s"),
                "<br/><ol><li>"
            ).replace(
                Regex("(?<=</li>)(?!\\n\\d+\\.\\s)"),
                "</ol>"
            )

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