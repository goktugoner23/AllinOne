package com.example.allinone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.LogEntryAdapter
import com.example.allinone.databinding.FragmentLogErrorsBinding
import com.example.allinone.viewmodels.LogErrorViewModel

class LogErrorsFragment : Fragment() {

    private var _binding: FragmentLogErrorsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: LogErrorViewModel
    private lateinit var adapter: LogEntryAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogErrorsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[LogErrorViewModel::class.java]
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup button listeners
        setupButtonListeners()
        
        // Observe LiveData
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        adapter = LogEntryAdapter(requireContext())
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupButtonListeners() {
        // Refresh button
        binding.refreshButton.setOnClickListener {
            viewModel.refreshLogs()
        }
        
        // Clear button
        binding.clearButton.setOnClickListener {
            viewModel.clearLogs()
        }
        
        // Share button
        binding.shareButton.setOnClickListener {
            shareLogErrors()
        }
    }
    
    private fun observeViewModel() {
        // Observe log entries
        viewModel.logEntries.observe(viewLifecycleOwner) { logEntries ->
            adapter.updateData(logEntries)
            
            // Show/hide empty view
            if (logEntries.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            }
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun shareLogErrors() {
        val formattedLogs = viewModel.getFormattedLogsForSharing()
        
        // First copy to clipboard
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Error Logs", formattedLogs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        
        // Then offer to share
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "AllinOne App Error Logs")
        shareIntent.putExtra(Intent.EXTRA_TEXT, formattedLogs)
        
        // Start the share activity
        startActivity(Intent.createChooser(shareIntent, "Share Error Logs"))
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 