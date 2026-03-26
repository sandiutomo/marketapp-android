package com.marketapp.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.EcommerceItem
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
class CategoryViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val analytics: AnalyticsManager,
    private val cartManager: CartManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val category: String = savedStateHandle.get<String>("category") ?: ""

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

    init { loadCategory() }

    fun reload() { loadCategory() }

    private fun loadCategory() {
        viewModelScope.launch {
            _products.value = UiState.Loading
            repository.getProductsByCategory(category)
                .onSuccess { list ->
                    _products.value = UiState.Success(list)
                    analytics.track(
                        AnalyticsEvent.ProductListViewed(
                            listId   = "category_$category",
                            listName = category.replaceFirstChar { it.uppercaseChar() },
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
                }
                .onFailure { _products.value = UiState.Error(it.message ?: "Unknown error") }
        }
    }

    fun onProductSelected(product: Product, position: Int) {
        analytics.track(
            AnalyticsEvent.ProductSelected(
                listId   = "category_$category",
                listName = category.replaceFirstChar { it.uppercaseChar() },
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
