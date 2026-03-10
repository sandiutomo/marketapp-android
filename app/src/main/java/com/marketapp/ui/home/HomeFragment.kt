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
import com.marketapp.data.model.Product
import com.marketapp.data.model.UiState
import com.marketapp.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: ProductAdapter
    private lateinit var promotionAdapter: PromotionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentHomeBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGreeting()
        setupRecycler()
        setupPromotions()
        setupSwipeRefresh()
        observeProducts()
        observePromotions()
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }

    private fun setupRecycler() {
        adapter = ProductAdapter { product, position ->
            navigateToProduct(product.id)
        }
        binding.recyclerHome.apply {
            layoutManager = LinearLayoutManager(requireContext()).also {
                it.initialPrefetchItemCount = 4
            }
            adapter = this@HomeFragment.adapter
            setItemViewCacheSize(20)
        }
    }

    private fun setupPromotions() {
        promotionAdapter = PromotionAdapter { promo ->
            viewModel.onPromotionTapped(promo)
        }
        binding.recyclerPromotions.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = promotionAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadHome()
        }
    }

    private fun observeProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.products.collectLatest { state ->
                    binding.swipeRefresh.isRefreshing = false
                    when (state) {
                        is UiState.Loading -> {
                            binding.progress.visibility = View.VISIBLE
                            binding.errorView.visibility = View.GONE
                        }
                        is UiState.Success -> {
                            binding.progress.visibility = View.GONE
                            binding.errorView.visibility = View.GONE
                            adapter.submitList(state.data)
                        }
                        is UiState.Error -> {
                            binding.progress.visibility = View.GONE
                            binding.errorView.visibility = View.VISIBLE
                            binding.btnRetry.setOnClickListener { viewModel.loadHome() }
                        }
                    }
                }
            }
        }
    }

    private fun observePromotions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.promotions.collectLatest { promos ->
                    promotionAdapter.submitList(promos)
                    binding.recyclerPromotions.visibility =
                        if (promos.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun navigateToProduct(productId: Int) {
        findNavController().navigate(
            R.id.action_home_to_product,
            android.os.Bundle().apply {
                putString("productId", productId.toString())
                putString("source", "home")
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
