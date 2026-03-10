package com.marketapp.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.appsflyer.AFInAppEventParameterName
import com.appsflyer.AFInAppEventType
import com.appsflyer.AppsFlyerConsent
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.AppsFlyerProperties
import com.appsflyer.deeplink.DeepLinkResult
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.marketapp.BuildConfig
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig
import com.microsoft.clarity.models.LogLevel
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.sessionreplay.MPSessionReplay
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dagger.hilt.android.qualifiers.ApplicationContext

import org.json.JSONObject
import java.math.BigDecimal
import java.util.Currency

import javax.inject.Inject
import javax.inject.Singleton

/** Facebook App Events -- e-commerce analytics for Ads Manager, Meta Pixel, and conversion optimization. */
@Singleton
class FacebookTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {    override val name = "Facebook"
    private lateinit var logger: AppEventsLogger

    override suspend fun initialize() {
        FacebookSdk.fullyInitialize()

        if (BuildConfig.DEBUG) {
            FacebookSdk.setIsDebugEnabled(true)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.DEVELOPER_ERRORS)
            FacebookSdk.setAdvertiserIDCollectionEnabled(true)
            Log.d("Analytics", "[Facebook] SDK ready. App ID: ${FacebookSdk.getApplicationId()}")
        }
        logger = AppEventsLogger.newLogger(context)
        AppEventsLogger.activateApp(context as android.app.Application)
    }

    override fun track(event: AnalyticsEvent) {
        if (!FacebookSdk.isInitialized()) return

        when (event) {
            is AnalyticsEvent.ProductViewed -> {
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_VIEWED_CONTENT,
                    event.price,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID,   event.productId)
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, "product")
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT,      event.productName)
                        putString(AppEventsConstants.EVENT_PARAM_CURRENCY,     CURRENCY_IDR)
                    }
                )
            }
            is AnalyticsEvent.AddToCart -> {
                val lineValue = event.price * event.quantity
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_ADDED_TO_CART,
                    lineValue,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID,   event.productId)
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, "product")
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT,      event.productName)
                        putString(AppEventsConstants.EVENT_PARAM_CURRENCY,     CURRENCY_IDR)
                        putInt(AppEventsConstants.EVENT_PARAM_NUM_ITEMS,       event.quantity)
                    }
                )
            }
            is AnalyticsEvent.ProductWishlisted -> {
                if (event.added) {
                    logger.logEvent(
                        AppEventsConstants.EVENT_NAME_ADDED_TO_WISHLIST,
                        Bundle().apply {
                            putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID,   event.productId)
                            putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, "product")
                        }
                    )
                }
            }
            is AnalyticsEvent.CheckoutStarted -> {
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_INITIATED_CHECKOUT,
                    event.totalValue,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, "product")
                        putString(AppEventsConstants.EVENT_PARAM_CURRENCY,     CURRENCY_IDR)
                        putInt(AppEventsConstants.EVENT_PARAM_NUM_ITEMS,       event.items.sumOf { it.quantity })
                    }
                )
            }
            is AnalyticsEvent.OrderPlaced -> {
                logger.logPurchase(
                    BigDecimal.valueOf(event.totalValue),
                    Currency.getInstance(CURRENCY_IDR),
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, "product")
                        putInt(AppEventsConstants.EVENT_PARAM_NUM_ITEMS,       event.items.sumOf { it.quantity })
                        event.couponUsed?.let { putString("coupon", it) }
                    }
                )
            }
            is AnalyticsEvent.SearchPerformed -> {
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_SEARCHED,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_SEARCH_STRING, event.query)
                        putString(AppEventsConstants.EVENT_PARAM_SUCCESS, if (event.resultCount > 0) "1" else "0")
                    }
                )
            }
            is AnalyticsEvent.UserRegistered -> {
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_REGISTRATION_METHOD, event.method)
                        putString(AppEventsConstants.EVENT_PARAM_SUCCESS, "1")
                    }
                )
            }
            is AnalyticsEvent.OnboardingCompleted -> {
                // Fires when the user accepts consent and completes the onboarding flow —
                // the final step of the full registration journey for Facebook attribution.
                // UserRegistered fires at account creation; this fires at consent acceptance,
                // capturing users who already had accounts but are new to this install.
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_REGISTRATION_METHOD, event.method)
                        putString(AppEventsConstants.EVENT_PARAM_SUCCESS, "1")
                    }
                )
            }
            is AnalyticsEvent.UserSignedIn -> {
                logger.logEvent(
                    "fb_mobile_login",  // AppEventsConstants.EVENT_NAME_LOGGED_IN is absent in facebook-core; raw value is equivalent
                    Bundle().apply {
                        // EVENT_PARAM_REGISTRATION_METHOD is the standard FB param for
                        // both sign_up and login (e.g. "email", "google", "apple").
                        putString(AppEventsConstants.EVENT_PARAM_REGISTRATION_METHOD, event.method)
                    }
                )
            }
            is AnalyticsEvent.UserSignedOut -> {
                // Facebook has no standard logout constant; "fb_mobile_logged_out" is
                // the accepted custom name used across Meta's own sample apps.
                logger.logEvent(
                    "fb_mobile_logged_out",
                    Bundle().apply {
                        putLong("session_duration_ms", event.sessionDuration)
                    }
                )
            }
            is AnalyticsEvent.PaymentMethodSelected -> {
                // GA4: add_payment_info — user selects payment method at checkout.
                val params = Bundle().apply {
                    putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, event.method)
                    putString(AppEventsConstants.EVENT_PARAM_CURRENCY,     CURRENCY_IDR)
                    putString(AppEventsConstants.EVENT_PARAM_SUCCESS,      "1")
                    if (event.items.isNotEmpty())
                        putInt(AppEventsConstants.EVENT_PARAM_NUM_ITEMS, event.items.sumOf { it.quantity })
                }
                if (event.totalValue > 0)
                    logger.logEvent(AppEventsConstants.EVENT_NAME_ADDED_PAYMENT_INFO, event.totalValue, params)
                else
                    logger.logEvent(AppEventsConstants.EVENT_NAME_ADDED_PAYMENT_INFO, params)
            }
            is AnalyticsEvent.PaymentMethodAdded -> {
                // Profile — user saves a payment card. Same FB standard event, no monetary value.
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_ADDED_PAYMENT_INFO,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_SUCCESS, "1")
                    }
                )
            }
            is AnalyticsEvent.Subscribe -> {
                // Fires when the user explicitly opts in to analytics tracking.
                // Maps to Meta's standard Subscribe conversion event.
                logger.logEvent(AppEventsConstants.EVENT_NAME_SUBSCRIBE)
            }
            is AnalyticsEvent.CampaignOpened -> {
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_AD_CLICK,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_AD_TYPE, event.medium)
                        putString("campaign", event.campaign)
                        putString("source",   event.source)
                        event.deepLink?.let { putString("deep_link", it) }
                    }
                )
            }
            else -> Unit
        }
        if (BuildConfig.DEBUG) {
            logger.flush()
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        AppEventsLogger.setUserID(userId)
        AppEventsLogger.setUserData(
            properties.email,
            properties.firstName ?: properties.name?.substringBefore(" "),
            properties.lastName  ?: properties.name?.substringAfter(" ", "")?.ifEmpty { null },
            properties.phone,
            null,
            null,
            null,
            null,
            null,
            properties.country
        )
    }

    override fun reset() {
        AppEventsLogger.setUserID(null)
        AppEventsLogger.clearUserData()
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        FacebookSdk.setAutoLogAppEventsEnabled(enabled)
        FacebookSdk.setAdvertiserIDCollectionEnabled(enabled)
    }

    companion object {
        private const val CURRENCY_IDR = "IDR"
    }
}

/** Firebase Analytics + Crashlytics tracker. */
@Singleton
class FirebaseTracker @Inject constructor() : AnalyticsTracker {
    override val name = "Firebase"
    private val fa by lazy { Firebase.analytics }
    private val fc by lazy { Firebase.crashlytics }

    override fun track(event: AnalyticsEvent) {
        when (event) {
            is AnalyticsEvent.ScreenView            -> fa.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW,       screenViewBundle(event))
            // Product list ───────────────────────────────────────────────────────────
            is AnalyticsEvent.ProductListViewed     -> fa.logEvent(FirebaseAnalytics.Event.VIEW_ITEM_LIST,    viewItemListBundle(event))
            is AnalyticsEvent.ProductSelected       -> fa.logEvent(FirebaseAnalytics.Event.SELECT_ITEM,       selectItemBundle(event))
            // Product detail ───────────────────────────────────────────────────────────
            is AnalyticsEvent.ProductViewed         -> fa.logEvent(FirebaseAnalytics.Event.VIEW_ITEM,         viewItemBundle(event))
            is AnalyticsEvent.ProductWishlisted     -> if (event.added) {
                fa.logEvent(FirebaseAnalytics.Event.ADD_TO_WISHLIST, addToWishlistBundle(event))
            } else {
                fa.logEvent(event.name, removeFromWishlistBundle(event))
            }
            // Cart ───────────────────────────────────────────────────────────
            is AnalyticsEvent.AddToCart             -> fa.logEvent(FirebaseAnalytics.Event.ADD_TO_CART,       addToCartBundle(event))
            is AnalyticsEvent.RemoveFromCart        -> fa.logEvent(FirebaseAnalytics.Event.REMOVE_FROM_CART,  removeFromCartBundle(event))
            is AnalyticsEvent.CartViewed            -> fa.logEvent(FirebaseAnalytics.Event.VIEW_CART,         viewCartBundle(event))
            // Checkout funnel ───────────────────────────────────────────────────────────
            is AnalyticsEvent.CheckoutStarted       -> fa.logEvent(FirebaseAnalytics.Event.BEGIN_CHECKOUT,    beginCheckoutBundle(event))
            is AnalyticsEvent.ShippingInfoAdded     -> fa.logEvent(FirebaseAnalytics.Event.ADD_SHIPPING_INFO, shippingInfoBundle(event))
            is AnalyticsEvent.PaymentMethodSelected -> fa.logEvent(FirebaseAnalytics.Event.ADD_PAYMENT_INFO,  paymentInfoBundle(event))
            is AnalyticsEvent.OrderPlaced           -> fa.logEvent(FirebaseAnalytics.Event.PURCHASE,          purchaseBundle(event))
            is AnalyticsEvent.OrderRefunded         -> fa.logEvent(FirebaseAnalytics.Event.REFUND,            refundBundle(event))
            // Promotions ───────────────────────────────────────────────────────────
            is AnalyticsEvent.PromotionViewed       -> fa.logEvent(FirebaseAnalytics.Event.VIEW_PROMOTION,    promotionBundle(event.promotionId, event.promotionName, event.creativeName, event.creativeSlot, event.locationId))
            is AnalyticsEvent.PromotionSelected     -> fa.logEvent(FirebaseAnalytics.Event.SELECT_PROMOTION,  promotionBundle(event.promotionId, event.promotionName, event.creativeName, event.creativeSlot, event.locationId))
            // Search / discovery ───────────────────────────────────────────────────────────
            is AnalyticsEvent.SearchPerformed       -> fa.logEvent(FirebaseAnalytics.Event.SEARCH,            searchBundle(event))
            is AnalyticsEvent.ProductShared         -> fa.logEvent(FirebaseAnalytics.Event.SHARE,             shareBundle(event))
            is AnalyticsEvent.CategorySelected      -> fa.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT,    selectContentBundle(event))
            // Authentication ───────────────────────────────────────────────────────────
            is AnalyticsEvent.UserSignedIn          -> fa.logEvent(FirebaseAnalytics.Event.LOGIN,             methodBundle(event.method))
            is AnalyticsEvent.UserRegistered        -> fa.logEvent(FirebaseAnalytics.Event.SIGN_UP,           methodBundle(event.method))
            // Campaign ───────────────────────────────────────────────────────────
            // campaign_details     → Firebase reserved event; recorded internally for session attribution (invisible in DebugView by design)
            // deeplink_campaign_open → custom event; visible in DebugView for monitoring
            is AnalyticsEvent.CampaignOpened        -> {
                fa.logEvent("campaign_details",       campaignBundle(event))
                fa.logEvent("deeplink_campaign_open", campaignBundle(event))
            }
            else                                    -> fa.logEvent(event.name, event.toProperties().toBundle())
        }
        // Crashlytics ───────────────────────────────────────────────────────────
        when (event) {
            // Navigation ───────────────────────────────────────────────────────────
            is AnalyticsEvent.ScreenView -> {
                fc.log("SCREEN → ${event.screenName}")
                fc.setCustomKey("current_screen", event.screenName)
            }
            // Product discovery ───────────────────────────────────────────────────────────
            is AnalyticsEvent.SearchPerformed -> {
                fc.log("SEARCH \"${event.query}\" → ${event.resultCount} results")
                fc.setCustomKey("last_search_query", event.query.take(100))
            }
            is AnalyticsEvent.ProductViewed -> {
                fc.log("VIEW_PRODUCT ${event.productId} \"${event.productName}\"")
                fc.setCustomKey("last_product_id",       event.productId)
                fc.setCustomKey("last_product_name",     event.productName.take(64))
                fc.setCustomKey("last_product_category", event.category)
            }
            // Cart ───────────────────────────────────────────────────────────
            is AnalyticsEvent.AddToCart -> {
                fc.log("ADD_TO_CART ${event.productId} qty=${event.quantity}")
                fc.setCustomKey("last_add_to_cart_id", event.productId)
            }
            is AnalyticsEvent.CartViewed -> {
                val totalQty = event.items.sumOf { it.quantity }
                fc.log("VIEW_CART items=$totalQty value=${event.totalValue}")
                fc.setCustomKey("cart_item_count", totalQty)
                fc.setCustomKey("cart_value",      event.totalValue)
            }
            // Checkout funnel ───────────────────────────────────────────────────────────
            is AnalyticsEvent.CheckoutStarted -> {
                fc.log("CHECKOUT_STARTED items=${event.items.size} value=${event.totalValue}")
                fc.setCustomKey("checkout_step",        "started")
                fc.setCustomKey("checkout_cart_value",  event.totalValue)
                fc.setCustomKey("checkout_item_count",  event.items.size)
            }
            is AnalyticsEvent.PaymentMethodSelected -> {
                fc.log("PAYMENT_SELECTED ${event.method}")
                fc.setCustomKey("checkout_step",           "payment_selected")
                fc.setCustomKey("checkout_payment_method", event.method)
            }
            is AnalyticsEvent.OrderPlaced -> {
                fc.log("ORDER_PLACED ${event.orderId} value=${event.totalValue}")
                fc.setCustomKey("checkout_step",    "completed")
                fc.setCustomKey("last_order_id",    event.orderId)
                fc.setCustomKey("last_order_value", event.totalValue)
            }
            is AnalyticsEvent.OrderFailed -> {
                fc.log("ORDER_FAILED reason=${event.reason}")
                fc.setCustomKey("checkout_step", "failed")
                // Record as a typed non-fatal so it gets its own Crashlytics issue group.
                fc.recordException(AppException("ORDER_FAILED", "Checkout", event.reason))
            }
            // Auth ───────────────────────────────────────────────────────────
            is AnalyticsEvent.UserSignedIn  -> fc.log("LOGIN method=${event.method}")
            is AnalyticsEvent.UserRegistered-> fc.log("REGISTER method=${event.method}")
            // Non-fatal errors ──────────────────────────────────────────────────────
            // AppException class name is used by Crashlytics to group issues, so each
            // distinct (screen, code) pair creates a separate, actionable issue entry.
            is AnalyticsEvent.ErrorOccurred -> {
                fc.log("ERROR [${event.screen}/${event.code}] ${event.message}")
                fc.recordException(AppException(event.code, event.screen, event.message))
            }
            else -> Unit
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        fa.setUserId(userId)
        // Email domain ───────────────────────────────────────────────────────────
        properties.email?.let          { fa.setUserProperty("email_domain",        it.substringAfter("@")) }
        // Geo ───────────────────────────────────────────────────────────
        properties.country?.let        { fa.setUserProperty("country",             it) }
        // Auth ───────────────────────────────────────────────────────────
        properties.loginMethod?.let    { fa.setUserProperty("login_method",        it) }
        // Purchase behavior ───────────────────────────────────────────────────────────
        properties.hasPurchased?.let   { fa.setUserProperty("has_purchased",       it.toString()) }
        properties.orderCount?.let     { fa.setUserProperty("order_count_bucket",  it.toOrderCountBucket()) }
        properties.lifetimeValue?.let  { fa.setUserProperty("ltv_bucket",          it.toLtvBucket()) }
        // Personalisation ───────────────────────────────────────────────────────────
        properties.preferredCategory?.let { fa.setUserProperty("preferred_category", it) }
        // Device identifiers ───────────────────────────────────────────────────────────
        fa.setUserProperty("c_user_id",      userId)
        properties.deviceId?.let       { fa.setUserProperty("c_device_id",    it) }
        properties.appSetId?.let       { fa.setUserProperty("app_set_id",     it) }
        properties.advertisingId?.let  { fa.setUserProperty("advertising_id", it) }

        fc.setUserId(userId)
        properties.email?.let         { fc.setCustomKey("user_email",     it) }
        properties.deviceId?.let      { fc.setCustomKey("c_device_id",    it) }
        properties.advertisingId?.let { fc.setCustomKey("advertising_id", it) }
    }
    private fun Int.toOrderCountBucket() = when {
        this == 0  -> "0"
        this == 1  -> "1"
        this <= 5  -> "2-5"
        this <= 20 -> "6-20"
        else       -> "21+"
    }
    private fun Double.toLtvBucket() = when {
        this <= 0            -> "none"
        this < 1_000_000     -> "low"   // < 1 M IDR
        this < 5_000_000     -> "mid"   // 1 M – 5 M IDR
        this < 20_000_000    -> "high"  // 5 M – 20 M IDR
        else                 -> "vip"   // 20 M+ IDR
    }
    override fun reset() {
        fa.setUserId(null)
        fc.setUserId("")
    }
    override fun setAnalyticsConsent(enabled: Boolean) {
        val status = if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED
                     else        FirebaseAnalytics.ConsentStatus.DENIED
        fa.setConsent(mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE  to status,
            FirebaseAnalytics.ConsentType.AD_STORAGE         to status,
            FirebaseAnalytics.ConsentType.AD_USER_DATA       to status,
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to status
        ))
    }
    private class AppException(
        code: String,
        screen: String,
        detail: String
    ) : Exception("[$screen/$code] $detail")

    // GA4 bundle builders ───────────────────────────────────────────────────────────
    private fun viewItemBundle(e: AnalyticsEvent.ProductViewed) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY, "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE, e.price)
        putParcelableArrayList(
            FirebaseAnalytics.Param.ITEMS,
            arrayListOf(itemBundle(e.productId, e.productName, e.price, e.category, 1))
        )
    }
    private fun addToWishlistBundle(e: AnalyticsEvent.ProductWishlisted) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY, "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE, e.price)
        putParcelableArrayList(
            FirebaseAnalytics.Param.ITEMS,
            arrayListOf(itemBundle(e.productId, e.productName, e.price, null, 1))
        )
    }
    private fun removeFromWishlistBundle(e: AnalyticsEvent.ProductWishlisted) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY,  "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE,      e.price)
        putString(FirebaseAnalytics.Param.ITEM_ID,   e.productId)
        putString(FirebaseAnalytics.Param.ITEM_NAME, e.productName)
    }
    private fun addToCartBundle(e: AnalyticsEvent.AddToCart) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY, "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE, e.price * e.quantity)
        putParcelableArrayList(
            FirebaseAnalytics.Param.ITEMS,
            arrayListOf(itemBundle(e.productId, e.productName, e.price, e.category, e.quantity))
        )
    }
    private fun removeFromCartBundle(e: AnalyticsEvent.RemoveFromCart) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY, "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE, e.price * e.quantity)
        putParcelableArrayList(
            FirebaseAnalytics.Param.ITEMS,
            arrayListOf(itemBundle(e.productId, e.productName, e.price, null, e.quantity))
        )
    }
    private fun viewCartBundle(e: AnalyticsEvent.CartViewed) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY, "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE, e.totalValue)
        putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, e.items.toItemBundleList())
    }
    private fun beginCheckoutBundle(e: AnalyticsEvent.CheckoutStarted) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY, "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE, e.totalValue)
        putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, e.items.toItemBundleList())
    }
    private fun purchaseBundle(e: AnalyticsEvent.OrderPlaced) = Bundle().apply {
        putString(FirebaseAnalytics.Param.TRANSACTION_ID, e.orderId)
        putString(FirebaseAnalytics.Param.CURRENCY, "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE, e.totalValue)
        e.couponUsed?.let { putString(FirebaseAnalytics.Param.COUPON, it) }
        putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, e.items.toItemBundleList())
    }
    private fun screenViewBundle(e: AnalyticsEvent.ScreenView) = Bundle().apply {
        putString(FirebaseAnalytics.Param.SCREEN_NAME, e.screenName)
        putString(FirebaseAnalytics.Param.SCREEN_CLASS, e.screenClass)
    }
    private fun searchBundle(e: AnalyticsEvent.SearchPerformed) = Bundle().apply {
        putString(FirebaseAnalytics.Param.SEARCH_TERM, e.query)
    }
    private fun methodBundle(method: String) = Bundle().apply {
        putString(FirebaseAnalytics.Param.METHOD, method)
    }
    private fun shareBundle(e: AnalyticsEvent.ProductShared) = Bundle().apply {
        putString(FirebaseAnalytics.Param.METHOD, e.method)
        putString(FirebaseAnalytics.Param.CONTENT_TYPE, "product")
        putString(FirebaseAnalytics.Param.ITEM_ID, e.productId)
    }
    private fun selectContentBundle(e: AnalyticsEvent.CategorySelected) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CONTENT_TYPE, "category")
        putString(FirebaseAnalytics.Param.ITEM_ID, e.categoryId)
    }
    private fun viewItemListBundle(e: AnalyticsEvent.ProductListViewed) = Bundle().apply {
        putString("item_list_id",   e.listId)
        putString("item_list_name", e.listName)
        putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, e.items.toItemBundleList())
    }
    private fun selectItemBundle(e: AnalyticsEvent.ProductSelected) = Bundle().apply {
        putString("item_list_id",   e.listId)
        putString("item_list_name", e.listName)
        putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, arrayListOf(e.item.toItemBundle()))
    }
    private fun shippingInfoBundle(e: AnalyticsEvent.ShippingInfoAdded) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY,      "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE,          e.totalValue)
        putString(FirebaseAnalytics.Param.SHIPPING_TIER,  e.shippingTier)
        e.couponUsed?.let { putString(FirebaseAnalytics.Param.COUPON, it) }
        putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, e.items.toItemBundleList())
    }
    private fun paymentInfoBundle(e: AnalyticsEvent.PaymentMethodSelected) = Bundle().apply {
        putString(FirebaseAnalytics.Param.CURRENCY,     "IDR")
        putString(FirebaseAnalytics.Param.PAYMENT_TYPE,  e.method)
        if (e.totalValue > 0) putDouble(FirebaseAnalytics.Param.VALUE, e.totalValue)
        if (e.items.isNotEmpty()) putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, e.items.toItemBundleList())
    }
    private fun refundBundle(e: AnalyticsEvent.OrderRefunded) = Bundle().apply {
        putString(FirebaseAnalytics.Param.TRANSACTION_ID, e.orderId)
        putString(FirebaseAnalytics.Param.CURRENCY,       "IDR")
        putDouble(FirebaseAnalytics.Param.VALUE,           e.value)
        if (e.items.isNotEmpty()) putParcelableArrayList(FirebaseAnalytics.Param.ITEMS, e.items.toItemBundleList())
    }
    private fun promotionBundle(
        promotionId: String, promotionName: String,
        creativeName: String?, creativeSlot: String?,
        locationId: String? = null
    ) = Bundle().apply {
        putString(FirebaseAnalytics.Param.PROMOTION_ID,   promotionId)
        putString(FirebaseAnalytics.Param.PROMOTION_NAME, promotionName)
        creativeName?.let { putString(FirebaseAnalytics.Param.CREATIVE_NAME, it) }
        creativeSlot?.let { putString(FirebaseAnalytics.Param.CREATIVE_SLOT, it) }
        locationId?.let   { putString("location_id", it) }
    }
    private fun itemBundle(
        itemId: String,
        itemName: String,
        price: Double,
        category: String?,
        quantity: Int,
        brand: String?        = null,
        variant: String?      = null,
        discount: Double?     = null,
        index: Int?           = null,
        affiliation: String?  = null,
        coupon: String?       = null,
        itemListId: String?   = null,
        itemListName: String? = null
    ) = Bundle().apply {
        putString(FirebaseAnalytics.Param.ITEM_ID,   itemId)
        putString(FirebaseAnalytics.Param.ITEM_NAME, itemName)
        putDouble(FirebaseAnalytics.Param.PRICE,     price)
        putLong(FirebaseAnalytics.Param.QUANTITY,    quantity.toLong())
        category?.let    { putString(FirebaseAnalytics.Param.ITEM_CATEGORY,  it) }
        brand?.let       { putString(FirebaseAnalytics.Param.ITEM_BRAND,     it) }
        variant?.let     { putString(FirebaseAnalytics.Param.ITEM_VARIANT,   it) }
        discount?.let    { putDouble(FirebaseAnalytics.Param.DISCOUNT,       it) }
        index?.let       { putLong(FirebaseAnalytics.Param.INDEX,            it.toLong()) }
        affiliation?.let { putString(FirebaseAnalytics.Param.AFFILIATION,    it) }
        coupon?.let      { putString(FirebaseAnalytics.Param.COUPON,         it) }
        itemListId?.let  { putString(FirebaseAnalytics.Param.ITEM_LIST_ID,   it) }
        itemListName?.let{ putString(FirebaseAnalytics.Param.ITEM_LIST_NAME, it) }
    }
    private fun EcommerceItem.toItemBundle() =
        itemBundle(itemId, itemName, price, category, quantity, brand, variant, discount, index,
                   affiliation, coupon, itemListId, itemListName)
    private fun List<EcommerceItem>.toItemBundleList(): ArrayList<Bundle> =
        ArrayList(map { it.toItemBundle() })
    private fun campaignBundle(e: AnalyticsEvent.CampaignOpened) = Bundle().apply {
        putString(FirebaseAnalytics.Param.SOURCE, e.source)
        e.medium?.let   { putString(FirebaseAnalytics.Param.MEDIUM,  it) }
        e.campaign?.let { putString("campaign",                       it) }
        e.term?.let     { putString(FirebaseAnalytics.Param.TERM,     it) }
        e.content?.let  { putString(FirebaseAnalytics.Param.CONTENT,  it) }
        e.deepLink?.let { putString("deep_link", it.substringBefore("?")) }
    }
    private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
        forEach { (key, value) ->
            val safeKey = key.take(40)
            when (value) {
                is String  -> bundle.putString(safeKey, value.take(100))
                is Int     -> bundle.putInt(safeKey, value)
                is Long    -> bundle.putLong(safeKey, value)
                is Double  -> bundle.putDouble(safeKey, value)
                is Float   -> bundle.putFloat(safeKey, value)
                is Boolean -> bundle.putString(safeKey, value.toString())
                else       -> bundle.putString(safeKey, value.toString().take(100))
            }
        }
    }
}

/** PostHog Analytics */
@Singleton
class PostHogTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "PostHog"

    override suspend fun initialize() {
        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host   = "https://us.posthog.com"
        ).apply {
            captureApplicationLifecycleEvents = true
            captureScreenViews = false
            captureDeepLinks = true
            debug              = BuildConfig.DEBUG
            // Session replay: 20% debug, 40% production. Decided once at init.
            val sessionRecorded = Math.random() < 0.4
            SessionReplayLogger.record("PostHog", sessionRecorded, debugPct = 40, prodPct = 40)
            sessionReplay      = sessionRecorded
            sessionReplayConfig.apply {
                screenshot        = true  // screenshot mode — full-fidelity pixel capture
                maskAllTextInputs = true  // all EditText / TextInputEditText masked (PII)
                maskAllImages     = false // product images intentional in e-commerce; mask sensitive ones individually via maskView()
                captureLogcat     = BuildConfig.DEBUG
                debouncerDelayMs  = 500L
            }
            if (BuildConfig.DEBUG) {
                flushAt = 1
                flushIntervalSeconds = 5
            }
        }
        PostHogAndroid.setup(context, config)
    }

    override fun track(event: AnalyticsEvent) {
        when (event) {
            is AnalyticsEvent.ScreenView -> {
                if (BuildConfig.DEBUG) Log.d("Analytics", "[PostHog] screen: ${event.screenName} (${event.screenClass})")
                PostHog.screen(
                    screenTitle = event.screenName,
                    properties  = mapOf("screen_class" to event.screenClass)
                )
            }
            else -> {
                if (BuildConfig.DEBUG) Log.d("Analytics", "[PostHog] capture: ${event.name}")
                PostHog.capture(event = event.name, properties = event.toProperties())
            }
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        val props = buildMap {
            properties.email?.let             { put("email",              it) }
            properties.name?.let              { put("name",               it) }
            properties.firstName?.let         { put("first_name",         it) }
            properties.lastName?.let          { put("last_name",          it) }
            properties.phone?.let             { put("phone",              it) }
            properties.country?.let           { put("country",            it) }
            properties.loginMethod?.let       { put("login_method",       it) }
            properties.hasPurchased?.let      { put("has_purchased",      it) }
            properties.orderCount?.let        { put("order_count",        it) }
            properties.lifetimeValue?.let     { put("lifetime_value",     it) }
            properties.preferredCategory?.let { put("preferred_category", it) }
            properties.deviceId?.let          { put("c_device_id",        it) }
            properties.appSetId?.let          { put("app_set_id",         it) }
            properties.advertisingId?.let     { put("advertising_id",     it) }
            putAll(properties.customAttributes)
        }
        val propsOnce = buildMap {
            put("created_at", System.currentTimeMillis())
            properties.loginMethod?.let       { put("initial_signup_method", it) }
            // Preserve original device identifiers across identity merges.
            put("c_user_id", userId)
            properties.deviceId?.let          { put("c_device_id_first",    it) }
        }
        PostHog.identify(
            distinctId            = userId,
            userProperties        = props,
            userPropertiesSetOnce = propsOnce
        )
    }
    override fun reset() { PostHog.reset() }

    override fun shutdown() { PostHog.flush() }

    override fun alias(newId: String, oldId: String) {
        PostHog.alias(alias = newId)
    }

    override fun maskView(view: android.view.View) {
        // PostHog 3.x isNoCapture() checks contentDescription.lowercase().contains("ph-no-capture")
        val existing = view.contentDescription?.toString() ?: ""
        if (!existing.lowercase().contains("ph-no-capture")) {
            view.contentDescription = if (existing.isEmpty()) "ph-no-capture" else "$existing ph-no-capture"
        }
    }

    override fun onSessionStart(sessionId: String) {
        PostHog.register("session_id", sessionId)
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        if (enabled) PostHog.optIn() else PostHog.optOut()
    }

}

/** Mixpanel Analytics + Session Replay. */
@Singleton
class MixpanelTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "Mixpanel"

    private lateinit var mp: MixpanelAPI

    override suspend fun initialize() {
        mp = MixpanelAPI.getInstance(context, BuildConfig.MIXPANEL_TOKEN, true)

        // Pre-decide whether this session will be recorded (20% debug / 40% prod)
        // so we can report the decision to SessionReplayLogger before initializing.
        val sessionRecorded = Math.random() < 0.4
        SessionReplayLogger.record("Mixpanel", sessionRecorded, debugPct = 40, prodPct = 40)
        val replayConfig = MPSessionReplayConfig(
            wifiOnly                 = false,
            enableLogging            = BuildConfig.DEBUG,
            recordingSessionsPercent = if (sessionRecorded) 100.0 else 0.4,
            // Text masks EditText/TextInputEditText (user PII) but not static TextViews.
            autoMaskedViews          = setOf(AutoMaskedView.Text)
        )
        MPSessionReplay.initialize(context, BuildConfig.MIXPANEL_TOKEN, mp.distinctId, replayConfig)
    }

    override fun track(event: AnalyticsEvent) {
        val props = event.toProperties().toMixpanelProps()

        when (event) {
            is AnalyticsEvent.OrderPlaced -> {
                // trackCharge adds the purchase to the People profile revenue timeline.
                // mp.track("purchase", ...) fires with $duration appended automatically
                // because timeEvent("purchase") was started in CheckoutStarted below.
                mp.people.trackCharge(event.totalValue, props)
                mp.people.increment("order_count", 1.0)
                mp.track(event.name, props)
            }
            is AnalyticsEvent.CheckoutStarted -> {
                // Start timing the checkout-to-purchase flow. Mixpanel will append a
                // $duration property (in seconds) to the next "purchase" event tracked
                // on this device. The timer resets if timeEvent() is called again.
                mp.timeEvent("purchase")
                mp.track(event.name, props)
            }
            is AnalyticsEvent.AddToCart -> {
                mp.people.increment("total_add_to_cart_events", 1.0)
                mp.track(event.name, props)
            }
            else -> mp.track(event.name, props)
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        mp.identify(userId)
        MPSessionReplay.getInstance()?.identify(userId)

        val people = mp.people
        properties.email?.let             { people.set("\$email", it) }
        properties.name?.let              { people.set("\$name", it) }
        properties.firstName?.let         { people.set("\$first_name", it) }
        properties.lastName?.let          { people.set("\$last_name", it) }
        properties.country?.let           { people.set("country", it) }
        properties.loginMethod?.let       { people.set("login_method", it) }
        properties.hasPurchased?.let      { people.set("has_purchased", it) }
        properties.orderCount?.let        { people.set("order_count", it) }
        properties.lifetimeValue?.let     { people.set("lifetime_value", it) }
        properties.preferredCategory?.let { people.set("preferred_category", it) }
        // Device identifiers — use setOnce so first-install values are preserved.
        // Mixpanel reserves $user_id (set via identify()), so we use c_user_id.
        people.setOnce("c_user_id",    properties.userId)
        properties.deviceId?.let       { people.setOnce("c_device_id",    it) }
        properties.appSetId?.let       { people.setOnce("app_set_id",     it) }
        properties.advertisingId?.let  { people.setOnce("advertising_id", it) }
        properties.customAttributes.forEach { (k, v) -> people.set(k, v.toString()) }

        val superProps = JSONObject().apply {
            properties.country?.let { put("user_country", it) }
        }
        mp.registerSuperProperties(superProps)
    }

    override fun maskView(view: android.view.View) {
        SensitiveViewManager.addSensitiveView(view)
    }

    override fun reset() {
        mp.reset()
        // Sync session replay to the new anonymous distinct ID generated by reset().
        MPSessionReplay.getInstance()?.identify(mp.distinctId)
    }

    override fun onNewPushToken(token: String) {
        // Register FCM token with Mixpanel People so Mixpanel can send push campaigns
        // to this device. Stored under the reserved $android_devices people property.
        mp.people.set("\$android_devices", org.json.JSONArray().put(token))
    }

    override fun onSessionStart(sessionId: String) {
        mp.registerSuperPropertiesOnce(JSONObject()
            .put("session_id", sessionId)
            .put("platform",   "android"))
        // app_version uses registerSuperProperties (not Once) so it updates after app upgrades.
        mp.registerSuperProperties(JSONObject().put("app_version", BuildConfig.VERSION_NAME))
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        if (enabled) mp.optInTracking() else mp.optOutTracking()
    }

    // Helper ───────────────────────────────────────────────────────────
    private fun Map<String, Any>.toMixpanelProps(): JSONObject = JSONObject().apply {
        forEach { (key, value) ->
            when (value) {
                is String  -> put(key, value)
                is Number  -> put(key, value)
                is Boolean -> put(key, value)
                is List<*> -> Unit
                else       -> put(key, value.toString())
            }
        }
    }
}

/** AppsFlyer MMP -- attribution, deep links, and in-app event tracking. */
@Singleton
class AppsFlyerTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "AppsFlyer"

    override suspend fun initialize() {
        AppsFlyerLib.getInstance().setDebugLog(BuildConfig.DEBUG)
        // Resolve redirect/shortened URLs (e.g. Bitly, custom redirects) before attribution
        // so AF can correctly attribute campaigns using redirect chains.
        AppsFlyerLib.getInstance().setResolveDeepLinkURLs("marketapp.onelink.me")

        // AppsFlyer consent ───────────────────────────────────────────────────────
        // Default to non-GDPR; updated when the user responds to the consent sheet
        // via setAnalyticsConsent(). On GDPR regions, override with forGDPRUser().
        AppsFlyerLib.getInstance().setConsentData(AppsFlyerConsent.forNonGDPRUser())

        // Extended Deferred Deep Linking ──────────────────────────────────────────
        // Fires on every launch. onConversionDataSuccess fires immediately when
        // AF returns install-conversion data. The is_first_launch guard ensures we
        // only route the user on genuine new installs, not re-opens.
        AppsFlyerLib.getInstance().registerConversionListener(context, object : AppsFlyerConversionListener {
            override fun onConversionDataSuccess(data: Map<String, Any>?) {
                if (data == null) return
                val isFirstLaunch = data["is_first_launch"] == true
                if (!isFirstLaunch) return
                // Only handle AF-attributed installs (mediaSource present)
                val mediaSource = data["media_source"]?.toString()
                    ?: data["pid"]?.toString() ?: return
                val source   = data["utm_source"]?.toString()   ?: mediaSource
                val medium   = data["utm_medium"]?.toString()   ?: data["af_channel"]?.toString() ?: ""
                val campaign = data["utm_campaign"]?.toString() ?: data["campaign"]?.toString()    ?: ""
                val term     = data["utm_term"]?.toString()
                val content  = data["utm_content"]?.toString()
                val deepLink = data["deep_link_value"]?.toString()
                if (BuildConfig.DEBUG) {
                    Log.d("Analytics", "[AF/EDDL] source=$source medium=$medium campaign=$campaign deepLink=$deepLink")
                }
                onDeepLink?.invoke(source, medium, campaign, term, content, deepLink)
            }
            override fun onConversionDataFail(error: String?) {
                Log.w("Analytics", "[AF/EDDL] Conversion data unavailable: $error")
            }
            override fun onAppOpenAttribution(data: Map<String, String>?) {
                // Re-engagement is handled by UDL (subscribeForDeepLink) below.
            }
            override fun onAttributionFailure(error: String?) {
                Log.w("Analytics", "[AF/EDDL] Attribution failure: $error")
            }
        })

        // Unified Deep Linking ────────────────────────────────────────────────────
        // Handles direct deep links (app already installed) and deferred deep links
        AppsFlyerLib.getInstance().subscribeForDeepLink({ result ->
            when (result.status) {
                DeepLinkResult.Status.FOUND -> {
                    val dl = result.deepLink ?: return@subscribeForDeepLink
                    // Require mediaSource
                    // Native scheme links (marketapp://) have no mediaSource and are already handled by handleNativeDeepLink().
                    val mediaSource = dl.mediaSource ?: return@subscribeForDeepLink
                    // Prefer UTM params on the OneLink template over AF attribution fields
                    val source   = dl.getStringValue("utm_source")   ?: mediaSource
                    val medium   = dl.getStringValue("utm_medium")   ?: dl.getStringValue("af_channel") ?: ""
                    val campaign = dl.getStringValue("utm_campaign") ?: dl.campaign    ?: ""
                    val term     = dl.getStringValue("utm_term")
                    val content  = dl.getStringValue("utm_content")
                    val url      = dl.getStringValue("af_dp")        ?: dl.getStringValue("deep_link_value")
                    if (BuildConfig.DEBUG) {
                        Log.d("Analytics", "[AF/UDL] source=$source medium=$medium campaign=$campaign deepLink=$url")
                    }
                    onDeepLink?.invoke(source, medium, campaign, term, content, url)
                }
                DeepLinkResult.Status.ERROR ->
                    Log.w("Analytics", "[AF/UDL] Deep link error: ${result.error}")
                else -> Unit // NOT_FOUND = normal app open, no action needed
            }
        }, 3000L)

        AppsFlyerLib.getInstance().init(BuildConfig.APPSFLYER_DEV_KEY, null, context)
        // Delay attribution start until setCustomerUserId() is called (after login).
        // This ensures installs are attributed to the correct user identity, not an anonymous ID.
        AppsFlyerLib.getInstance().waitForCustomerUserId(true)
        AppsFlyerLib.getInstance().start(context)
    }

    override fun track(event: AnalyticsEvent) {
        val eventName: String
        val params: Map<String, Any>

        when (event) {
            is AnalyticsEvent.ProductViewed -> {
                eventName = AFInAppEventType.CONTENT_VIEW
                params = mapOf(
                    AFInAppEventParameterName.CONTENT_ID   to event.productId,
                    AFInAppEventParameterName.CONTENT_TYPE to event.category,
                    AFInAppEventParameterName.PRICE        to event.price,
                    AFInAppEventParameterName.CURRENCY     to "IDR"
                )
            }
            is AnalyticsEvent.ProductWishlisted -> {
                eventName = if (event.added) AFInAppEventType.ADD_TO_WISH_LIST else event.name
                params = mapOf(
                    AFInAppEventParameterName.CONTENT_ID to event.productId,
                    AFInAppEventParameterName.PRICE      to event.price,
                    AFInAppEventParameterName.CURRENCY   to "IDR"
                )
            }
            is AnalyticsEvent.AddToCart -> {
                eventName = AFInAppEventType.ADD_TO_CART
                params = mapOf(
                    AFInAppEventParameterName.CONTENT_ID   to event.productId,
                    AFInAppEventParameterName.CONTENT_TYPE to event.category,
                    AFInAppEventParameterName.PRICE        to event.price,
                    AFInAppEventParameterName.QUANTITY     to event.quantity,
                    AFInAppEventParameterName.CURRENCY     to "IDR"
                )
            }
            is AnalyticsEvent.RemoveFromCart -> {
                eventName = event.name
                params = mapOf(
                    AFInAppEventParameterName.CONTENT_ID to event.productId,
                    AFInAppEventParameterName.PRICE      to event.price,
                    AFInAppEventParameterName.QUANTITY   to event.quantity,
                    AFInAppEventParameterName.CURRENCY   to "IDR"
                )
            }
            is AnalyticsEvent.CartViewed -> {
                eventName = event.name
                params = mapOf(
                    AFInAppEventParameterName.PRICE    to event.totalValue,
                    AFInAppEventParameterName.QUANTITY to event.items.sumOf { it.quantity },
                    AFInAppEventParameterName.CURRENCY to "IDR"
                )
            }
            is AnalyticsEvent.CheckoutStarted -> {
                eventName = AFInAppEventType.INITIATED_CHECKOUT
                params = mapOf(
                    AFInAppEventParameterName.PRICE    to event.totalValue,
                    AFInAppEventParameterName.QUANTITY to event.items.sumOf { it.quantity },
                    AFInAppEventParameterName.CURRENCY to "IDR"
                )
            }
            is AnalyticsEvent.OrderPlaced -> {
                eventName = AFInAppEventType.PURCHASE
                params = buildMap {
                    put(AFInAppEventParameterName.REVENUE,      event.totalValue)
                    put(AFInAppEventParameterName.ORDER_ID,     event.orderId)
                    put(AFInAppEventParameterName.CURRENCY,     "IDR")
                    put(AFInAppEventParameterName.QUANTITY,     event.items.sumOf { it.quantity })
                    // Product-level data for AppsFlyer item-level ROAS and audience segmentation
                    put(AFInAppEventParameterName.CONTENT_LIST, ArrayList(event.items.map { it.itemId }))
                    val categories = event.items.mapNotNull { it.category }.distinct()
                    if (categories.isNotEmpty()) put(AFInAppEventParameterName.CONTENT_TYPE, categories.joinToString(","))
                }
                // Log one af_purchase event per line item for item-level ROAS in AF dashboards.
                for (item in event.items) {
                    AppsFlyerLib.getInstance().logEvent(
                        context,
                        AFInAppEventType.PURCHASE,
                        mapOf(
                            AFInAppEventParameterName.CONTENT_ID   to item.itemId,
                            AFInAppEventParameterName.CONTENT_TYPE to (item.category ?: ""),
                            AFInAppEventParameterName.REVENUE      to item.price * item.quantity,
                            AFInAppEventParameterName.PRICE        to item.price,
                            AFInAppEventParameterName.QUANTITY     to item.quantity,
                            AFInAppEventParameterName.ORDER_ID     to event.orderId,
                            AFInAppEventParameterName.CURRENCY     to "IDR"
                        )
                    )
                }
            }
            is AnalyticsEvent.OrderFailed -> {
                eventName = "af_order_failed"
                params = mapOf("reason" to event.reason)
            }
            is AnalyticsEvent.SearchPerformed -> {
                eventName = AFInAppEventType.SEARCH
                params = mapOf(
                    AFInAppEventParameterName.SEARCH_STRING to event.query,
                    "result_count"                          to event.resultCount
                )
            }
            is AnalyticsEvent.UserSignedIn -> {
                eventName = AFInAppEventType.LOGIN
                params = mapOf("method" to event.method)
            }
            is AnalyticsEvent.UserRegistered -> {
                eventName = AFInAppEventType.COMPLETE_REGISTRATION
                params = mapOf(AFInAppEventParameterName.REGISTRATION_METHOD to event.method)
            }
            is AnalyticsEvent.ShippingInfoAdded -> {
                eventName = "af_add_shipping_info"
                params = buildMap {
                    put(AFInAppEventParameterName.PRICE,    event.totalValue)
                    put(AFInAppEventParameterName.CURRENCY, "IDR")
                    put("shipping_tier",                    event.shippingTier)
                    event.couponUsed?.let { put(AFInAppEventParameterName.COUPON_CODE, it) }
                }
            }
            is AnalyticsEvent.PaymentMethodSelected -> {
                eventName = AFInAppEventType.ADD_PAYMENT_INFO
                params = buildMap {
                    put("payment_type",                     event.method)
                    put(AFInAppEventParameterName.CURRENCY, "IDR")
                    if (event.totalValue > 0) put(AFInAppEventParameterName.PRICE, event.totalValue)
                }
            }
            is AnalyticsEvent.OrderRefunded -> {
                eventName = "af_refund"
                params = mapOf(
                    AFInAppEventParameterName.REVENUE  to event.value,
                    AFInAppEventParameterName.ORDER_ID to event.orderId,
                    AFInAppEventParameterName.CURRENCY to "IDR"
                )
            }
            is AnalyticsEvent.PromotionSelected -> {
                eventName = "af_select_promotion"
                params = mapOf(
                    "promotion_id"   to event.promotionId,
                    "promotion_name" to event.promotionName
                )
            }
            else -> {
                eventName = event.name
                params = event.toProperties().filterValues { it !is List<*> }
            }
        }

        AppsFlyerLib.getInstance().logEvent(context, eventName, params)
    }

    // ── Braze integration ─────────────────────────────────────────────────────

    /**
     * Called after ALL trackers have initialized, so Braze is guaranteed ready.
     * Sets brazeCustomerId in AF additional data so every AF postback carries the
     * Braze device ID — required for the AppsFlyer ↔ Braze partner connection.
     */
    override fun onSessionStart(sessionId: String) {
        // setAdditionalData replaces all previous data on each call — merge everything
        // into one map so Braze and Amplitude values coexist in the same postback.
        val data = mutableMapOf<String, Any>(
            "brazeCustomerId" to com.braze.Braze.getInstance(context).deviceId
        )
        AmplitudeTracker.deviceId?.let  { data["AmplitudeDeviceId"]  = it }
        AmplitudeTracker.sessionId
            .takeIf { it > 0 }?.let    { data["AmplitudeSessionId"] = it.toString() }
        AppsFlyerLib.getInstance().setAdditionalData(data)
    }

    override fun identify(userId: String, properties: UserProperties) {
        // setCustomerUserId automatically triggers AF start when waitForCustomerUserId(true) is set.
        AppsFlyerLib.getInstance().setCustomerUserId(userId)

        // Braze Audiences: pass the Braze external ID so AF can sync cohorts
        // back to Braze without relying on device-ID matching alone.
        AppsFlyerLib.getInstance().setPartnerData(
            "braze_inc",
            mapOf("external_id" to userId)
        )

        properties.currency?.let { AppsFlyerLib.getInstance().setCurrencyCode(it) }
        properties.email?.let { email ->
            AppsFlyerLib.getInstance().setUserEmails(
                AppsFlyerProperties.EmailsCryptType.SHA256, email
            )
        }
        properties.phone?.let { phone ->
            AppsFlyerLib.getInstance().setPhoneNumber(phone)
        }
    }

    override fun reset() {
        AppsFlyerLib.getInstance().setCustomerUserId(null)
    }

    override fun onNewPushToken(token: String) {
        // Forward FCM token to AppsFlyer for uninstall measurement.
        AppsFlyerLib.getInstance().updateServerUninstallToken(context, token)
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        // AppsFlyerConsent: inform AF SDK of the user's GDPR consent status so
        // attribution data is processed in compliance with privacy regulations.
        val consent = AppsFlyerConsent.forGDPRUser(
            hasConsentForDataUsage          = enabled,
            hasConsentForAdsPersonalization = enabled
        )
        AppsFlyerLib.getInstance().setConsentData(consent)
        AppsFlyerLib.getInstance().anonymizeUser(!enabled)
    }

    companion object {
        /** Registered by MainActivity to receive OneLink attribution and dispatch
         * AnalyticsEvent.CampaignOpened to all analytics platforms.
         */
        var onDeepLink: ((
            source: String,
            medium: String,
            campaign: String,
            term: String?,
            content: String?,
            deepLink: String?
        ) -> Unit)? = null
    }
}

/** Microsoft Clarity -- session replay + heatmaps. */
@Singleton
class MicrosoftClarityTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "MicrosoftClarity"

    private var consentEnabled = true

    override suspend fun initialize() {
        val config = ClarityConfig(
            projectId = BuildConfig.CLARITY_PROJECT_ID,
            logLevel  = if (BuildConfig.DEBUG) LogLevel.Verbose else LogLevel.None
        )
        Clarity.initialize(context as android.app.Application, config)
        // Forward the Clarity session ID to Firebase as a user property
        Clarity.setOnSessionStartedCallback { sessionId ->
            Firebase.analytics.setUserProperty("clarity_session_id", sessionId)
        }
    }

    override fun track(event: AnalyticsEvent) {
        if (!consentEnabled) return
        if (event is AnalyticsEvent.ScreenView) {
            Clarity.setCurrentScreenName(event.screenName)
        }

        when (event) {
            is AnalyticsEvent.ProductViewed        -> {
                Clarity.setCustomTag("product_id",       event.productId)
                Clarity.setCustomTag("product_name",     event.productName)
                Clarity.setCustomTag("product_category", event.category)
                Clarity.setCustomTag("product_price",    event.price.toString())
                Clarity.setCustomTag("product_source",   event.source)
            }
            is AnalyticsEvent.ProductWishlisted    -> {
                Clarity.setCustomTag("product_id",      event.productId)
                Clarity.setCustomTag("wishlist_action", if (event.added) "added" else "removed")
            }
            is AnalyticsEvent.AddToCart            -> {
                Clarity.setCustomTag("product_id",         event.productId)
                Clarity.setCustomTag("product_name",       event.productName)
                Clarity.setCustomTag("cart_item_category", event.category)
                Clarity.setCustomTag("cart_item_price",    event.price.toString())
                Clarity.setCustomTag("cart_item_qty",      event.quantity.toString())
            }
            is AnalyticsEvent.RemoveFromCart       -> {
                Clarity.setCustomTag("product_id",    event.productId)
                Clarity.setCustomTag("cart_item_qty", event.quantity.toString())
            }
            is AnalyticsEvent.CartViewed           -> {
                Clarity.setCustomTag("cart_value",      event.totalValue.toString())
                Clarity.setCustomTag("cart_item_count", event.items.sumOf { it.quantity }.toString())
            }
            is AnalyticsEvent.CheckoutStarted      -> {
                Clarity.setCustomTag("checkout_value",      event.totalValue.toString())
                Clarity.setCustomTag("checkout_item_count", event.items.sumOf { it.quantity }.toString())
            }
            is AnalyticsEvent.PaymentMethodSelected -> {
                Clarity.setCustomTag("payment_type",   event.method)
                if (event.totalValue > 0) Clarity.setCustomTag("checkout_value", event.totalValue.toString())
            }
            is AnalyticsEvent.OrderPlaced          -> {
                Clarity.setCustomTag("order_id",      event.orderId)
                Clarity.setCustomTag("order_value",   event.totalValue.toString())
                Clarity.setCustomTag("order_payment", event.paymentMethod)
                event.couponUsed?.let { Clarity.setCustomTag("order_coupon", it) }
            }
            is AnalyticsEvent.OrderFailed          -> Clarity.setCustomTag("order_fail_reason", event.reason)
            is AnalyticsEvent.SearchPerformed      -> {
                Clarity.setCustomTag("search_query",   event.query)
                Clarity.setCustomTag("search_results", event.resultCount.toString())
            }
            is AnalyticsEvent.SearchResultTapped   -> {
                Clarity.setCustomTag("product_id",      event.productId)
                Clarity.setCustomTag("search_position", event.position.toString())
            }
            is AnalyticsEvent.CategorySelected     -> {
                Clarity.setCustomTag("category_id",   event.categoryId)
                Clarity.setCustomTag("category_name", event.categoryName)
            }
            is AnalyticsEvent.PromotionViewed       -> {
                Clarity.setCustomTag("promotion_id",   event.promotionId)
                Clarity.setCustomTag("promotion_name", event.promotionName)
            }
            is AnalyticsEvent.PromotionSelected     -> {
                Clarity.setCustomTag("promotion_id",   event.promotionId)
                Clarity.setCustomTag("promotion_name", event.promotionName)
            }
            is AnalyticsEvent.ProductListViewed     -> {
                Clarity.setCustomTag("list_id",         event.listId)
                Clarity.setCustomTag("list_name",       event.listName)
                Clarity.setCustomTag("list_item_count", event.items.size.toString())
            }
            is AnalyticsEvent.ProductSelected       -> {
                Clarity.setCustomTag("list_id",      event.listId)
                Clarity.setCustomTag("product_id",   event.item.itemId)
                Clarity.setCustomTag("product_name", event.item.itemName)
                event.item.index?.let { Clarity.setCustomTag("product_index", it.toString()) }
            }
            is AnalyticsEvent.CheckoutAddressSelected -> {
                Clarity.setCustomTag("checkout_city",       event.city)
                Clarity.setCustomTag("checkout_country",    event.country)
                Clarity.setCustomTag("checkout_new_address", event.isNewAddress.toString())
            }
            is AnalyticsEvent.ShippingInfoAdded     -> {
                Clarity.setCustomTag("shipping_tier",  event.shippingTier)
                Clarity.setCustomTag("checkout_value", event.totalValue.toString())
            }
            is AnalyticsEvent.OrderRefunded         -> {
                Clarity.setCustomTag("order_id",     event.orderId)
                Clarity.setCustomTag("refund_value", event.value.toString())
            }
            is AnalyticsEvent.UserSignedIn         -> Clarity.setCustomTag("login_method",  event.method)
            is AnalyticsEvent.UserRegistered       -> Clarity.setCustomTag("signup_method", event.method)
            is AnalyticsEvent.UserSignedOut        -> Clarity.setCustomTag("session_duration_ms", event.sessionDuration.toString())
            is AnalyticsEvent.CampaignOpened       -> {
                Clarity.setCustomTag("campaign_source", event.source)
                Clarity.setCustomTag("campaign_medium", event.medium)
                Clarity.setCustomTag("campaign_name",   event.campaign)
            }
            is AnalyticsEvent.PushTapped           -> {
                event.campaignId?.let { Clarity.setCustomTag("push_campaign_id", it) }
                event.deepLink?.let   { Clarity.setCustomTag("push_deep_link",   it) }
            }
            is AnalyticsEvent.ErrorOccurred        -> {
                Clarity.setCustomTag("error_screen",  event.screen)
                Clarity.setCustomTag("error_code",    event.code)
                Clarity.setCustomTag("error_message", event.message)
            }
            else -> Unit
        }
        Clarity.sendCustomEvent(event.name)
    }

    override fun identify(userId: String, properties: UserProperties) {
        Clarity.setCustomUserId(userId)
        properties.country?.let           { Clarity.setCustomTag("user_country",        it) }
        properties.loginMethod?.let       { Clarity.setCustomTag("login_method",        it) }
        properties.hasPurchased?.let      { Clarity.setCustomTag("has_purchased",       it.toString()) }
        properties.orderCount?.let        { Clarity.setCustomTag("order_count",         it.toString()) }
        properties.preferredCategory?.let { Clarity.setCustomTag("preferred_category",  it) }
    }

    override fun reset() {
        Clarity.setCustomUserId("")
    }

    override fun maskView(view: android.view.View) {
        Clarity.maskView(view)
    }

    override fun setAnalyticsConsent(enabled: Boolean) { consentEnabled = enabled }
}

/**
 * Braze — customer engagement platform.
 *
 * Features enabled:
 *  - Push notifications (token forwarded from MarketFirebaseMessagingService)
 *  - In-app messages (auto-registered via BrazeActivityLifecycleCallbackListener)
 *  - Content Cards / Banners (refreshed on init; launch BrazeContentCardsActivity to display)
 *  - Ecommerce recommended events (braze.com/docs/.../ecommerce_events)
 *  - logPurchase per item kept alongside ecommerce.order_placed for Braze revenue analytics
 *  - User attributes via currentUser setter methods
 *  - Uninstall tracking: explicit BrazeNotificationPayload.isUninstallTrackingPush guard in
 *    MarketFirebaseMessagingService discards silent pings before any processing
 *  - Deep links from push: handled automatically (setHandlePushDeepLinksAutomatically = true)
 *  - Geofences: enabled with automatic location requests; call onLocationPermissionGranted()
 *    from the Activity/ViewModel after ACCESS_FINE_LOCATION is granted at runtime
 *
 * Push: configure your FCM Sender ID (940226439607) in the Braze dashboard under
 *   App Settings → Push Notifications → Android so Braze can dispatch notifications.
 */
@Singleton
class BrazeTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "Braze"

    private var consentEnabled = true

    override suspend fun initialize() {
        // Must be set before Braze.configure() so the full init sequence is captured.
        if (BuildConfig.DEBUG) com.braze.support.BrazeLogger.logLevel = Log.VERBOSE

        val config = com.braze.configuration.BrazeConfig.Builder()
            .setApiKey(BuildConfig.BRAZE_API_KEY)
            .setCustomEndpoint(ENDPOINT)
            // Open Braze-sent push deep links automatically without extra routing code.
            .setHandlePushDeepLinksAutomatically(true)
            // Geofences: enable geofence evaluation and let Braze auto-request geofences
            // at the user's current location when location services are available.
            // After ACCESS_FINE_LOCATION is granted, call onLocationPermissionGranted()
            // from the Activity/ViewModel to start resolving nearby geofences.
            .setIsLocationCollectionEnabled(true)
            .setGeofencesEnabled(true)
            .setAutomaticGeofenceRequestsEnabled(true)
            .build()
        com.braze.Braze.configure(context, config)

        // BrazeActivityLifecycleCallbackListener handles:
        //  - Braze session open/close (openSession/closeSession per activity)
        //  - Automatic in-app message registration/unregistration per activity
        // Called on the Application instance so it covers every Activity in the app.
        (context.applicationContext as android.app.Application)
            .registerActivityLifecycleCallbacks(
                com.braze.BrazeActivityLifecycleCallbackListener()
            )

        // Pre-fetch content cards so banners are available immediately when the
        // user navigates to a screen that shows the BrazeContentCardsActivity.
        com.braze.Braze.getInstance(context).requestContentCardsRefresh()
        // Note: Braze Banners (BannerView) requires a newer SDK version. Upgrade braze > 33.0.0 to enable.
    }

    override fun track(event: AnalyticsEvent) {
        if (!consentEnabled) return
        val braze = com.braze.Braze.getInstance(context)
        when (event) {
            // ── ecommerce.product_viewed ─────────────────────────────────────
            is AnalyticsEvent.ProductViewed -> {
                braze.logCustomEvent(
                    "ecommerce.product_viewed",
                    com.braze.models.outgoing.BrazeProperties().apply {
                        addProperty("product_id",   event.productId)
                        addProperty("product_name", event.productName)
                        addProperty("variant_id",   event.productId)
                        addProperty("price",        event.price)
                        addProperty("currency",     CURRENCY_IDR)
                        addProperty("source",       event.source)
                    }
                )
            }
            // ── ecommerce.cart_updated (add) ───────────────────────────────────
            is AnalyticsEvent.AddToCart -> {
                braze.logCustomEvent(
                    "ecommerce.cart_updated",
                    com.braze.models.outgoing.BrazeProperties().apply {
                        addProperty("total_value", event.price * event.quantity)
                        addProperty("currency",    CURRENCY_IDR)
                        addProperty("action",      "add")
                        addProperty("products",    org.json.JSONArray().apply {
                            put(event.toProductJson())
                        })
                    }
                )
            }
            // ── ecommerce.cart_updated (remove) ───────────────────────────────
            is AnalyticsEvent.RemoveFromCart -> {
                braze.logCustomEvent(
                    "ecommerce.cart_updated",
                    com.braze.models.outgoing.BrazeProperties().apply {
                        addProperty("total_value", event.price * event.quantity)
                        addProperty("currency",    CURRENCY_IDR)
                        addProperty("action",      "remove")
                        addProperty("products",    org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("product_id",   event.productId)
                                put("product_name", event.productName)
                                put("variant_id",   event.productId)
                                put("quantity",     event.quantity)
                                put("price",        event.price)
                            })
                        })
                    }
                )
            }
            // ── ecommerce.checkout_started ────────────────────────────────────
            is AnalyticsEvent.CheckoutStarted -> {
                braze.logCustomEvent(
                    "ecommerce.checkout_started",
                    com.braze.models.outgoing.BrazeProperties().apply {
                        addProperty("total_value", event.totalValue)
                        addProperty("currency",    CURRENCY_IDR)
                        addProperty("item_count",  event.items.sumOf { it.quantity })
                        addProperty("products",    event.items.toProductJsonArray())
                    }
                )
            }
            // ── ecommerce.order_placed + logPurchase ──────────────────────────
            is AnalyticsEvent.OrderPlaced -> {
                // Braze recommended event
                braze.logCustomEvent(
                    "ecommerce.order_placed",
                    com.braze.models.outgoing.BrazeProperties().apply {
                        addProperty("order_id",        event.orderId)
                        addProperty("total_value",     event.totalValue)
                        addProperty("currency",        CURRENCY_IDR)
                        addProperty("payment_method",  event.paymentMethod)
                        addProperty("item_count",      event.items.sumOf { it.quantity })
                        event.couponUsed?.let { addProperty("coupon", it) }
                        addProperty("products", event.items.toProductJsonArray())
                    }
                )
                for (item in event.items) {
                    braze.logPurchase(
                        item.itemId,
                        CURRENCY_IDR,
                        BigDecimal.valueOf(item.price),
                        item.quantity,
                        com.braze.models.outgoing.BrazeProperties().apply {
                            addProperty("order_id",       event.orderId)
                            addProperty("item_name",      item.itemName)
                            addProperty("payment_method", event.paymentMethod)
                            item.category?.let { addProperty("item_category", it) }
                            event.couponUsed?.let { addProperty("coupon", it) }
                        }
                    )
                }
                // Flush immediately on purchase — revenue events must never wait in the queue.
                braze.requestImmediateDataFlush()
            }
            // ── ecommerce.order_refunded ──────────────────────────────────────
            is AnalyticsEvent.OrderRefunded -> {
                braze.logCustomEvent(
                    "ecommerce.order_refunded",
                    com.braze.models.outgoing.BrazeProperties().apply {
                        addProperty("order_id",   event.orderId)
                        addProperty("value",      event.value)
                        addProperty("currency",   CURRENCY_IDR)
                        if (event.items.isNotEmpty()) {
                            addProperty("item_count", event.items.size)
                            addProperty("products",   event.items.toProductJsonArray())
                        }
                    }
                )
            }
            // ── onboarding_completed ──────────────────────────────────────────
            is AnalyticsEvent.OnboardingCompleted -> {
                braze.logCustomEvent(
                    "onboarding_completed",
                    com.braze.models.outgoing.BrazeProperties().apply {
                        addProperty("method", event.method)
                    }
                )
            }
            // ── subscribe ─────────────────────────────────────────────────────
            is AnalyticsEvent.Subscribe -> {
                braze.logCustomEvent("subscribe")
            }
            // ── All other events — generic custom event ───────────────────────
            else -> {
                val props = event.toProperties().filterValues { it !is List<*> }
                if (props.isEmpty()) {
                    braze.logCustomEvent(event.name)
                } else {
                    braze.logCustomEvent(event.name, props.toBrazeProperties())
                }
                // Flush immediately for trigger_ events so Braze evaluates
                // action-based campaigns (in-app, content card) without delay.
                if (event.name.startsWith("trigger_")) {
                    braze.requestImmediateDataFlush()
                }
            }
        }
        // In debug, flush immediately so events appear in the Braze dashboard
        if (BuildConfig.DEBUG && event !is AnalyticsEvent.OrderPlaced) {
            braze.requestImmediateDataFlush()
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        val braze = com.braze.Braze.getInstance(context)
        braze.changeUser(userId)
        val user = braze.currentUser ?: return
        properties.email?.let              { user.setEmail(it) }
        properties.phone?.let              { user.setPhoneNumber(it) }
        properties.country?.let            { user.setCountry(it) }
        user.setLanguage(java.util.Locale.getDefault().language)
        // First/last name — use dedicated fields if present, fall back to splitting full name
        val firstName = properties.firstName ?: properties.name?.substringBefore(" ")
        val lastName  = properties.lastName  ?: properties.name?.substringAfter(" ", "")?.ifEmpty { null }
        firstName?.let { user.setFirstName(it) }
        lastName?.let  { user.setLastName(it) }
        // Custom user attributes
        properties.loginMethod?.let       { user.setCustomUserAttribute("login_method",       it) }
        properties.hasPurchased?.let      { user.setCustomUserAttribute("has_purchased",      it) }
        properties.orderCount?.let        { user.setCustomUserAttribute("order_count",        it) }
        properties.lifetimeValue?.let     { user.setCustomUserAttribute("lifetime_value",     it) }
        properties.preferredCategory?.let { user.setCustomUserAttribute("preferred_category", it) }
        // Device identifiers
        properties.deviceId?.let          { user.setCustomUserAttribute("c_device_id",    it) }
        properties.appSetId?.let          { user.setCustomUserAttribute("app_set_id",     it) }
        properties.advertisingId?.let     { user.setCustomUserAttribute("advertising_id", it) }
        properties.customAttributes.forEach { (k, v) ->
            when (v) {
                is Boolean -> user.setCustomUserAttribute(k, v)
                is Int     -> user.setCustomUserAttribute(k, v)
                is Double  -> user.setCustomUserAttribute(k, v)
                is String  -> user.setCustomUserAttribute(k, v)
                else       -> user.setCustomUserAttribute(k, v.toString())
            }
        }
    }

    override fun reset() {
        com.braze.Braze.getInstance(context)
            .changeUser(java.util.UUID.randomUUID().toString())
    }

    override fun onNewPushToken(token: String) {
        com.braze.Braze.getInstance(context).registeredPushToken = token
    }

    /**
     * Call once after the user grants [android.Manifest.permission.ACCESS_FINE_LOCATION]
     * (and [android.Manifest.permission.ACCESS_BACKGROUND_LOCATION] on API 29+).
     * Braze will start resolving geofences near the user's current position.
     *
     * Typical call site — Activity / ViewModel location permission result callback:
     * ```kotlin
     * if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
     *     brazeTracker.onLocationPermissionGranted()
     * }
     */
    fun onLocationPermissionGranted() {
        com.braze.Braze.getInstance(context).requestLocationInitialization()
    }

    /**
     * Manually request Braze geofences for the given coordinates.
     * Useful after a FusedLocationProviderClient update when you want to refresh
     * the active geofence set for the user's current position.
     *
     * Note: Braze allows only one geofence request per session — either automatic
     * (automaticGeofenceRequestsEnabled = true) or a single manual call here.
     * Subsequent calls within the same session are no-ops.
     */
    fun requestGeofencesAt(latitude: Double, longitude: Double) {
        com.braze.Braze.getInstance(context).requestGeofences(latitude, longitude)
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        consentEnabled = enabled
        val user = com.braze.Braze.getInstance(context).currentUser ?: return
        // Global push & email subscription state:
        //   consent given  → OPTED_IN  (user has explicitly agreed)
        //   consent revoked → UNSUBSCRIBED
        val subType = if (enabled) com.braze.enums.NotificationSubscriptionType.OPTED_IN
                      else         com.braze.enums.NotificationSubscriptionType.UNSUBSCRIBED
        user.setPushNotificationSubscriptionType(subType)
        user.setEmailNotificationSubscriptionType(subType)
        // Channel-level subscription groups (WhatsApp + Email):
        if (enabled) {
            user.addToSubscriptionGroup(WHATSAPP_GROUP_ID)
            user.addToSubscriptionGroup(EMAIL_GROUP_ID)
        } else {
            user.removeFromSubscriptionGroup(WHATSAPP_GROUP_ID)
            user.removeFromSubscriptionGroup(EMAIL_GROUP_ID)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /** Flat Map → BrazeProperties (used by the generic fallback branch). */
    private fun Map<String, Any>.toBrazeProperties(): com.braze.models.outgoing.BrazeProperties =
        com.braze.models.outgoing.BrazeProperties().also { bp ->
            forEach { (k, v) ->
                when (v) {
                    is Boolean -> bp.addProperty(k, v)
                    is Int     -> bp.addProperty(k, v)
                    is Long    -> bp.addProperty(k, v.toInt())
                    is Double  -> bp.addProperty(k, v)
                    is Float   -> bp.addProperty(k, v.toDouble())
                    is String  -> bp.addProperty(k, v)
                    else       -> bp.addProperty(k, v.toString())
                }
            }
        }

    /** Single-item product JSON for ecommerce.cart_updated (add action). */
    private fun AnalyticsEvent.AddToCart.toProductJson(): JSONObject =
        JSONObject().apply {
            put("product_id",   productId)
            put("product_name", productName)
            put("variant_id",   productId)   // no variant in our model; use productId as fallback
            put("quantity",     quantity)
            put("price",        price)
            put("currency",     CURRENCY_IDR)
        }

    /** EcommerceItem list → Braze-schema products JSONArray. */
    private fun List<EcommerceItem>.toProductJsonArray(): org.json.JSONArray =
        org.json.JSONArray().apply {
            forEach { item ->
                put(JSONObject().apply {
                    put("product_id",   item.itemId)
                    put("product_name", item.itemName)
                    put("variant_id",   item.itemId)  // use itemId as variant_id fallback
                    put("quantity",     item.quantity)
                    put("price",        item.price)
                    item.category?.let { put("item_category", it) }
                    item.brand?.let    { put("brand",         it) }
                    item.variant?.let  { put("variant",       it) }
                    item.discount?.let { put("discount",      it) }
                })
            }
        }

    companion object {
        private const val ENDPOINT             = "sdk.fra-01.braze.eu"
        private const val CURRENCY_IDR         = "IDR"
        private const val WHATSAPP_GROUP_ID = "36bba8fd-c772-4ca2-8a83-81bbc411501d"
        private const val EMAIL_GROUP_ID = "8cd50cfa-c961-4d42-afc0-348bf9772c18"

    }
}

/** OneSignal — push notification and engagement platform. */
@Singleton
class OneSignalTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "OneSignal"

    private var consentEnabled = true
    private var lastEmail: String? = null
    private var lastPhone: String? = null

    override suspend fun initialize() {
        com.onesignal.OneSignal.initWithContext(context, BuildConfig.ONESIGNAL_APP_ID)
        if (BuildConfig.DEBUG) {
            com.onesignal.OneSignal.Debug.logLevel = com.onesignal.debug.LogLevel.VERBOSE
        }
    }

    override fun track(event: AnalyticsEvent) {
        // OneSignal is push-focused — log outcomes for conversion attribution.
        when (event) {
            is AnalyticsEvent.OrderPlaced        ->
                com.onesignal.OneSignal.Session.addOutcomeWithValue("purchase", event.totalValue.toFloat())
            is AnalyticsEvent.AddToCart          ->
                com.onesignal.OneSignal.Session.addOutcome("add_to_cart")
            is AnalyticsEvent.CheckoutStarted    ->
                com.onesignal.OneSignal.Session.addOutcome("begin_checkout")
            is AnalyticsEvent.UserRegistered     ->
                com.onesignal.OneSignal.Session.addOutcome("sign_up")
            is AnalyticsEvent.PushReceived       ->
                com.onesignal.OneSignal.Session.addOutcome("push_received")
            is AnalyticsEvent.PushTapped         ->
                com.onesignal.OneSignal.Session.addOutcome("push_opened")
            is AnalyticsEvent.ProductWishlisted  ->
                if (event.added) com.onesignal.OneSignal.Session.addOutcome("wishlist_add")
            is AnalyticsEvent.PushPermissionGranted ->
                if (event.granted) com.onesignal.OneSignal.User.pushSubscription.optIn()
                else               com.onesignal.OneSignal.User.pushSubscription.optOut()
            else -> Unit
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        // Login sets the External User ID — enables cross-device targeting and
        // audience segmentation based on your own user identity.
        com.onesignal.OneSignal.login(userId)

        val user = com.onesignal.OneSignal.User
        // Cache identity fields so setAnalyticsConsent can re-add / remove them.
        properties.email?.let { lastEmail = it }
        properties.phone?.let { lastPhone = it }
        // Only register email/SMS channels when consent is active.
        if (consentEnabled) {
            lastEmail?.let { user.addEmail(it) }
            lastPhone?.let { user.addSms(it) }
        }

        val tags = buildMap {
            properties.name?.let              { put("name",               it) }
            properties.firstName?.let         { put("first_name",         it) }
            properties.lastName?.let          { put("last_name",          it) }
            properties.country?.let           { put("country",            it) }
            properties.loginMethod?.let       { put("login_method",       it) }
            properties.hasPurchased?.let      { put("has_purchased",      it.toString()) }
            properties.orderCount?.let        { put("order_count",        it.toString()) }
            properties.lifetimeValue?.let     { put("lifetime_value",     it.toString()) }
            properties.preferredCategory?.let { put("preferred_category", it) }
            // c_device_id and advertising_id omitted — OneSignal tracks device
            // identity natively; including them as tags wastes tag-key quota.
        }
        if (tags.isNotEmpty()) user.addTags(tags)
    }

    override fun reset() {
        // Remove tags before logout so they don't bleed into the next user's profile
        // if a different account signs in on the same device.
        com.onesignal.OneSignal.User.removeTags(
            listOf("name", "first_name", "last_name", "country", "login_method",
                   "has_purchased", "order_count", "lifetime_value", "preferred_category")
        )
        lastEmail = null
        lastPhone = null
        com.onesignal.OneSignal.logout()
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        consentEnabled = enabled
        val user = com.onesignal.OneSignal.User
        if (enabled) {
            user.pushSubscription.optIn()
            lastEmail?.let { user.addEmail(it) }
            lastPhone?.let { user.addSms(it) }
        } else {
            user.pushSubscription.optOut()
            lastEmail?.let { user.removeEmail(it) }
            lastPhone?.let { user.removeSms(it) }
        }
    }
}