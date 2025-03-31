package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.databinding.FragmentDatabaseManagementBinding
import com.example.allinone.databinding.ItemDatabaseRecordBinding
import com.example.allinone.firebase.DataChangeNotifier
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseManagementFragment : Fragment() {
    private var _binding: FragmentDatabaseManagementBinding? = null
    private val binding get() = _binding!!
    
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var recordAdapter: DatabaseRecordAdapter
    
    // All available collections in Firebase
    private val collections = listOf(
        "transactions", 
        "investments", 
        "notes", 
        "students", 
        "events", 
        "wtLessons", 
        "registrations",
        "counters"
    )
    
    // Currently selected collection
    private var currentCollection = "transactions"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDatabaseManagementBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupTabLayout()
        setupRefreshButton()
        setupDataChangeObservers()
        
        // Load data for the initial tab
        loadCollectionData(currentCollection)
    }
    
    private fun setupRecyclerView() {
        recordAdapter = DatabaseRecordAdapter()
        binding.dataRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recordAdapter
        }
    }
    
    private fun setupTabLayout() {
        // Set the initial tab based on savedInstanceState or default
        val initialPosition = collections.indexOf(currentCollection).takeIf { it >= 0 } ?: 0
        binding.tabLayout.getTabAt(initialPosition)?.select()
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val position = tab.position
                if (position >= 0 && position < collections.size) {
                    currentCollection = collections[position]
                    binding.collectionName.text = currentCollection.capitalize()
                    loadCollectionData(currentCollection)
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun setupRefreshButton() {
        binding.refreshButton.setOnClickListener {
            loadCollectionData(currentCollection)
        }
    }
    
    private fun setupDataChangeObservers() {
        // Observe changes for all collections
        DataChangeNotifier.transactionsChanged.observe(viewLifecycleOwner) {
            if (it == true && currentCollection == "transactions") {
                loadCollectionData(currentCollection)
            }
        }
        
        DataChangeNotifier.investmentsChanged.observe(viewLifecycleOwner) {
            if (it == true && currentCollection == "investments") {
                loadCollectionData(currentCollection)
            }
        }
        
        DataChangeNotifier.notesChanged.observe(viewLifecycleOwner) {
            if (it == true && currentCollection == "notes") {
                loadCollectionData(currentCollection)
            }
        }
        
        DataChangeNotifier.studentsChanged.observe(viewLifecycleOwner) {
            if (it == true && currentCollection == "students") {
                loadCollectionData(currentCollection)
            }
        }
        
        DataChangeNotifier.eventsChanged.observe(viewLifecycleOwner) {
            if (it == true && currentCollection == "events") {
                loadCollectionData(currentCollection)
            }
        }
        
        DataChangeNotifier.lessonsChanged.observe(viewLifecycleOwner) {
            if (it == true && currentCollection == "wtLessons") {
                loadCollectionData(currentCollection)
            }
        }
        
        DataChangeNotifier.registrationsChanged.observe(viewLifecycleOwner) {
            if (it == true && currentCollection == "registrations") {
                loadCollectionData(currentCollection)
            }
        }
    }
    
    private fun loadCollectionData(collectionName: String) {
        // Show loading indicators
        binding.dataLoadingProgress.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val documents = withContext(Dispatchers.IO) {
                    firestore.collection(collectionName).get().await().documents
                }
                
                if (documents.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    recordAdapter.submitList(emptyList())
                } else {
                    val records = documents.map { doc ->
                        createDatabaseRecord(doc, collectionName)
                    }
                    recordAdapter.submitList(records)
                }
                
                binding.itemCount.text = "${documents.size} items"
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // Hide loading indicators
                binding.dataLoadingProgress.visibility = View.GONE
            }
        }
    }
    
    private fun createDatabaseRecord(document: DocumentSnapshot, collectionName: String): DatabaseRecord {
        val data = document.data ?: mapOf()
        
        // Extract common fields with fallbacks
        val id = (data["id"] as? Number)?.toLong() ?: 0L
        
        // Generate title based on collection type
        val title = when (collectionName) {
            "transactions" -> (data["description"] as? String) ?: "Transaction"
            "investments" -> (data["name"] as? String) ?: "Investment"
            "notes" -> (data["title"] as? String) ?: "Note"
            "students" -> (data["name"] as? String) ?: "Student"
            "events" -> (data["title"] as? String) ?: "Event"
            "wtLessons" -> "Lesson ${id}"
            "registrations" -> "Registration ${id}"
            "counters" -> "Counter: ${document.id}"
            else -> document.id
        }
        
        // Extract subtitle based on collection type
        val subtitle = when (collectionName) {
            "transactions" -> {
                val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                val isIncome = (data["isIncome"] as? Boolean) ?: false
                val symbol = if (isIncome) "+" else "-"
                "$symbol ₺${amount}"
            }
            "investments" -> {
                val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                val type = (data["type"] as? String) ?: ""
                "$type: ₺${amount}"
            }
            "notes" -> {
                val content = (data["content"] as? String) ?: ""
                if (content.length > 50) content.substring(0, 50) + "..." else content
            }
            "students" -> {
                val phone = (data["phoneNumber"] as? String) ?: ""
                val email = (data["email"] as? String) ?: ""
                if (phone.isNotEmpty()) phone else email
            }
            "events" -> {
                val description = (data["description"] as? String) ?: ""
                if (description.length > 50) description.substring(0, 50) + "..." else description
            }
            "wtLessons" -> {
                val day = (data["dayOfWeek"] as? Number)?.toInt() ?: 0
                val dayName = when (day) {
                    1 -> "Monday"
                    2 -> "Tuesday"
                    3 -> "Wednesday"
                    4 -> "Thursday"
                    5 -> "Friday"
                    6 -> "Saturday"
                    7 -> "Sunday"
                    else -> "Unknown"
                }
                val startHour = (data["startHour"] as? Number)?.toInt() ?: 0
                val startMin = (data["startMinute"] as? Number)?.toInt() ?: 0
                val endHour = (data["endHour"] as? Number)?.toInt() ?: 0
                val endMin = (data["endMinute"] as? Number)?.toInt() ?: 0
                "$dayName $startHour:${startMin.toString().padStart(2, '0')} - $endHour:${endMin.toString().padStart(2, '0')}"
            }
            "registrations" -> {
                val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                val studentId = (data["studentId"] as? Number)?.toLong() ?: 0L
                "Student ID: $studentId, Amount: ₺$amount"
            }
            "counters" -> {
                val count = (data["count"] as? Number)?.toLong() ?: 0L
                "Current value: $count"
            }
            else -> ""
        }
        
        // Get details
        val details = buildDetailsString(data, collectionName)
        
        // Get date
        val date = getDateFromDocument(data)
        
        // Generate tags
        val tags = generateTagsForCollection(data, collectionName)
        
        return DatabaseRecord(
            id = id,
            documentId = document.id,
            title = title,
            subtitle = subtitle,
            details = details,
            date = date,
            tags = tags,
            originalData = data,
            collectionName = collectionName
        )
    }
    
    private fun getDateFromDocument(data: Map<String, Any?>): Date? {
        // Try different date fields
        val dateFields = listOf("date", "lastEdited", "paymentDate", "startDate", "endDate", "last_updated")
        
        for (field in dateFields) {
            when (val value = data[field]) {
                is Timestamp -> return value.toDate()
                is Date -> return value
                is Long -> return Date(value)
            }
        }
        
        return null
    }
    
    private fun buildDetailsString(data: Map<String, Any?>, collectionName: String): String {
        return when (collectionName) {
            "transactions" -> {
                val type = (data["type"] as? String) ?: ""
                val category = (data["category"] as? String) ?: ""
                val relatedRegId = (data["relatedRegistrationId"] as? Number)?.toLong()
                
                buildString {
                    append("Type: $type")
                    if (category.isNotEmpty()) append("\nCategory: $category")
                    if (relatedRegId != null) append("\nRelated Registration: $relatedRegId")
                    if (data["deviceId"] != null) append("\nDevice ID: ${data["deviceId"]}")
                }
            }
            "counters" -> {
                "This counter is used for generating sequential IDs for ${data.keys.firstOrNull() ?: "unknown"} collection"
            }
            else -> {
                // Generic details for other collections
                data.entries
                    .filter { !listOf("id", "title", "name", "content", "date", "imageUri", "deviceId").contains(it.key) }
                    .take(5)
                    .joinToString("\n") { "${it.key}: ${formatValue(it.value)}" }
            }
        }
    }
    
    private fun formatValue(value: Any?): String {
        return when (value) {
            is Timestamp -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(value.toDate())
            is Date -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(value)
            is Boolean -> if (value) "Yes" else "No"
            is Map<*, *> -> "{${value.size} items}"
            is List<*> -> "[${value.size} items]"
            is Long, is Int, is Double, is Float -> value.toString()
            is String -> if (value.length > 30) value.substring(0, 30) + "..." else value
            null -> "-"
            else -> value.toString()
        }
    }
    
    private fun generateTagsForCollection(data: Map<String, Any?>, collectionName: String): List<String> {
        val tags = mutableListOf<String>()
        
        when (collectionName) {
            "transactions" -> {
                val isIncome = (data["isIncome"] as? Boolean) ?: false
                tags.add(if (isIncome) "Income" else "Expense")
                (data["category"] as? String)?.let { if (it.isNotEmpty()) tags.add(it) }
            }
            "investments" -> {
                (data["type"] as? String)?.let { if (it.isNotEmpty()) tags.add(it) }
                val isPast = (data["isPast"] as? Boolean) ?: false
                tags.add(if (isPast) "Past" else "Active")
            }
            "notes" -> {
                val isRichText = (data["isRichText"] as? Boolean) ?: false
                tags.add(if (isRichText) "Rich Text" else "Plain Text")
                if ((data["imageUris"] as? String)?.isNotEmpty() == true) tags.add("With Images")
            }
            "students" -> {
                val isActive = (data["isActive"] as? Boolean) ?: true
                tags.add(if (isActive) "Active" else "Inactive")
            }
            "registrations" -> {
                val isPaid = (data["isPaid"] as? Boolean) ?: false
                tags.add(if (isPaid) "Paid" else "Unpaid")
            }
        }
        
        return tags
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Model class for database records
    data class DatabaseRecord(
        val id: Long,
        val documentId: String,
        val title: String,
        val subtitle: String,
        val details: String,
        val date: Date?,
        val tags: List<String>,
        val originalData: Map<String, Any?>,
        val collectionName: String
    )
    
    // Adapter for database records
    inner class DatabaseRecordAdapter : RecyclerView.Adapter<DatabaseRecordAdapter.RecordViewHolder>() {
        private var recordsList = listOf<DatabaseRecord>()
        
        fun submitList(newRecords: List<DatabaseRecord>) {
            recordsList = newRecords
            notifyDataSetChanged()
        }
        
        // Add method to remove item at position
        fun removeItem(position: Int) {
            if (position >= 0 && position < recordsList.size) {
                val newList = recordsList.toMutableList()
                newList.removeAt(position)
                recordsList = newList
                notifyItemRemoved(position)
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
            val binding = ItemDatabaseRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return RecordViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
            holder.bind(recordsList[position])
        }
        
        override fun getItemCount() = recordsList.size
        
        inner class RecordViewHolder(private val binding: ItemDatabaseRecordBinding) : 
            RecyclerView.ViewHolder(binding.root) {
            
            fun bind(record: DatabaseRecord) {
                binding.recordId.text = "ID: ${record.id}"
                binding.recordTitle.text = record.title
                binding.recordSubtitle.text = record.subtitle
                binding.recordDetails.text = record.details
                
                // Format date if available
                binding.recordDate.text = record.date?.let { 
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it)
                } ?: ""
                
                // Set up tags
                binding.recordTags.removeAllViews()
                record.tags.forEach { tagText ->
                    val chip = Chip(binding.root.context).apply {
                        text = tagText
                        isCheckable = false
                    }
                    binding.recordTags.addView(chip)
                }
                
                // Show full details dialog when user clicks the details button
                binding.detailsButton.setOnClickListener {
                    showDetailsDialog(record)
                }
                
                // Delete action
                binding.deleteButton.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        confirmDelete(record, position)
                    }
                }
            }
        }
    }
    
    private fun showDetailsDialog(record: DatabaseRecord) {
        // Create a formatted string with all document data
        val details = record.originalData.entries.joinToString("\n") { (key, value) ->
            "$key: ${formatValue(value)}"
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("${record.collectionName.capitalize()} #${record.id}")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun confirmDelete(record: DatabaseRecord, position: Int) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Confirmation")
            .setMessage("Are you sure you want to delete this ${record.collectionName} (ID: ${record.id})? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteDocument(record, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteDocument(record: DatabaseRecord, position: Int) {
        // Create and show a progress indicator dialog using MaterialAlertDialogBuilder
        val progressView = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressTextView = progressView.findViewById<TextView>(R.id.progress_text)
        progressTextView.text = "Deleting document..."
        
        val progressDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .show()
        
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    firestore.collection(record.collectionName)
                        .document(record.documentId)
                        .delete()
                        .await()
                }
                
                // Notify other parts of the app about the change
                DataChangeNotifier.notifyCollectionChanged(record.collectionName)
                
                recordAdapter.removeItem(position)
                
                // Update item count
                val newCount = (binding.itemCount.text.toString().split(" ")[0].toIntOrNull() ?: 0) - 1
                binding.itemCount.text = "$newCount items"
                
                Toast.makeText(
                    requireContext(),
                    "${record.collectionName.capitalize()} #${record.id} deleted successfully",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Show empty view if no items left
                if (recordAdapter.itemCount == 0) {
                    binding.emptyView.visibility = View.VISIBLE
                }
                
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error deleting document: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressDialog.dismiss()
            }
        }
    }
    
    // Extension function to capitalize first letter
    private fun String.capitalize(): String {
        return this.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        }
    }
} 