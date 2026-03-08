package com.marketapp.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.NumberFormat
import java.util.Locale

// 1 USD ≈ 15 000 IDR — adjust when needed
private const val IDR_RATE = 15_000.0
private val idrFormat = NumberFormat.getIntegerInstance(Locale("in", "ID"))

private fun Double.toIdr(): String = "Rp ${idrFormat.format((this * IDR_RATE).toLong())}"

// ── API Response Models ───────────────────────────────────────────────────────

@Parcelize
data class Product(
    val id: Int,
    val title: String,
    val price: Double,
    val description: String,
    val category: String,
    val image: String,
    val rating: Rating = Rating()
) : Parcelable {
    val priceIdr: Double get() = price * IDR_RATE
    val formattedPrice: String get() = price.toIdr()
    val shortTitle: String get() = if (title.length > 50) title.take(47) + "…" else title
}

@Parcelize
data class Rating(
    val rate: Double = 0.0,
    val count: Int = 0
) : Parcelable

// ── Cart ─────────────────────────────────────────────────────────────────────

data class CartItem(
    val product: Product,
    var quantity: Int = 1
) {
    val subtotal: Double get() = product.price * quantity
    val formattedSubtotal: String get() = subtotal.toIdr()
}

data class Cart(
    val items: List<CartItem> = emptyList()
) {
    val totalItems: Int get() = items.sumOf { it.quantity }
    val totalValue: Double get() = items.sumOf { it.subtotal }
    val totalValueIdr: Double get() = items.sumOf { it.product.priceIdr * it.quantity }
    val formattedTotal: String get() = totalValue.toIdr()
    val isEmpty: Boolean get() = items.isEmpty()
}

// ── Order ─────────────────────────────────────────────────────────────────────

data class Order(
    val id: String,
    val items: List<CartItem>,
    val totalValue: Double,
    val paymentMethod: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: OrderStatus = OrderStatus.CONFIRMED
)

enum class OrderStatus { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

// ── Promotion ─────────────────────────────────────────────────────────────────

data class PromotionItem(
    val id: String,
    val name: String,
    val creativeName: String,
    val creativeSlot: String,       // e.g. "home_banner_0"
    val locationId: String = "home_feed"
)

// ── UI State wrapper ──────────────────────────────────────────────────────────

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val code: String = "UNKNOWN") : UiState<Nothing>()
}
