package com.example.allinone.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.allinone.R
import com.example.allinone.data.Investment
import java.text.NumberFormat
import java.util.Locale

/**
 * Custom adapter for displaying investments in a dropdown
 */
class InvestmentDropdownAdapter(
    context: Context,
    private val investments: List<Investment>
) : ArrayAdapter<Investment>(context, R.layout.item_investment_dropdown, investments) {

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_investment_dropdown, parent, false)
        
        val investment = getItem(position) ?: return view
        
        // Set the investment name and details
        view.findViewById<TextView>(R.id.investmentName).text = investment.name
        view.findViewById<TextView>(R.id.investmentType).text = investment.type
        view.findViewById<TextView>(R.id.investmentAmount).text = currencyFormatter.format(investment.amount)
        
        return view
    }
    
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }
} 