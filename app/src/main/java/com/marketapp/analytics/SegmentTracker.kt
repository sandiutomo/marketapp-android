package com.marketapp.analytics

import android.content.Context
import com.appsflyer.AppsFlyerLib
import com.marketapp.BuildConfig
import com.marketapp.config.ExperimentManager
import com.marketapp.config.FeatureGate
import com.segment.analytics.kotlin.android.Analytics as segmentAnalytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Segment Analytics tracker.
 * Standalone: full Segment Ecommerce Spec event mapping.
 * Event mapping follows the Segment Ecommerce Spec:
 *   https://segment.com/docs/connections/spec/ecommerce/v2/
 */
@Singleton
class SegmentTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val experimentManager: ExperimentManager
) : AnalyticsTracker {

    override val name = "Segment"

    private lateinit var analytics: Analytics
    private var sdkEnabled    = true
    private var consentEnabled = true

    override suspend fun initialize() {
        sdkEnabled = experimentManager.isStatsigGateEnabled(FeatureGate.SDK_SEGMENT.key)
        if (!sdkEnabled) return
        analytics = segmentAnalytics(BuildConfig.SEGMENT_WRITE_KEY, context) {
            trackApplicationLifecycleEvents = true
            flushAt                         = 3
            flushInterval                   = 10
        }
        // Append cross-SDK identifiers so every Segment event can be joined with
        // Amplitude sessions and AppsFlyer attribution in a data warehouse.
        analytics.add(CrossSdkEnrichmentPlugin(context))
    }

    override fun track(event: AnalyticsEvent) {
        if (!sdkEnabled || !consentEnabled) return
        if (event.isBrazeOnly || event.isAmplitudeOnly) return

        when (event) {
            // ── Ecommerce Spec ────────────────────────────────────────────────
            is AnalyticsEvent.ProductViewed -> analytics.track("Product Viewed", buildJsonObject {
                put("product_id", event.productId)
                put("name",       event.productName)
                put("price",      event.price)
                put("category",   event.category)
                put("currency",   "IDR")
            })

            is AnalyticsEvent.AddToCart -> analytics.track("Product Added", buildJsonObject {
                put("product_id", event.productId)
                put("name",       event.productName)
                put("price",      event.price)
                put("quantity",   event.quantity)
                put("category",   event.category)
                put("currency",   "IDR")
            })

            is AnalyticsEvent.RemoveFromCart -> analytics.track("Product Removed", buildJsonObject {
                put("product_id", event.productId)
                put("name",       event.productName)
                put("price",      event.price)
                put("quantity",   event.quantity)
                put("currency",   "IDR")
            })

            is AnalyticsEvent.CartViewed -> analytics.track("Cart Viewed", buildJsonObject {
                put("products", JsonArray(event.items.map { it.toSegmentJson() }))
            })

            is AnalyticsEvent.CheckoutStarted -> analytics.track("Checkout Started", buildJsonObject {
                put("revenue",  event.totalValue)
                put("currency", "IDR")
                put("products", JsonArray(event.items.map { it.toSegmentJson() }))
            })

            is AnalyticsEvent.OrderPlaced -> analytics.track("Order Completed", buildJsonObject {
                put("order_id", event.orderId)
                put("total",    event.totalValue)
                put("revenue",  event.totalValue)
                put("currency", "IDR")
                put("products", JsonArray(event.items.map { it.toSegmentJson() }))
            })

            is AnalyticsEvent.OrderRefunded -> analytics.track("Order Refunded", buildJsonObject {
                put("order_id", event.orderId)
                put("revenue",  event.value)
                put("currency", "IDR")
                put("products", JsonArray(event.items.map { it.toSegmentJson() }))
            })

            is AnalyticsEvent.ProductListViewed -> analytics.track("Product List Viewed", buildJsonObject {
                put("list_id",  event.listId)
                put("category", event.listName)
                put("products", JsonArray(event.items.map { it.toSegmentJson() }))
            })

            is AnalyticsEvent.ProductSelected -> analytics.track("Product Clicked", buildJsonObject {
                put("list_id",    event.listId)
                put("product_id", event.item.itemId)
                put("name",       event.item.itemName)
                put("price",      event.item.price)
                put("position",   event.item.index ?: 0)
            })

            is AnalyticsEvent.SearchPerformed -> analytics.track("Products Searched", buildJsonObject {
                put("query", event.query)
            })

            is AnalyticsEvent.PromotionViewed -> analytics.track("Promotion Viewed", buildJsonObject {
                put("promotion_id", event.promotionId)
                put("name",         event.promotionName)
                event.creativeName?.let { put("creative",  it) }
                event.creativeSlot?.let { put("position",  it) }
            })

            is AnalyticsEvent.PromotionSelected -> analytics.track("Promotion Clicked", buildJsonObject {
                put("promotion_id", event.promotionId)
                put("name",         event.promotionName)
                event.creativeName?.let { put("creative",  it) }
                event.creativeSlot?.let { put("position",  it) }
            })

            is AnalyticsEvent.UserRegistered -> analytics.track("Signed Up", buildJsonObject {
                put("method", event.method)
            })

            is AnalyticsEvent.UserSignedIn -> analytics.track("Signed In", buildJsonObject {
                put("method", event.method)
            })

            is AnalyticsEvent.ScreenView -> analytics.track("Screen View", buildJsonObject {
                put("screen_name",  event.screenName)
                put("screen_class", event.screenClass)
            })

            // Generic fallback
            else -> {
                val eventName = event.name.toSegmentEventName()
                val props     = event.toProperties()
                if (props.isEmpty()) analytics.track(eventName)
                else analytics.track(eventName, props.toJsonObject())
            }
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        if (!sdkEnabled || !consentEnabled) return
        analytics.identify(userId, buildJsonObject {
            properties.email?.let             { put("email",              JsonPrimitive(it)) }
            val displayName = listOfNotNull(properties.firstName, properties.lastName)
                .joinToString(" ").ifEmpty { null } ?: properties.name
            displayName?.let                  { put("name",               JsonPrimitive(it)) }
            properties.firstName?.let         { put("first_name",         JsonPrimitive(it)) }
            properties.lastName?.let          { put("last_name",          JsonPrimitive(it)) }
            properties.country?.let           { put("country",            JsonPrimitive(it)) }
            properties.loginMethod?.let       { put("login_method",       JsonPrimitive(it)) }
            properties.hasPurchased?.let      { put("has_purchased",      JsonPrimitive(it)) }
            properties.orderCount?.let        { put("order_count",        JsonPrimitive(it)) }
            properties.lifetimeValue?.let     { put("lifetime_value",     JsonPrimitive(it)) }
            properties.preferredCategory?.let { put("preferred_category", JsonPrimitive(it)) }
            properties.customAttributes.forEach { (k, v) -> put(k, JsonPrimitive(v.toString())) }
        })
    }

    override fun reset() {
        if (!sdkEnabled) return
        analytics.reset()
    }

    override fun alias(newId: String, oldId: String) {
        if (!sdkEnabled) return
        analytics.alias(newId)
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        consentEnabled = enabled
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
     private fun String.toSegmentEventName() =
        split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    private fun EcommerceItem.toSegmentJson() = buildJsonObject {
        put("product_id", itemId)
        put("name",       itemName)
        put("price",      price)
        put("quantity",   quantity)
        category?.let { put("category", it) }
    }

    // ── Cross-SDK enrichment ────────────────────────────────────────────────────
    /**
     * Enrichment plugin that appends Amplitude session ID and AppsFlyer UID to
     * every event's context before it leaves the device. This lets analysts join
     * Segment events with Amplitude session replays and AppsFlyer attribution in
     * a data warehouse (BigQuery, Snowflake, Redshift) without a custom identity
     * graph.
     */
    private class CrossSdkEnrichmentPlugin(
        private val context: Context
    ) : Plugin {
        override val type = Plugin.Type.Enrichment
        override lateinit var analytics: Analytics

        override fun execute(event: BaseEvent): BaseEvent? {
            val sessionId = AmplitudeTracker.sessionId
            val afUid     = runCatching { AppsFlyerLib.getInstance().getAppsFlyerUID(context) }.getOrNull()
            if (sessionId <= 0 && afUid.isNullOrEmpty()) return event
            event.context = buildJsonObject {
                // Preserve all existing context fields.
                event.context.entries.forEach { (k, v) -> put(k, v) }
                if (sessionId > 0) put("amplitude_session_id", sessionId)
                afUid?.takeIf { it.isNotEmpty() }?.let { put("af_uid", it) }
            }
            return event
        }
    }

    private fun Map<String, Any>.toJsonObject() = buildJsonObject {
        forEach { (key, value) ->
            when (value) {
                is String  -> put(key, JsonPrimitive(value))
                is Int     -> put(key, JsonPrimitive(value))
                is Long    -> put(key, JsonPrimitive(value))
                is Double  -> put(key, JsonPrimitive(value))
                is Float   -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, JsonPrimitive(value))
                is List<*> -> put(key, JsonPrimitive(value.joinToString(",")))
                else       -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }
}