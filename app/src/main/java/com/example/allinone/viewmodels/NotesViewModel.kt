package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Note
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _allNotes = MutableLiveData<List<Note>>(emptyList())
    val allNotes: LiveData<List<Note>> = _allNotes
    
    init {
        // Collect notes from the repository flow
        viewModelScope.launch {
            repository.notes.collect { notes ->
                _allNotes.value = notes
            }
        }
    }

    fun addNote(title: String, content: String, imageUris: String? = null) {
        viewModelScope.launch {
            val note = Note(
                id = UUID.randomUUID().mostSignificantBits,
                title = title,
                content = content,
                date = Date(),
                imageUris = imageUris,
                lastEdited = Date(),
                isRichText = true
            )
            // Add note to Firebase
            repository.insertNote(note)
            
            // Refresh notes to ensure UI consistency
            repository.refreshNotes()
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            // Update note in Firebase
            repository.updateNote(note)
            
            // Refresh notes to ensure UI consistency
            repository.refreshNotes()
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            // Delete note from Firebase
            repository.deleteNote(note)
            
            // Refresh notes to ensure UI consistency
            repository.refreshNotes()
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            repository.refreshNotes()
        }
    }
} 