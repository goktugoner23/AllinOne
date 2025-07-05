package com.example.allinone.ui

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.InvestmentAdapter
import com.example.allinone.adapters.InvestmentImageAdapter
import com.example.allinone.adapters.InvestmentPagerAdapter
import com.example.allinone.data.Investment
import com.example.allinone.databinding.DialogEditInvestmentBinding
import com.example.allinone.databinding.FragmentInvestmentsBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.example.allinone.viewmodels.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import com.google.firebase.storage.FirebaseStorage
import com.bumptech.glide.Glide
import com.example.allinone.adapters.FullscreenImageAdapter
import com.example.allinone.firebase.DataChangeNotifier
import com.example.allinone.utils.NumberFormatUtils

class InvestmentsFragment : Fragment() {
    private var _binding: FragmentInvestmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var adapter: InvestmentAdapter
    private lateinit var imageAdapter: InvestmentImageAdapter
    private val selectedImages = mutableListOf<Uri>()
    private val PERMISSION_REQUEST_CODE = 123
    private val repository by lazy {
        com.example.allinone.firebase.FirebaseRepository(requireActivity().application)
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.let { selectedUris ->
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupTabLayout()
        } else {
            Toast.makeText(context, "Permission required to show images", Toast.LENGTH_SHORT).show()
        }
    }

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
        setupTabLayout()
        setupClickListeners()
        setupObservers()

        // Check for pending transaction data
        arguments?.let { args ->
            if (args.containsKey("pendingTransactionAmount")) {
                val amount = args.getDouble("pendingTransactionAmount", 0.0)
                val description = args.getString("pendingTransactionDescription")
                val isIncome = args.getBoolean("pendingTransactionIsIncome", false)

                if (amount > 0) {
                    // Show the add investment dialog with pre-filled data
                    showAddInvestmentDialog(
                        pendingAmount = amount,
                        pendingDescription = description,
                        isIncome = isIncome
                    )

                    // Clear the arguments to prevent processing again on configuration change
                    arguments?.clear()
                }
            }
        }
    }

    private fun setupObservers() {
        // Observe add status
        viewModel.addStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is InvestmentsViewModel.AddStatus.SUCCESS -> {
                    Log.d("InvestmentsFragment", "Investment added successfully")
                    // Refresh data in all tabs
                    refreshInvestmentData()
                }
                is InvestmentsViewModel.AddStatus.ERROR -> {
                    Log.e("InvestmentsFragment", "Error adding investment")
                    Toast.makeText(requireContext(), "Error adding investment", Toast.LENGTH_SHORT).show()
                }
                else -> { /* Do nothing */ }
            }
        }

        // Observe update status
        viewModel.updateStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is InvestmentsViewModel.UpdateStatus.SUCCESS -> {
                    Log.d("InvestmentsFragment", "Investment updated successfully")
                    refreshInvestmentData()
                }
                is InvestmentsViewModel.UpdateStatus.ERROR -> {
                    Log.e("InvestmentsFragment", "Error updating investment")
                    Toast.makeText(requireContext(), "Error updating investment", Toast.LENGTH_SHORT).show()
                }
                else -> { /* Do nothing */ }
            }
        }

        // Observe delete status
        viewModel.deleteStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is InvestmentsViewModel.DeleteStatus.SUCCESS -> {
                    Log.d("InvestmentsFragment", "Investment deleted successfully")
                    refreshInvestmentData()
                }
                is InvestmentsViewModel.DeleteStatus.ERROR -> {
                    Log.e("InvestmentsFragment", "Error deleting investment")
                    Toast.makeText(requireContext(), "Error deleting investment", Toast.LENGTH_SHORT).show()
                }
                else -> { /* Do nothing */ }
            }
        }
    }

    private fun refreshInvestmentData() {
        viewModel.refreshData()
    }

    private fun checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun setupTabLayout() {
        // Setup ViewPager with adapter
        val pagerAdapter = InvestmentPagerAdapter(this)
        binding.investmentViewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.investmentTabLayout, binding.investmentViewPager) { tab, position ->
            tab.text = when (position) {
                InvestmentPagerAdapter.INVESTMENTS_TAB -> "Investments"
                InvestmentPagerAdapter.FUTURES_TAB -> "Futures"
                else -> null
            }
        }.attach()
    }

    private fun setupClickListeners() {
        binding.addInvestmentButton.setOnClickListener {
            showAddInvestmentDialog()
        }
    }

    fun showAddInvestmentDialog(
        pendingAmount: Double? = null,
        pendingDescription: String? = null,
        isIncome: Boolean = false
    ) {
        selectedImages.clear()
        val dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)
        this.dialogBinding = dialogBinding

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Investment")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .create()

        // Setup investment type dropdown
        val investmentTypes = arrayOf("Stocks", "Crypto", "Gold", "Other")
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, investmentTypes)
        dialogBinding.typeInput.setAdapter(arrayAdapter)

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
                showFullscreenImage(uri.toString())
            }
        )

        dialogBinding.imagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
        }

        dialogBinding.addImageButton.setOnClickListener {
            getContent.launch("image/*")
        }

        // Add custom positive button with validation
        dialog.setButton(Dialog.BUTTON_POSITIVE, "Save") { _, _ ->
            val name = dialogBinding.nameInput.text?.toString()
            val amountText = dialogBinding.amountInput.text?.toString()
            val type = (dialogBinding.typeInput as? AutoCompleteTextView)?.text?.toString()
            val description = dialogBinding.descriptionInput.text?.toString()
            val isPast = dialogBinding.isPastInvestmentCheckbox.isChecked

            if (name.isNullOrBlank() || amountText.isNullOrBlank() || type.isNullOrBlank()) {
                Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setButton
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setButton
            }

            // Create basic investment without images first
            val investment = Investment(
                id = 0, // Will be auto-generated
                name = name,
                amount = amount,
                type = type,
                description = description,
                imageUri = null, // Will be updated after upload
                date = Date(),
                isPast = isPast
            )

            // Show loading dialog
            val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(R.layout.dialog_loading)
                .setCancelable(false)
                .create()
            loadingDialog.show()

            lifecycleScope.launch {
                try {
                    // SIMPLIFIED APPROACH:
                    // 1. First save investment to get ID
                    val investmentId = viewModel.addInvestmentAndGetId(investment)

                    if (investmentId != null) {
                        Log.d("InvestmentsFragment", "Got investment ID: $investmentId")

                        // 2. If there are images, upload them one by one
                        if (selectedImages.isNotEmpty()) {
                            val imageUrls = mutableListOf<String>()

                            for (imageUri in selectedImages) {
                                // Get the real file URI that Firebase can handle
                                // This is a critical step that might be missing
                                val realUri = getFileUri(imageUri)

                                if (realUri != null) {
                                    try {
                                        val uuid = UUID.randomUUID().toString()
                                        val storageRef = FirebaseStorage.getInstance().reference
                                            .child("investments")
                                            .child(investmentId.toString())
                                            .child("$uuid.jpg")

                                        // Upload the file
                                        val uploadTask = storageRef.putFile(realUri)
                                        uploadTask.await()

                                        // Get download URL
                                        val downloadUrl = storageRef.downloadUrl.await()
                                        imageUrls.add(downloadUrl.toString())
                                        Log.d("InvestmentsFragment", "Image uploaded: $downloadUrl")
                                    } catch (e: Exception) {
                                        Log.e("InvestmentsFragment", "Error uploading image", e)
                                    }
                                }
                            }

                            // 3. Update the investment with image URLs
                            if (imageUrls.isNotEmpty()) {
                                val updatedInvestment = investment.copy(
                                    id = investmentId,
                                    imageUri = imageUrls.joinToString(",")
                                )
                                viewModel.updateInvestment(updatedInvestment)
                            }
                        }

                        // 4. Apply pending transaction if exists
                        if (pendingAmount != null) {
                            val investmentObject = repository.getInvestmentById(investmentId)
                            if (investmentObject != null) {
                                if (isIncome) {
                                    homeViewModel.addIncomeToInvestment(pendingAmount, investmentObject, pendingDescription)
                                } else {
                                    homeViewModel.addExpenseToInvestment(pendingAmount, investmentObject, pendingDescription)
                                }
                                Log.d("InvestmentsFragment", "Applied pending ${if (isIncome) "income" else "expense"} transaction of $pendingAmount to investment $investmentId")
                            }
                        }

                        // Refresh the UI data
                        refreshInvestmentData()
                    } else {
                        Log.e("InvestmentsFragment", "Got null investment ID")
                    }

                    // Hide loading dialog and show success message
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        Toast.makeText(requireContext(), "Investment saved", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    Log.e("InvestmentsFragment", "Error creating investment: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Prefill with pending data if any
        if (pendingAmount != null) {
            dialogBinding.amountInput.setText(pendingAmount.toString())
        }
        if (!pendingDescription.isNullOrBlank()) {
            dialogBinding.descriptionInput.setText(pendingDescription)
        }

        dialog.show()
    }

    fun showInvestmentDetails(investment: Investment) {
        // Skip the view modal and directly open the edit modal
        showEditInvestmentDialog(investment)
    }

    private fun showEditInvestmentDialog(investment: Investment) {
        val dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)
        this.dialogBinding = dialogBinding

        // Populate fields with investment data
        dialogBinding.nameInput.setText(investment.name)
        dialogBinding.amountInput.setText(investment.amount.toString())
        dialogBinding.typeInput.setText(investment.type)
        dialogBinding.descriptionInput.setText(investment.description ?: "")
        dialogBinding.isPastInvestmentCheckbox.isChecked = investment.isPast

        // Setup investment type dropdown
        val investmentTypes = arrayOf("Stocks", "Crypto", "Gold", "Other")
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, investmentTypes)
        dialogBinding.typeInput.setAdapter(arrayAdapter)

        // Setup images
        selectedImages.clear()
        val existingImageUris = investment.imageUri?.split(",")?.filter { it.isNotBlank() }?.map { Uri.parse(it) } ?: emptyList()
        selectedImages.addAll(existingImageUris)

        imageAdapter = InvestmentImageAdapter(
            onDeleteClick = { uri ->
                selectedImages.remove(uri)
                imageAdapter.submitList(selectedImages.toList())
                if (selectedImages.isEmpty()) {
                    dialogBinding.imagesRecyclerView.visibility = View.GONE
                }
            },
            onImageClick = { uri -> showFullscreenImage(uri.toString()) }
        )

        dialogBinding.imagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
            visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
        }

        imageAdapter.submitList(selectedImages.toList())

        dialogBinding.addImageButton.setOnClickListener {
            getContent.launch("image/*")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Investment")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setButton(Dialog.BUTTON_POSITIVE, "Save") { _, _ ->
            val name = dialogBinding.nameInput.text?.toString()
            val amountText = dialogBinding.amountInput.text?.toString()
            val type = (dialogBinding.typeInput as? AutoCompleteTextView)?.text?.toString()
            val description = dialogBinding.descriptionInput.text?.toString()
            val isPast = dialogBinding.isPastInvestmentCheckbox.isChecked

            if (name.isNullOrBlank() || amountText.isNullOrBlank() || type.isNullOrBlank()) {
                Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setButton
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setButton
            }

            // Handle update with existing and new images
            lifecycleScope.launch {
                try {
                    // First handle images if needed
                    var imageUriString = investment.imageUri

                    // Check if image list was modified
                    val oldImages = investment.imageUri?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                    val currentImageUris = selectedImages.map { it.toString() }.toSet()

                    if (oldImages != currentImageUris) {
                        // Images changed, update them
                        val newImageUris = mutableListOf<String>()

                        // Keep track of existing images that we're keeping
                        val existingImages = selectedImages.filter {
                            it.toString().startsWith("http")
                        }.map { it.toString() }
                        newImageUris.addAll(existingImages)

                        // Upload any new images
                        val newImages = selectedImages.filter {
                            !it.toString().startsWith("http")
                        }

                        if (newImages.isNotEmpty()) {
                            for (imageUri in newImages) {
                                val realUri = getFileUri(imageUri)
                                if (realUri != null) {
                                    try {
                                        val uuid = UUID.randomUUID().toString()
                                        val storageRef = FirebaseStorage.getInstance().reference
                                            .child("investments")
                                            .child(investment.id.toString())
                                            .child("$uuid.jpg")

                                        val uploadTask = storageRef.putFile(realUri)
                                        uploadTask.await()

                                        val downloadUrl = storageRef.downloadUrl.await()
                                        newImageUris.add(downloadUrl.toString())
                                    } catch (e: Exception) {
                                        Log.e("InvestmentsFragment", "Error uploading image", e)
                                    }
                                }
                            }
                        }

                        imageUriString = newImageUris.joinToString(",")
                    }

                    // Check if investment amount is being decreased and not marked as past
                    if (!isPast && amount < investment.amount) {
                        // Amount is being decreased, ask user if they want to add the difference as income
                        val amountDifference = investment.amount - amount

                        withContext(Dispatchers.Main) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Investment Reduced")
                                .setMessage("You've reduced this investment by ${amountDifference}. Would you like to add this amount as income (e.g., from liquidation)?")
                                .setPositiveButton("Yes, Add as Income") { _, _ ->
                                    // Create the updated investment
                                    val updatedInvestment = investment.copy(
                                        name = name,
                                        amount = amount,
                                        type = type,
                                        description = description,
                                        imageUri = imageUriString,
                                        isPast = isPast
                                    )

                                    // Update with flag to add difference as income
                                    viewModel.updateInvestmentWithAmountReduction(updatedInvestment, true)

                                    // Explicitly refresh after update
                                    refreshInvestmentData()

                                    Toast.makeText(requireContext(), "Investment updated with income added", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                .setNegativeButton("No, Just Reduce") { _, _ ->
                                    // Create the updated investment
                                    val updatedInvestment = investment.copy(
                                        name = name,
                                        amount = amount,
                                        type = type,
                                        description = description,
                                        imageUri = imageUriString,
                                        isPast = isPast
                                    )

                                    // Update without adding difference as income
                                    viewModel.updateInvestmentWithAmountReduction(updatedInvestment, false)

                                    // Explicitly refresh after update
                                    refreshInvestmentData()

                                    Toast.makeText(requireContext(), "Investment updated", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                .show()
                        }
                    } else {
                        // Normal update (amount increased or unchanged, or investment is marked as past)
                        val updatedInvestment = investment.copy(
                            name = name,
                            amount = amount,
                            type = type,
                            description = description,
                            imageUri = imageUriString,
                            isPast = isPast
                        )

                        viewModel.updateInvestment(updatedInvestment)

                        // Explicitly refresh after update
                        refreshInvestmentData()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Investment updated", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }

                } catch (e: Exception) {
                    Log.e("InvestmentsFragment", "Error updating investment", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error updating investment: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    fun showDeleteConfirmation(investment: Investment) {
        val options = arrayOf("Add Profit/Loss", "Delete", "Liquidate")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Investment Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Add Profit/Loss
                        showProfitLossDialog(investment)
                    }
                    1 -> { // Delete
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete Investment")
                            .setMessage("Are you sure you want to delete '${investment.name}'?")
                            .setPositiveButton("Delete") { _, _ ->
                                viewModel.deleteInvestment(investment)
                                Toast.makeText(requireContext(), "${investment.name} deleted", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    2 -> { // Liquidate
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Liquidate Investment")
                            .setMessage("Are you sure you want to liquidate '${investment.name}'? This will remove the investment without affecting your transaction history.")
                            .setPositiveButton("Liquidate") { _, _ ->
                                liquidateInvestment(investment)
                                Toast.makeText(requireContext(), "${investment.name} liquidated", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showProfitLossDialog(investment: Investment) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profit_loss, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Profit/Loss to ${investment.name}")
            .setView(dialogView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val amountInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.profitLossAmountInput)
                val profitRadio = dialogView.findViewById<com.google.android.material.radiobutton.MaterialRadioButton>(R.id.profitRadio)
                
                val amount = amountInput.text.toString().toDoubleOrNull()
                
                if (amount == null || amount <= 0) {
                    amountInput.error = "Please enter a valid amount"
                    return@setOnClickListener
                }
                
                val isProfit = profitRadio.isChecked
                
                viewModel.addProfitLossToInvestment(investment, amount, isProfit)
                
                Toast.makeText(requireContext(), 
                    "${if (isProfit) "Profit" else "Loss"} of ${NumberFormatUtils.formatAmount(amount)} added to ${investment.name}", 
                    Toast.LENGTH_SHORT).show()
                
                dialog.dismiss()
            }
        }

        dialog.show()
    }
    
    private fun liquidateInvestment(investment: Investment) {
        lifecycleScope.launch {
            try {
                // Use the viewModel's liquidateInvestment method
                viewModel.liquidateInvestment(investment)
            } catch (e: Exception) {
                Log.e("InvestmentsFragment", "Error liquidating investment: ${e.message}", e)
                Toast.makeText(requireContext(), "Error liquidating investment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showFullscreenImage(uri: String?) {
        if (uri == null) return

        // Determine if uri is part of an investment with multiple images
        val allImages = mutableListOf<String>()
        var initialPosition = 0

        // Get the current investment (if any) that contains this image
        val investment = viewModel.allInvestments.value?.find { inv ->
            inv.imageUri?.split(",")?.filter { it.isNotBlank() }?.contains(uri) == true
        }

        // If we found the investment, get all its images
        if (investment != null && !investment.imageUri.isNullOrBlank()) {
            val images = investment.imageUri.split(",").filter { it.isNotBlank() }
            allImages.addAll(images)
            initialPosition = images.indexOf(uri).coerceAtLeast(0)
        } else {
            // Just show this single image
            allImages.add(uri)
        }

        // Create the dialog
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_fullscreen_image, null)
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)

        // Setup ViewPager
        val viewPager = dialogView.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.fullscreenViewPager)
        val imageCounter = dialogView.findViewById<TextView>(R.id.imageCounterText)

        // Setup adapter for the ViewPager
        val adapter = com.example.allinone.adapters.FullscreenImageAdapter(requireContext(), allImages)
        viewPager.adapter = adapter

        // Set initial position
        viewPager.setCurrentItem(initialPosition, false)

        // Update counter text
        updateImageCounter(imageCounter, initialPosition, allImages.size)

        // Listen for page changes
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateImageCounter(imageCounter, position, allImages.size)
            }
        })

        // Show dialog
        dialog.show()

        // Close on click anywhere on the screen
        dialogView.setOnClickListener { dialog.dismiss() }
    }

    private fun updateImageCounter(textView: TextView, position: Int, total: Int) {
        if (total <= 1) {
            textView.visibility = View.GONE
        } else {
            textView.visibility = View.VISIBLE
            textView.text = "${position + 1} / $total"
        }
    }

    private fun getFileUri(uri: Uri): Uri? {
        return try {
            uri
        } catch (e: Exception) {
            Log.e("InvestmentsFragment", "Error getting file URI", e)
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dialogBinding = null
    }
}