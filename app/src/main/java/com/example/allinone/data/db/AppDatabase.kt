package com.example.allinone.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.allinone.data.db.converters.DateConverter
import com.example.allinone.data.db.dao.PendingOperationDao
import com.example.allinone.data.db.entities.PendingOperationEntity

/**
 * Main Room database for the application.
 * 
 * This database stores all the entities for offline caching and synchronization.
 */
@Database(
    entities = [
        PendingOperationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun pendingOperationsDao(): PendingOperationDao
    
    companion object {
        private const val DATABASE_NAME = "allinone-db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
} 