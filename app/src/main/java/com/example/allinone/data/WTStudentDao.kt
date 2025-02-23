package com.example.allinone.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WTStudentDao {
    @Query("SELECT * FROM wt_students ORDER BY startDate DESC")
    fun getAllStudents(): Flow<List<WTStudent>>

    @Query("SELECT * FROM wt_students WHERE isPaid = 0 ORDER BY startDate DESC")
    fun getUnpaidStudents(): Flow<List<WTStudent>>

    @Query("SELECT * FROM wt_students WHERE isPaid = 1 ORDER BY paymentDate DESC")
    fun getPaidStudents(): Flow<List<WTStudent>>

    @Insert
    suspend fun insertStudent(student: WTStudent)

    @Update
    suspend fun updateStudent(student: WTStudent)

    @Delete
    suspend fun deleteStudent(student: WTStudent)
} 