package com.example.allinone.ui.workout.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.ProgramExercise

class ProgramExerciseAdapter(
    private var exercises: List<ProgramExercise> = emptyList(),
    private val onExerciseClick: (ProgramExercise) -> Unit
) : RecyclerView.Adapter<ProgramExerciseAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val exerciseName: TextView = itemView.findViewById(R.id.exercise_name)
        val exerciseSets: TextView = itemView.findViewById(R.id.exercise_sets)
        val exerciseReps: TextView = itemView.findViewById(R.id.exercise_reps)
        val exerciseWeight: TextView = itemView.findViewById(R.id.exercise_weight)
        val exerciseNotes: TextView = itemView.findViewById(R.id.exercise_notes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_program_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        
        holder.exerciseName.text = exercise.exerciseName
        holder.exerciseSets.text = exercise.sets.toString()
        holder.exerciseReps.text = exercise.reps.toString()
        holder.exerciseWeight.text = String.format("%.1f kg", exercise.weight)
        
        if (!exercise.notes.isNullOrEmpty()) {
            holder.exerciseNotes.text = exercise.notes
            holder.exerciseNotes.visibility = View.VISIBLE
        } else {
            holder.exerciseNotes.visibility = View.GONE
        }
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onExerciseClick(exercise)
        }
    }

    override fun getItemCount(): Int = exercises.size

    fun updateExercises(newExercises: List<ProgramExercise>) {
        exercises = newExercises
        notifyDataSetChanged()
    }
}
