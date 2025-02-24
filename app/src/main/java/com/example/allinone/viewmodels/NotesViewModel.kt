package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Note
import com.example.allinone.data.TransactionDatabase
import kotlinx.coroutines.launch
import java.util.Date

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase.getDatabase(application)
    private val noteDao = database.noteDao()

    val allNotes: LiveData<List<Note>> = noteDao.getAllNotes().asLiveData()

    fun addNote(title: String, content: String, imageUri: String? = null) {
        viewModelScope.launch {
            val note = Note(
                title = title,
                content = content,
                date = Date(),
                imageUri = imageUri
            )
            noteDao.insertNote(note)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            noteDao.updateNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
        }
    }
} 