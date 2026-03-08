package com.marketapp.data.repository

import com.marketapp.data.model.Cart
import com.marketapp.data.model.CartItem
import com.marketapp.data.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for cart state.
 * Exposed as [StateFlow] so any Fragment/ViewModel can observe reactively.
 */
@Singleton
class CartManager @Inject constructor() {

    private val _cart = MutableStateFlow(Cart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    fun addItem(product: Product, quantity: Int = 1) {
        val current = _cart.value.items.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = current[index].quantity + quantity)
        } else {
            current.add(CartItem(product, quantity))
        }
        _cart.value = Cart(current)
    }

    fun removeItem(productId: Int) {
        _cart.value = Cart(_cart.value.items.filter { it.product.id != productId })
    }

    fun updateQuantity(productId: Int, quantity: Int) {
        if (quantity <= 0) { removeItem(productId); return }
        val current = _cart.value.items.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) current[index] = current[index].copy(quantity = quantity)
        _cart.value = Cart(current)
    }

    fun clearCart() { _cart.value = Cart() }

    fun itemCount(productId: Int): Int =
        _cart.value.items.find { it.product.id == productId }?.quantity ?: 0

    fun contains(productId: Int): Boolean =
        _cart.value.items.any { it.product.id == productId }
}
