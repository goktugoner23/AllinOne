package com.example.allinone.ui.workout.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Workout
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class WorkoutLogAdapter(
    private var workouts: List<Workout> = emptyList(),
    private val onWorkoutClick: (Workout) -> Unit
) : RecyclerView.Adapter<WorkoutLogAdapter.WorkoutLogViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

    class WorkoutLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val workoutProgramName: TextView = itemView.findViewById(R.id.workout_program_name)
        val workoutDate: TextView = itemView.findViewById(R.id.workout_date)
        val workoutDuration: TextView = itemView.findViewById(R.id.workout_duration)
        val workoutExerciseCount: TextView = itemView.findViewById(R.id.workout_exercise_count)
        val cardView: MaterialCardView = itemView as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_log, parent, false)
        return WorkoutLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkoutLogViewHolder, position: Int) {
        val workout = workouts[position]
        
        // Set program name
        holder.workoutProgramName.text = workout.programName ?: "Custom Workout"
        
        // Set date
        holder.workoutDate.text = dateFormat.format(workout.startTime)
        
        // Set duration
        holder.workoutDuration.text = formatDuration(workout.duration)
        
        // Set exercise count
        val exerciseCount = workout.exercises.size
        holder.workoutExerciseCount.text = exerciseCount.toString()
        
        // Set click listener
        holder.cardView.setOnClickListener {
            onWorkoutClick(workout)
        }
    }

    override fun getItemCount(): Int = workouts.size

    fun updateWorkouts(newWorkouts: List<Workout>) {
        workouts = newWorkouts.sortedByDescending { it.startTime }
        notifyDataSetChanged()
    }
    
    private fun formatDuration(durationMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        
        return when {
            hours > 0 -> "$hours hr ${minutes % 60} min"
            else -> "$minutes min"
        }
    }
}
