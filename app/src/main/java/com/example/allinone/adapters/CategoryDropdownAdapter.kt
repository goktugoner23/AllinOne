package com.example.allinone.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.allinone.R
import com.example.allinone.config.TransactionCategories

/**
 * Custom adapter for displaying transaction categories with icons in a dropdown
 */
class CategoryDropdownAdapter(
    context: Context,
    private val categories: Array<String>
) : ArrayAdapter<String>(context, R.layout.item_category_dropdown, categories) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_category_dropdown, parent, false)
        
        val category = getItem(position) ?: return view
        
        // Set the category name
        view.findViewById<TextView>(R.id.categoryName).text = category
        
        // Set the category icon if available
        val iconView = view.findViewById<ImageView>(R.id.categoryIcon)
        val iconResId = TransactionCategories.CATEGORY_ICONS[category]
        if (iconResId != null) {
            iconView.setImageResource(iconResId)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }
        
        return view
    }
    
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }
}
