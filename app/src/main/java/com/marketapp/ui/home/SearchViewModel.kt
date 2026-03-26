package com.marketapp.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketapp.BuildConfig
import com.marketapp.ai.AiRepository
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.EcommerceItem
import com.marketapp.analytics.PerformanceMonitor
import com.marketapp.config.FeatureFlag
import com.marketapp.config.RemoteConfigManager
import com.marketapp.data.model.Product
import com.marketapp.data.model.UiState
import com.marketapp.data.repository.CartManager
import com.marketapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val analytics: AnalyticsManager,
    private val perf: PerformanceMonitor,
    private val aiRepository: AiRepository,
    private val remoteConfig: RemoteConfigManager,
    private val cartManager: CartManager
) : ViewModel() {

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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<UiState<List<Product>>?>(null)
    val results: StateFlow<UiState<List<Product>>?> = _results

    init {
        // Debounce flow — keyword search only, no AI calls while typing
        viewModelScope.launch {
            _query
                .debounce(800)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    if (remoteConfig.isEnabled(FeatureFlag.SEARCH_AUTOCOMPLETE)) searchKeyword(query)
                }
        }
    }

    fun onQueryChanged(q: String) {
        _query.value = q
        if (q.length < 2) _results.value = null
    }

    // Called from SearchFragment when the user presses the IME Search key.
    // Runs AI search (with keyword fallback) — one explicit call per submit.
    fun onSearchSubmitted(query: String) {
        if (query.length < 2) return
        viewModelScope.launch {
            _results.value = UiState.Loading
            perf.trace("search_load") { trace ->
                trace.putAttribute("query_length", query.length.toString())
                val aiEnabled = remoteConfig.isEnabled(FeatureFlag.AI_SEARCH_ENABLED) && query.length >= 4
                if (BuildConfig.DEBUG) Log.d("FirebaseAI", "[search] ai_search_enabled=$aiEnabled query=\"$query\"")
                val products = if (aiEnabled) {
                    val allProducts = repository.getProducts().getOrNull() ?: emptyList()
                    val aiResult = aiRepository.searchProducts(query, allProducts)
                    if (BuildConfig.DEBUG) Log.d("FirebaseAI", "[search] ai returned ${aiResult?.size ?: "null (fallback)"}")
                    aiResult ?: repository.searchProducts(query).getOrElse { emptyList() }
                } else {
                    repository.searchProducts(query).getOrElse { emptyList() }
                }
                trace.putAttribute("result_count", products.size.toString())
                _results.value = if (products.isEmpty()) UiState.Error("No results found")
                                 else UiState.Success(products)
                analytics.track(AnalyticsEvent.SearchPerformed(query, products.size))
            }
        }
    }

    private fun searchKeyword(query: String) {
        viewModelScope.launch {
            _results.value = UiState.Loading
            perf.trace("search_load") { trace ->
                trace.putAttribute("query_length", query.length.toString())
                val products = repository.searchProducts(query).getOrElse { emptyList() }
                trace.putAttribute("result_count", products.size.toString())
                _results.value = if (products.isEmpty()) UiState.Error("No results found")
                                 else UiState.Success(products)
            }
        }
    }

    fun clearResults() {
        _query.value = ""
        _results.value = null
    }

    fun onResultTapped(product: Product, position: Int) {
        analytics.track(AnalyticsEvent.SearchResultTapped(product.id.toString(), position))
        analytics.track(
            AnalyticsEvent.ProductSelected(
                listId   = "search_results",
                listName = "Search Results",
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
}
