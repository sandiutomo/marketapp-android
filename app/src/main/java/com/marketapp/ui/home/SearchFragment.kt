package com.marketapp.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.marketapp.R
import com.marketapp.data.model.UiState
import com.marketapp.databinding.FragmentSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: ProductAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentSearchBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupSearchBar()
        observeResults()
    }

    private fun setupAdapter() {
        adapter = ProductAdapter { product, position ->
            viewModel.onResultTapped(product, position)
            findNavController().navigate(
                R.id.action_search_to_product,
                android.os.Bundle().apply {
                    putInt("productId", product.id)
                    putString("source", "search")
                }
            )
        }
        binding.recyclerResults.apply {
            layoutManager = LinearLayoutManager(requireContext()).also {
                it.initialPrefetchItemCount = 4
            }
            adapter = this@SearchFragment.adapter
            setHasFixedSize(false)
            setItemViewCacheSize(20)
        }
    }

    private fun setupSearchBar() {
        // SearchBar doesn't expose a text change listener directly — use EditText inside
        binding.searchBar.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = viewModel.onQueryChanged(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    private fun observeResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.results.collectLatest { state ->
                when (state) {
                    null -> {
                        binding.recyclerResults.visibility = View.GONE
                        binding.progress.visibility        = View.GONE
                        binding.emptyView.visibility       = View.VISIBLE
                        binding.tvEmptyLabel.text          = "Search for products"
                    }
                    is UiState.Loading -> {
                        binding.recyclerResults.visibility = View.GONE
                        binding.progress.visibility        = View.VISIBLE
                        binding.emptyView.visibility       = View.GONE
                    }
                    is UiState.Success -> {
                        binding.progress.visibility = View.GONE
                        if (state.data.isEmpty()) {
                            binding.recyclerResults.visibility = View.GONE
                            binding.emptyView.visibility       = View.VISIBLE
                            binding.tvEmptyLabel.text          = "No results found"
                        } else {
                            binding.recyclerResults.visibility = View.VISIBLE
                            binding.emptyView.visibility       = View.GONE
                            adapter.submitList(state.data)
                        }
                    }
                    is UiState.Error -> {
                        binding.progress.visibility  = View.GONE
                        binding.emptyView.visibility = View.VISIBLE
                        binding.tvEmptyLabel.text    = state.message
                    }
                }
            }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
