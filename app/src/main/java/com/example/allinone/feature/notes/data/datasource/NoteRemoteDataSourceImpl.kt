package com.example.allinone.feature.notes.data.datasource

import android.util.Log
import com.example.allinone.data.Note
import com.example.allinone.firebase.FirebaseManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-based implementation of NoteRemoteDataSource
 * Handles remote synchronization of notes with Firebase Firestore
 */
@Singleton
class NoteRemoteDataSourceImpl @Inject constructor(
    private val firebaseManager: FirebaseManager
) : NoteRemoteDataSource {
    
    companion object {
        private const val TAG = "NoteRemoteDataSource"
    }
    
    override suspend fun getAll(): List<Note> {
        return try {
            firebaseManager.getNotes()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all notes from Firebase: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun getById(id: Long): Note? {
        return try {
            // Firebase doesn't have a direct getById for notes
            // We'll need to fetch all and filter (or implement specific query)
            getAll().find { it.id == id }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting note by id $id from Firebase: ${e.message}")
            null
        }
    }
    
    override suspend fun save(item: Note) {
        try {
            firebaseManager.saveNote(item)
            Log.d(TAG, "Note saved to Firebase with id: ${item.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving note to Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun saveAll(items: List<Note>) {
        try {
            items.forEach { note ->
                firebaseManager.saveNote(note)
            }
            Log.d(TAG, "Saved ${items.size} notes to Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notes to Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun delete(item: Note) {
        try {
            firebaseManager.deleteNote(item)
            Log.d(TAG, "Note deleted from Firebase with id: ${item.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note from Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun deleteById(id: Long) {
        try {
            // Create a temporary note object for deletion
            val tempNote = Note(
                id = id,
                title = "",
                content = "",
                date = java.util.Date(),
                imageUris = null,
                videoUris = null,
                voiceNoteUris = null,
                lastEdited = java.util.Date(),
                isRichText = false
            )
            firebaseManager.deleteNote(tempNote)
            Log.d(TAG, "Note deleted from Firebase with id: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note by id from Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun deleteAll() {
        try {
            // This would require a batch delete operation in Firebase
            // For now, we'll get all notes and delete them individually
            val allNotes = getAll()
            allNotes.forEach { note ->
                firebaseManager.deleteNote(note)
            }
            Log.d(TAG, "All notes deleted from Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all notes from Firebase: ${e.message}")
            throw e
        }
    }
    
    override suspend fun getCount(): Int {
        return try {
            getAll().size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting note count from Firebase: ${e.message}")
            0
        }
    }
} 