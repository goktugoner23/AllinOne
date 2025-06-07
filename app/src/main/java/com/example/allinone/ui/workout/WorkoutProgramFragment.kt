package com.example.allinone.ui.workout

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.allinone.R
import com.example.allinone.config.MuscleGroups
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
    private val viewModel: WorkoutViewModel by activityViewModels()

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

        // Set up SwipeRefreshLayout
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshPrograms()
        }

        // Set refresh indicator colors
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
        )

        // Observe programs data
        viewModel.allPrograms.observe(viewLifecycleOwner) { programs ->
            // Hide refresh indicator
            binding.swipeRefreshLayout.isRefreshing = false

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

    /**
     * Refresh programs data from the server
     */
    private fun refreshPrograms() {
        android.util.Log.d("WorkoutProgramFragment", "Refreshing programs")
        viewModel.refreshPrograms()
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
                showEditProgramDialog(program)
            }
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.delete) { _, _ ->
                confirmDeleteProgram(program)
            }
            .show()
    }

    private fun showEditProgramDialog(program: Program) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_program, null)

        val programNameInput = dialogView.findViewById<EditText>(R.id.program_name_input)
        val programDescriptionInput = dialogView.findViewById<EditText>(R.id.program_description_input)
        val exercisesContainer = dialogView.findViewById<LinearLayout>(R.id.exercises_container)

        // Pre-fill with existing data
        programNameInput.setText(program.name)
        if (!program.description.isNullOrEmpty()) {
            programDescriptionInput.setText(program.description)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edit Program")
            .setView(dialogView)
            .setPositiveButton("Save", null) // We'll set the listener later
            .setNegativeButton("Cancel", null)
            .create()

        // Add existing exercises
        for (exercise in program.exercises) {
            addExerciseField(exercisesContainer, exercise)
        }

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
                val muscleGroupDropdown = exerciseLayout.findViewById<AutoCompleteTextView>(R.id.muscle_group_dropdown)

                // Get the hidden exerciseId if it exists
                val exerciseIdTag = exerciseLayout.tag
                val exerciseId = if (exerciseIdTag != null) {
                    exerciseIdTag as Long
                } else {
                    System.currentTimeMillis() + i // New temporary ID
                }

                val name = nameInput.text.toString().trim()
                val sets = setsInput.text.toString().toIntOrNull()
                val reps = repsInput.text.toString().toIntOrNull()
                val weight = weightInput.text.toString().toDoubleOrNull()
                val muscleGroup = muscleGroupDropdown.text.toString()
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
                        exerciseId = exerciseId,
                        exerciseName = name,
                        sets = sets,
                        reps = reps,
                        weight = weight,
                        muscleGroup = muscleGroup,
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

            // Update program
            val updatedProgram = program.copy(
                name = programName,
                description = if (programDescription.isNotEmpty()) programDescription else null,
                exercises = exercises,
                lastModifiedDate = java.util.Date()
            )

            // Save updated program
            viewModel.saveProgram(updatedProgram)

            dialog.dismiss()
            Toast.makeText(requireContext(), R.string.program_updated, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteProgram(program: Program) {
        // Check if any workouts reference this program
        val workoutsWithProgram = viewModel.allWorkouts.value?.filter { it.programId == program.id } ?: emptyList()

        val message = if (workoutsWithProgram.isEmpty()) {
            getString(R.string.delete_program_confirmation, program.name)
        } else {
            // Create a more detailed warning when the program is referenced by workouts
            val workoutCount = workoutsWithProgram.size
            "This program is referenced by $workoutCount ${if (workoutCount == 1) "workout" else "workouts"} in your history.\n\n" +
            "Deleting this program will not delete those workouts, but they may display incorrectly.\n\n" +
            "Are you sure you want to delete \"${program.name}\"?"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_program)
            .setMessage(message)
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
                val muscleGroupDropdown = exerciseLayout.findViewById<AutoCompleteTextView>(R.id.muscle_group_dropdown)

                val name = nameInput.text.toString().trim()
                val sets = setsInput.text.toString().toIntOrNull()
                val reps = repsInput.text.toString().toIntOrNull()
                val weight = weightInput.text.toString().toDoubleOrNull()
                val muscleGroup = muscleGroupDropdown.text.toString()
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
                        muscleGroup = muscleGroup,
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

    private fun addExerciseField(container: LinearLayout, existingExercise: ProgramExercise? = null) {
        val exerciseView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_add_exercise, container, false)

        // Set up muscle group dropdown
        val muscleGroupDropdown = exerciseView.findViewById<AutoCompleteTextView>(R.id.muscle_group_dropdown)
        val muscleGroupAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            MuscleGroups.MUSCLE_GROUPS
        )
        muscleGroupDropdown.setAdapter(muscleGroupAdapter)

        // If editing an existing exercise, pre-fill the fields
        if (existingExercise != null) {
            exerciseView.findViewById<EditText>(R.id.exercise_name_input).setText(existingExercise.exerciseName)
            exerciseView.findViewById<EditText>(R.id.exercise_sets_input).setText(existingExercise.sets.toString())
            exerciseView.findViewById<EditText>(R.id.exercise_reps_input).setText(existingExercise.reps.toString())
            exerciseView.findViewById<EditText>(R.id.exercise_weight_input).setText(existingExercise.weight.toString())

            if (!existingExercise.notes.isNullOrEmpty()) {
                exerciseView.findViewById<EditText>(R.id.exercise_notes_input).setText(existingExercise.notes)
            }

            if (!existingExercise.muscleGroup.isNullOrEmpty()) {
                muscleGroupDropdown.setText(existingExercise.muscleGroup, false)
            }

            // Store the exercise ID in the view's tag
            exerciseView.tag = existingExercise.exerciseId
        }

        // Add remove button
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
