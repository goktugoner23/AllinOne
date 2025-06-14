package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Note
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.firebase.DataChangeNotifier
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Date

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    private val idManager = FirebaseIdManager()

    private val _allNotes = MutableLiveData<List<Note>>(emptyList())
    val allNotes: LiveData<List<Note>> = _allNotes

    // Add isLoading property
    val isLoading: LiveData<Boolean> = repository.isLoading

    init {
        // Collect notes from the repository flow
        viewModelScope.launch {
            repository.notes.collect { notes ->
                _allNotes.value = notes
            }
        }
    }

    fun addNote(title: String, content: String, imageUris: String? = null, videoUris: String? = null, voiceNoteUris: String? = null) {
        viewModelScope.launch {
            // Get next sequential ID for notes
            val noteId = idManager.getNextId("notes")

            val note = Note(
                id = noteId,
                title = title,
                content = content,
                date = Date(),
                imageUris = imageUris,
                videoUris = videoUris,
                voiceNoteUris = voiceNoteUris,
                lastEdited = Date(),
                isRichText = true
            )
            // Add note to Firebase
            repository.insertNote(note)

            // Notify about data change
            DataChangeNotifier.notifyNotesChanged()

            // Refresh notes to ensure UI consistency
            repository.refreshNotes()
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            // Update note in Firebase
            repository.updateNote(note)

            // Notify about data change
            DataChangeNotifier.notifyNotesChanged()

            // Refresh notes to ensure UI consistency
            repository.refreshNotes()
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            // Delete note from Firebase
            repository.deleteNote(note)

            // Notify about data change
            DataChangeNotifier.notifyNotesChanged()

            // Refresh notes to ensure UI consistency
            repository.refreshNotes()
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshNotes()
        }
    }

    /**
     * Get the ID of the last created note
     * This is used when creating a new note with voice notes
     */
    fun getLastCreatedNoteId(): Long {
        // Get the highest ID from the current notes list
        return _allNotes.value?.maxByOrNull { it.id }?.id ?: 0L
    }

    /**
     * Check if a note has voice notes in Firestore
     * This is used to determine if the voice note indicator should be shown
     */
    fun checkNoteVoiceNotes(noteId: Long, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val note = _allNotes.value?.find { it.id == noteId }
            val hasVoiceNotes = !note?.voiceNoteUris.isNullOrEmpty()
            callback(hasVoiceNotes)
        }
    }
}