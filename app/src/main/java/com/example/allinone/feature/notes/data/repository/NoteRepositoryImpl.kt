package com.example.allinone.feature.notes.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.allinone.data.Note
import com.example.allinone.feature.notes.data.datasource.NoteLocalDataSource
import com.example.allinone.feature.notes.data.datasource.NoteRemoteDataSource
import com.example.allinone.feature.notes.domain.repository.NoteRepository
import com.example.allinone.firebase.OfflineQueue
import com.example.allinone.utils.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combined repository that implements the DataSource pattern.
 * Uses local data source (Room) for caching and remote data source (Firebase) for synchronization.
 * This is the clean architecture approach from the refactoring plan.
 */
@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val localDataSource: NoteLocalDataSource,
    private val remoteDataSource: NoteRemoteDataSource,
    private val networkUtils: NetworkUtils,
    private val offlineQueue: OfflineQueue
) : NoteRepository {
    
    companion object {
        private const val TAG = "NoteRepository"
    }
    
    private val gson = Gson()
    
    // State management
    private val _isLoading = MutableLiveData<Boolean>(false)
    override val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    override val errorMessage: LiveData<String?> = _errorMessage
    
    override val notes: Flow<List<Note>> = localDataSource.getAllAsFlow()
    
    override suspend fun refreshNotes() {
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                
                if (!networkUtils.isActiveNetworkConnected()) {
                    Log.d(TAG, "No network connection, using cached notes")
                    _isLoading.postValue(false)
                    return@withContext
                }
                
                // Fetch from remote and cache locally
                val remoteNotes = remoteDataSource.getAll()
                localDataSource.saveAll(remoteNotes)
                
                Log.d(TAG, "Refreshed ${remoteNotes.size} notes from remote")
                _isLoading.postValue(false)
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing notes: ${e.message}")
                _isLoading.postValue(false)
                Log.e(TAG, "Error refreshing notes", e)
            }
        }
    }
    
    override suspend fun getNotes(): List<Note> {
        return try {
            // Try local first for immediate response
            val localNotes = localDataSource.getAllAsFlow().first()
            if (localNotes.isEmpty() && networkUtils.isActiveNetworkConnected()) {
                // If no local data and network is available, fetch from remote
                val remoteNotes = remoteDataSource.getAll()
                localDataSource.saveAll(remoteNotes)
                remoteNotes
            } else {
                localNotes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting notes: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun insertNote(note: Note) {
        try {
            // Save locally immediately for responsiveness
            localDataSource.save(note)
            
            // Then sync to remote if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                try {
                    remoteDataSource.save(note)
                    Log.d(TAG, "Note saved to remote: ${note.id}")
                } catch (e: Exception) {
                    // Queue for offline sync
                    queueOfflineOperation(note, OfflineQueue.Operation.INSERT)
                    _errorMessage.postValue("Note saved locally. Will sync when network is available.")
                }
            } else {
                queueOfflineOperation(note, OfflineQueue.Operation.INSERT)
                _errorMessage.postValue("Note saved locally. Will sync when network is available.")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving note: ${e.message}")
            Log.e(TAG, "Error inserting note", e)
        }
    }
    
    override suspend fun updateNote(note: Note) {
        try {
            // Update locally immediately
            localDataSource.save(note)
            
            // Then sync to remote if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                try {
                    remoteDataSource.save(note)
                    Log.d(TAG, "Note updated in remote: ${note.id}")
                } catch (e: Exception) {
                    // Queue for offline sync
                    queueOfflineOperation(note, OfflineQueue.Operation.UPDATE)
                    _errorMessage.postValue("Note updated locally. Will sync when network is available.")
                }
            } else {
                queueOfflineOperation(note, OfflineQueue.Operation.UPDATE)
                _errorMessage.postValue("Note updated locally. Will sync when network is available.")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating note: ${e.message}")
            Log.e(TAG, "Error updating note", e)
        }
    }
    
    override suspend fun deleteNote(note: Note) {
        try {
            // Delete locally immediately
            localDataSource.delete(note)
            
            // Then sync to remote if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                try {
                    remoteDataSource.delete(note)
                    Log.d(TAG, "Note deleted from remote: ${note.id}")
                } catch (e: Exception) {
                    // Queue for offline sync
                    queueOfflineOperation(note, OfflineQueue.Operation.DELETE)
                    _errorMessage.postValue("Note deleted locally. Will sync when network is available.")
                }
            } else {
                queueOfflineOperation(note, OfflineQueue.Operation.DELETE)
                _errorMessage.postValue("Note deleted locally. Will sync when network is available.")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting note: ${e.message}")
            Log.e(TAG, "Error deleting note", e)
        }
    }
    
    override suspend fun getNoteById(id: Long): Note? {
        return try {
            localDataSource.getById(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting note by id: ${e.message}")
            null
        }
    }
    
    override fun searchNotes(query: String): Flow<List<Note>> {
        return localDataSource.search(query)
    }
    
    override fun getRecentlyEditedNotes(since: Long): Flow<List<Note>> {
        return localDataSource.getRecentlyEdited(since)
    }
    
    override fun getNotesByType(isRichText: Boolean): Flow<List<Note>> {
        return localDataSource.getNotesByType(isRichText)
    }
    
    override fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Queue an operation for offline sync
     */
    private fun queueOfflineOperation(note: Note, operation: OfflineQueue.Operation) {
        try {
            offlineQueue.enqueue(
                OfflineQueue.DataType.NOTE,
                operation,
                gson.toJson(note)
            )
            Log.d(TAG, "Note operation queued for offline sync: $operation")
        } catch (e: Exception) {
            Log.e(TAG, "Error queueing offline operation: ${e.message}")
        }
    }
} 