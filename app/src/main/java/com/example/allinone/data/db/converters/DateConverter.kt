package com.example.allinone.data.db.converters

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converter for Room database to handle Date objects.
 * 
 * This class provides conversion between Date objects and Long timestamps
 * for storing date values in SQLite database.
 */
class DateConverter {
    
    /**
     * Converts a timestamp (Long) to a Date object
     * 
     * @param value The timestamp to convert
     * @return The Date object, or null if the timestamp is null
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    /**
     * Converts a Date object to a timestamp (Long)
     * 
     * @param date The Date object to convert
     * @return The timestamp, or null if the Date is null
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
} 