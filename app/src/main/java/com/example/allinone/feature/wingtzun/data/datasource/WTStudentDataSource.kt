package com.example.allinone.feature.wingtzun.data.datasource

import com.example.allinone.core.data.datasource.LocalDataSource
import com.example.allinone.core.data.datasource.RemoteDataSource
import com.example.allinone.core.data.datasource.SearchableDataSource
import com.example.allinone.data.WTStudent
import kotlinx.coroutines.flow.Flow

/**
 * Remote DataSource interface for WTStudent operations via Firebase
 */
interface WTStudentRemoteDataSource : RemoteDataSource<WTStudent>

/**
 * Local DataSource interface for WTStudent operations via Room
 */
interface WTStudentLocalDataSource : SearchableDataSource<WTStudent>, LocalDataSource<WTStudent> {
    fun getStudentsByRank(rank: String): Flow<List<WTStudent>>
    fun getStudentsByActiveStatus(isActive: Boolean): Flow<List<WTStudent>>
    fun getStudentsByRecentAttendance(since: Long): Flow<List<WTStudent>>
    fun getRecentRegistrations(since: Long): Flow<List<WTStudent>>
    suspend fun getActiveStudentCount(): Int
    suspend fun getStudentCountByRank(rank: String): Int
    suspend fun getTotalMonthlyRevenue(): Double
} 