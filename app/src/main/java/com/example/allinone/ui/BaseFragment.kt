package com.example.allinone.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.allinone.MainActivity
import com.example.allinone.R

/**
 * Base Fragment class with drawer functionality
 */
abstract class BaseFragment : Fragment() {
    
    /**
     * Open the navigation drawer
     */
    protected fun openDrawer() {
        val activity = activity as? MainActivity ?: return
        activity.openDrawer()
    }
} 