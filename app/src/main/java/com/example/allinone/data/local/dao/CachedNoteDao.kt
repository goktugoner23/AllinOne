package com.example.allinone.data.local.dao

import androidx.room.*
import com.example.allinone.data.local.entities.CachedNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedNoteDao {
    
    @Query("SELECT * FROM cached_notes ORDER BY lastEdited DESC")
    fun getAllNotes(): Flow<List<CachedNoteEntity>>
    
    @Query("SELECT * FROM cached_notes WHERE id = :id")
    suspend fun getNoteById(id: Long): CachedNoteEntity?
    
    @Query("SELECT * FROM cached_notes WHERE title LIKE '%' || :searchQuery || '%' OR content LIKE '%' || :searchQuery || '%' ORDER BY lastEdited DESC")
    fun searchNotes(searchQuery: String): Flow<List<CachedNoteEntity>>
    
    @Query("SELECT * FROM cached_notes WHERE isRichText = :isRichText ORDER BY lastEdited DESC")
    fun getNotesByType(isRichText: Boolean): Flow<List<CachedNoteEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: CachedNoteEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllNotes(notes: List<CachedNoteEntity>)
    
    @Update
    suspend fun updateNote(note: CachedNoteEntity)
    
    @Delete
    suspend fun deleteNote(note: CachedNoteEntity)
    
    @Query("DELETE FROM cached_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)
    
    @Query("DELETE FROM cached_notes")
    suspend fun deleteAllNotes()
    
    @Query("SELECT COUNT(*) FROM cached_notes")
    suspend fun getNoteCount(): Int
    
    @Query("DELETE FROM cached_notes WHERE cachedAt < :expiredTime")
    suspend fun deleteExpiredNotes(expiredTime: Long)
    
    @Query("SELECT * FROM cached_notes WHERE lastEdited >= :since ORDER BY lastEdited DESC")
    fun getRecentlyEditedNotes(since: Long): Flow<List<CachedNoteEntity>>
} 