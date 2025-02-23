package com.example.allinone.ui.wt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.adapters.WTStudentAdapter
import com.example.allinone.databinding.FragmentWtHistoryBinding
import com.example.allinone.viewmodels.WTRegisterViewModel

class WTHistoryFragment : Fragment() {
    private var _binding: FragmentWtHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private lateinit var adapter: WTStudentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observePaidStudents()
    }

    private fun setupRecyclerView() {
        adapter = WTStudentAdapter(
            onItemClick = { /* No action needed for history items */ },
            onPaymentStatusClick = { /* No action needed for payment status in history */ }
        )
        
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WTHistoryFragment.adapter
        }
    }

    private fun observePaidStudents() {
        viewModel.paidStudents.observe(viewLifecycleOwner) { paidStudents ->
            adapter.submitList(paidStudents)
            binding.emptyStateText.visibility = 
                if (paidStudents.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 