package com.example.allinone.ui.workout.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Program
import com.google.android.material.card.MaterialCardView

class ProgramAdapter(
    private var programs: List<Program> = emptyList(),
    private val onProgramClick: (Program) -> Unit
) : RecyclerView.Adapter<ProgramAdapter.ProgramViewHolder>() {

    class ProgramViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val programName: TextView = itemView.findViewById(R.id.program_name)
        val programExerciseCount: TextView = itemView.findViewById(R.id.program_exercise_count)
        val programDescription: TextView = itemView.findViewById(R.id.program_description)
        val cardView: MaterialCardView = itemView as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_program, parent, false)
        return ProgramViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProgramViewHolder, position: Int) {
        val program = programs[position]
        
        holder.programName.text = program.name
        
        // Set exercise count
        val exerciseCount = program.exercises.size
        holder.programExerciseCount.text = holder.itemView.context.resources.getQuantityString(
            R.plurals.exercise_count, exerciseCount, exerciseCount
        )
        
        // Set description if available
        if (!program.description.isNullOrEmpty()) {
            holder.programDescription.text = program.description
            holder.programDescription.visibility = View.VISIBLE
        } else {
            holder.programDescription.visibility = View.GONE
        }
        
        // Set click listener
        holder.cardView.setOnClickListener {
            onProgramClick(program)
        }
    }

    override fun getItemCount(): Int = programs.size

    fun updatePrograms(newPrograms: List<Program>) {
        programs = newPrograms
        notifyDataSetChanged()
    }
}
