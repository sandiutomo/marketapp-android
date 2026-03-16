package com.marketapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketapp.ai.AiRepository
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.EcommerceItem
import com.marketapp.analytics.PerformanceMonitor
import com.marketapp.config.FeatureFlag
import com.marketapp.config.RemoteConfigManager
import com.marketapp.data.model.Product
import com.marketapp.data.model.PromotionItem
import com.marketapp.data.model.UiState
import com.marketapp.data.repository.AuthRepository
import com.marketapp.data.repository.OrderRepository
import com.marketapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val analytics: AnalyticsManager,
    private val perf: PerformanceMonitor,
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    private val aiRepository: AiRepository,
    private val remoteConfig: RemoteConfigManager
) : ViewModel() {

    private val _products = MutableStateFlow<UiState<List<Product>>>(UiState.Loading)
    val products: StateFlow<UiState<List<Product>>> = _products

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    private val _promotions = MutableStateFlow<List<PromotionItem>>(emptyList())
    val promotions: StateFlow<List<PromotionItem>> = _promotions

    private val homePromotions = listOf(
        PromotionItem("promo_flash", "Flash Sale",    "banner_sale", "home_banner_0"),
        PromotionItem("promo_new",   "New Arrivals",  "banner_new",  "home_banner_1")
    )

    init {
        loadHome()
    }

    fun loadHome() {
        // Promotions are static — show them immediately without waiting for the network.
        _promotions.value = homePromotions
        viewModelScope.launch {
            _products.value = UiState.Loading
            perf.trace("home_screen_load") { trace ->
                repository.getProducts()
                    .onSuccess { list ->
                        trace.putAttribute("item_count", list.size.toString())
                        _products.value = UiState.Success(list)
                        applyAiSort(list)
                        analytics.track(
                            AnalyticsEvent.ProductListViewed(
                                listId   = "home_feed",
                                listName = "Home Feed",
                                items    = list.mapIndexed { i, p ->
                                    EcommerceItem(
                                        itemId   = p.id.toString(),
                                        itemName = p.title,
                                        price    = p.priceIdr,
                                        quantity = 1,
                                        category = p.category,
                                        index    = i
                                    )
                                }
                            )
                        )
                        homePromotions.forEach { promo ->
                            analytics.track(
                                AnalyticsEvent.PromotionViewed(
                                    promotionId   = promo.id,
                                    promotionName = promo.name,
                                    creativeName  = promo.creativeName,
                                    creativeSlot  = promo.creativeSlot,
                                    locationId    = promo.locationId
                                )
                            )
                        }
                    }
                    .onFailure { e ->
                        val msg = e.message ?: "Unknown error"
                        _products.value = UiState.Error(msg)
                        analytics.trackError("Home", "LOAD_FAILED", msg)
                    }
            }
            repository.getCategories()
                .onSuccess { _categories.value = it }
        }
    }

    private suspend fun applyAiSort(base: List<Product>) {
        val userId = authRepository.currentUserId ?: return
        if (!remoteConfig.isEnabled(FeatureFlag.AI_PRODUCT_SORTING_ENABLED)) return
        try {
            val history = orderRepository.getRecentOrders(userId)
            if (history.isEmpty()) return
            val rankedIds = aiRepository.rankProducts(base, history)
            if (rankedIds.isEmpty()) return
            val reordered = rankedIds.mapNotNull { id -> base.find { it.id == id } } +
                base.filter { p -> p.id !in rankedIds }
            _products.value = UiState.Success(reordered)
        } catch (_: Exception) {
            // AI sort is best-effort — leave products as returned by the network
        }
    }

    fun onCategorySelected(category: String) {
        analytics.track(AnalyticsEvent.CategorySelected(category, category))
    }

    fun onPromotionTapped(promo: PromotionItem) {
        analytics.track(
            AnalyticsEvent.PromotionSelected(
                promotionId   = promo.id,
                promotionName = promo.name,
                creativeName  = promo.creativeName,
                creativeSlot  = promo.creativeSlot,
                locationId    = promo.locationId
            )
        )
    }
}
