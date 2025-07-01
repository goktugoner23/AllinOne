package com.example.allinone.ui.instagram

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.databinding.FragmentInstagramPostsBinding
import com.example.allinone.feature.instagram.data.model.*
import com.example.allinone.feature.instagram.ui.adapter.PostsAdapter
import com.example.allinone.feature.instagram.ui.viewmodel.InstagramViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class InstagramPostsFragment : Fragment() {

    private var _binding: FragmentInstagramPostsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstagramViewModel by viewModels()
    private lateinit var postsAdapter: PostsAdapter

    companion object {
        private const val TAG = "InstagramPostsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstagramPostsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        setupRefresh()

        // Initialize data loading
        viewModel.initialize()
    }

    private fun setupRecyclerView() {
        postsAdapter = PostsAdapter { post ->
            showPostDetails(post)
        }

        binding.recyclerInstagramPosts.apply {
            adapter = postsAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPosts(forceSync = true)
        }
    }

    private fun observeViewModel() {
        // Observe Instagram posts data
        viewModel.postsData.observe(viewLifecycleOwner) { result ->
            when (result) {
                is InstagramResult.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.emptyStateView.isVisible = false
                }

                is InstagramResult.Success -> {
                    binding.progressBar.isVisible = false
                    binding.emptyStateView.isVisible = false
                    postsAdapter.updatePosts(result.data.posts)
                    
                    // Update UI with sync info
                    val syncInfo = result.data.syncInfo
                    val syncMessage = when {
                        syncInfo.triggered -> "Synced: ${syncInfo.reason} (${syncInfo.processingTime}ms)"
                        else -> "Loaded from ${result.data.source}"
                    }
                    binding.textLastSync.text = syncMessage
                    
                    Log.d(TAG, "Posts loaded successfully: ${result.data.count} posts from ${result.data.source}")
                    if (syncInfo.triggered) {
                        Log.d(TAG, "Sync triggered: ${syncInfo.reason}")
                    }
                }

                is InstagramResult.Error -> {
                    binding.progressBar.isVisible = false
                    binding.emptyStateView.isVisible = true
                    binding.emptyStateView.text = "Error: ${result.message}"
                    Toast.makeText(context, "Failed to load posts: ${result.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Posts load failed: ${result.message}")
                }
            }
        }

        // Observe sync info for detailed status
        viewModel.lastSyncInfo.observe(viewLifecycleOwner) { syncInfo ->
            syncInfo?.let {
                if (it.triggered && it.newPosts != null && it.newPosts > 0) {
                    Toast.makeText(context, "Found ${it.newPosts} new posts!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe loading states
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            binding.swipeRefresh.isRefreshing = isRefreshing
        }
    }

    private fun showPostDetails(post: InstagramPost) {
        val details = StringBuilder()
        details.append("📝 Caption: ${post.caption}\n\n")
        details.append("📅 Posted: ${post.formattedDate ?: formatDate(post.timestamp)}\n")
        details.append("📱 Type: ${post.mediaType}\n")
        details.append("🔗 Link: ${post.permalink}\n\n")
        details.append("📊 METRICS:\n")
        
        // Core metrics
        details.append("❤️ Likes: ${formatNumber(post.metrics.likesCount)}\n")
        details.append("💬 Comments: ${formatNumber(post.metrics.commentsCount)}\n")
        details.append("📈 Engagement Rate: ${String.format("%.1f", post.metrics.engagementRate)}%\n")
        
        // Optional metrics
        post.metrics.reachCount?.let { 
            details.append("👥 Reach: ${formatNumber(it)}\n")
        }
        post.metrics.impressionsCount?.let { 
            details.append("👁️ Impressions: ${formatNumber(it)}\n")
        }
        post.metrics.savesCount?.let { 
            details.append("🔖 Saves: ${formatNumber(it)}\n")
        }
        post.metrics.videoViewsCount?.let { 
            details.append("🎬 Video Views: ${formatNumber(it)}\n")
        }
        post.metrics.totalInteractions?.let { 
            details.append("📊 Total Interactions: ${formatNumber(it)}\n")
        }
        
        // Hashtags
        if (post.hashtags.isNotEmpty()) {
            details.append("\n🏷️ Hashtags:\n${post.hashtags.joinToString(" ")}")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Post Details")
            .setMessage(details.toString())
            .setPositiveButton("Open Link") { _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(post.permalink))
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun formatDate(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp.take(10) // Fallback to just the date part
        }
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> "${String.format("%.1f", number / 1_000_000.0)}M"
            number >= 1_000 -> "${String.format("%.1f", number / 1_000.0)}K"
            else -> java.text.NumberFormat.getNumberInstance().format(number)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}