package com.example.allinone.ui.workout.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.WorkoutExercise
import com.example.allinone.data.WorkoutSet

class WorkoutExerciseAdapter(
    private var exercises: List<WorkoutExercise> = emptyList(),
    private val onExerciseCompletedChange: (WorkoutExercise, Boolean) -> Unit
) : RecyclerView.Adapter<WorkoutExerciseAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val exerciseName: TextView = itemView.findViewById(R.id.exercise_name)
        val exerciseSets: TextView = itemView.findViewById(R.id.exercise_sets)
        val exerciseReps: TextView = itemView.findViewById(R.id.exercise_reps)
        val exerciseWeight: TextView = itemView.findViewById(R.id.exercise_weight)
        val exerciseNotes: TextView = itemView.findViewById(R.id.exercise_notes)
        val exerciseCompleted: CheckBox = itemView.findViewById(R.id.exercise_completed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        
        holder.exerciseName.text = exercise.exerciseName
        
        // Get the first set to display (assuming all sets have the same reps/weight)
        val firstSet = exercise.sets.firstOrNull()
        
        // Set sets count
        holder.exerciseSets.text = exercise.sets.size.toString()
        
        // Set reps if available
        holder.exerciseReps.text = firstSet?.reps?.toString() ?: "0"
        
        // Set weight if available
        holder.exerciseWeight.text = String.format("%.1f kg", firstSet?.weight ?: 0.0)
        
        // Check if all sets are completed
        val allSetsCompleted = exercise.sets.all { it.completed }
        holder.exerciseCompleted.isChecked = allSetsCompleted
        
        // Set click listener for completion checkbox
        holder.exerciseCompleted.setOnCheckedChangeListener { _, isChecked ->
            onExerciseCompletedChange(exercise, isChecked)
        }
        
        // Get notes from the first set that has notes
        val notes = getNotes(exercise)
        if (notes.isNotEmpty()) {
            holder.exerciseNotes.text = notes
            holder.exerciseNotes.visibility = View.VISIBLE
        } else {
            holder.exerciseNotes.visibility = View.GONE
        }
    }
    
    private fun getNotes(_unused: WorkoutExercise): String {
        // For now, we'll just return an empty string since our model doesn't have notes at the exercise level
        return ""
    }

    override fun getItemCount(): Int = exercises.size

    fun updateExercises(newExercises: List<WorkoutExercise>) {
        exercises = newExercises
        notifyDataSetChanged()
    }
    
    fun updateExerciseCompletion(exerciseId: Long, _unused: Boolean) {
        val position = exercises.indexOfFirst { it.exerciseId == exerciseId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }
}
