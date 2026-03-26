package com.marketapp.data.repository

import android.content.Context
import com.marketapp.data.model.Cart
import com.marketapp.data.model.CartItem
import com.marketapp.data.model.Product
import com.marketapp.data.model.Rating
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME   = "cart_prefs"
private const val KEY_CART     = "cart_json"
private const val KEY_WISHLIST = "wishlist_ids"

/**
 * Single source of truth for cart state.
 * Exposed as [StateFlow] so any Fragment/ViewModel can observe reactively.
 * Cart is persisted to SharedPreferences so it survives process death.
 */
@Singleton
class CartManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _cart = MutableStateFlow(loadCart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    private val _wishlist = MutableStateFlow(loadWishlist())
    val wishlist: StateFlow<Set<Int>> = _wishlist.asStateFlow()

    fun toggleWishlist(productId: Int): Boolean {
        val current = _wishlist.value.toMutableSet()
        val added = if (productId in current) { current.remove(productId); false }
                    else { current.add(productId); true }
        _wishlist.value = current
        saveWishlist(current)
        return added
    }

    fun isWishlisted(productId: Int) = _wishlist.value.contains(productId)

    fun addItem(product: Product, quantity: Int = 1) {
        val current = _cart.value.items.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = current[index].quantity + quantity)
        } else {
            current.add(CartItem(product, quantity))
        }
        _cart.value = Cart(current)
        saveCart()
    }

    fun removeItem(productId: Int) {
        _cart.value = Cart(_cart.value.items.filter { it.product.id != productId })
        saveCart()
    }

    fun updateQuantity(productId: Int, quantity: Int) {
        if (quantity <= 0) { removeItem(productId); return }
        val current = _cart.value.items.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) current[index] = current[index].copy(quantity = quantity)
        _cart.value = Cart(current)
        saveCart()
    }

    fun clearCart() {
        _cart.value = Cart()
        saveCart()
    }

    fun itemCount(productId: Int): Int =
        _cart.value.items.find { it.product.id == productId }?.quantity ?: 0

    fun contains(productId: Int): Boolean =
        _cart.value.items.any { it.product.id == productId }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun loadWishlist(): Set<Int> =
        prefs.getStringSet(KEY_WISHLIST, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    private fun saveWishlist(ids: Set<Int>) =
        prefs.edit().putStringSet(KEY_WISHLIST, ids.map { it.toString() }.toSet()).apply()

    private fun saveCart() {
        val array = JSONArray()
        for (item in _cart.value.items) {
            val p = item.product
            array.put(JSONObject().apply {
                put("quantity", item.quantity)
                put("product", JSONObject().apply {
                    put("id",          p.id)
                    put("title",       p.title)
                    put("price",       p.price)
                    put("description", p.description)
                    put("category",    p.category)
                    put("image",       p.image)
                    put("rate",        p.rating.rate)
                    put("ratingCount", p.rating.count)
                })
            })
        }
        prefs.edit().putString(KEY_CART, array.toString()).apply()
    }

    private fun loadCart(): Cart {
        val json = prefs.getString(KEY_CART, null) ?: return Cart()
        return try {
            val array = JSONArray(json)
            val items = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val p   = obj.getJSONObject("product")
                CartItem(
                    product  = Product(
                        id          = p.getInt("id"),
                        title       = p.getString("title"),
                        price       = p.getDouble("price"),
                        description = p.getString("description"),
                        category    = p.getString("category"),
                        image       = p.getString("image"),
                        rating      = Rating(
                            rate  = p.getDouble("rate"),
                            count = p.getInt("ratingCount")
                        )
                    ),
                    quantity = obj.getInt("quantity")
                )
            }
            Cart(items)
        } catch (_: Exception) {
            Cart()
        }
    }
}
