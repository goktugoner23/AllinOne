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
import com.example.allinone.data.Investment
import com.example.allinone.databinding.DialogEditInvestmentBinding
import com.example.allinone.databinding.FragmentInvestmentsBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.example.allinone.viewmodels.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import com.google.firebase.storage.FirebaseStorage

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            observeViewModel()
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
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()

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

    private fun checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
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

    private fun showAddInvestmentDialog(
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
                                    Log.d("InvestmentsFragment", "Uploading image: $realUri")

                                    // Direct Firebase Storage reference approach
                                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                                        .child("investments") // Folder name
                                        .child(investmentId.toString()) // ID subfolder
                                        .child("img_" + UUID.randomUUID().toString() + ".jpg") // Unique image name

                                    try {
                                        // Use putFile with null metadata to avoid permission issues
                                        Log.d("InvestmentsFragment", "Starting upload to path: investments/${investmentId}/img_xxx.jpg")
                                        val uploadTask = storageRef.putFile(realUri,
                                            com.google.firebase.storage.StorageMetadata.Builder()
                                                .setContentType("image/jpeg")
                                                .build()
                                        )

                                        // Add progress listener
                                        uploadTask.addOnProgressListener { taskSnapshot ->
                                            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                                            Log.d("InvestmentsFragment", "Upload progress: $progress%")
                                        }

                                        val taskSnapshot = uploadTask.await()
                                        val downloadUrl = taskSnapshot.storage.downloadUrl.await().toString()

                                        Log.d("InvestmentsFragment", "Upload success: $downloadUrl")
                                        imageUrls.add(downloadUrl)
                                    } catch (e: Exception) {
                                        Log.e("InvestmentsFragment", "Failed to upload image: ${e.message}", e)
                                    }
                                } else {
                                    Log.e("InvestmentsFragment", "Failed to get real URI for $imageUri")
                                }
                            }

                            // 3. Update investment with image URLs
                            if (imageUrls.isNotEmpty()) {
                                Log.d("InvestmentsFragment", "Updating investment with ${imageUrls.size} image URLs")
                                val joinedUrls = imageUrls.joinToString(",")
                                val updatedInvestment = investment.copy(id = investmentId, imageUri = joinedUrls)
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

        dialog.show()
    }

    /**
     * Get a file URI that Firebase can handle (content:// URI)
     */
    private fun getFileUri(uri: Uri): Uri? {
        // If it's already a file URI or content URI, return it
        val uriString = uri.toString()
        Log.d("InvestmentsFragment", "Getting file URI for: $uriString")

        return when {
            uriString.startsWith("content://") -> uri
            uriString.startsWith("file://") -> uri
            else -> {
                try {
                    // For http/https URLs, we can't upload directly
                    // These are likely existing URLs that are already in Firebase
                    if (uriString.startsWith("http")) {
                        Log.d("InvestmentsFragment", "URL is already in Firebase: $uriString")
                        return uri
                    }

                    // Try to convert to URI
                    Uri.parse(uriString)
                } catch (e: Exception) {
                    Log.e("InvestmentsFragment", "Error parsing URI: $uriString", e)
                    null
                }
            }
        }
    }

    /**
     * Upload investment images to ID-specific folders and then update the investment
     * @deprecated No longer used - uploads are handled directly in showAddInvestmentDialog
     */
    @Suppress("UNUSED_PARAMETER")
    private fun uploadInvestmentWithImages(initialInvestment: Investment, imageUris: List<Uri>) {
        // This method is deprecated in favor of direct approach in showAddInvestmentDialog
        // We're keeping the method signature to avoid compilation errors but it's no longer used
        Log.d("InvestmentsFragment", "This method is deprecated, using direct upload instead")
    }

    private fun showInvestmentDetails(investment: Investment? = null) {
        if (investment == null) return

        Log.d("InvestmentsFragment", "Showing details for investment: ${investment.name}, image URIs: ${investment.imageUri}")

        dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)

        // Setup investment type dropdown
        val types = arrayOf("Crypto", "Stock", "Gold", "Other")
        val typeAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, types)
        dialogBinding?.typeInput?.apply {
            setAdapter(typeAdapter)
            setText(investment.type, false)  // false prevents filtering the dropdown list
        }

        // Populate fields
        dialogBinding?.nameInput?.setText(investment.name)
        dialogBinding?.amountInput?.setText(investment.amount.toString())
        dialogBinding?.descriptionInput?.setText(investment.description)
        dialogBinding?.isPastInvestmentCheckbox?.isChecked = investment.isPast

        // Setup images
        selectedImages.clear()

        // Check if we have image URIs in the investment
        if (!investment.imageUri.isNullOrEmpty()) {
            Log.d("InvestmentsFragment", "Found image URIs: ${investment.imageUri}")

            // Split by comma and process each URI
            val imageUrisList = investment.imageUri.split(",").filter { it.isNotEmpty() }
            Log.d("InvestmentsFragment", "Processing ${imageUrisList.size} image URIs")

            for (uriString in imageUrisList) {
                try {
                    val trimmedUri = uriString.trim()
                    Log.d("InvestmentsFragment", "Processing URI: $trimmedUri")
                    val uri = Uri.parse(trimmedUri)
                    Log.d("InvestmentsFragment", "Adding URI to selectedImages: $uri")
                    selectedImages.add(uri)
                } catch (e: Exception) {
                    Log.e("InvestmentsFragment", "Error parsing URI: $uriString", e)
                }
            }

            Log.d("InvestmentsFragment", "Final selected images count: ${selectedImages.size}")
        } else {
            Log.d("InvestmentsFragment", "No image URIs found in investment")
        }

        imageAdapter = InvestmentImageAdapter(
            onDeleteClick = { uri ->
                Log.d("InvestmentsFragment", "Removing image URI: $uri")
                selectedImages.remove(uri)
                imageAdapter.submitList(selectedImages.toList())
                if (selectedImages.isEmpty()) {
                    dialogBinding?.imagesRecyclerView?.visibility = View.GONE
                }
            },
            onImageClick = { uri ->
                Log.d("InvestmentsFragment", "Image clicked: $uri")
                showFullscreenImage(uri)
            }
        )

        dialogBinding?.imagesRecyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
            visibility = if (selectedImages.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Make sure to update the adapter with the latest list
        imageAdapter.submitList(selectedImages.toList())

        // Add click listener for the add image button
        dialogBinding?.addImageButton?.setOnClickListener {
            getContent.launch("image/*")
        }

        // Create dialog with update button
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Investment Details")
            .setView(dialogBinding?.root)
            .setPositiveButton("Update") { _, _ ->
                val updatedInvestment = investment.copy(
                    name = dialogBinding?.nameInput?.text?.toString() ?: "",
                    amount = dialogBinding?.amountInput?.text?.toString()?.toDoubleOrNull() ?: 0.0,
                    type = dialogBinding?.typeInput?.text?.toString() ?: "",
                    description = dialogBinding?.descriptionInput?.text?.toString(),
                    imageUri = selectedImages.joinToString(",") { it.toString() },
                    isPast = dialogBinding?.isPastInvestmentCheckbox?.isChecked ?: false
                )

                // Use the same loading dialog and direct Firebase Storage approach for edit mode too
                val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                    .setView(R.layout.dialog_loading)
                    .setCancelable(false)
                    .create()

                lifecycleScope.launch {
                    try {
                        loadingDialog.show()

                        // Find any new images that need to be uploaded (local URIs)
                        val newImages = selectedImages.filter {
                            !it.toString().startsWith("http") && !(investment.imageUri?.contains(it.toString()) ?: false)
                        }

                        // If there are new images, upload them and append to existing URLs
                        if (newImages.isNotEmpty()) {
                            Log.d("InvestmentsFragment", "Uploading ${newImages.size} new images for investment ${investment.id}")

                            val imageUrls = mutableListOf<String>()
                            // Add existing HTTP URLs
                            imageUrls.addAll(selectedImages.filter { it.toString().startsWith("http") }.map { it.toString() })

                            // Upload new images
                            for (imageUri in newImages) {
                                val realUri = getFileUri(imageUri)
                                if (realUri != null) {
                                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                                        .child("investments")
                                        .child(investment.id.toString())
                                        .child("img_" + UUID.randomUUID().toString() + ".jpg")

                                    try {
                                        Log.d("InvestmentsFragment", "Uploading new image in edit mode: $realUri")
                                        val uploadTask = storageRef.putFile(realUri,
                                            com.google.firebase.storage.StorageMetadata.Builder()
                                                .setContentType("image/jpeg")
                                                .build()
                                        )
                                        val taskSnapshot = uploadTask.await()
                                        val downloadUrl = taskSnapshot.storage.downloadUrl.await().toString()

                                        Log.d("InvestmentsFragment", "Edit mode upload success: $downloadUrl")
                                        imageUrls.add(downloadUrl)
                                    } catch (e: Exception) {
                                        Log.e("InvestmentsFragment", "Failed to upload image in edit mode: ${e.message}", e)
                                    }
                                }
                            }

                            // Update image URIs with both existing and new URLs
                            if (imageUrls.isNotEmpty()) {
                                val investmentWithImages = updatedInvestment.copy(imageUri = imageUrls.joinToString(","))
                                viewModel.updateInvestment(investmentWithImages)
                            } else {
                                viewModel.updateInvestment(updatedInvestment)
                            }
                        } else {
                            // No new images to upload, just update the investment
                            viewModel.updateInvestment(updatedInvestment)
                        }

                        withContext(Dispatchers.Main) {
                            loadingDialog.dismiss()
                            Toast.makeText(context, "Investment updated", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("InvestmentsFragment", "Error updating investment: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            loadingDialog.dismiss()
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
    }

    private fun showFullscreenImage(uri: Uri) {
        Log.d("InvestmentsFragment", "Showing fullscreen image: $uri")
        try {
            val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val imageView = PhotoView(requireContext()).apply {
                try {
                    // If it's a cloud URL, use Glide to load it
                    val uriString = uri.toString()
                    Log.d("InvestmentsFragment", "Loading image URI: $uriString")

                    if (uriString.startsWith("http")) {
                        Log.d("InvestmentsFragment", "Loading HTTP URL with Glide")
                        com.bumptech.glide.Glide.with(requireContext())
                            .load(uriString)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_close_clear_cancel)
                            .into(this)
                    } else {
                        // It's a content URI, load directly
                        Log.d("InvestmentsFragment", "Loading local URI directly")
                        setImageURI(uri)
                    }

                    // Set layout parameters
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                } catch (e: Exception) {
                    Log.e("InvestmentsFragment", "Error loading image: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Set content view and show dialog
            dialog.setContentView(imageView)
            dialog.show()

            // Add click listener to dismiss on tap
            imageView.setOnClickListener {
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Log.e("InvestmentsFragment", "Error showing fullscreen image: ${e.message}", e)
            Toast.makeText(requireContext(), "Error showing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(investment: Investment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Investment")
            .setMessage("Are you sure you want to delete this investment?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteInvestmentAndTransaction(investment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.allInvestments.observe(viewLifecycleOwner) { investments ->
            adapter.submitList(investments)
            binding.emptyStateText.visibility = if (investments.isEmpty()) View.VISIBLE else View.GONE

            // Update summary card
            val totalAmount = investments.sumOf { it.amount }
            binding.totalInvestmentsText.text = String.format("Total Investments: ₺%.2f", totalAmount)
            binding.investmentCountText.text = "Number of Investments: ${investments.size}"
        }
    }

    private fun uploadImageToFirebaseStorage(imageUri: Uri, callback: (Uri?) -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference
            .child("images")
            .child("investment_${System.currentTimeMillis()}") // Use timestamp instead of UUID

        // Start upload
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    callback(uri)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    // Helper method to show toast messages
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dialogBinding = null
    }
}