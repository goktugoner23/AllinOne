package com.example.allinone.di

import android.content.Context
import com.example.allinone.cache.CacheManager
import com.example.allinone.data.local.RoomCacheManager
import com.example.allinone.data.local.AppDatabase
import com.example.allinone.data.local.dao.CachedNoteDao
import com.example.allinone.data.local.dao.CachedWorkoutDao
import com.example.allinone.data.local.dao.CachedProgramDao
import com.example.allinone.data.local.dao.CachedWTStudentDao
import com.example.allinone.feature.notes.data.datasource.NoteLocalDataSource
import com.example.allinone.feature.notes.data.datasource.NoteLocalDataSourceImpl
import com.example.allinone.feature.notes.data.datasource.NoteRemoteDataSource
import com.example.allinone.feature.notes.data.datasource.NoteRemoteDataSourceImpl
import com.example.allinone.feature.notes.data.repository.NoteRepositoryImpl
import com.example.allinone.feature.notes.domain.repository.NoteRepository
import com.example.allinone.feature.workout.data.datasource.WorkoutLocalDataSource
import com.example.allinone.feature.workout.data.datasource.WorkoutLocalDataSourceImpl
import com.example.allinone.feature.workout.data.datasource.WorkoutRemoteDataSource
import com.example.allinone.feature.workout.data.datasource.WorkoutRemoteDataSourceImpl
import com.example.allinone.feature.workout.data.repository.WorkoutRepositoryImpl
import com.example.allinone.feature.workout.domain.repository.WorkoutRepository
import com.example.allinone.firebase.FirebaseManager
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.firebase.OfflineQueue
import com.example.allinone.utils.LogcatHelper
import com.example.allinone.utils.NetworkUtils
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module that provides application-level dependencies.
 * These dependencies are shared across the entire application and are instantiated only once.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides a singleton instance of FirebaseManager
     */
    @Provides
    @Singleton
    fun provideFirebaseManager(@ApplicationContext context: Context): FirebaseManager {
        return FirebaseManager(context)
    }

    /**
     * Provides a singleton instance of OfflineQueue
     */
    @Provides
    @Singleton
    fun provideOfflineQueue(@ApplicationContext context: Context): OfflineQueue {
        return OfflineQueue(context)
    }

    /**
     * Provides a singleton instance of FirebaseRepository
     */
    @Provides
    @Singleton
    fun provideFirebaseRepository(@ApplicationContext context: Context): FirebaseRepository {
        return FirebaseRepository(context)
    }

    /**
     * Provides a singleton instance of NetworkUtils
     */
    @Provides
    @Singleton
    fun provideNetworkUtils(@ApplicationContext context: Context): NetworkUtils {
        return NetworkUtils(context)
    }

    /**
     * Provides a singleton instance of the legacy CacheManager (deprecated)
     * TODO: Remove this once all usages are migrated to RoomCacheManager
     */
    @Provides
    @Singleton
    fun provideCacheManager(@ApplicationContext context: Context): CacheManager {
        return CacheManager(context)
    }

    /**
     * Provides a singleton instance of the new RoomCacheManager
     * This replaces the inefficient SharedPreferences JSON caching
     */
    @Provides
    @Singleton
    fun provideRoomCacheManager(@ApplicationContext context: Context): RoomCacheManager {
        return RoomCacheManager(context)
    }

    /**
     * Provides a singleton instance of LogcatHelper
     */
    @Provides
    @Singleton
    fun provideLogcatHelper(@ApplicationContext context: Context): LogcatHelper {
        return LogcatHelper(context)
    }

    /**
     * Provides a singleton instance of AppDatabase
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    /**
     * Provides CachedNoteDao from AppDatabase
     */
    @Provides
    fun provideCachedNoteDao(database: AppDatabase): CachedNoteDao {
        return database.noteDao()
    }

    /**
     * Provides CachedWorkoutDao from AppDatabase
     */
    @Provides
    fun provideCachedWorkoutDao(database: AppDatabase): CachedWorkoutDao {
        return database.workoutDao()
    }

    /**
     * Provides CachedProgramDao from AppDatabase
     */
    @Provides
    fun provideCachedProgramDao(database: AppDatabase): CachedProgramDao {
        return database.programDao()
    }

    /**
     * Provides CachedWTStudentDao from AppDatabase
     */
    @Provides
    fun provideCachedWTStudentDao(database: AppDatabase): CachedWTStudentDao {
        return database.wtStudentDao()
    }

    /**
     * Provides NoteLocalDataSource implementation
     */
    @Provides
    @Singleton
    fun provideNoteLocalDataSource(noteDao: CachedNoteDao): NoteLocalDataSource {
        return NoteLocalDataSourceImpl(noteDao)
    }

    /**
     * Provides NoteRemoteDataSource implementation
     */
    @Provides
    @Singleton
    fun provideNoteRemoteDataSource(firebaseManager: FirebaseManager): NoteRemoteDataSource {
        return NoteRemoteDataSourceImpl(firebaseManager)
    }

    /**
     * Provides NoteRepository implementation
     */
    @Provides
    @Singleton
    fun provideNoteRepository(
        localDataSource: NoteLocalDataSource,
        remoteDataSource: NoteRemoteDataSource,
        networkUtils: NetworkUtils,
        offlineQueue: OfflineQueue
    ): NoteRepository {
        return NoteRepositoryImpl(localDataSource, remoteDataSource, networkUtils, offlineQueue)
    }

    /**
     * Provides WorkoutLocalDataSource implementation
     */
    @Provides
    @Singleton
    fun provideWorkoutLocalDataSource(workoutDao: CachedWorkoutDao): WorkoutLocalDataSource {
        return WorkoutLocalDataSourceImpl(workoutDao)
    }

    /**
     * Provides WorkoutRemoteDataSource implementation
     */
    @Provides
    @Singleton
    fun provideWorkoutRemoteDataSource(firebaseManager: FirebaseManager): WorkoutRemoteDataSource {
        return WorkoutRemoteDataSourceImpl(firebaseManager)
    }

    /**
     * Provides WorkoutRepository implementation
     */
    @Provides
    @Singleton
    fun provideWorkoutRepository(
        localDataSource: WorkoutLocalDataSource,
        remoteDataSource: WorkoutRemoteDataSource,
        networkUtils: NetworkUtils,
        offlineQueue: OfflineQueue
    ): WorkoutRepository {
        return WorkoutRepositoryImpl(localDataSource, remoteDataSource, networkUtils, offlineQueue)
    }
} 