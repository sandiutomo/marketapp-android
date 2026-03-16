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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.braze.Braze
import com.braze.events.BannersUpdatedEvent
import com.braze.events.IEventSubscriber
import com.marketapp.R
import com.marketapp.config.ExperimentManager
import com.marketapp.config.FeatureFlag
import com.marketapp.config.PostHogFlag
import com.marketapp.data.model.UiState
import com.marketapp.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    @Inject lateinit var experiments: ExperimentManager
    private var bannerSubscriber: IEventSubscriber<BannersUpdatedEvent>? = null
    private lateinit var adapter: ProductAdapter
    private lateinit var promotionAdapter: PromotionAdapter
    private lateinit var promotionsHeaderAdapter: PromotionsHeaderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentHomeBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGreeting()
        setupRecycler()
        setupSwipeRefresh()
        observeProducts()
        observePromotions()
        val braze = Braze.getInstance(requireContext())
        bannerSubscriber = IEventSubscriber { event ->
            val banner = event.getBanner("home_banner")
            val hasContent = banner != null && !banner.html.isNullOrEmpty() && !banner.isControl && !banner.isExpired()
            activity?.runOnUiThread {
                if (_binding != null) {
                    binding.bannerContainer.visibility = if (hasContent) View.VISIBLE else View.GONE
                }
            }
        }
        braze.subscribeToBannersUpdates(bannerSubscriber!!)
        braze.requestBannersRefresh(listOf("home_banner"))
        binding.btnCloseBanner.setOnClickListener {
            binding.bannerContainer.visibility = View.GONE
        }
    }

    private fun setupGreeting() {
        val variant = experiments.getPostHogFlag(PostHogFlag.HOME_GREETING_VARIANT.key)?.toString()
        binding.tvGreeting.text = when (variant) {
            "welcome-back" -> listOf(
                "Welcome back!",
                "Hi again!"
            ).random()
            "lets-shop" -> listOf(
                "Let's find something great!",
                "Ready to shop?",
                "Discover today's picks!",
                "What are you looking for?"
            ).random()
            else -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                when {
                    hour < 12 -> "Good morning!"
                    hour < 17 -> "Good afternoon!"
                    else      -> "Good evening!"
                }
            }
        }
    }

    private fun setupRecycler() {
        adapter = ProductAdapter(
            isWishlistEnabled = { experiments.isEnabled(FeatureFlag.WISHLIST_ENABLED) }
        ) { product, _ -> navigateToProduct(product.id) }

        // Re-render cards once the async RC fetch completes, so flags changed in the console
        // take effect immediately rather than requiring a second relaunch.
        experiments.doOnRcFetchComplete { adapter.notifyItemRangeChanged(0, adapter.itemCount) }
        promotionAdapter = PromotionAdapter { promo -> viewModel.onPromotionTapped(promo) }
        promotionsHeaderAdapter = PromotionsHeaderAdapter(promotionAdapter)
        binding.recyclerHome.apply {
            layoutManager = LinearLayoutManager(requireContext()).also {
                it.initialPrefetchItemCount = 4
            }
            adapter = ConcatAdapter(promotionsHeaderAdapter, this@HomeFragment.adapter)
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
                    when (state) {
                        is UiState.Loading -> {
                            binding.errorView.visibility = View.GONE
                            // Only show shimmer on initial load; swipe-to-refresh has its own spinner.
                            if (adapter.itemCount == 0) {
                                binding.shimmer.startShimmer()
                                binding.shimmer.visibility = View.VISIBLE
                                binding.swipeRefresh.visibility = View.INVISIBLE
                            }
                        }
                        is UiState.Success -> {
                            binding.swipeRefresh.isRefreshing = false
                            binding.shimmer.stopShimmer()
                            binding.shimmer.visibility = View.GONE
                            binding.swipeRefresh.visibility = View.VISIBLE
                            binding.errorView.visibility = View.GONE
                            adapter.submitList(state.data)
                        }
                        is UiState.Error -> {
                            binding.swipeRefresh.isRefreshing = false
                            binding.shimmer.stopShimmer()
                            binding.shimmer.visibility = View.GONE
                            binding.swipeRefresh.visibility = View.VISIBLE
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
                    promotionsHeaderAdapter.setVisible(promos.isNotEmpty())
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
        bannerSubscriber?.let {
            Braze.getInstance(requireContext()).removeSingleSubscription(it, BannersUpdatedEvent::class.java)
        }
        bannerSubscriber = null
        _binding = null
    }
}