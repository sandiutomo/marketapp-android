package com.marketapp.ui.home

import android.os.Bundle
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
import com.marketapp.config.ExperimentManager
import com.marketapp.config.FeatureFlag
import com.marketapp.data.model.UiState
import com.marketapp.databinding.FragmentCategoryBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoryFragment : Fragment() {

    private var _binding: FragmentCategoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoryViewModel by viewModels()
    private lateinit var adapter: ProductAdapter

    @Inject lateinit var experiments: ExperimentManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentCategoryBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.title = viewModel.category.replaceFirstChar { it.uppercaseChar() }
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ProductAdapter(
            isWishlistEnabled = { experiments.isEnabled(FeatureFlag.WISHLIST_ENABLED) }
        ) { product, position ->
            viewModel.onProductSelected(product, position)
            findNavController().navigate(
                R.id.action_category_to_product,
                Bundle().apply {
                    putString("productId", product.id.toString())
                    putString("source", "category")
                }
            )
        }
        experiments.doOnRcFetchComplete { adapter.notifyItemRangeChanged(0, adapter.itemCount) }

        binding.rvProducts.apply {
            layoutManager = LinearLayoutManager(requireContext()).also {
                it.initialPrefetchItemCount = 4
            }
            adapter = this@CategoryFragment.adapter
            setHasFixedSize(false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.products.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.errorView.visibility = View.GONE
                            binding.rvProducts.visibility = View.GONE
                            binding.shimmer.startShimmer()
                            binding.shimmer.visibility = View.VISIBLE
                        }
                        is UiState.Success -> {
                            binding.shimmer.stopShimmer()
                            binding.shimmer.visibility = View.GONE
                            binding.errorView.visibility = View.GONE
                            binding.rvProducts.visibility = View.VISIBLE
                            adapter.submitList(state.data)
                        }
                        is UiState.Error -> {
                            binding.shimmer.stopShimmer()
                            binding.shimmer.visibility = View.GONE
                            binding.rvProducts.visibility = View.GONE
                            binding.errorView.visibility = View.VISIBLE
                            binding.btnRetry.setOnClickListener { viewModel.reload() }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
