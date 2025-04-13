package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.allinone.BuildConfig
import com.example.allinone.R
import com.example.allinone.databinding.FragmentInstagramBusinessBinding
import android.util.Log

class InstagramBusinessFragment : BaseFragment() {
    
    private var _binding: FragmentInstagramBusinessBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstagramBusinessBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Read the token that was baked into BuildConfig at build time from .env file
        val instagramToken = BuildConfig.INSTAGRAM_TOKEN
        
        // Display the token status in the UI
        if (instagramToken.isNotEmpty() && instagramToken != "NOT_SET" && instagramToken != "your_instagram_token_here") {
            binding.textInstagramBusiness.text = "Instagram Business\nToken from .env file: ${maskToken(instagramToken)}"
            Log.d("InstagramBusiness", "Instagram token successfully read from .env and baked into app")
        } else {
            binding.textInstagramBusiness.text = "Instagram Business\nToken not found in .env file"
            Log.e("InstagramBusiness", "Instagram token not configured in .env file")
            Toast.makeText(
                context,
                "Please configure your INSTAGRAM_TOKEN in the .env file and rebuild",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Helper function to mask token for display
    private fun maskToken(token: String): String {
        return if (token.length > 8) {
            "${token.substring(0, 4)}...${token.substring(token.length - 4)}"
        } else {
            "***"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 