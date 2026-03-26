package com.marketapp.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.perf.metrics.Trace
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.EcommerceItem
import com.marketapp.analytics.toEcommerceItems
import com.marketapp.analytics.PerformanceMonitor
import com.marketapp.analytics.UserProperties
import com.marketapp.data.model.Cart
import com.marketapp.data.model.Order
import com.marketapp.data.repository.AuthRepository
import com.marketapp.data.repository.CartManager
import com.marketapp.config.FeatureFlag
import com.marketapp.config.RemoteConfigManager
import com.marketapp.data.repository.OrderRepository
import com.marketapp.data.repository.UserStatsRepository
import android.util.Log
import com.marketapp.ai.AiRepository
import java.text.NumberFormat
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val analytics: AnalyticsManager,
    private val authRepository: AuthRepository,
    private val userStats: UserStatsRepository,
    private val perf: PerformanceMonitor,
    private val orderRepository: OrderRepository,
    private val remoteConfig: RemoteConfigManager,
    private val aiRepository: AiRepository
) : ViewModel() {

    val cart: StateFlow<Cart> = cartManager.cart

    private val _aiOrderMessage = MutableStateFlow("")
    val aiOrderMessage: StateFlow<String> = _aiOrderMessage.asStateFlow()

    // Spans the full checkout flow: started in onCheckoutStarted(), stopped in placeOrder().
    private var checkoutTrace: Trace? = null

    fun onCheckoutStarted() {
        val c = cart.value
        checkoutTrace = perf.startTrace("checkout_complete").also { t ->
            t.putAttribute("item_count", c.items.size.toString())
        }
        analytics.track(AnalyticsEvent.CheckoutStarted(
            items      = c.toEcommerceItems(),
            totalValue = c.totalValueIdr
        ))
    }

    // GA4: add_shipping_info — fired when the user confirms their shipping address.
    fun onShippingConfirmed(name: String, address: String, city: String, zip: String) {
        val c = cart.value
        analytics.track(
            AnalyticsEvent.ShippingInfoAdded(
                items        = c.toEcommerceItems(),
                totalValue   = c.totalValueIdr,
                shippingTier = "standard"
            )
        )
    }

    // Captured in placeOrder() before the cart is cleared; used by onOrderConfirmed().
    private var pendingOrderId: String = ""
    private var pendingOrderItems: List<com.marketapp.analytics.EcommerceItem> = emptyList()
    private var pendingOrderValue: Double = 0.0
    private var pendingPaymentMethod: String = ""

    // Retained after onOrderConfirmed() so the refund button can reference the placed order.
    private var confirmedOrderId: String = ""
    private var confirmedOrderValue: Double = 0.0

    // GA4: add_payment_info — fired when the user taps "Place Order" on the payment screen.
    fun placeOrder(paymentMethod: String): Order {
        val c = cart.value
        val orderId = "MKT-${UUID.randomUUID().toString().take(8).uppercase()}"
        val order = Order(
            id = orderId,
            items = c.items,
            totalValue = c.totalValue,
            paymentMethod = paymentMethod
        )

        val ecommerceItems = c.toEcommerceItems()
        val idrTotal = c.totalValueIdr + shippingFeeIdr()

        analytics.track(
            AnalyticsEvent.PaymentMethodSelected(
                method     = paymentMethod,
                items      = ecommerceItems,
                totalValue = idrTotal
            )
        )

        checkoutTrace?.putAttribute("payment_method", paymentMethod)
        checkoutTrace?.stop()
        checkoutTrace = null

        // Capture order data before clearing the cart — needed for the purchase event.
        pendingOrderId      = orderId
        pendingOrderItems   = ecommerceItems
        pendingOrderValue   = idrTotal
        pendingPaymentMethod = paymentMethod

        cartManager.clearCart()
        userStats.recordOrder(idrTotal)

        val userId = authRepository.currentUserId ?: return order
        if (remoteConfig.isEnabled(FeatureFlag.FIRESTORE_WRITE_ENABLED)) {
            Log.d(TAG, "firestore_write_enabled=true — writing order ${order.id} to Firestore")
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                orderRepository.saveOrder(userId, order)
                Log.d(TAG, "Firestore write dispatched for order ${order.id}")
            }
        } else {
            Log.d(TAG, "firestore_write_enabled=false — Firestore write skipped for order ${order.id}")
        }
        if (remoteConfig.isEnabled(FeatureFlag.AI_ORDER_MESSAGE_ENABLED)) {
            viewModelScope.launch {
                _aiOrderMessage.value = aiRepository.generateOrderMessage(order, userStats.orderCount)
            }
        }
        val preferredCategory = c.items.maxByOrNull { it.quantity }?.product?.category
        analytics.identify(
            userId = userId,
            properties = UserProperties(
                userId            = userId,
                hasPurchased      = true,
                orderCount        = userStats.orderCount,
                lifetimeValue     = userStats.lifetimeValue,
                preferredCategory = preferredCategory
            )
        )

        return order
    }

    // GA4: purchase — fired when the order confirmation screen is shown.
    fun onOrderConfirmed() {
        if (pendingOrderId.isEmpty()) return
        analytics.track(
            AnalyticsEvent.OrderPlaced(
                orderId       = pendingOrderId,
                totalValue    = pendingOrderValue,
                items         = pendingOrderItems,
                paymentMethod = pendingPaymentMethod,
                couponUsed    = null
            )
        )
        confirmedOrderId    = pendingOrderId
        confirmedOrderValue = pendingOrderValue
        pendingOrderId       = ""
        pendingOrderItems    = emptyList()
        pendingOrderValue    = 0.0
        pendingPaymentMethod = ""
        _aiOrderMessage.value = ""
    }

    fun onRefundRequested() {
        if (confirmedOrderId.isEmpty()) return
        analytics.track(
            AnalyticsEvent.OrderRefunded(
                orderId = confirmedOrderId,
                value   = confirmedOrderValue
            )
        )
    }

    // ── Shipping fee ─────────────────────────────────────────────────────────────

    private val idrFormat = NumberFormat.getIntegerInstance(Locale("in", "ID"))

    fun shippingFeeIdr(): Double = 0.0

    fun formattedShippingFee(): String {
        val fee = shippingFeeIdr()
        return if (fee > 0.0) "Rp ${idrFormat.format(fee.toLong())}" else "Free"
    }

    fun formattedGrandTotal(): String {
        val total = cart.value.totalValueIdr + shippingFeeIdr()
        return "Rp ${idrFormat.format(total.toLong())}"
    }

    companion object {
        private const val TAG = "CheckoutViewModel"
        const val SHIPPING_FEE_IDR = 150_000.0
    }
}
