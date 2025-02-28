package com.example.allinone.adapters

import android.net.Uri
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(note: Note) {
            titleTextView.text = note.title
            dateTextView.text = dateFormat.format(note.lastEdited)
            
            // Always render content as HTML to ensure proper display
            if (note.content.isNotEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    contentTextView.text = Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION")
                    contentTextView.text = Html.fromHtml(note.content)
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
                        imageView.visibility = View.GONE
                    }
                } else {
                    imageView.visibility = View.GONE
                }
            } else if (note.imageUri != null) {
                // For backward compatibility with older notes
                imageView.visibility = View.VISIBLE
                try {
                    imageView.setImageURI(Uri.parse(note.imageUri))
                } catch (e: Exception) {
                    imageView.visibility = View.GONE
                }
            } else {
                imageView.visibility = View.GONE
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