package com.example.allinone.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.allinone.data.WTStudent

@Entity(tableName = "cached_wt_students")
data class CachedWTStudentEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val phoneNumber: String?,
    val email: String?,
    val instagram: String?,
    val isActive: Boolean,
    val deviceId: String?,
    val notes: String?,
    val photoUri: String?,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toWTStudent(): WTStudent {
        return WTStudent(
            id = id,
            name = name,
            phoneNumber = phoneNumber,
            email = email,
            instagram = instagram,
            isActive = isActive,
            deviceId = deviceId,
            notes = notes,
            photoUri = photoUri
        )
    }
    
    companion object {
        fun fromWTStudent(student: WTStudent): CachedWTStudentEntity {
            return CachedWTStudentEntity(
                id = student.id,
                name = student.name,
                phoneNumber = student.phoneNumber,
                email = student.email,
                instagram = student.instagram,
                isActive = student.isActive,
                deviceId = student.deviceId,
                notes = student.notes,
                photoUri = student.photoUri
            )
        }
    }
} 