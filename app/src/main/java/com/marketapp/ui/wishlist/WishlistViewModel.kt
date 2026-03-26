package com.marketapp.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.data.model.Product
import com.marketapp.data.model.UiState
import com.marketapp.data.repository.CartManager
import com.marketapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val repository: ProductRepository,
    private val analytics: AnalyticsManager
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

    init {
        viewModelScope.launch {
            cartManager.wishlist.collectLatest { ids ->
                if (ids.isEmpty()) {
                    _products.value = UiState.Success(emptyList())
                    return@collectLatest
                }
                _products.value = UiState.Loading
                val loaded = ids.mapNotNull { repository.getProduct(it).getOrNull() }
                _products.value = UiState.Success(loaded)
            }
        }
    }
}
