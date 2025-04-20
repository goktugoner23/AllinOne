package com.example.allinone.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.allinone.ui.FuturesFragment
import com.example.allinone.ui.InvestmentsTabFragment

class InvestmentPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    companion object {
        const val INVESTMENTS_TAB = 0
        const val FUTURES_TAB = 1
        const val TAB_COUNT = 2
    }

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            INVESTMENTS_TAB -> InvestmentsTabFragment()
            FUTURES_TAB -> FuturesFragment()
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }
}