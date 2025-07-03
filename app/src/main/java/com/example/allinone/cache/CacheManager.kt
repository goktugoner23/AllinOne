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
        private const val KEY_TASKS = "cache_tasks"
        private const val KEY_STUDENTS = "cache_students"
        private const val KEY_EVENTS = "cache_events"
        private const val KEY_LESSONS = "cache_lessons"
        private const val KEY_REGISTRATIONS = "cache_registrations"
        private const val KEY_PROGRAMS = "cache_programs"
        private const val KEY_WORKOUTS = "cache_workouts"

        // Last update timestamps
        private const val KEY_TRANSACTIONS_UPDATED = "cache_transactions_updated"
        private const val KEY_INVESTMENTS_UPDATED = "cache_investments_updated"
        private const val KEY_NOTES_UPDATED = "cache_notes_updated"
        private const val KEY_TASKS_UPDATED = "cache_tasks_updated"
        private const val KEY_STUDENTS_UPDATED = "cache_students_updated"
        private const val KEY_EVENTS_UPDATED = "cache_events_updated"
        private const val KEY_LESSONS_UPDATED = "cache_lessons_updated"
        private const val KEY_REGISTRATIONS_UPDATED = "cache_registrations_updated"
        private const val KEY_PROGRAMS_UPDATED = "cache_programs_updated"
        private const val KEY_WORKOUTS_UPDATED = "cache_workouts_updated"

        // Default cache expiration (10 minutes)
        private const val DEFAULT_CACHE_EXPIRATION_MS = 10 * 60 * 1000L
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // Cache expiration times (milliseconds)
    private var transactionCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var investmentCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var noteCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var taskCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var studentCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var eventCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var lessonCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var registrationCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var programCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS
    private var workoutCacheExpiration = DEFAULT_CACHE_EXPIRATION_MS

    /**
     * Set custom cache expiration for a specific data type
     */
    fun setCacheExpiration(dataType: String, expirationMs: Long) {
        when (dataType) {
            "transactions" -> transactionCacheExpiration = expirationMs
            "investments" -> investmentCacheExpiration = expirationMs
            "notes" -> noteCacheExpiration = expirationMs
            "tasks" -> taskCacheExpiration = expirationMs
            "students" -> studentCacheExpiration = expirationMs
            "events" -> eventCacheExpiration = expirationMs
            "lessons" -> lessonCacheExpiration = expirationMs
            "registrations" -> registrationCacheExpiration = expirationMs
            "programs" -> programCacheExpiration = expirationMs
            "workouts" -> workoutCacheExpiration = expirationMs
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
            KEY_TASKS -> taskCacheExpiration
            KEY_STUDENTS -> studentCacheExpiration
            KEY_EVENTS -> eventCacheExpiration
            KEY_LESSONS -> lessonCacheExpiration
            KEY_REGISTRATIONS -> registrationCacheExpiration
            KEY_PROGRAMS -> programCacheExpiration
            KEY_WORKOUTS -> workoutCacheExpiration
            else -> DEFAULT_CACHE_EXPIRATION_MS
        }
    }

    private fun getLastUpdateKey(cacheType: String): String {
        return when (cacheType) {
            KEY_TRANSACTIONS -> KEY_TRANSACTIONS_UPDATED
            KEY_INVESTMENTS -> KEY_INVESTMENTS_UPDATED
            KEY_NOTES -> KEY_NOTES_UPDATED
            KEY_TASKS -> KEY_TASKS_UPDATED
            KEY_STUDENTS -> KEY_STUDENTS_UPDATED
            KEY_EVENTS -> KEY_EVENTS_UPDATED
            KEY_LESSONS -> KEY_LESSONS_UPDATED
            KEY_REGISTRATIONS -> KEY_REGISTRATIONS_UPDATED
            KEY_PROGRAMS -> KEY_PROGRAMS_UPDATED
            KEY_WORKOUTS -> KEY_WORKOUTS_UPDATED
            else -> "${cacheType}_updated"
        }
    }

    // Cache Transaction data
    fun cacheTransactions(transactions: List<Transaction>) {
        cacheData(KEY_TRANSACTIONS, transactions)
    }

    fun getCachedTransactions(): List<Transaction> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<Transaction>>() {}.type
        return getCachedData(KEY_TRANSACTIONS, listType) ?: emptyList()
    }

    // Cache Investment data
    fun cacheInvestments(investments: List<Investment>) {
        cacheData(KEY_INVESTMENTS, investments)
    }

    fun getCachedInvestments(): List<Investment> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<Investment>>() {}.type
        return getCachedData(KEY_INVESTMENTS, listType) ?: emptyList()
    }

    // Cache Note data
    fun cacheNotes(notes: List<Note>) {
        cacheData(KEY_NOTES, notes)
    }

    fun getCachedNotes(): List<Note> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<Note>>() {}.type
        return getCachedData(KEY_NOTES, listType) ?: emptyList()
    }

    // Cache Task data
    fun cacheTasks(tasks: List<Task>) {
        cacheData(KEY_TASKS, tasks)
    }

    fun getCachedTasks(): List<Task> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<Task>>() {}.type
        return getCachedData(KEY_TASKS, listType) ?: emptyList()
    }

    // Cache Student data
    fun cacheStudents(students: List<WTStudent>) {
        cacheData(KEY_STUDENTS, students)
    }

    fun getCachedStudents(): List<WTStudent> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<WTStudent>>() {}.type
        return getCachedData(KEY_STUDENTS, listType) ?: emptyList()
    }

    // Cache Event data
    fun cacheEvents(events: List<Event>) {
        cacheData(KEY_EVENTS, events)
    }

    fun getCachedEvents(): List<Event> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<Event>>() {}.type
        return getCachedData(KEY_EVENTS, listType) ?: emptyList()
    }

    // Cache Lesson data
    fun cacheLessons(lessons: List<WTLesson>) {
        cacheData(KEY_LESSONS, lessons)
    }

    fun getCachedLessons(): List<WTLesson> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<WTLesson>>() {}.type
        return getCachedData(KEY_LESSONS, listType) ?: emptyList()
    }

    // Cache Registration data
    fun cacheRegistrations(registrations: List<WTRegistration>) {
        cacheData(KEY_REGISTRATIONS, registrations)
    }

    fun getCachedRegistrations(): List<WTRegistration> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<WTRegistration>>() {}.type
        return getCachedData(KEY_REGISTRATIONS, listType) ?: emptyList()
    }

    // Cache Program data
    fun cachePrograms(programs: List<Program>) {
        cacheData(KEY_PROGRAMS, programs)
    }

    fun getCachedPrograms(): List<Program> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<Program>>() {}.type
        return getCachedData(KEY_PROGRAMS, listType) ?: emptyList()
    }

    // Cache Workout data
    fun cacheWorkouts(workouts: List<Workout>) {
        cacheData(KEY_WORKOUTS, workouts)
    }

    fun getCachedWorkouts(): List<Workout> {
        // More explicit TypeToken creation that's safer for ProGuard
        val listType = object : TypeToken<ArrayList<Workout>>() {}.type
        return getCachedData(KEY_WORKOUTS, listType) ?: emptyList()
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