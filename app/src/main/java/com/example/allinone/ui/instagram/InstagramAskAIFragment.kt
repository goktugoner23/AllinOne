package com.example.allinone.ui.instagram

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.allinone.R

class InstagramAskAIFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Simple empty fragment that just shows a "Coming Soon" message
        return inflater.inflate(R.layout.fragment_instagram_ask_ai, container, false)
    }
} 