package com.example.allinone.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.allinone.data.local.dao.CachedTransactionDao
import com.example.allinone.data.local.dao.CachedNoteDao
import com.example.allinone.data.local.dao.CachedWorkoutDao
import com.example.allinone.data.local.dao.CachedProgramDao
import com.example.allinone.data.local.dao.CachedWTStudentDao
import com.example.allinone.data.local.entities.CachedTransactionEntity
import com.example.allinone.data.local.entities.CachedInvestmentEntity
import com.example.allinone.data.local.entities.CachedNoteEntity
import com.example.allinone.data.local.entities.CachedWorkoutEntity
import com.example.allinone.data.local.entities.CachedProgramEntity
import com.example.allinone.data.local.entities.CachedWTStudentEntity

@Database(
    entities = [
        CachedTransactionEntity::class,
        CachedInvestmentEntity::class,
        CachedNoteEntity::class,
        CachedWorkoutEntity::class,
        CachedProgramEntity::class,
        CachedWTStudentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): CachedTransactionDao
    abstract fun noteDao(): CachedNoteDao
    abstract fun workoutDao(): CachedWorkoutDao
    abstract fun programDao(): CachedProgramDao
    abstract fun wtStudentDao(): CachedWTStudentDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "allinone_cache_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 