package com.example.allinone.feature.instagram.data.api

import com.example.allinone.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object InstagramApiClient {
    
    // External API URL - pointing to your external backend
    private val BASE_URL = when {
        BuildConfig.DEBUG -> "http://129.212.143.6:3000/" // Your external backend
        else -> "http://129.212.143.6:3000/" // Same for production for now
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS) // Longer timeout for Instagram API processing
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val service: InstagramApiService = retrofit.create(InstagramApiService::class.java)
    
    /**
     * Get the configured base URL for reference
     */
    fun getBaseUrl(): String = BASE_URL
} 