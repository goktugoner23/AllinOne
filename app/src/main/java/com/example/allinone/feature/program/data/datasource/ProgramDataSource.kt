package com.example.allinone.feature.program.data.datasource

import com.example.allinone.core.data.datasource.LocalDataSource
import com.example.allinone.core.data.datasource.RemoteDataSource
import com.example.allinone.core.data.datasource.SearchableDataSource
import com.example.allinone.data.Program
import kotlinx.coroutines.flow.Flow

/**
 * Remote DataSource interface for Program operations via Firebase
 */
interface ProgramRemoteDataSource : RemoteDataSource<Program>

/**
 * Local DataSource interface for Program operations via Room
 */
interface ProgramLocalDataSource : SearchableDataSource<Program>, LocalDataSource<Program> {
    fun getProgramsByLastModified(): Flow<List<Program>>
    fun getProgramsByCreated(): Flow<List<Program>>
} 