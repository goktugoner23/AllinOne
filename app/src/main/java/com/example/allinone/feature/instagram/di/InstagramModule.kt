package com.example.allinone.feature.instagram.di

import com.example.allinone.feature.instagram.data.repository.InstagramRepositoryImpl
import com.example.allinone.feature.instagram.domain.repository.InstagramRepository
import com.example.allinone.feature.instagram.domain.usecase.AnalyzeMultimodalContentUseCase
import com.example.allinone.feature.instagram.domain.usecase.AnalyzeInstagramURLUseCase
import com.example.allinone.feature.instagram.domain.usecase.GetMultimodalSuggestionsUseCase
import com.example.allinone.feature.instagram.domain.usecase.ProcessAudioRecordingUseCase
import com.example.allinone.feature.instagram.domain.usecase.UploadFileForAnalysisUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class InstagramModule {

    @Binds
    abstract fun bindInstagramRepository(
        instagramRepositoryImpl: InstagramRepositoryImpl
    ): InstagramRepository
    
    companion object {
        @Provides
        fun provideAnalyzeMultimodalContentUseCase(
            repository: InstagramRepository
        ): AnalyzeMultimodalContentUseCase = AnalyzeMultimodalContentUseCase(repository)
        
        @Provides
        fun provideUploadFileForAnalysisUseCase(
            repository: InstagramRepository
        ): UploadFileForAnalysisUseCase = UploadFileForAnalysisUseCase(repository)
        
        @Provides
        fun provideAnalyzeInstagramURLUseCase(
            repository: InstagramRepository
        ): AnalyzeInstagramURLUseCase = AnalyzeInstagramURLUseCase(repository)
        
        @Provides
        fun provideProcessAudioRecordingUseCase(
            repository: InstagramRepository
        ): ProcessAudioRecordingUseCase = ProcessAudioRecordingUseCase(repository)
        
        @Provides
        fun provideGetMultimodalSuggestionsUseCase(): GetMultimodalSuggestionsUseCase = 
            GetMultimodalSuggestionsUseCase()
    }
} 