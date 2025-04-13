package com.example.allinone.adapters

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.VoiceNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceNoteAdapter(
    private val voiceNotes: List<VoiceNote>,
    private val onPlayClick: (VoiceNote) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<VoiceNoteAdapter.VoiceNoteViewHolder>() {

    private var currentlyPlayingPosition: Int = -1
    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private var currentPlaybackTime: Int = 0
    
    // Coroutine scope for updating playback progress
    private val scope = CoroutineScope(Dispatchers.Main)
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceNoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voice_note, parent, false)
        return VoiceNoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoiceNoteViewHolder, position: Int) {
        holder.bind(voiceNotes[position], position)
    }
    
    override fun getItemCount(): Int = voiceNotes.size

    inner class VoiceNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.voiceNoteTitle)
        private val durationText: TextView = itemView.findViewById(R.id.voiceNoteDuration)
        private val currentTimeText: TextView = itemView.findViewById(R.id.voiceNoteCurrentTime)
        private val timeSlash: TextView = itemView.findViewById(R.id.timeSlash)
        private val playPauseButton: ImageButton = itemView.findViewById(R.id.playPauseButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteVoiceNoteButton)

        fun bind(voiceNote: VoiceNote, position: Int) {
            // Format display title based on date
            val date = Date(voiceNote.timestamp)
            titleText.text = "Recording (${dateFormat.format(date)})"
            
            // Set duration
            durationText.text = voiceNote.getFormattedDuration()
            
            // Set play/pause icon based on current state
            val isPlaying = position == currentlyPlayingPosition && mediaPlayer?.isPlaying == true
            
            playPauseButton.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            
            // Show current playback time if this is the playing item
            if (isPlaying) {
                currentTimeText.visibility = View.VISIBLE
                timeSlash.visibility = View.VISIBLE
                
                // Format and display current time
                val currentSeconds = currentPlaybackTime / 1000
                val minutes = currentSeconds / 60
                val seconds = currentSeconds % 60
                currentTimeText.text = String.format("%02d:%02d", minutes, seconds)
            } else {
                currentTimeText.visibility = View.GONE
                timeSlash.visibility = View.GONE
            }
            
            // Set click listeners
            playPauseButton.setOnClickListener {
                if (position == currentlyPlayingPosition && mediaPlayer?.isPlaying == true) {
                    // Pause current playback
                    mediaPlayer?.pause()
                    notifyItemChanged(position)
                } else if (position == currentlyPlayingPosition && mediaPlayer?.isPlaying == false) {
                    // Resume current playback
                    mediaPlayer?.start()
                    startProgressUpdates()
                    notifyItemChanged(position)
                } else {
                    // Start new playback
                    onPlayClick(voiceNote)
                }
            }
            
            deleteButton.setOnClickListener {
                // Stop playback if this item is currently playing
                if (position == currentlyPlayingPosition) {
                    stopPlayback()
                }
                onDeleteClick(position)
            }
        }
    }
    
    fun stopPlayback() {
        updateJob?.cancel()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        currentPlaybackTime = 0
        val oldPosition = currentlyPlayingPosition
        currentlyPlayingPosition = -1
        if (oldPosition != -1) notifyItemChanged(oldPosition)
    }
    
    private fun startProgressUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            val isActive = true
            while (isActive) {
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        currentPlaybackTime = player.currentPosition
                        notifyItemChanged(currentlyPlayingPosition)
                    } else {
                        break // Exit the loop if the player is not playing
                    }
                    delay(500) // Update every half second
                } catch (e: Exception) {
                    Log.e("VoiceNoteAdapter", "Error updating progress: ${e.message}")
                    break // Break the loop if there's an error
                }
            }
        }
    }
    
    fun updatePlaybackState(isPlaying: Boolean, position: Int, player: MediaPlayer? = null) {
        val oldPosition = currentlyPlayingPosition
        currentlyPlayingPosition = if (isPlaying) position else -1
        
        // Set the media player reference if provided
        if (player != null) {
            this.mediaPlayer = player
            if (isPlaying) {
                startProgressUpdates()
            }
        }
        
        if (oldPosition != position) {
            if (oldPosition != -1) notifyItemChanged(oldPosition)
            if (position != -1) notifyItemChanged(position)
        } else {
            notifyItemChanged(position)
        }
    }
    
    fun releaseMediaPlayer() {
        stopPlayback()
    }
} 