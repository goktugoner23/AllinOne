package com.example.allinone.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investments ORDER BY date DESC")
    fun getAllInvestments(): Flow<List<Investment>>

    @Query("SELECT SUM(amount) FROM investments")
    fun getTotalInvestment(): Flow<Double?>

    @Query("SELECT SUM(profitLoss) FROM investments")
    fun getTotalProfitLoss(): Flow<Double?>

    @Insert
    suspend fun insertInvestment(investment: Investment)

    @Update
    suspend fun updateInvestment(investment: Investment)

    @Delete
    suspend fun deleteInvestment(investment: Investment)
} 