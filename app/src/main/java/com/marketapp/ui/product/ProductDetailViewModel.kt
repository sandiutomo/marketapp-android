package com.marketapp.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.PerformanceMonitor
import com.marketapp.data.model.Product
import com.marketapp.data.model.UiState
import com.marketapp.data.repository.CartManager
import com.marketapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val cartManager: CartManager,
    private val analytics: AnalyticsManager,
    private val perf: PerformanceMonitor
) : ViewModel() {

    private val _product = MutableStateFlow<UiState<Product>>(UiState.Loading)
    val product: StateFlow<UiState<Product>> = _product

    private val _isWishlisted = MutableStateFlow(false)
    val isWishlisted: StateFlow<Boolean> = _isWishlisted

    private val _addedToCart = MutableStateFlow(false)
    val addedToCart: StateFlow<Boolean> = _addedToCart

    fun loadProduct(id: String, source: String) {
        // Guard against re-firing on configuration change (ViewModel survives, Fragment recreates).
        if (_product.value is UiState.Success) return
        viewModelScope.launch {
            perf.trace("product_detail_load") { trace ->
                trace.putAttribute("source", source)
                repository.getProduct(id.toIntOrNull() ?: 0)
                    .onSuccess {
                        trace.putAttribute("product_id",       it.id.toString())
                        trace.putAttribute("product_category", it.category)
                        _product.value = UiState.Success(it)
                        // Fired here so screen_view (dispatched by OnDestinationChangedListener
                        // before onViewCreated) is always sent first.
                        analytics.track(
                            AnalyticsEvent.ProductViewed(
                                productId   = it.id.toString(),
                                productName = it.title,
                                price       = it.priceIdr,
                                category    = it.category,
                                source      = source
                            )
                        )
                    }
                    .onFailure {
                        _product.value = UiState.Error(it.message ?: "Failed to load product")
                        analytics.trackError("ProductDetail", "LOAD_FAILED", it.message ?: "")
                    }
            }
        }
    }

    fun addToCart() {
        val product = (_product.value as? UiState.Success)?.data ?: return
        cartManager.addItem(product)
        _addedToCart.value = true
        analytics.track(
            AnalyticsEvent.AddToCart(
                productId = product.id.toString(),
                productName = product.title,
                price = product.priceIdr,
                quantity = 1,
                category = product.category
            )
        )
    }

    fun toggleWishlist() {
        val product = (_product.value as? UiState.Success)?.data ?: return
        val newState = !_isWishlisted.value
        _isWishlisted.value = newState
        analytics.track(
            AnalyticsEvent.ProductWishlisted(
                productId = product.id.toString(),
                productName = product.title,
                price = product.priceIdr,
                added = newState
            )
        )
    }

    fun onShare() {
        val product = (_product.value as? UiState.Success)?.data ?: return
        analytics.track(AnalyticsEvent.ProductShared(product.id.toString(), "native_share"))
    }
}
