package com.example.allinone.feature.instagram.di

import com.example.allinone.feature.instagram.data.repository.InstagramRepositoryImpl
import com.example.allinone.feature.instagram.domain.repository.InstagramRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InstagramModule {
    
    @Binds
    @Singleton
    abstract fun bindInstagramRepository(
        instagramRepositoryImpl: InstagramRepositoryImpl
    ): InstagramRepository
} 