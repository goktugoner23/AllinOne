package com.example.allinone.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.adapters.NotesAdapter
import com.example.allinone.data.Note
import com.example.allinone.viewmodels.NotesViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NotesFragment : Fragment() {

    private lateinit var notesViewModel: NotesViewModel
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var emptyStateView: View
    private lateinit var addNoteFab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notes, container, false)
        
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView)
        emptyStateView = view.findViewById(R.id.emptyStateText)
        addNoteFab = view.findViewById(R.id.addNoteFab)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupRecyclerView()
        setupFab()
    }

    private fun setupViewModel() {
        notesViewModel = ViewModelProvider(this)[NotesViewModel::class.java]
        
        notesViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            notesAdapter.submitList(notes)
            
            // Show empty state if no notes
            if (notes.isEmpty()) {
                emptyStateView.visibility = View.VISIBLE
                notesRecyclerView.visibility = View.GONE
            } else {
                emptyStateView.visibility = View.GONE
                notesRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter { note ->
            showNoteDetailDialog(note)
        }
        
        notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notesAdapter
        }
    }

    private fun setupFab() {
        addNoteFab.setOnClickListener {
            showAddNoteDialog()
        }
    }

    private fun showAddNoteDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val titleInput = EditText(context).apply {
            hint = "Title"
            layout.addView(this)
        }

        val contentInput = EditText(context).apply {
            hint = "Content"
            minLines = 3
            layout.addView(this)
        }

        AlertDialog.Builder(context)
            .setTitle("Add New Note")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                
                if (title.isNotEmpty() && content.isNotEmpty()) {
                    notesViewModel.addNote(title, content)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNoteDetailDialog(note: Note) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val titleInput = EditText(context).apply {
            setText(note.title)
            layout.addView(this)
        }

        val contentInput = EditText(context).apply {
            setText(note.content)
            minLines = 5
            layout.addView(this)
        }

        AlertDialog.Builder(context)
            .setTitle("Edit Note")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val updatedTitle = titleInput.text.toString().trim()
                val updatedContent = contentInput.text.toString().trim()
                
                if (updatedTitle.isNotEmpty() && updatedContent.isNotEmpty()) {
                    val updatedNote = note.copy(
                        title = updatedTitle,
                        content = updatedContent
                    )
                    notesViewModel.updateNote(updatedNote)
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmationDialog(note)
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(note: Note) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                notesViewModel.deleteNote(note)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
} 