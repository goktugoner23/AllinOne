package com.example.allinone.ui.workout.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    
    // Colors for workout completion status
    private val colorLightGreen = Color.parseColor("#BAFFBA")  // Light green for completed workouts
    private val colorLightRed = Color.parseColor("#FFBABA")    // Light red for partially completed workouts

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
        
        // Log workout data for debugging
        android.util.Log.d("WorkoutLogAdapter", "Binding workout: ${workout.programName ?: "Unnamed"} (ID: ${workout.id}) with ${workout.exercises.size} exercises")
        if (workout.exercises.isNotEmpty()) {
            android.util.Log.d("WorkoutLogAdapter", "Exercises: ${workout.exercises.map { it.exerciseName }}")
        }
        
        // Set program name with better handling for null values
        holder.workoutProgramName.text = when {
            !workout.programName.isNullOrBlank() -> workout.programName
            workout.exercises.isNotEmpty() -> "Workout with ${workout.exercises.size} exercises"
            else -> "Unnamed Workout"
        }
        
        // Set date
        holder.workoutDate.text = dateFormat.format(workout.startTime)
        
        // Set duration with both minutes and seconds
        holder.workoutDuration.text = formatDuration(workout.duration)
        
        // Set exercise count
        holder.workoutExerciseCount.text = workout.exercises.size.toString()
        
        // Set card color based on workout completion status
        setCardColorByCompletionStatus(holder.cardView, workout)
        
        // Set click listener
        holder.cardView.setOnClickListener {
            onWorkoutClick(workout)
        }
    }

    private fun setCardColorByCompletionStatus(cardView: MaterialCardView, workout: Workout) {
        // Check if workout has any exercises
        if (workout.exercises.isEmpty()) {
            // No exercises, use default color
            cardView.setCardBackgroundColor(Color.WHITE)
            return
        }
        
        // Count exercises with all sets completed
        val completedExercisesCount = workout.exercises.count { exercise -> 
            exercise.sets.isNotEmpty() && exercise.sets.all { it.completed }
        }
        
        // Calculate completion percentage
        val totalExercises = workout.exercises.size
        
        // Apply appropriate color
        when {
            completedExercisesCount == totalExercises -> {
                // All exercises are completed, set light green background
                cardView.setCardBackgroundColor(colorLightGreen)
                android.util.Log.d("WorkoutLogAdapter", "Workout ${workout.id} is fully completed (${completedExercisesCount}/${totalExercises})")
            }
            completedExercisesCount > 0 -> {
                // Some exercises are completed, set light red background
                cardView.setCardBackgroundColor(colorLightRed)
                android.util.Log.d("WorkoutLogAdapter", "Workout ${workout.id} is partially completed (${completedExercisesCount}/${totalExercises})")
            }
            else -> {
                // No exercises are completed, use default color
                cardView.setCardBackgroundColor(Color.WHITE)
                android.util.Log.d("WorkoutLogAdapter", "Workout ${workout.id} has no completed exercises (0/${totalExercises})")
            }
        }
    }

    override fun getItemCount(): Int = workouts.size

    fun updateWorkouts(newWorkouts: List<Workout>) {
        workouts = newWorkouts
        notifyDataSetChanged()
    }
    
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        
        // Always show minutes and seconds
        return String.format("%d min %02d sec", minutes, seconds)
    }
}
