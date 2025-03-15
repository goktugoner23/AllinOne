package com.example.allinone.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.allinone.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Manages local caching of data to improve performance and reduce Firebase calls
 */
class CacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CacheManager"
        private const val PREFS_NAME = "allinone_cache"
        
        // Cache keys
        private const val KEY_TRANSACTIONS = "cache_transactions"
        private const val KEY_INVESTMENTS = "cache_investments"
        private const val KEY_NOTES = "cache_notes"
        private const val KEY_STUDENTS = "cache_students"
        private const val KEY_EVENTS = "cache_events"
        private const val KEY_LESSONS = "cache_lessons"
        private const val KEY_REGISTRATIONS = "cache_registrations"
        
        // Last update timestamps
        private const val KEY_TRANSACTIONS_UPDATED = "cache_transactions_updated"
        private const val KEY_INVESTMENTS_UPDATED = "cache_investments_updated"
        private const val KEY_NOTES_UPDATED = "cache_notes_updated"
        private const val KEY_STUDENTS_UPDATED = "cache_students_updated"
        private const val KEY_EVENTS_UPDATED = "cache_events_updated"
        private const val KEY_LESSONS_UPDATED = "cache_lessons_updated"
        
        // Default cache expiration (10 minutes)
        private const val DEFAULT_CACHE_EXPIRATION_MS = 10 * 60 * 1000L
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Cache expiration times (milliseconds)
    private var transactionCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var investmentCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var noteCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var studentCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var eventCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var lessonCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    
    /**
     * Set custom cache expiration for a specific data type
     */
    fun setCacheExpiration(dataType: String, expirationMs: Long) {
        when (dataType) {
            "transactions" -> transactionCacheExpiration = expirationMs
            "investments" -> investmentCacheExpiration = expirationMs
            "notes" -> noteCacheExpiration = expirationMs
            "students" -> studentCacheExpiration = expirationMs
            "events" -> eventCacheExpiration = expirationMs
            "lessons" -> lessonCacheExpiration = expirationMs
        }
    }
    
    /**
     * Check if cache for given type is valid (not expired)
     */
    fun isCacheValid(cacheType: String): Boolean {
        val lastUpdateKey = getLastUpdateKey(cacheType)
        val lastUpdate = sharedPreferences.getLong(lastUpdateKey, 0)
        val now = System.currentTimeMillis()
        val expirationMs = getCacheExpirationForType(cacheType)
        
        return (now - lastUpdate) < expirationMs
    }
    
    private fun getCacheExpirationForType(cacheType: String): Long {
        return when (cacheType) {
            KEY_TRANSACTIONS -> transactionCacheExpiration
            KEY_INVESTMENTS -> investmentCacheExpiration
            KEY_NOTES -> noteCacheExpiration
            KEY_STUDENTS -> studentCacheExpiration
            KEY_EVENTS -> eventCacheExpiration
            KEY_LESSONS -> lessonCacheExpiration
            KEY_REGISTRATIONS -> DEFAULT_CACHE_EXPIRATION_MS
            else -> DEFAULT_CACHE_EXPIRATION_MS
        }
    }
    
    private fun getLastUpdateKey(cacheType: String): String {
        return when (cacheType) {
            KEY_TRANSACTIONS -> KEY_TRANSACTIONS_UPDATED
            KEY_INVESTMENTS -> KEY_INVESTMENTS_UPDATED
            KEY_NOTES -> KEY_NOTES_UPDATED
            KEY_STUDENTS -> KEY_STUDENTS_UPDATED
            KEY_EVENTS -> KEY_EVENTS_UPDATED
            KEY_LESSONS -> KEY_LESSONS_UPDATED
            KEY_REGISTRATIONS -> "${cacheType}_updated"
            else -> "${cacheType}_updated"
        }
    }
    
    // Cache Transaction data
    fun cacheTransactions(transactions: List<Transaction>) {
        cacheData(KEY_TRANSACTIONS, transactions)
    }
    
    fun getCachedTransactions(): List<Transaction> {
        val type = object : TypeToken<List<Transaction>>() {}.type
        return getCachedData(KEY_TRANSACTIONS, type) ?: emptyList()
    }
    
    // Cache Investment data
    fun cacheInvestments(investments: List<Investment>) {
        cacheData(KEY_INVESTMENTS, investments)
    }
    
    fun getCachedInvestments(): List<Investment> {
        val type = object : TypeToken<List<Investment>>() {}.type
        return getCachedData(KEY_INVESTMENTS, type) ?: emptyList()
    }
    
    // Cache Note data
    fun cacheNotes(notes: List<Note>) {
        cacheData(KEY_NOTES, notes)
    }
    
    fun getCachedNotes(): List<Note> {
        val type = object : TypeToken<List<Note>>() {}.type
        return getCachedData(KEY_NOTES, type) ?: emptyList()
    }
    
    // Cache Student data
    fun cacheStudents(students: List<WTStudent>) {
        cacheData(KEY_STUDENTS, students)
    }
    
    fun getCachedStudents(): List<WTStudent> {
        val type = object : TypeToken<List<WTStudent>>() {}.type
        return getCachedData(KEY_STUDENTS, type) ?: emptyList()
    }
    
    // Cache Event data
    fun cacheEvents(events: List<Event>) {
        cacheData(KEY_EVENTS, events)
    }
    
    fun getCachedEvents(): List<Event> {
        val type = object : TypeToken<List<Event>>() {}.type
        return getCachedData(KEY_EVENTS, type) ?: emptyList()
    }
    
    // Cache Lesson data
    fun cacheLessons(lessons: List<WTLesson>) {
        cacheData(KEY_LESSONS, lessons)
    }
    
    fun getCachedLessons(): List<WTLesson> {
        val type = object : TypeToken<List<WTLesson>>() {}.type
        return getCachedData(KEY_LESSONS, type) ?: emptyList()
    }
    
    // Cache Registration data
    fun cacheRegistrations(registrations: List<WTRegistration>) {
        cacheData(KEY_REGISTRATIONS, registrations)
    }
    
    fun getCachedRegistrations(): List<WTRegistration> {
        val type = object : TypeToken<List<WTRegistration>>() {}.type
        return getCachedData(KEY_REGISTRATIONS, type) ?: emptyList()
    }
    
    // Generic methods for caching and retrieving data
    private fun <T> cacheData(key: String, data: T) {
        try {
            val json = gson.toJson(data)
            sharedPreferences.edit()
                .putString(key, json)
                .putLong(getLastUpdateKey(key), System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Cached data for $key: ${json.length} characters")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching data for $key: ${e.message}")
        }
    }
    
    private fun <T> getCachedData(key: String, type: Type): T? {
        try {
            val json = sharedPreferences.getString(key, null) ?: return null
            return gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached data for $key: ${e.message}")
            return null
        }
    }
    
    // Clear all cached data
    fun clearAllCache() {
        sharedPreferences.edit().clear().apply()
        Log.d(TAG, "All cache cleared")
    }
    
    // Clear specific cache
    fun clearCache(cacheType: String) {
        sharedPreferences.edit()
            .remove(cacheType)
            .remove(getLastUpdateKey(cacheType))
            .apply()
        Log.d(TAG, "Cache cleared for $cacheType")
    }
} 