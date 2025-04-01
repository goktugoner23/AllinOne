package com.example.allinone.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.adapters.FileStructureAdapter
import com.example.allinone.data.model.SourceCodeModel
import com.example.allinone.utils.SourceCodeUtils

class SourceCodeViewerFragment : Fragment() {
    private val TAG = "SourceCodeViewerFrag"
    private lateinit var fileStructureAdapter: FileStructureAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    
    // Track if we're at root level (to show drawer or back button)
    private var isAtRootLevel = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle back button press
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Try to navigate up in directory structure first
                if (::fileStructureAdapter.isInitialized && !fileStructureAdapter.isAtRootLevel()) {
                    // If we're not at the root, navigate back one level in the directory structure
                    fileStructureAdapter.navigateBack()
                    
                    // Update the directory title
                    updateToolbarTitle()
                    
                    // Update home button icon based on whether we're now at root
                    isAtRootLevel = fileStructureAdapter.isAtRootLevel()
                    updateHomeAsUpIndicator()
                } else {
                    // We're at the root, exit the source code viewer
                    isEnabled = false
                    findNavController().navigateUp()
                    isEnabled = true
                }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_source_code_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated")
        
        recyclerView = view.findViewById(R.id.fileStructureRecyclerView)
        emptyMessage = view.findViewById(R.id.emptyFilesMessage)
        
        // Set root level immediately to true to ensure drawer icon shows on initial load
        isAtRootLevel = true
        setupToolbar()
        setupRecyclerView()
        showLoading(true)
        loadSourceCode()
        
        // New way to handle menu
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here if needed
            }
            
            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                // Handle menu item selection here
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure drawer icon is always shown when returning to this fragment
        if (::fileStructureAdapter.isInitialized && fileStructureAdapter.isAtRootLevel()) {
            isAtRootLevel = true
            updateHomeAsUpIndicator()
        }
    }
    
    private fun setupToolbar() {
        // Just configure the activity's toolbar
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Source Code"
        }
        
        // Force drawer icon to appear at start
        updateHomeAsUpIndicator()
        
        // Get parent activity
        val mainActivity = requireActivity() as AppCompatActivity
        
        // Get parent activity's toolbar
        val activityToolbar = mainActivity.findViewById<Toolbar>(R.id.toolbar)
        
        // Set up navigation icon click listener on the activity's toolbar
        activityToolbar?.setNavigationOnClickListener {
            if (!isAtRootLevel && ::fileStructureAdapter.isInitialized) {
                // In a subdirectory, go back one level
                if (fileStructureAdapter.navigateBack()) {
                    // Update the directory title
                    updateToolbarTitle()
                    
                    // Update the root level flag based on the adapter's state
                    isAtRootLevel = fileStructureAdapter.isAtRootLevel()
                    updateHomeAsUpIndicator()
                }
            } else {
                // At root level, use MainActivity's drawer toggle
                try {
                    // Try to access the drawer toggle method via reflection
                    val method = mainActivity.javaClass.getDeclaredMethod("openDrawer")
                    method.isAccessible = true
                    method.invoke(mainActivity)
                } catch (e: Exception) {
                    // Fallback to standard behavior
                    Log.e(TAG, "Could not open drawer: ${e.message}")
                    
                    // Try to find DrawerLayout directly and open it
                    try {
                        val drawerLayout = mainActivity.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
                        if (drawerLayout != null) {
                            drawerLayout.openDrawer(GravityCompat.START)
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Fallback drawer open failed: ${e2.message}")
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        fileStructureAdapter = FileStructureAdapter { item ->
            if (item.isDirectory) {
                // If it's a directory, navigate to it
                fileStructureAdapter.navigateToDirectory(item)
                isAtRootLevel = fileStructureAdapter.isAtRootLevel()
                updateToolbarTitle()
                updateHomeAsUpIndicator()
            } else {
                // If it's a file, navigate to the code viewer
                navigateToCodeViewer(item)
            }
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = fileStructureAdapter
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            recyclerView.visibility = View.GONE
            emptyMessage.visibility = View.VISIBLE
            emptyMessage.text = "Loading source code files..."
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyMessage.visibility = View.GONE
        }
    }
    
    private fun updateToolbarTitle() {
        // Use shorter title format to avoid duplication
        if (::fileStructureAdapter.isInitialized) {
            val directory = fileStructureAdapter.currentDirectory
            if (directory == "Source Code") {
                (requireActivity() as AppCompatActivity).supportActionBar?.title = "Source Code"
            } else {
                (requireActivity() as AppCompatActivity).supportActionBar?.title = directory
            }
        } else {
            (requireActivity() as AppCompatActivity).supportActionBar?.title = "Source Code"
        }
    }

    private fun updateHomeAsUpIndicator() {
        val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
        if (actionBar != null) {
            if (isAtRootLevel) {
                // Show hamburger menu at root level
                actionBar.setHomeAsUpIndicator(R.drawable.ic_menu)
            } else {
                // Show back arrow in subdirectories
                actionBar.setHomeAsUpIndicator(R.drawable.ic_back)
            }
        }
    }

    private fun loadSourceCode() {
        Log.d(TAG, "Starting to load source code")
        context?.let { context ->
            // Run in background thread to avoid UI freeze
            Thread {
                try {
                    Log.d(TAG, "Loading source code from JSON")
                    val sourceCode = SourceCodeUtils.loadSourceCodeFromJson(context)
                    
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        
                        if (sourceCode != null) {
                            Log.d(TAG, "Source code loaded successfully. Children count: ${sourceCode.children?.size ?: 0}")
                            
                            // Debug children info
                            sourceCode.children?.forEachIndexed { index, child ->
                                Log.d(TAG, "Child $index: ${child.name}, isDirectory: ${child.isDirectory}")
                            }
                            
                            if (sourceCode.children?.isNotEmpty() == true) {
                                fileStructureAdapter.submitInitialList(sourceCode.children)
                                isAtRootLevel = true  // Always true after initial load
                                updateToolbarTitle()
                                updateHomeAsUpIndicator()
                                showLoading(false)
                            } else {
                                Log.w(TAG, "Source code has no children")
                                showNoFilesMessage()
                            }
                        } else {
                            Log.e(TAG, "Source code is null")
                            showErrorMessage("Error loading source code structure")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading source code", e)
                    activity?.runOnUiThread {
                        if (isAdded) {
                            showErrorMessage("Error: ${e.message}")
                            Toast.makeText(context, "Failed to load source code: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.start()
        }
    }
    
    private fun showNoFilesMessage() {
        recyclerView.visibility = View.GONE
        emptyMessage.visibility = View.VISIBLE
        emptyMessage.text = "No source code files found"
    }
    
    private fun showErrorMessage(message: String) {
        recyclerView.visibility = View.GONE
        emptyMessage.visibility = View.VISIBLE
        emptyMessage.text = "Error: $message"
    }
    
    private fun navigateToCodeViewer(file: SourceCodeModel) {
        Log.d(TAG, "Navigating to file: ${file.name}, path: ${file.path}")
        
        // Create a bundle with file information
        val bundle = Bundle().apply {
            putString("filePath", file.path)
            putString("fileName", file.name)
        }
        
        // Navigate to the code viewer fragment
        findNavController().navigate(R.id.action_nav_source_code_to_codeViewer, bundle)
    }
} 