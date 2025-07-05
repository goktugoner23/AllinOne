package com.example.allinone.feature.notes.data.datasource

import com.example.allinone.core.data.datasource.LocalDataSource
import com.example.allinone.core.data.datasource.RemoteDataSource
import com.example.allinone.core.data.datasource.SearchableDataSource
import com.example.allinone.data.Note
import kotlinx.coroutines.flow.Flow

/**
 * Remote DataSource interface for Note operations via Firebase
 */
interface NoteRemoteDataSource : RemoteDataSource<Note>

/**
 * Local DataSource interface for Note operations via Room
 */
interface NoteLocalDataSource : SearchableDataSource<Note>, LocalDataSource<Note> {
    fun getRecentlyEdited(since: Long): Flow<List<Note>>
    fun getNotesByType(isRichText: Boolean): Flow<List<Note>>
} 