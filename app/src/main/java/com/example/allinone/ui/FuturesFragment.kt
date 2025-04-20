package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.allinone.databinding.FragmentFuturesBinding
import com.google.android.material.tabs.TabLayoutMediator

class FuturesFragment : Fragment() {
    private var _binding: FragmentFuturesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFuturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the ViewPager with the tabs adapter
        setupViewPager()
    }

    private fun setupViewPager() {
        // Create the adapter that will return fragments for each tab
        val futuresPagerAdapter = FuturesPagerAdapter(this)
        binding.futuresViewPager.adapter = futuresPagerAdapter

        // Connect the TabLayout with the ViewPager
        TabLayoutMediator(binding.futuresTabLayout, binding.futuresViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "USD-M"
                1 -> "COIN-M"
                else -> null
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * A [FragmentStateAdapter] that returns fragments for each tab
     */
    private inner class FuturesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> UsdmFuturesFragment()
                1 -> CoinMFuturesFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}
