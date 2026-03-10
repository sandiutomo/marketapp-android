package com.marketapp.ui.checkout

import androidx.lifecycle.ViewModel
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
import com.marketapp.data.repository.UserStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val analytics: AnalyticsManager,
    private val authRepository: AuthRepository,
    private val userStats: UserStatsRepository,
    private val perf: PerformanceMonitor
) : ViewModel() {

    val cart: StateFlow<Cart> = cartManager.cart

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
        val idrTotal = c.totalValueIdr

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
        pendingOrderId       = ""
        pendingOrderItems    = emptyList()
        pendingOrderValue    = 0.0
        pendingPaymentMethod = ""
    }

}
