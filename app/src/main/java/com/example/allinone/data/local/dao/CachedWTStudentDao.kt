package com.example.allinone.data.local.dao

import androidx.room.*
import com.example.allinone.data.local.entities.CachedWTStudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedWTStudentDao {
    
    @Query("SELECT * FROM cached_wt_students ORDER BY name ASC")
    fun getAllStudents(): Flow<List<CachedWTStudentEntity>>
    
    @Query("SELECT * FROM cached_wt_students WHERE id = :id")
    suspend fun getStudentById(id: Long): CachedWTStudentEntity?
    
    @Query("SELECT * FROM cached_wt_students WHERE name LIKE '%' || :searchQuery || '%' OR email LIKE '%' || :searchQuery || '%' ORDER BY name ASC")
    fun searchStudents(searchQuery: String): Flow<List<CachedWTStudentEntity>>
    
    @Query("SELECT * FROM cached_wt_students WHERE isActive = :isActive ORDER BY name ASC")
    fun getStudentsByActiveStatus(isActive: Boolean): Flow<List<CachedWTStudentEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: CachedWTStudentEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStudents(students: List<CachedWTStudentEntity>)
    
    @Update
    suspend fun updateStudent(student: CachedWTStudentEntity)
    
    @Delete
    suspend fun deleteStudent(student: CachedWTStudentEntity)
    
    @Query("DELETE FROM cached_wt_students WHERE id = :id")
    suspend fun deleteStudentById(id: Long)
    
    @Query("DELETE FROM cached_wt_students")
    suspend fun deleteAllStudents()
    
    @Query("SELECT COUNT(*) FROM cached_wt_students")
    suspend fun getStudentCount(): Int
    
    @Query("DELETE FROM cached_wt_students WHERE cachedAt < :cutoffTime")
    suspend fun deleteOldCache(cutoffTime: Long)
} 