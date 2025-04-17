package com.example.allinone.ui.workout

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.data.Program
import com.example.allinone.data.ProgramExercise
import com.example.allinone.databinding.FragmentWorkoutProgramBinding
import com.example.allinone.ui.workout.adapters.ProgramAdapter
import com.example.allinone.ui.workout.adapters.ProgramExerciseAdapter
import com.google.android.material.textfield.TextInputLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WorkoutProgramFragment : Fragment() {
    private var _binding: FragmentWorkoutProgramBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkoutViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutProgramBinding.inflate(inflater, container, false)
        return binding.root
    }

    private lateinit var programAdapter: ProgramAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        binding.programsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter
        programAdapter = ProgramAdapter(emptyList()) { program ->
            // Handle program click - show program details
            showProgramDetailsDialog(program)
        }
        binding.programsRecyclerView.adapter = programAdapter

        // Observe programs data
        viewModel.allPrograms.observe(viewLifecycleOwner) { programs ->
            if (programs.isEmpty()) {
                binding.emptyProgramsText.visibility = View.VISIBLE
                binding.programsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyProgramsText.visibility = View.GONE
                binding.programsRecyclerView.visibility = View.VISIBLE
                programAdapter.updatePrograms(programs)
            }
        }

        // Set up FAB
        binding.addProgramFab.setOnClickListener {
            showAddProgramDialog()
        }
    }

    private fun showProgramDetailsDialog(program: Program) {
        // Create a dialog to show program details
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_program_details, null)

        val programNameText = dialogView.findViewById<TextView>(R.id.program_name_text)
        val programDescriptionText = dialogView.findViewById<TextView>(R.id.program_description_text)
        val exercisesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.exercises_recycler_view)

        // Set program details
        programNameText.text = program.name

        if (!program.description.isNullOrEmpty()) {
            programDescriptionText.text = program.description
            programDescriptionText.visibility = View.VISIBLE
        } else {
            programDescriptionText.visibility = View.GONE
        }

        // Set up exercises RecyclerView
        exercisesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val exerciseAdapter = ProgramExerciseAdapter(program.exercises) { _ ->
            // Handle exercise click if needed
        }
        exercisesRecyclerView.adapter = exerciseAdapter

        // Show dialog
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.program_details)
            .setView(dialogView)
            .setPositiveButton(R.string.edit) { _, _ ->
                // TODO: Implement edit program functionality
                Toast.makeText(requireContext(), R.string.edit_coming_soon, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.delete) { _, _ ->
                confirmDeleteProgram(program)
            }
            .show()
    }

    private fun confirmDeleteProgram(program: Program) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_program)
            .setMessage(getString(R.string.delete_program_confirmation, program.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteProgram(program.id)
                Toast.makeText(requireContext(), R.string.program_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddProgramDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_program, null)

        val programNameInput = dialogView.findViewById<EditText>(R.id.program_name_input)
        val programDescriptionInput = dialogView.findViewById<EditText>(R.id.program_description_input)
        val exercisesContainer = dialogView.findViewById<LinearLayout>(R.id.exercises_container)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Create Program")
            .setView(dialogView)
            .setPositiveButton("Save", null) // We'll set the listener later
            .setNegativeButton("Cancel", null)
            .create()

        // Add first exercise field
        addExerciseField(exercisesContainer)

        // Add button to add more exercises
        dialogView.findViewById<View>(R.id.add_exercise_button).setOnClickListener {
            addExerciseField(exercisesContainer)
        }

        dialog.show()

        // Override the positive button to validate input before dismissing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val programName = programNameInput.text.toString().trim()
            if (programName.isEmpty()) {
                programNameInput.error = "Program name is required"
                return@setOnClickListener
            }

            val programDescription = programDescriptionInput.text.toString().trim()

            // Collect exercises
            val exercises = mutableListOf<ProgramExercise>()
            var hasError = false

            for (i in 0 until exercisesContainer.childCount) {
                val exerciseLayout = exercisesContainer.getChildAt(i) as? LinearLayout ?: continue

                val nameInput = exerciseLayout.findViewById<EditText>(R.id.exercise_name_input)
                val setsInput = exerciseLayout.findViewById<EditText>(R.id.exercise_sets_input)
                val repsInput = exerciseLayout.findViewById<EditText>(R.id.exercise_reps_input)
                val weightInput = exerciseLayout.findViewById<EditText>(R.id.exercise_weight_input)
                val notesInput = exerciseLayout.findViewById<EditText>(R.id.exercise_notes_input)

                val name = nameInput.text.toString().trim()
                val sets = setsInput.text.toString().toIntOrNull()
                val reps = repsInput.text.toString().toIntOrNull()
                val weight = weightInput.text.toString().toDoubleOrNull()
                val notes = notesInput.text.toString().trim()

                if (name.isEmpty()) {
                    nameInput.error = "Exercise name is required"
                    hasError = true
                    continue
                }

                if (sets == null || sets <= 0) {
                    setsInput.error = "Valid sets required"
                    hasError = true
                    continue
                }

                if (reps == null || reps <= 0) {
                    repsInput.error = "Valid reps required"
                    hasError = true
                    continue
                }

                if (weight == null || weight <= 0) {
                    weightInput.error = "Valid weight required"
                    hasError = true
                    continue
                }

                exercises.add(
                    ProgramExercise(
                        exerciseId = System.currentTimeMillis() + i, // Temporary ID
                        exerciseName = name,
                        sets = sets,
                        reps = reps,
                        weight = weight,
                        notes = if (notes.isNotEmpty()) notes else null
                    )
                )
            }

            if (hasError) {
                return@setOnClickListener
            }

            if (exercises.isEmpty()) {
                Toast.makeText(requireContext(), "Add at least one exercise", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create program
            val program = Program(
                name = programName,
                description = if (programDescription.isNotEmpty()) programDescription else null,
                exercises = exercises
            )

            // Save program
            viewModel.saveProgram(program)

            dialog.dismiss()
            Toast.makeText(requireContext(), "Program created", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addExerciseField(container: LinearLayout) {
        val exerciseView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_add_exercise, container, false)

        // Add remove button functionality
        exerciseView.findViewById<View>(R.id.remove_exercise_button).setOnClickListener {
            container.removeView(exerciseView)
        }

        container.addView(exerciseView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
