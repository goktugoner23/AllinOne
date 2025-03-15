package com.example.allinone.di

import android.content.Context
import com.example.allinone.cache.CacheManager
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.utils.LogcatHelper
import com.example.allinone.utils.NetworkUtils
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
     * Provides a singleton instance of CacheManager
     */
    @Provides
    @Singleton
    fun provideCacheManager(@ApplicationContext context: Context): CacheManager {
        return CacheManager(context)
    }

    /**
     * Provides a singleton instance of LogcatHelper
     */
    @Provides
    @Singleton
    fun provideLogcatHelper(@ApplicationContext context: Context): LogcatHelper {
        return LogcatHelper(context)
    }
} 