package com.marketapp.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.android.DeadClickOptions
import com.amplitude.android.InteractionsOptions
import com.amplitude.android.RageClickOptions
import com.amplitude.android.events.Identify
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.common.Logger
import com.marketapp.BuildConfig
import com.microsoft.clarity.Clarity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Amplitude Analytics tracker. */
@Singleton
class AmplitudeTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "Amplitude"

    private lateinit var amplitude: Amplitude

    // Pre-decided at initialize() so the same value is used in track() when
    // the SessionReplayPlugin is lazily attached after the session starts.
    private var sessionRecorded = false
    private var sessionReplayAttached = false

    override suspend fun initialize() {
        sessionRecorded = Math.random() < 0.4
        SessionReplayLogger.record("Amplitude", sessionRecorded, debugPct = 40, prodPct = 40)

        withContext(Dispatchers.Main) {
            amplitude = Amplitude(
                Configuration(
                    apiKey  = BuildConfig.AMPLITUDE_API_KEY,
                    context = context,
                    locationListening = true,
                    // Disable remote config in debug so the locally-set SessionReplayPlugin
                    // sampleRate = 1.0 is not overridden by the dashboard value (0.1).
                    enableAutocaptureRemoteConfig = !BuildConfig.DEBUG,
                    // Flush pending events when the app goes to background so they aren't
                    // lost if the user doesn't reopen the app before the next auto-flush.
                    flushEventsOnClose = true,
                    // Auto-track session start/end, app install/update/open, deep links, and
                    // frustration interactions (rage clicks + dead clicks).
                    autocapture = setOf(
                        AutocaptureOption.SESSIONS,
                        AutocaptureOption.APP_LIFECYCLES,
                        AutocaptureOption.DEEP_LINKS,
                        AutocaptureOption.FRUSTRATION_INTERACTIONS
                    ),
                    // Frustration interaction configuration:
                    //   RageClick  — ≥4 rapid taps on the same element within 1 second
                    //   DeadClick  — tap on an interactive element with no visible reaction within 3 s
                    interactionsOptions = InteractionsOptions(
                        rageClick = RageClickOptions(enabled = true),
                        deadClick = DeadClickOptions(enabled = true)
                    ),
                    minTimeBetweenSessionsMillis = 5 * 60 * 1_000L
                )
            )
            if (BuildConfig.DEBUG) {
                amplitude.logger.logMode = Logger.LogMode.DEBUG
            }
            deviceId = amplitude.getDeviceId()
        }
        if (sessionRecorded) {
            (context as Application).registerActivityLifecycleCallbacks(softwareLayerFix)
        }
    }

    /**
     * Traverses the view tree of every resumed Activity and downgrades any
     * [ImageView] that is not already in software-layer mode.
     *
     * Tradeoff: software layers draw on the CPU, which is slightly slower than the
     * default GPU path. In practice, for normal product-card sizes this is imperceptible.
     * If you notice jank on a heavy grid screen, call [View.setLayerType] back to
     * [View.LAYER_TYPE_NONE] on specific views after they are bound.
     */
    private val softwareLayerFix = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) =
            forceSoftwareLayer(activity.window.decorView)

        override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
        override fun onActivityStarted(a: Activity)             = Unit
        override fun onActivityPaused(a: Activity)              = Unit
        override fun onActivityStopped(a: Activity)             = Unit
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
        override fun onActivityDestroyed(a: Activity)           = Unit
    }

    private fun forceSoftwareLayer(view: View) {
        if (view is ImageView && view.layerType != View.LAYER_TYPE_SOFTWARE) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) forceSoftwareLayer(view.getChildAt(i))
        }
    }

    override fun track(event: AnalyticsEvent) {
        // Attach the SessionReplayPlugin the first time we have a valid session.
        // By the time the first event is tracked, the Activity is on screen and
        // Amplitude has established a session (sessionId is a positive timestamp).
        if (amplitude.sessionId > 0) sessionId = amplitude.sessionId
        if (!sessionReplayAttached && sessionRecorded && amplitude.sessionId > 0) {
            sessionReplayAttached = true
            amplitude.add(SessionReplayPlugin(sampleRate = 1.0))
            // Update Clarity now that we have the real session ID.
            Clarity.setCustomTag("amplitude_session_id", amplitude.sessionId.toString())
        }

        val props = event.toProperties().filterValues { it !is List<*> }

        when (event) {
            // Revenue events: log one Revenue object per line item so Amplitude can
            // break down LTV by product. The order-level event is still tracked for
            // funnel analysis (checkout → order_placed conversion rate).
            is AnalyticsEvent.OrderPlaced -> {
                for (item in event.items) {
                    val revenue = com.amplitude.android.events.Revenue().apply {
                        productId   = item.itemId
                        price       = item.price
                        quantity    = item.quantity
                        revenueType = "purchase"
                        receiptSig  = event.orderId
                    }
                    amplitude.revenue(revenue)
                }
                amplitude.track(event.name, props)
                amplitude.flush()
            }
            is AnalyticsEvent.OrderRefunded -> {
                val revenue = com.amplitude.android.events.Revenue().apply {
                    price       = -event.value
                    quantity    = 1
                    revenueType = "refund"
                    receiptSig  = event.orderId
                }
                amplitude.revenue(revenue)
                amplitude.track(event.name, props)
            }
            else -> {
                if (props.isEmpty()) amplitude.track(event.name)
                else amplitude.track(event.name, props)
            }
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        amplitude.setUserId(userId)
        // Keep Clarity in sync when user ID changes (e.g. after sign-in / sign-up).
        Clarity.setCustomTag("amplitude_session_id", amplitude.sessionId.toString())

        val identify = Identify().apply {
            properties.email?.let             { set("email",              it) }
            properties.name?.let              { set("name",               it) }
            properties.firstName?.let         { set("first_name",         it) }
            properties.lastName?.let          { set("last_name",          it) }
            properties.country?.let           { set("country",            it) }
            properties.loginMethod?.let {
                set("login_method", it)
                // setOnce preserves the original acquisition channel — subsequent
                // sign-ins via other methods (e.g. Google after email) don't overwrite it.
                setOnce("first_login_method", it)
            }
            properties.hasPurchased?.let      { set("has_purchased",      it) }
            // add() is an atomic server-side increment — more accurate than set() when
            // multiple devices or concurrent sessions update the same counter.
            properties.orderCount?.let        { add("order_count",        it.toDouble()) }
            properties.lifetimeValue?.let     { set("lifetime_value",     it) }
            properties.preferredCategory?.let { set("preferred_category", it) }
            // Device identifiers — set once so the original install device is preserved.
            properties.deviceId?.let          { setOnce("c_device_id",    it) }
            properties.appSetId?.let          { setOnce("app_set_id",     it) }
            properties.advertisingId?.let     { setOnce("advertising_id", it) }
            set("c_user_id", properties.userId)
            properties.customAttributes.forEach { (k, v) -> set(k, v.toString()) }
        }
        amplitude.identify(identify)
    }

    override fun maskView(view: View) {
        // Static companion — no plugin instance needed.
        SessionReplayPlugin.mask(view)
    }

    override fun reset() {
        sessionReplayAttached = false
        amplitude.reset()
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        amplitude.configuration.optOut = !enabled
    }

    override fun shutdown() {
        amplitude.flush()
    }

    companion object {
        /** Exposed for cross-SDK integrations (e.g. AppsFlyer setAdditionalData). */
        @Volatile var deviceId: String? = null
            private set
        @Volatile var sessionId: Long = -1L
            private set
    }
}
