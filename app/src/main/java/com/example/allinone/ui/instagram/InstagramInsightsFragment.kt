package com.example.allinone.ui.instagram

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.allinone.R
import com.example.allinone.databinding.FragmentInstagramInsightsBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InstagramInsightsFragment : Fragment() {

    private var _binding: FragmentInstagramInsightsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val instagramCollection = db.collection("instagram_business")

    companion object {
        private const val TAG = "InstagramInsights"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstagramInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the FAB click listener
        binding.fabShareInsights.setOnClickListener {
            shareInsightsAsJson()
        }
    }

    private fun shareInsightsAsJson() {
        lifecycleScope.launch {
            try {
                // Show a toast to indicate that we're fetching data
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fetching Instagram insights data...", Toast.LENGTH_SHORT).show()
                }

                // Fetch data from Firebase
                val insightsData = fetchInsightsData()

                // Convert to pretty-printed JSON
                val gson = GsonBuilder().setPrettyPrinting().create()
                val jsonString = gson.toJson(insightsData)

                // Share the JSON data
                if (jsonString.isNotEmpty()) {
                    shareJson(jsonString)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No Instagram insights data available", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing insights data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun fetchInsightsData(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch posts data from Firebase
                val postsSnapshot = instagramCollection.get().await()

                // Create a map to hold all the data
                val insightsData = mutableMapOf<String, Any>()

                // Add posts data
                val postsList = mutableListOf<Map<String, Any>>()
                for (document in postsSnapshot.documents) {
                    val postData = document.data
                    if (postData != null) {
                        postsList.add(postData)
                    }
                }

                // Add posts to the insights data
                insightsData["posts"] = postsList

                // Add metadata
                insightsData["metadata"] = mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "count" to postsList.size,
                    "source" to "AllinOne App"
                )

                insightsData
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching insights data", e)
                mapOf("error" to e.message.toString())
            }
        }
    }

    private fun shareJson(jsonString: String) {
        try {
            // Create a timestamp for the filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "instagram_insights_$timestamp.json"

            // Get the app's external files directory
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val jsonFile = File(storageDir, fileName)

            // Write the JSON string to the file
            jsonFile.writeText(jsonString)

            // Get a content URI for the file using FileProvider
            val fileUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                jsonFile
            )

            // Create a share intent for the file
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "application/json"
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Add subject and text for email clients
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Instagram Insights Data")
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Attached is the Instagram Insights data exported from AllinOne App.")

            // Show the share dialog
            startActivity(Intent.createChooser(shareIntent, "Share Instagram Insights JSON File"))

            // Also copy a shorter version to clipboard for convenience
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val shortJson = if (jsonString.length > 1000) jsonString.substring(0, 1000) + "... (truncated)" else jsonString
            val clip = ClipData.newPlainText("Instagram Insights JSON (Preview)", shortJson)
            clipboard.setPrimaryClip(clip)

            // Show a toast to indicate success
            Toast.makeText(context, "JSON file created and ready to share", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing JSON file", e)
            Toast.makeText(context, "Error creating JSON file: ${e.message}", Toast.LENGTH_SHORT).show()

            // Fall back to text sharing if file sharing fails
            fallbackToTextSharing(jsonString)
        }
    }

    private fun fallbackToTextSharing(jsonString: String) {
        try {
            // Create a share intent for plain text
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Instagram Insights Data")
            shareIntent.putExtra(Intent.EXTRA_TEXT, jsonString)

            // Show the share dialog
            startActivity(Intent.createChooser(shareIntent, "Share Instagram Insights Data (Text)"))

            // Copy to clipboard
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Instagram Insights JSON", jsonString)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, "Sharing as text instead. JSON copied to clipboard.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback text sharing", e)
            Toast.makeText(context, "Could not share data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}