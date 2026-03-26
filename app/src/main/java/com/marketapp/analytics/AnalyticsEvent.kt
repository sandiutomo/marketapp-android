package com.marketapp.analytics

import com.marketapp.data.model.Cart

data class EcommerceItem(
    val itemId: String,
    val itemName: String,
    val price: Double,
    val quantity: Int,
    val category: String?     = null,
    val brand: String?        = null,
    val variant: String?      = null,
    val discount: Double?     = null,
    val index: Int?           = null,     // position in list — required for view_item_list / select_item
    // Item-scoped context — carried per-item so GA4 can attribute each line item
    // to the list it came from and the seller/store it belongs to.
    val affiliation: String?  = null,     // seller or store name (marketplace attribution)
    val coupon: String?       = null,     // item-level discount coupon code
    val itemListId: String?   = null,     // list the item was presented in (e.g. "home_recommended")
    val itemListName: String? = null      // human-readable list name  (e.g. "Recommended for You")
)

/**
 * Sealed class representing every trackable event in the app.
 *
 * Event naming: GA4 standard event names are used directly wherever available
 * (e.g. "view_item", "add_to_cart", "purchase") so PostHog, Clarity, and
 * future trackers all receive the same name without extra mapping.
 *
 * toProperties() uses GA4 parameter names (item_id, currency, value, etc.)
 * so property schemas stay consistent across all platforms.
 *
 * Platform-specific behavior (GA4 Bundle structure, Mixpanel trackCharge,
 * AppsFlyer AFInAppEventType, Clarity tags) lives in each [AnalyticsTracker].
 */
sealed class AnalyticsEvent(val name: String) {
    // App Lifecycle ───────────────────────────────────────────────────────────────────────────────
    object AppOpen      : AnalyticsEvent("app_open")
    object AppBackground: AnalyticsEvent("app_background")

    // Screen Tracking ─────────────────────────────────────────────────────────────────────────────
    // GA4: screen_view
    data class ScreenView(val screenName: String, val screenClass: String) : AnalyticsEvent("screen_view")

    // ── Onboarding ────────────────────────────────────────────────────────────
    data class OnboardingCompleted(val method: String) : AnalyticsEvent("onboarding_completed")

    // ── Promotions / Banners ──────────────────────────────────────────────────
    // GA4: view_promotion — banner / hero / splash visible on screen
    data class PromotionViewed(
        val promotionId: String,
        val promotionName: String,
        val creativeName: String? = null,
        val creativeSlot: String? = null,
        val locationId: String?   = null
    ) : AnalyticsEvent("view_promotion")

    // GA4: select_promotion — banner tapped by user
    data class PromotionSelected(
        val promotionId: String,
        val promotionName: String,
        val creativeName: String? = null,
        val creativeSlot: String? = null,
        val locationId: String?   = null
    ) : AnalyticsEvent("select_promotion")

    // GA4: select_content — category tapped
    data class CategorySelected(val categoryId: String, val categoryName: String) : AnalyticsEvent("select_content")

    // ── Search ────────────────────────────────────────────────────────────────
    // GA4: search
    data class SearchPerformed(val query: String, val resultCount: Int) : AnalyticsEvent("search")
    data class SearchResultTapped(val productId: String, val position: Int) : AnalyticsEvent("search_result_tapped")

    // ── Product List ──────────────────────────────────────────────────────────
    // GA4: view_item_list — products visible in a feed / category / search results
    data class ProductListViewed(
        val listId: String,
        val listName: String,
        val items: List<EcommerceItem>
    ) : AnalyticsEvent("view_item_list")

    // GA4: select_item — product tapped from a list
    data class ProductSelected(
        val listId: String,
        val listName: String,
        val item: EcommerceItem
    ) : AnalyticsEvent("select_item")

    // ── Product Detail ────────────────────────────────────────────────────────
    // GA4: view_item
    data class ProductViewed(
        val productId: String,
        val productName: String,
        val price: Double,
        val category: String,
        val source: String          // "home", "search", "category", "recommendation"
    ) : AnalyticsEvent("view_item")

    data class ProductImageSwiped(val productId: String, val imageIndex: Int) : AnalyticsEvent("product_image_swiped")

    // GA4: share
    data class ProductShared(val productId: String, val method: String) : AnalyticsEvent("share")

    // GA4: add_to_wishlist (added=true) / custom remove_from_wishlist (added=false)
    data class ProductWishlisted(
        val productId: String,
        val productName: String,
        val price: Double,
        val added: Boolean
    ) : AnalyticsEvent(if (added) "add_to_wishlist" else "remove_from_wishlist")

    // ── Cart ──────────────────────────────────────────────────────────────────
    // GA4: add_to_cart
    data class AddToCart(
        val productId: String,
        val productName: String,
        val price: Double,
        val quantity: Int,
        val category: String,
        val cartItemCount: Int
    ) : AnalyticsEvent("add_to_cart")

    // GA4: remove_from_cart
    data class RemoveFromCart(
        val productId: String,
        val productName: String,
        val price: Double,
        val quantity: Int,
        val cartItemCount: Int
    ) : AnalyticsEvent("remove_from_cart")

    // GA4: view_cart
    data class CartViewed(
        val items: List<EcommerceItem>,
        val totalValue: Double
    ) : AnalyticsEvent("view_cart")


    // ── Checkout ──────────────────────────────────────────────────────────────
    // Complete funnel:
    //   begin_checkout → checkout_address_selected → add_shipping_info → add_payment_info → purchase

    // GA4: begin_checkout — Step 1
    data class CheckoutStarted(
        val items: List<EcommerceItem>,
        val totalValue: Double
    ) : AnalyticsEvent("begin_checkout")

    // Custom — Step 2: user selects or adds a delivery address.
    // Fires before ShippingInfoAdded (which captures the shipping method/tier).
    // isNewAddress = true when the user types in a new address rather than picking a saved one.
    data class CheckoutAddressSelected(
        val addressId: String,
        val city: String,
        val country: String,
        val isNewAddress: Boolean = false
    ) : AnalyticsEvent("checkout_address_selected")

    // GA4: add_shipping_info — Step 3: user confirms shipping method / tier
    data class ShippingInfoAdded(
        val items: List<EcommerceItem>,
        val totalValue: Double,
        val shippingTier: String,
        val couponUsed: String? = null
    ) : AnalyticsEvent("add_shipping_info")

    // GA4: add_payment_info — Step 4: user confirms payment method at checkout
    data class PaymentMethodSelected(
        val method: String,
        val items: List<EcommerceItem> = emptyList(),
        val totalValue: Double         = 0.0
    ) : AnalyticsEvent("add_payment_info")

    // GA4: purchase
    data class OrderPlaced(
        val orderId: String,
        val totalValue: Double,
        val items: List<EcommerceItem>,
        val paymentMethod: String,
        val couponUsed: String? = null
    ) : AnalyticsEvent("purchase")

    data class OrderFailed(val reason: String) : AnalyticsEvent("order_failed")

    // GA4: refund
    data class OrderRefunded(
        val orderId: String,
        val value: Double,
        val items: List<EcommerceItem> = emptyList()
    ) : AnalyticsEvent("refund")

    // ── Profile ───────────────────────────────────────────────────────────────
    // GA4: login / sign_up
    data class UserSignedIn(val method: String) : AnalyticsEvent("login")
    data class UserSignedOut(val sessionDuration: Long) : AnalyticsEvent("user_signed_out")
    data class UserRegistered(val method: String) : AnalyticsEvent("sign_up")

    object AddressAdded      : AnalyticsEvent("address_added")
    object PaymentMethodAdded: AnalyticsEvent("payment_method_added")  // profile — saving a card
    object Subscribe         : AnalyticsEvent("subscribe")             // user opts in to analytics tracking

    // ── Campaign / Deep Link ──────────────────────────────────────────────────
    data class CampaignOpened(
        val source: String,
        val medium: String?   = null,
        val campaign: String? = null,
        val term: String?     = null,
        val content: String?  = null,
        val deepLink: String? = null
    ) : AnalyticsEvent("campaign_open")

    // ── Notifications ─────────────────────────────────────────────────────────
    data class PushReceived(val campaignId: String?, val type: String) : AnalyticsEvent("notification_receive")
    data class PushTapped(val campaignId: String?, val deepLink: String?) : AnalyticsEvent("notification_open")
    data class PushPermissionGranted(val granted: Boolean) : AnalyticsEvent("push_permission_result")

    // ── Error / Debug ─────────────────────────────────────────────────────────
    data class ErrorOccurred(val screen: String, val code: String, val message: String) : AnalyticsEvent("error_occurred")

    // ── QA / Testing (DEBUG builds only) ──────────────────────────────────────
    // Fire these from the profile debug panel to trigger action-based campaigns
    // in Braze (Action-Based Delivery) and OneSignal (In-App triggers).
    object TriggerPushTest        : AnalyticsEvent("trigger_for_pushnotif")
    object TriggerBannerTest      : AnalyticsEvent("trigger_for_banner")
    object TriggerInAppTest       : AnalyticsEvent("trigger_for_inapp")
    object TriggerContentCardTest : AnalyticsEvent("trigger_for_content_card")
    object TriggerAmplitudeGuide  : AnalyticsEvent("trigger_for_amplitude_guide")

    /** Only meaningful to Braze (action-based campaigns). All other trackers skip these. */
    val isBrazeOnly: Boolean get() = this is TriggerPushTest || this is TriggerBannerTest ||
        this is TriggerInAppTest || this is TriggerContentCardTest

    /** Only meaningful to Amplitude Guides/Surveys. All other trackers skip this. */
    val isAmplitudeOnly: Boolean get() = this is TriggerAmplitudeGuide

    /**
     * Flat property map for PostHog, Clarity, and generic trackers.
     * GA4 parameter names are used wherever a standard exists.
     * List values (items arrays) are included — each tracker filters them as needed.
     */
    fun toProperties(): Map<String, Any> = when (this) {
        is AppOpen, is AppBackground,
        is AddressAdded, is PaymentMethodAdded,
        is Subscribe -> emptyMap()

        is ScreenView            -> mapOf("screen_name" to screenName, "screen_class" to screenClass)
        is OnboardingCompleted   -> mapOf("method" to method)

        // GA4: view_promotion / select_promotion
        is PromotionViewed       -> buildMap {
            put("promotion_id",   promotionId)
            put("promotion_name", promotionName)
            creativeName?.let { put("creative_name", it) }
            creativeSlot?.let { put("creative_slot", it) }
            locationId?.let   { put("location_id",   it) }
        }
        is PromotionSelected     -> buildMap {
            put("promotion_id",   promotionId)
            put("promotion_name", promotionName)
            creativeName?.let { put("creative_name", it) }
            creativeSlot?.let { put("creative_slot", it) }
            locationId?.let   { put("location_id",   it) }
        }

        // GA4: select_content
        is CategorySelected      -> mapOf(
            "item_id" to categoryId, "content_type" to "category", "name" to categoryName
        )

        // GA4: search
        is SearchPerformed       -> mapOf("search_term" to query, "result_count" to resultCount)
        is SearchResultTapped    -> mapOf("item_id" to productId, "position" to position)

        // GA4: view_item_list
        is ProductListViewed     -> mapOf(
            "item_list_id"   to listId,
            "item_list_name" to listName,
            "items"          to items.map { it.toItemMap() }
        )

        // GA4: select_item
        is ProductSelected       -> mapOf(
            "item_list_id"   to listId,
            "item_list_name" to listName,
            "item_id"        to item.itemId,
            "item_name"      to item.itemName,
            "price"          to item.price,
            "index"          to (item.index ?: 0)
        )

        // GA4: view_item
        is ProductViewed         -> mapOf(
            "item_id" to productId, "item_name" to productName,
            "price" to price, "item_category" to category,
            "currency" to "IDR", "value" to price, "source" to source
        )
        is ProductImageSwiped    -> mapOf("item_id" to productId, "image_index" to imageIndex)

        // GA4: share
        is ProductShared         -> mapOf(
            "item_id" to productId, "method" to method, "content_type" to "product"
        )

        // GA4: add_to_wishlist / remove_from_wishlist
        is ProductWishlisted     -> mapOf(
            "item_id" to productId, "item_name" to productName,
            "price" to price, "value" to price, "currency" to "IDR"
        )

        // GA4: add_to_cart
        is AddToCart             -> mapOf(
            "item_id" to productId, "item_name" to productName,
            "price" to price, "quantity" to quantity,
            "item_category" to category,
            "value" to price * quantity, "currency" to "IDR"
        )

        // GA4: remove_from_cart
        is RemoveFromCart        -> mapOf(
            "item_id" to productId, "item_name" to productName,
            "price" to price, "quantity" to quantity,
            "value" to price * quantity, "currency" to "IDR"
        )

        // GA4: view_cart
        is CartViewed            -> mapOf(
            "value" to totalValue, "currency" to "IDR",
            "item_count" to items.sumOf { it.quantity },
            "items" to items.map { it.toItemMap() }
        )

        // GA4: begin_checkout
        is CheckoutStarted          -> mapOf(
            "value" to totalValue, "currency" to "IDR",
            "item_count" to items.sumOf { it.quantity },
            "items" to items.map { it.toItemMap() }
        )

        // Custom: checkout_address_selected
        is CheckoutAddressSelected  -> mapOf(
            "address_id"     to addressId,
            "city"           to city,
            "country"        to country,
            "is_new_address" to isNewAddress
        )

        // GA4: add_shipping_info
        is ShippingInfoAdded     -> buildMap {
            put("value", totalValue); put("currency", "IDR")
            put("shipping_tier", shippingTier)
            put("item_count", items.sumOf { it.quantity })
            put("items", items.map { it.toItemMap() })
            couponUsed?.let { put("coupon", it) }
        }

        // GA4: add_payment_info
        is PaymentMethodSelected -> buildMap {
            put("payment_type", method); put("currency", "IDR")
            if (totalValue > 0) put("value", totalValue)
            if (items.isNotEmpty()) {
                put("item_count", items.sumOf { it.quantity })
                put("items", items.map { it.toItemMap() })
            }
        }

        // GA4: purchase
        is OrderPlaced           -> mapOf(
            "transaction_id" to orderId, "value" to totalValue, "currency" to "IDR",
            "coupon" to (couponUsed ?: ""), "payment_method" to paymentMethod,
            "item_count" to items.sumOf { it.quantity },
            "items" to items.map { it.toItemMap() }
        )
        is OrderFailed           -> mapOf("reason" to reason)

        // GA4: refund
        is OrderRefunded         -> buildMap {
            put("transaction_id", orderId); put("value", value); put("currency", "IDR")
            if (items.isNotEmpty()) put("items", items.map { it.toItemMap() })
        }

        // GA4: login / sign_up
        is UserSignedIn          -> mapOf("method" to method)
        is UserSignedOut         -> mapOf("session_duration_ms" to sessionDuration)
        is UserRegistered        -> mapOf("method" to method)

        is CampaignOpened        -> buildMap {
            put("source", source)
            medium?.let   { put("medium",    it) }
            campaign?.let { put("campaign",  it) }
            term?.let     { put("term",      it) }
            content?.let  { put("content",   it) }
            deepLink?.let { put("deep_link", it) }
        }
        is PushReceived          -> mapOf("campaign_id" to (campaignId ?: ""), "type" to type)
        is PushTapped            -> mapOf("campaign_id" to (campaignId ?: ""), "deep_link" to (deepLink ?: ""))
        is PushPermissionGranted -> mapOf("granted" to granted)
        is ErrorOccurred         -> mapOf("screen" to screen, "code" to code, "message" to message)

        is TriggerPushTest,
        is TriggerBannerTest,
        is TriggerInAppTest,
        is TriggerContentCardTest,
        is TriggerAmplitudeGuide  -> emptyMap()
    }
}

// ── Shared cart conversion ────────────────────────────────────────────────────

fun Cart.toEcommerceItems(): List<EcommerceItem> = items.map {
    EcommerceItem(
        itemId   = it.product.id.toString(),
        itemName = it.product.title,
        price    = it.product.priceIdr,
        quantity = it.quantity,
        category = it.product.category
    )
}

// ── Private helper ─────────────────────────────────────────────────────────────
private fun EcommerceItem.toItemMap(): Map<String, Any> = buildMap {
    put("item_id",   itemId)
    put("item_name", itemName)
    put("price",     price)
    put("quantity",  quantity)
    category?.let { put("item_category", it) }
    brand?.let    { put("item_brand",    it) }
    variant?.let  { put("item_variant",  it) }
    discount?.let { put("discount",      it) }
    index?.let    { put("index",         it) }
}