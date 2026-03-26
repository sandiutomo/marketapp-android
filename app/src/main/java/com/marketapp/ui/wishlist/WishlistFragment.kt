package com.marketapp.ui.wishlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.marketapp.data.model.UiState
import com.marketapp.databinding.FragmentWishlistBinding
import com.marketapp.ui.home.ProductAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WishlistFragment : Fragment() {

    private var _binding: FragmentWishlistBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WishlistViewModel by viewModels()
    private lateinit var adapter: ProductAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentWishlistBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ProductAdapter(
            onWishlistToggle = { viewModel.toggleWishlist(it) }
        ) { product, _ ->
            findNavController().navigate(
                WishlistFragmentDirections.actionWishlistToProduct(product.id.toString())
            )
        }
        binding.recyclerWishlist.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWishlist.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.wishlist.collect { adapter.updateWishlistIds(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.products.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.recyclerWishlist.isVisible = false
                            binding.emptyView.isVisible = false
                        }
                        is UiState.Success -> {
                            adapter.submitList(state.data)
                            binding.recyclerWishlist.isVisible = state.data.isNotEmpty()
                            binding.emptyView.isVisible = state.data.isEmpty()
                        }
                        is UiState.Error -> {
                            binding.recyclerWishlist.isVisible = false
                            binding.emptyView.isVisible = true
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
