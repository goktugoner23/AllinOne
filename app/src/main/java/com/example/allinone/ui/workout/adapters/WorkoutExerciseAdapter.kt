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
    private val onExerciseCompletedChange: (WorkoutExercise, Boolean) -> Unit,
    private val readOnly: Boolean = false
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
        
        // Log exercise details for debugging
        android.util.Log.d("WorkoutExerciseAdapter", "Binding exercise: ${exercise.exerciseName} with ${exercise.sets.size} sets")
        exercise.sets.forEachIndexed { index, set ->
            android.util.Log.d("WorkoutExerciseAdapter", "Set ${index+1}: ${set.reps} reps, ${set.weight}kg, completed: ${set.completed}")
        }
        
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
        val allSetsCompleted = exercise.sets.isNotEmpty() && exercise.sets.all { it.completed }
        holder.exerciseCompleted.isChecked = allSetsCompleted
        
        // If in readOnly mode, disable the checkbox
        if (readOnly) {
            holder.exerciseCompleted.isEnabled = false
            holder.exerciseCompleted.isFocusable = false
            holder.exerciseCompleted.isClickable = false
        } else {
            holder.exerciseCompleted.isEnabled = true
            holder.exerciseCompleted.isFocusable = true
            holder.exerciseCompleted.isClickable = true
        }

        // Count how many sets are completed
        val completedSets = exercise.sets.count { it.completed }
        
        // Show completion status in text (e.g. "3/5 sets completed")
        if (exercise.sets.isNotEmpty()) {
            val completionText = "$completedSets/${exercise.sets.size} sets completed"
            holder.exerciseNotes.text = completionText
            holder.exerciseNotes.visibility = View.VISIBLE
            
            // Change background color based on completion status
            if (allSetsCompleted) {
                // Fully completed - green
                holder.itemView.setBackgroundResource(R.drawable.completed_exercise_background)
            } else if (completedSets > 0) {
                // Partially completed - red
                holder.itemView.setBackgroundResource(R.drawable.uncompleted_exercise_background)
            } else {
                // Not started - white/gray
                holder.itemView.setBackgroundResource(R.drawable.fully_uncompleted_exercise_background)
            }
        } else {
            holder.exerciseNotes.visibility = View.GONE
            holder.itemView.setBackgroundResource(R.drawable.fully_uncompleted_exercise_background)
        }
        
        // Set click listener for completion checkbox
        holder.exerciseCompleted.setOnCheckedChangeListener { _, isChecked ->
            onExerciseCompletedChange(exercise, isChecked)
        }
    }

    override fun getItemCount(): Int = exercises.size

    fun updateExercises(newExercises: List<WorkoutExercise>) {
        exercises = newExercises
        notifyDataSetChanged()
    }
    
    fun updateExerciseCompletion(exerciseId: Long, isCompleted: Boolean) {
        // Find the exercise by ID
        val index = exercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index != -1) {
            val exercise = exercises[index]
            
            // Update all sets in the exercise with the new completion status
            val updatedSets = exercise.sets.map { set ->
                set.copy(completed = isCompleted)
            }
            
            // Create updated exercise with new completion status
            val updatedExercise = exercise.copy(sets = updatedSets)
            
            // Replace the old exercise with the updated one
            (exercises as? MutableList)?.set(index, updatedExercise)
            
            // Notify the RecyclerView that this item has changed
            notifyItemChanged(index)
        }
    }
}
