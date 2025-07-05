package com.example.allinone.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.allinone.data.Note
import java.util.Date

@Entity(tableName = "cached_notes")
data class CachedNoteEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val content: String,
    val date: Long,
    val imageUris: String?,
    val videoUris: String?,
    val voiceNoteUris: String?,
    val lastEdited: Long,
    val isRichText: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toNote(): Note {
        return Note(
            id = id,
            title = title,
            content = content,
            date = Date(date),
            imageUris = imageUris,
            videoUris = videoUris,
            voiceNoteUris = voiceNoteUris,
            lastEdited = Date(lastEdited),
            isRichText = isRichText
        )
    }
    
    companion object {
        fun fromNote(note: Note): CachedNoteEntity {
            return CachedNoteEntity(
                id = note.id,
                title = note.title,
                content = note.content,
                date = note.date.time,
                imageUris = note.imageUris,
                videoUris = note.videoUris,
                voiceNoteUris = note.voiceNoteUris,
                lastEdited = note.lastEdited.time,
                isRichText = note.isRichText
            )
        }
    }
} 