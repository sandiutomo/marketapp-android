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
import com.marketapp.data.repository.CartManager
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
    private val remoteConfig: RemoteConfigManager,
    private val cartManager: CartManager
) : ViewModel() {

    private val _products = MutableStateFlow<UiState<List<Product>>>(UiState.Loading)
    val products: StateFlow<UiState<List<Product>>> = _products

    val wishlist: StateFlow<Set<Int>> = cartManager.wishlist

    fun toggleWishlist(product: Product) {
        val added = cartManager.toggleWishlist(product.id)
        analytics.track(
            AnalyticsEvent.ProductWishlisted(
                productId   = product.id.toString(),
                productName = product.title,
                price       = product.priceIdr,
                added       = added
            )
        )
    }

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
        // Promotions killswitch — hide entire carousel when flag is off.
        _promotions.value = if (remoteConfig.isEnabled(FeatureFlag.SHOW_PROMOTIONS_BANNER)) homePromotions else emptyList()
        viewModelScope.launch {
            _products.value = UiState.Loading
            perf.trace("home_screen_load") { trace ->
                repository.getProducts()
                    .onSuccess { list ->
                        val shuffled = list.shuffled()
                        trace.putAttribute("item_count", shuffled.size.toString())
                        _products.value = UiState.Success(shuffled)
                        _categories.value = list.map { it.category }.distinct().sorted()
                        applyAiSort(shuffled)
                        if (remoteConfig.isEnabled(FeatureFlag.VIEW_ITEM_LIST_ENABLED)) {
                            analytics.track(
                                AnalyticsEvent.ProductListViewed(
                                    listId   = "home_feed",
                                    listName = "Home Feed",
                                    items    = shuffled.mapIndexed { i, p ->
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
                        }
                        if (remoteConfig.isEnabled(FeatureFlag.SHOW_PROMOTIONS_BANNER)) {
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
                    }
                    .onFailure { e ->
                        val msg = e.message ?: "Unknown error"
                        _products.value = UiState.Error(msg)
                        analytics.trackError("Home", "LOAD_FAILED", msg)
                    }
            }
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

    fun onProductSelected(product: Product, position: Int) {
        analytics.track(
            AnalyticsEvent.ProductSelected(
                listId   = "home_feed",
                listName = "Home Feed",
                item     = EcommerceItem(
                    itemId   = product.id.toString(),
                    itemName = product.title,
                    price    = product.priceIdr,
                    quantity = 1,
                    category = product.category,
                    index    = position
                )
            )
        )
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
