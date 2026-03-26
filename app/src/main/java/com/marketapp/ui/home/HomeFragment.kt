package com.marketapp.ui.home

import android.os.Bundle
import android.util.Log
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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.braze.Braze
import com.braze.events.BannersUpdatedEvent
import com.braze.events.IEventSubscriber
import com.google.android.material.chip.Chip
import com.marketapp.R
import com.marketapp.config.ALL_BANNER_PLACEMENTS
import com.marketapp.config.BannerDismissManager
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
        observeCategories()
        observeWishlist()
        val braze = Braze.getInstance(requireContext())
        bannerSubscriber = IEventSubscriber { event ->
            val banner = event.getBanner("home_banner")
            val html = banner?.html
            val hasContent = banner != null && !html.isNullOrEmpty() && !banner.isControl && !banner.isExpired()
            val show = hasContent && BannerDismissManager.shouldShow("home_banner", html!!)
            activity?.runOnUiThread {
                if (_binding != null) {
                    binding.bannerContainer.visibility = if (show) View.VISIBLE else View.GONE
                }
            }
        }
        braze.subscribeToBannersUpdates(bannerSubscriber!!)
        braze.requestBannersRefresh(ALL_BANNER_PLACEMENTS)
        binding.btnCloseBanner.setOnClickListener {
            BannerDismissManager.dismiss("home_banner")
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
            isWishlistEnabled  = { experiments.isEnabled(FeatureFlag.WISHLIST_ENABLED) },
            onWishlistToggle   = { viewModel.toggleWishlist(it) }
        ) { product, position ->
            viewModel.onProductSelected(product, position)
            navigateToProduct(product.id)
        }

        // Re-render cards once the async RC fetch completes, so flags changed in the console
        // take effect immediately rather than requiring a second relaunch.
        experiments.doOnRcFetchComplete { adapter.notifyItemRangeChanged(0, adapter.itemCount) }
        promotionAdapter = PromotionAdapter { promo -> viewModel.onPromotionTapped(promo) }
        promotionsHeaderAdapter = PromotionsHeaderAdapter(promotionAdapter)

        // Amplitude Experiment: home_layout — "grid" (2-col) vs control (list).
        // Dashboard: Amplitude → Experiment → create flag key "home_layout",
        //   variants: control (default) and "grid".
        val layoutVariant = experiments.getAmplitudeVariant("home_layout")
        Log.d("HomeLayout", "setupRecycler: variant=\"$layoutVariant\" (cached)")
        val layoutManager: RecyclerView.LayoutManager = if (layoutVariant == "treatment") {
            adapter.compact = true
            GridLayoutManager(requireContext(), 2).also { glm ->
                glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (position < promotionsHeaderAdapter.itemCount) 2 else 1
                }
            }
        } else {
            LinearLayoutManager(requireContext()).also { it.initialPrefetchItemCount = 4 }
        }
        if (layoutVariant != null) experiments.trackMixpanelExposure("home_layout", layoutVariant)

        binding.recyclerHome.apply {
            this.layoutManager = layoutManager
            adapter = ConcatAdapter(promotionsHeaderAdapter, this@HomeFragment.adapter)
            setHasFixedSize(true)
        }

        // On first launch there is no cached variant yet — re-apply layout once the async
        // Amplitude Experiment fetch completes and the variant becomes available.
        experiments.doOnAmplitudeExperimentFetchComplete {
            if (_binding == null) return@doOnAmplitudeExperimentFetchComplete
            val fetchedVariant = experiments.getAmplitudeVariant("home_layout")
            Log.d("HomeLayout", "fetchComplete: variant=\"$fetchedVariant\"")
            if (fetchedVariant == "treatment" && binding.recyclerHome.layoutManager !is GridLayoutManager) {
                adapter.compact = true
                binding.recyclerHome.layoutManager = GridLayoutManager(requireContext(), 2).also { glm ->
                    glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int) =
                            if (position < promotionsHeaderAdapter.itemCount) 2 else 1
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
                experiments.trackMixpanelExposure("home_layout", fetchedVariant)
            }
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

    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categories.collectLatest { categories ->
                    binding.scrollCategories.isVisible = categories.isNotEmpty()
                    binding.chipGroupCategories.removeAllViews()
                    categories.forEach { slug ->
                        val chip = Chip(requireContext()).apply {
                            text = slug.replaceFirstChar { it.uppercaseChar() }
                            isCheckable = false
                            setEnsureMinTouchTargetSize(false)
                        }
                        chip.setOnClickListener {
                            viewModel.onCategorySelected(slug)
                            findNavController().navigate(
                                R.id.action_home_to_category,
                                Bundle().apply { putString("category", slug) }
                            )
                        }
                        binding.chipGroupCategories.addView(chip)
                    }
                }
            }
        }
    }

    private fun observeWishlist() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.wishlist.collect { adapter.updateWishlistIds(it) }
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