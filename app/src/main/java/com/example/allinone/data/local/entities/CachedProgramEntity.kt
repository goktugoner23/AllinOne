package com.example.allinone.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.allinone.data.Program
import com.example.allinone.data.ProgramExercise
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

@Entity(tableName = "cached_programs")
data class CachedProgramEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String?,
    val exercisesJson: String, // Store exercises as JSON for simplicity
    val createdDate: Long,
    val lastModifiedDate: Long,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toProgram(): Program {
        val gson = Gson()
        val exerciseListType = object : TypeToken<List<ProgramExercise>>() {}.type
        val exercises = try {
            gson.fromJson<List<ProgramExercise>>(exercisesJson, exerciseListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        return Program(
            id = id,
            name = name,
            description = description,
            exercises = exercises,
            createdDate = Date(createdDate),
            lastModifiedDate = Date(lastModifiedDate)
        )
    }
    
    companion object {
        fun fromProgram(program: Program): CachedProgramEntity {
            val gson = Gson()
            val exercisesJson = gson.toJson(program.exercises)
            
            return CachedProgramEntity(
                id = program.id,
                name = program.name,
                description = program.description,
                exercisesJson = exercisesJson,
                createdDate = program.createdDate.time,
                lastModifiedDate = program.lastModifiedDate.time
            )
        }
    }
} 