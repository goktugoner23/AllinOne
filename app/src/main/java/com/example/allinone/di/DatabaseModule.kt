package com.example.allinone.di

import android.content.Context
import androidx.room.Room
import com.example.allinone.data.db.AppDatabase
import com.example.allinone.data.db.dao.PendingOperationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module that provides database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides the Room database instance.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "allinone-db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    /**
     * Provides the PendingOperationDao.
     */
    @Provides
    fun providePendingOperationDao(appDatabase: AppDatabase): PendingOperationDao {
        return appDatabase.pendingOperationsDao()
    }
} 