package com.example.allinone.ui.wt

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class WTPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WTRegisterFragment()
            1 -> WTHistoryFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
} 