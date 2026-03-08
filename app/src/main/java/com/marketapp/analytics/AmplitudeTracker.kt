package com.marketapp.analytics

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.android.DeadClickOptions
import com.amplitude.android.InteractionsOptions
import com.amplitude.android.RageClickOptions
import com.amplitude.android.events.Identify
import com.amplitude.android.plugins.SessionReplayPlugin
import com.marketapp.BuildConfig
import com.microsoft.clarity.Clarity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Amplitude Analytics tracker. */
@Singleton
class AmplitudeTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {

    override val name = "Amplitude"

    private lateinit var amplitude: Amplitude

    override suspend fun initialize() {
        amplitude = Amplitude(
            Configuration(
                apiKey  = BuildConfig.AMPLITUDE_API_KEY,
                context = context,
                locationListening = true,
                // Flush pending events when the app goes to background so they aren't
                // lost if the user doesn't reopen the app before the next auto-flush.
                flushEventsOnClose = true,
                // Auto-track session start/end, app install/update/open, deep links, and
                // frustration interactions (rage clicks + dead clicks). screenViews excluded
                // because we fire AnalyticsEvent.ScreenView manually for consistent naming.
                // Requires analytics-android 1.22.0+.
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
                // Treat gaps ≥ 5 minutes as a new session (standard mobile convention).
                minTimeBetweenSessionsMillis = 5 * 60 * 1_000L
            )
        )
        // Session Replay: 20% sampling in debug, 40% in production.
        amplitude.add(SessionReplayPlugin(sampleRate = if (BuildConfig.DEBUG) 0.2 else 0.4))
        // Link Clarity recordings to Amplitude sessions for cross-tool correlation.
        // Clarity.setCustomTag() is safe to call before Clarity finishes initializing.
        Clarity.setCustomTag("amplitude_session_id", amplitude.sessionId.toString())
    }

    override fun track(event: AnalyticsEvent) {
        // Strip list values (items arrays) — Amplitude event properties must be flat key/value
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
            // c_user_id mirrors userId for platforms where "user_id" is reserved.
            set("c_user_id", properties.userId)
            properties.customAttributes.forEach { (k, v) -> set(k, v.toString()) }
        }
        amplitude.identify(identify)
    }

    override fun reset() {
        amplitude.reset()
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        amplitude.configuration.optOut = !enabled
    }

    override fun shutdown() {
        // Flush all queued events before the process exits.
        amplitude.flush()
    }
}