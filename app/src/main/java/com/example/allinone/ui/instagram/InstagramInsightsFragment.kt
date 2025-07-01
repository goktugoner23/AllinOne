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
import com.example.allinone.R
import com.example.allinone.databinding.FragmentInstagramInsightsBinding
import com.example.allinone.feature.instagram.data.model.*
import com.example.allinone.feature.instagram.ui.viewmodel.InstagramViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InstagramInsightsFragment : Fragment() {

    private var _binding: FragmentInstagramInsightsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstagramViewModel by viewModels()

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
        
        observeViewModel()
        
        // Load analytics data
        viewModel.loadAnalytics()
    }

    private fun observeViewModel() {
        viewModel.analytics.observe(viewLifecycleOwner) { result ->
            when (result) {
                is InstagramResult.Loading -> {
                    showLoading(true)
                }

                is InstagramResult.Success -> {
                    showLoading(false)
                    displayAnalytics(result.data)
                }

                is InstagramResult.Error -> {
                    showLoading(false)
                    showError(result.message)
                }
            }
        }
    }

    private fun displayAnalytics(analytics: InstagramAnalytics) {
        binding.apply {
            // Hide message and progress
            textInsightsMessage.isVisible = false
            progressBar.isVisible = false
            
            // Overview Cards
            textTotalPosts.text = formatNumber(analytics.summary.totalPosts)
            textTotalEngagement.text = formatNumber(analytics.summary.totalEngagement)
            textAvgEngagementRate.text = "${String.format("%.1f", analytics.summary.avgEngagementRate)}%"
            
            // Total Reach from detailed metrics
            analytics.summary.detailedMetrics?.totals?.let { totals ->
                textTotalReach.text = formatNumber(totals.totalReach)
            }
            
            // Performance Indicators
            analytics.summary.detailedMetrics?.let { metrics ->
                metrics.trends?.let { trends ->
                    textEngagementTrend.text = formatTrend(trends.recentEngagementTrend)
                    textEngagementTrend.setTextColor(getTrendColor(trends.recentEngagementTrend))
                    
                    textReachTrend.text = formatTrend(trends.recentReachTrend)
                    textReachTrend.setTextColor(getTrendColor(trends.recentReachTrend))
                }
                
                metrics.performance?.let { performance ->
                    textConsistencyScore.text = "${String.format("%.1f", performance.consistencyScore)}%"
                    textGrowthPotential.text = "${String.format("%.1f", performance.growthPotential)}%"
                }
            }
            
            // Top Performing Post
            analytics.summary.topPerformingPost?.let { topPost ->
                cardTopPost.isVisible = true
                textTopPostCaption.text = if (topPost.caption.length > 100) {
                    "${topPost.caption.take(100)}..."
                } else topPost.caption
                textTopPostEngagement.text = "📈 ${String.format("%.1f", topPost.metrics.engagementRate)}%"
                textTopPostInteractions.text = "📊 ${formatNumber(topPost.metrics.totalInteractions)}"
                
                // Click listener for top post
                cardTopPost.setOnClickListener {
                    showTopPostDetails(topPost)
                }
            } ?: run {
                cardTopPost.isVisible = false
            }
            
            // Content Analysis
            analytics.summary.detailedMetrics?.contentAnalysis?.let { contentAnalysis ->
                contentAnalysis.mediaTypeBreakdown?.let { breakdown ->
                    // Videos
                    breakdown.videos?.let { videos ->
                        textVideoCount.text = "${videos.count} (${String.format("%.1f", videos.percentage)}%)"
                        textVideoEngagement.text = "${String.format("%.1f", videos.avgEngagementRate)}%"
                    }
                    
                    // Images
                    breakdown.images?.let { images ->
                        textImageCount.text = "${images.count} (${String.format("%.1f", images.percentage)}%)"
                        textImageEngagement.text = "${String.format("%.1f", images.avgEngagementRate)}%"
                    }
                }
                
                // Posting Frequency
                contentAnalysis.postingFrequency?.let { frequency ->
                    textPostingFrequency.text = buildString {
                        append("• Posts per week: ${String.format("%.1f", frequency.postsPerWeek)}\n")
                        append("• Posts per month: ${String.format("%.1f", frequency.postsPerMonth)}\n")
                        append("• Avg days between posts: ${String.format("%.1f", frequency.avgDaysBetweenPosts)}")
                    }
                }
            }
        }
        
        Log.d(TAG, "Analytics displayed successfully: ${analytics.summary.totalPosts} posts")
    }

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.isVisible = isLoading
            if (isLoading) {
                textInsightsMessage.isVisible = true
                textInsightsMessage.text = "Loading Instagram insights..."
            } else {
                textInsightsMessage.isVisible = false
            }
        }
    }

    private fun showError(message: String) {
        binding.apply {
            progressBar.isVisible = false
            textInsightsMessage.isVisible = true
            textInsightsMessage.text = "Error loading insights: $message"
        }
        Toast.makeText(context, "Failed to load insights: $message", Toast.LENGTH_LONG).show()
        Log.e(TAG, "Analytics load failed: $message")
    }
    
    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> "${String.format("%.1f", number / 1_000_000.0)}M"
            number >= 1_000 -> "${String.format("%.1f", number / 1_000.0)}K"
            else -> java.text.NumberFormat.getNumberInstance().format(number)
        }
    }
    
    private fun formatTrend(trend: Double): String {
        val sign = if (trend >= 0) "+" else ""
        return "$sign${String.format("%.1f", trend)}%"
    }
    
    private fun getTrendColor(trend: Double): Int {
        return androidx.core.content.ContextCompat.getColor(
            requireContext(),
            when {
                trend > 0 -> R.color.excellent_green
                trend < 0 -> R.color.poor_red
                else -> android.R.color.black
            }
        )
    }
    
    private fun showTopPostDetails(topPost: TopPerformingPost) {
        val details = buildString {
            append("🏆 TOP PERFORMING POST\n\n")
            append("📝 Caption: ${topPost.caption}\n\n")
            append("📊 PERFORMANCE:\n")
            append("📈 Engagement Rate: ${String.format("%.1f", topPost.metrics.engagementRate)}%\n")
            append("📊 Total Interactions: ${formatNumber(topPost.metrics.totalInteractions)}\n")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Top Performing Post")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}