package com.marketapp.ui.cart

import androidx.lifecycle.ViewModel
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.toEcommerceItems
import com.marketapp.data.model.Cart
import com.marketapp.data.repository.CartManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val analytics: AnalyticsManager
) : ViewModel() {

    val cart: StateFlow<Cart> = cartManager.cart

    fun onCartViewed() {
        val c = cart.value
        analytics.track(AnalyticsEvent.CartViewed(
            items      = c.toEcommerceItems(),
            totalValue = c.totalValueIdr
        ))
    }

    fun increaseQty(productId: Int) {
        val item = cartManager.cart.value.items.find { it.product.id == productId } ?: return
        val newQty = item.quantity + 1
        cartManager.updateQuantity(productId, newQty)
    }

    fun decreaseQty(productId: Int) {
        val item = cartManager.cart.value.items.find { it.product.id == productId } ?: return
        if (item.quantity <= 1) {
            removeItem(productId); return
        }
        val newQty = item.quantity - 1
        cartManager.updateQuantity(productId, newQty)
    }

    fun removeItem(productId: Int) {
        val item = cartManager.cart.value.items.find { it.product.id == productId } ?: return
        cartManager.removeItem(productId)
        analytics.track(AnalyticsEvent.RemoveFromCart(
            productId = item.product.id.toString(),
            productName = item.product.title,
            price = item.product.priceIdr,
            quantity = item.quantity
        ))
    }
}
