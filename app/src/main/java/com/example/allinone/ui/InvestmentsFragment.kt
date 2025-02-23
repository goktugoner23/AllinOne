package com.example.allinone.ui

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.InvestmentAdapter
import com.example.allinone.adapters.InvestmentImageAdapter
import com.example.allinone.data.Investment
import com.example.allinone.databinding.DialogEditInvestmentBinding
import com.example.allinone.databinding.FragmentInvestmentsBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.example.allinone.viewmodels.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.chrisbanes.photoview.PhotoView
import java.util.Date

class InvestmentsFragment : Fragment() {
    private var _binding: FragmentInvestmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var adapter: InvestmentAdapter
    private lateinit var imageAdapter: InvestmentImageAdapter
    private val selectedImages = mutableListOf<Uri>()
    private val PERMISSION_REQUEST_CODE = 123

    private val getContent = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris?.let { selectedUris ->
            selectedUris.forEach { uri ->
                try {
                    // Take persistable permission for the URI
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            selectedImages.addAll(selectedUris)
            imageAdapter.submitList(selectedImages.toList())
            dialogBinding?.imagesRecyclerView?.visibility = View.VISIBLE
        }
    }

    private var dialogBinding: DialogEditInvestmentBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvestmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkAndRequestPermissions()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            if (requireContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = InvestmentAdapter(
            onItemClick = { investment -> showInvestmentDetails(investment) },
            onItemLongClick = { investment -> showDeleteConfirmation(investment) },
            onImageClick = { uri -> showFullscreenImage(uri) }
        )
        
        binding.investmentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InvestmentsFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupClickListeners() {
        binding.addInvestmentButton.setOnClickListener {
            showAddInvestmentDialog()
        }
    }

    private fun showAddInvestmentDialog() {
        selectedImages.clear()
        val dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)
        this.dialogBinding = dialogBinding

        // Setup investment type dropdown
        val investmentTypes = arrayOf("Stocks", "Crypto", "Real Estate", "Gold", "Other")
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, investmentTypes)
        (dialogBinding.typeInput as? AutoCompleteTextView)?.setAdapter(arrayAdapter)

        // Setup image adapter
        imageAdapter = InvestmentImageAdapter(
            onDeleteClick = { uri ->
                selectedImages.remove(uri)
                imageAdapter.submitList(selectedImages.toList())
                if (selectedImages.isEmpty()) {
                    dialogBinding.imagesRecyclerView.visibility = View.GONE
                }
            },
            onImageClick = { uri ->
                showFullscreenImage(uri)
            }
        )

        dialogBinding.imagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
        }

        dialogBinding.addImageButton.setOnClickListener {
            getContent.launch("image/*")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Investment")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogBinding.nameInput.text?.toString()
                val amountText = dialogBinding.amountInput.text?.toString()
                val type = (dialogBinding.typeInput as? AutoCompleteTextView)?.text?.toString()
                val description = dialogBinding.descriptionInput.text?.toString()

                if (name.isNullOrBlank() || amountText.isNullOrBlank() || type.isNullOrBlank()) {
                    Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val amount = amountText.toDoubleOrNull()
                if (amount == null) {
                    Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Save investment with images
                val imageUris = selectedImages.joinToString(",") { it.toString() }
                
                val investment = Investment(
                    name = name,
                    amount = amount,
                    type = type,
                    description = description,
                    imageUri = if (imageUris.isNotEmpty()) imageUris else null,
                    date = Date()
                )
                
                viewModel.addInvestment(investment)

                // Add as expense in the main app
                homeViewModel.addTransaction(
                    amount = amount,
                    type = "Investment",
                    description = "Investment in $name ($type)",
                    isIncome = false,
                    category = "Investment"
                )

                // Show confirmation
                Toast.makeText(context, "Investment added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInvestmentDetails(investment: Investment) {
        // Show investment details in a dialog with images
        val dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)
        
        // Populate fields
        dialogBinding.nameInput.setText(investment.name)
        dialogBinding.amountInput.setText(investment.amount.toString())
        dialogBinding.typeInput.setText(investment.type)
        dialogBinding.descriptionInput.setText(investment.description)

        // Setup images
        selectedImages.clear()
        investment.imageUri?.split(",")?.forEach { uriString ->
            selectedImages.add(Uri.parse(uriString))
        }

        imageAdapter = InvestmentImageAdapter(
            onDeleteClick = { uri ->
                selectedImages.remove(uri)
                imageAdapter.submitList(selectedImages.toList())
            },
            onImageClick = { uri ->
                showFullscreenImage(uri)
            }
        )

        dialogBinding.imagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
            visibility = if (selectedImages.isNotEmpty()) View.VISIBLE else View.GONE
        }

        imageAdapter.submitList(selectedImages.toList())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Investment Details")
            .setView(dialogBinding.root)
            .setPositiveButton("Update") { _, _ ->
                // Handle update
                val updatedInvestment = investment.copy(
                    name = dialogBinding.nameInput.text.toString(),
                    amount = dialogBinding.amountInput.text.toString().toDoubleOrNull() ?: 0.0,
                    type = dialogBinding.typeInput.text.toString(),
                    description = dialogBinding.descriptionInput.text.toString(),
                    imageUri = selectedImages.joinToString(",") { it.toString() }
                )
                viewModel.updateInvestment(updatedInvestment)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showFullscreenImage(uri: Uri) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = PhotoView(requireContext()).apply {
            setImageURI(uri)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        dialog.setContentView(imageView)
        dialog.show()

        imageView.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showDeleteConfirmation(investment: Investment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Investment")
            .setMessage("Are you sure you want to delete this investment?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteInvestment(investment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.allInvestments.observe(viewLifecycleOwner) { investments ->
            adapter.submitList(investments)
            binding.emptyStateText.visibility = if (investments.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dialogBinding = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, refresh the view
                    observeViewModel()
                } else {
                    Toast.makeText(context, "Permission required to show images", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}