package com.example.allinone.data.local.dao

import androidx.room.*
import com.example.allinone.data.local.entities.CachedProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedProgramDao {
    
    @Query("SELECT * FROM cached_programs ORDER BY name ASC")
    fun getAllPrograms(): Flow<List<CachedProgramEntity>>
    
    @Query("SELECT * FROM cached_programs WHERE id = :id")
    suspend fun getProgramById(id: Long): CachedProgramEntity?
    
    @Query("SELECT * FROM cached_programs WHERE name LIKE '%' || :searchQuery || '%' OR description LIKE '%' || :searchQuery || '%' ORDER BY name ASC")
    fun searchPrograms(searchQuery: String): Flow<List<CachedProgramEntity>>
    
    @Query("SELECT * FROM cached_programs ORDER BY lastModifiedDate DESC")
    fun getProgramsByLastModified(): Flow<List<CachedProgramEntity>>
    
    @Query("SELECT * FROM cached_programs ORDER BY createdDate DESC")
    fun getProgramsByCreated(): Flow<List<CachedProgramEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: CachedProgramEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPrograms(programs: List<CachedProgramEntity>)
    
    @Update
    suspend fun updateProgram(program: CachedProgramEntity)
    
    @Delete
    suspend fun deleteProgram(program: CachedProgramEntity)
    
    @Query("DELETE FROM cached_programs WHERE id = :id")
    suspend fun deleteProgramById(id: Long)
    
    @Query("DELETE FROM cached_programs")
    suspend fun deleteAllPrograms()
    
    @Query("SELECT COUNT(*) FROM cached_programs")
    suspend fun getProgramCount(): Int
    
    @Query("DELETE FROM cached_programs WHERE cachedAt < :expiredTime")
    suspend fun deleteExpiredPrograms(expiredTime: Long)
} 