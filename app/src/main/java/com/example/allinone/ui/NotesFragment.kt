package com.example.allinone.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
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
import android.transition.TransitionManager
import android.transition.Slide
import android.view.Gravity
import android.app.Dialog
import com.github.chrisbanes.photoview.PhotoView
import com.bumptech.glide.Glide
import android.util.Log

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
    private var searchMenuItem: MenuItem? = null
    private var allNotes: List<Note> = emptyList()

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

    // Register for activity result from EditNoteActivity
    private val editNoteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            // Refresh notes data when a note is added, updated, or deleted
            viewModel.refreshData()
        }
    }

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
        setupMenu()
        observeNotes()
        setupSwipeRefresh()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.search_notes, menu)
                searchMenuItem = menu.findItem(R.id.action_search)

                val searchView = searchMenuItem?.actionView as? SearchView
                searchView?.let {
                    // Set search view expanded listener
                    it.setOnSearchClickListener { _ ->
                        // Animate search view expansion
                        val slide = Slide(Gravity.END)
                        slide.duration = 200
                        TransitionManager.beginDelayedTransition((activity as AppCompatActivity).findViewById(R.id.toolbar), slide)
                    }

                    // Set search view collapse listener
                    it.setOnCloseListener {
                        // Animate search view collapse
                        val slide = Slide(Gravity.END)
                        slide.duration = 200
                        TransitionManager.beginDelayedTransition((activity as AppCompatActivity).findViewById(R.id.toolbar), slide)

                        // Reset the notes list
                        resetNotesList()
                        true
                    }

                    // Set up query listener
                    it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            query?.let { searchQuery ->
                                filterNotes(searchQuery)
                            }

                            // Hide keyboard
                            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            imm.hideSoftInputFromWindow(it.windowToken, 0)

                            return true
                        }

                        override fun onQueryTextChange(newText: String?): Boolean {
                            newText?.let { searchQuery ->
                                filterNotes(searchQuery)
                            }
                            return true
                        }
                    })

                    // Customize search view appearance
                    val searchEditText = it.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                    searchEditText?.apply {
                        setHintTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        hint = getString(R.string.search_hint)
                        imeOptions = EditorInfo.IME_ACTION_SEARCH

                        // Set X button to reset search
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                // Hide keyboard
                                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.hideSoftInputFromWindow(this.windowToken, 0)
                                return@setOnEditorActionListener true
                            }
                            false
                        }
                    }

                    // Set search icon color to white (expanded state)
                    val searchIcon = it.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
                    searchIcon?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white),
                        android.graphics.PorterDuff.Mode.SRC_IN)

                    // Make sure search icon is visible and properly sized
                    searchIcon?.apply {
                        visibility = View.VISIBLE
                        val iconSize = resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dropdownitem_icon_width)
                        val params = layoutParams
                        params.width = iconSize
                        params.height = iconSize
                        layoutParams = params
                    }

                    // Set close button color to white
                    val closeButton = it.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                    closeButton?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white),
                        android.graphics.PorterDuff.Mode.SRC_IN)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_search -> {
                        // The search icon click is handled by the SearchView
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun resetNotesList() {
        // Sort notes by last edited date (newest first)
        val sortedNotes = allNotes.sortedByDescending { it.lastEdited }
        notesAdapter.submitList(sortedNotes)
        binding.emptyStateText.visibility = if (sortedNotes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun filterNotes(query: String) {
        val filteredNotes = if (query.isBlank()) {
            allNotes
        } else {
            allNotes.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                // For content, remove HTML tags before searching
                (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT).toString()
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(note.content).toString()
                }).contains(query, ignoreCase = true)
            }
        }

        // Sort filtered notes by last edited date (newest first)
        val sortedFilteredNotes = filteredNotes.sortedByDescending { it.lastEdited }
        notesAdapter.submitList(sortedFilteredNotes)
        binding.emptyStateText.visibility = if (sortedFilteredNotes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onNoteClick = { note ->
                // Use the activity result launcher instead of direct startActivity
                editNoteLauncher.launch(EditNoteActivity.newIntent(requireContext(), note.id))
            },
            onImageClick = { uri -> showFullscreenImage(uri) },
            viewModel = viewModel // Pass the viewModel to the adapter
        )

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notesAdapter
        }
    }

    private fun setupFab() {
        binding.addNoteFab.setOnClickListener {
            // Use the activity result launcher instead of direct startActivity
            editNoteLauncher.launch(EditNoteActivity.newIntent(requireContext()))
        }
    }

    private fun observeNotes() {
        viewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            // Store all notes for filtering
            allNotes = notes

            // Apply search filter if search is active
            val searchView = searchMenuItem?.actionView as? SearchView
            if (searchView?.isIconified == false && !searchView.query.isNullOrEmpty()) {
                filterNotes(searchView.query.toString())
            } else {
                // Sort notes by last edited date (newest first)
                val sortedNotes = notes.sortedByDescending { it.lastEdited }
                notesAdapter.submitList(sortedNotes)
                binding.emptyStateText.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }

        // Observe loading state to hide the refresh indicator when done
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
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

    private fun showFullscreenImage(uri: Uri) {
        try {
            val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val photoView = PhotoView(requireContext()).apply {
                try {
                    // Use Glide to load the image
                    Glide.with(requireContext())
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
                    Log.e("NotesFragment", "Error loading image: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Log.e("NotesFragment", "Error showing fullscreen image: ${e.message}", e)
            Toast.makeText(requireContext(), "Error showing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dialogBinding = null
    }
}