package com.example.allinone.feature.notes.domain.repository

import androidx.lifecycle.LiveData
import com.example.allinone.data.Note
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for Note repository operations
 * This interface defines the contract for note data operations
 * following clean architecture principles
 */
interface NoteRepository {
    
    // Reactive data access
    val notes: Flow<List<Note>>
    val isLoading: LiveData<Boolean>
    val errorMessage: LiveData<String?>
    
    // Core operations
    suspend fun refreshNotes()
    suspend fun getNotes(): List<Note>
    suspend fun insertNote(note: Note)
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(note: Note)
    suspend fun getNoteById(id: Long): Note?
    
    // Search and filtering
    fun searchNotes(query: String): Flow<List<Note>>
    fun getRecentlyEditedNotes(since: Long): Flow<List<Note>>
    fun getNotesByType(isRichText: Boolean): Flow<List<Note>>
    
    // Error handling
    fun clearErrorMessage()
} 