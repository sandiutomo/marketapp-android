package com.marketapp.config

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.marketapp.BuildConfig
import com.marketapp.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor() {

    private val rc by lazy {
        Firebase.remoteConfig.also {
            it.setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3600L
            })
            it.setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }

    /** Call once on app start — fire-and-forget; next launch uses freshly fetched values. */
    fun fetchAndActivate() { rc.fetchAndActivate() }

    fun isEnabled(flag: FeatureFlag): Boolean = rc.getBoolean(flag.key)
    fun getString(flag: FeatureFlag): String   = rc.getString(flag.key)
    fun getDouble(flag: FeatureFlag): Double   = rc.getDouble(flag.key)
    fun getLong(flag: FeatureFlag):   Long     = rc.getLong(flag.key)
}

/**
 * Firebase Remote Config feature flags and configuration values.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FIREBASE REMOTE CONFIG — E-COMMERCE USE CASES & SETUP GUIDE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * 1. KILL SWITCHES  (Boolean flags, default = true)
 *    Disable critical flows instantly without a release:
 *      CHECKOUT_ENABLED           → disable checkout during maintenance/outage
 *      PAYMENT_METHOD_COD_ENABLED → toggle Cash-on-Delivery by region
 *      PAYMENT_METHOD_VA_ENABLED  → toggle Virtual Account payment method
 *
 * 2. FEATURE ROLLOUT  (Boolean flags, default = false, use % audience condition)
 *    Deploy to a percentage of users and monitor metrics before full rollout.
 *    In the Firebase Console → Remote Config, create a "Percentage audience"
 *    condition (e.g. random_percentile ≤ 10) and override the flag to true:
 *      HOME_PERSONALISATION        → AI-driven personalised home feed
 *      PRODUCT_RECOMMENDATIONS     → cross-sell / upsell widgets on PDP
 *      REVIEW_MODULE               → product review display + submission
 *      LOYALTY_POINTS_ENABLED      → loyalty/rewards programme
 *      REFERRAL_PROGRAM_ENABLED    → refer-a-friend flow
 *
 * 3. BUSINESS CONFIGURATION VALUES  (Numeric/Boolean flags)
 *    Tune business rules without a code change or re-release:
 *      FREE_SHIPPING_THRESHOLD_IDR → order value for free shipping (IDR)
 *      CART_ITEM_LIMIT             → max line-items allowed in a single cart
 *      MINIMUM_ORDER_VALUE_IDR     → minimum order value required for checkout
 *      MAX_SHIPPING_DAYS           → maximum promised delivery window (days)
 *
 * 4. UX / MERCHANDISING  (Boolean flags)
 *    Control banners and UI widgets from the dashboard:
 *      SHOW_PROMOTIONS_BANNER     → seasonal promo banner on the home screen
 *      FLASH_SALE_BANNER_ENABLED  → limited-time flash-sale banner
 *      SEARCH_AUTOCOMPLETE        → live autocomplete in the search bar
 *      WISHLIST_ENABLED           → heart/wishlist button on product cards
 *
 * SETUP STEPS
 *  1. Create each flag in Firebase Console → Remote Config with the matching key.
 *  2. Set a default in remote_config_defaults.xml (used when offline or before
 *     the first successful fetch).
 *  3. For gradual rollout, create a Percentage audience condition in the console
 *     (Conditions → Add condition → User property: random_percentile).
 *  4. For A/B tests use Firebase A/B Testing (wraps Remote Config) — select a
 *     GA4 metric as goal, set traffic split, Firebase picks the winning variant.
 *  5. fetchAndActivate() is called once per launch in MarketApplication.
 *     New values take effect on the NEXT launch (activate() is atomic).
 *  6. For urgent kill-switches lower minimumFetchIntervalInSeconds to 60 s,
 *     or call fetchAndActivate() again just before the critical screen.
 * ─────────────────────────────────────────────────────────────────────────────
 */
enum class FeatureFlag(val key: String) {

    // ── UX / Merchandising ────────────────────────────────────────────────────
    SHOW_PROMOTIONS_BANNER      ("show_promotions_banner"),
    FLASH_SALE_BANNER_ENABLED   ("flash_sale_banner_enabled"),
    SEARCH_AUTOCOMPLETE         ("search_autocomplete_enabled"),
    WISHLIST_ENABLED            ("wishlist_enabled"),

    // ── Kill Switches ─────────────────────────────────────────────────────────
    CHECKOUT_ENABLED            ("checkout_enabled"),
    PAYMENT_METHOD_COD_ENABLED  ("payment_method_cod_enabled"),
    PAYMENT_METHOD_VA_ENABLED   ("payment_method_va_enabled"),

    // ── Feature Rollouts ──────────────────────────────────────────────────────
    HOME_PERSONALISATION        ("home_personalisation_enabled"),
    PRODUCT_RECOMMENDATIONS     ("product_recommendations_enabled"),
    REVIEW_MODULE               ("review_module_enabled"),
    LOYALTY_POINTS_ENABLED      ("loyalty_points_enabled"),
    REFERRAL_PROGRAM_ENABLED    ("referral_program_enabled"),

    // ── Business Configuration Values ────────────────────────────────────────
    CART_ITEM_LIMIT             ("cart_item_limit"),
    FREE_SHIPPING_THRESHOLD_IDR ("free_shipping_threshold_idr"),
    MINIMUM_ORDER_VALUE_IDR     ("minimum_order_value_idr"),
    MAX_SHIPPING_DAYS           ("max_shipping_days"),
}
