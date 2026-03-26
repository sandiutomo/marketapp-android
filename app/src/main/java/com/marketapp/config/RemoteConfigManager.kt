package com.marketapp.config

import android.util.Log
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
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 30L else 3600L
            })
            it.setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }

    @Volatile private var fetchCompleteListener: (() -> Unit)? = null

    /**
     * Register a one-shot callback to be invoked (on the main thread) once the current
     * [fetchAndActivate] completes. Use this to re-render UI that read a flag before the
     * fetch finished — a common race on the first launch after a flag change in the console.
     *
     * The listener is cleared after it fires, so re-register on each [onViewCreated] if needed.
     */
    fun doOnNextFetchComplete(action: () -> Unit) {
        fetchCompleteListener = action
    }

    /**
     * Force-fetch ignoring the minimum interval — use from the debug panel before a demo.
     * Restores the normal interval after the fetch completes.
     */
    fun forceRefresh(onComplete: (() -> Unit)? = null) {
        rc.setConfigSettingsAsync(remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0L
        }).addOnCompleteListener {
            rc.fetchAndActivate().addOnCompleteListener { task ->
                rc.setConfigSettingsAsync(remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 30L else 3600L
                })
                if (BuildConfig.DEBUG) Log.d(TAG, "force fetch ${if (task.isSuccessful) "OK — ${if (task.result) "updated" else "unchanged"}" else "FAILED"}")
                onComplete?.invoke()
            }
        }
    }

    /**
     * Call once on app start — fire-and-forget; next launch uses freshly fetched values.
     */
    fun fetchAndActivate() {
        rc.fetchAndActivate().addOnCompleteListener { task ->
            if (BuildConfig.DEBUG) {
                if (task.isSuccessful) {
                    Log.d(TAG, "fetch OK — values ${if (task.result) "updated" else "unchanged"}")
                    logAll()
                } else {
                    Log.w(TAG, "fetch FAILED — using cached/default values", task.exception)
                }
            }
            fetchCompleteListener?.invoke()
            fetchCompleteListener = null
        }
    }

    fun isEnabled(flag: FeatureFlag): Boolean = rc.getBoolean(flag.key).also { value ->
        if (BuildConfig.DEBUG) Log.d(TAG, "${flag.key}=$value")
    }
    fun getString(flag: FeatureFlag): String = rc.getString(flag.key).also { value ->
        if (BuildConfig.DEBUG) Log.d(TAG, "${flag.key}=\"$value\"")
    }
    fun getDouble(flag: FeatureFlag): Double = rc.getDouble(flag.key)
    fun getLong(flag: FeatureFlag):   Long   = rc.getLong(flag.key)

    /**
     * Raw string value for any flag — used by the debug panel (no logging side effect).
     */
    fun rawValue(flag: FeatureFlag): String = rc.getValue(flag.key).asString()

    /**
     * Dumps every flag's current value to logcat.
     * Called automatically after a successful fetch.
     */
    fun logAll() {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "┌─── Remote Config Values ────────────────────────────")
        FeatureFlag.entries.forEach { flag ->
            val source = rc.getKeysByPrefix("").contains(flag.key)
            val value  = rc.getValue(flag.key)
            Log.d(TAG, "│  ${flag.key.padEnd(42)} = ${value.asString()}${if (!source) " [default]" else ""}")
        }
        Log.d(TAG, "└─────────────────────────────────────────────────────")
    }

    companion object {
        private const val TAG = "RemoteConfig"
    }
}

/**
 * Firebase Remote Config feature flags and configuration values.
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
    // Kill Switches ───────────────────────────────────────────────────────────────────────────────
    AI_PRODUCT_SORTING_ENABLED       ("ai_product_sorting_enabled"),
    AI_SEARCH_ENABLED                ("ai_search_enabled"),
    AI_ORDER_MESSAGE_ENABLED         ("ai_order_message_enabled"),
    FIRESTORE_WRITE_ENABLED          ("firestore_write_enabled"),
    SHOW_PROMOTIONS_BANNER      ("show_promotions_banner"),
    VIEW_ITEM_LIST_ENABLED           ("view_item_list_enabled"),
    SEARCH_AUTOCOMPLETE         ("search_autocomplete_enabled"),
    WISHLIST_ENABLED            ("wishlist_enabled"),
    PAYMENT_METHOD_COD_ENABLED       ("payment_method_cod_enabled"),
    REQUEST_REFUND_ENABLED           ("request_refund_enabled"),

    // Business Configuration Values ───────────────────────────────────────────────────────────────
    MAX_SHIPPING_DAYS           ("max_shipping_days"),
}