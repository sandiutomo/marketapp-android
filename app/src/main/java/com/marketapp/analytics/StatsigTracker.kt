package com.marketapp.analytics

import android.content.Context
import com.marketapp.BuildConfig
import com.statsig.androidsdk.IStatsigCallback
import com.statsig.androidsdk.Statsig
import com.statsig.androidsdk.StatsigOptions
import com.statsig.androidsdk.StatsigUser
import com.statsig.androidsdk.Tier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Statsig analytics tracker. Statsig is primarily a feature-flagging / experimentation SDK. */
@Singleton
class StatsigTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsTracker {
    override val name = "Statsig"

    private var consentEnabled = true
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var flushJob: Job? = null

    /**
     * Statsig.initialize() is a suspend function — called directly here since
     * AnalyticsTracker.initialize() is suspend. No runBlocking needed.
     * AnalyticsManager waits for this coroutine to complete (via initJob) before
     * dispatching any events, so feature gates and experiments are ready on first use.
     */
    override suspend fun initialize() {
        val app = context.applicationContext as android.app.Application
        val options = StatsigOptions().apply {
            // Server-side timeout — mirrors the withTimeoutOrNull guard below
            initTimeoutMs = 5_000L
            // Activity names are noisy in Statsig logs and have no analytical value
            disableCurrentActivityLogging = true
            // Diagnostic pings add network overhead; only useful in development
            disableDiagnosticsLogging = !BuildConfig.DEBUG
            // Automatically refresh experiment/gate config after identify() updates
            // the user context, so gate evaluations reflect the latest user properties
            enableAutoValueUpdate = true
            // Route events to DEVELOPMENT tier in debug builds so test traffic doesn't
            // pollute production experiment metrics or gate evaluation logs
            setTier(if (BuildConfig.DEBUG) Tier.DEVELOPMENT else Tier.PRODUCTION)
        }
        withTimeoutOrNull(5_000L) {
            Statsig.initialize(
                application = app,
                sdkKey      = BuildConfig.STATSIG_CLIENT_KEY,
                user        = buildAnonymousUser(),
                options     = options
            )
        }
    }

    override fun track(event: AnalyticsEvent) {
        if (!consentEnabled) return
        val props = event.toProperties()
        // Revenue takes priority over generic value for the Statsig event numeric field
        val value    = (props["revenue"] as? Double) ?: (props["value"] as? Double) ?: 0.0
        val metadata = props
            .filterValues { it !is List<*> }
            .mapValues   { (_, v) -> v.toString() }
        Statsig.logEvent(event.name, value, metadata)

        // In debug: debounce flush so we get one network call 500ms after the last event
        // burst instead of one network call per event. Statsig auto-flushes on app background
        // in release — no manual flush needed there.
        if (BuildConfig.DEBUG) {
            flushJob?.cancel()
            flushJob = flushScope.launch {
                delay(500L)
                Statsig.flush()
            }
        }
    }

    override fun identify(userId: String, properties: UserProperties) {
        val user = StatsigUser(userID = userId).apply {
            email      = properties.email
            country    = properties.country
            locale     = Locale.getDefault().toLanguageTag()
            appVersion = BuildConfig.VERSION_NAME
            custom = buildMap {
                properties.name?.let              { put("name",               it) }
                properties.loginMethod?.let       { put("login_method",       it) }
                properties.hasPurchased?.let      { put("has_purchased",      it.toString()) }
                properties.orderCount?.let        { put("order_count",        it.toString()) }
                properties.lifetimeValue?.let     { put("lifetime_value",     it.toString()) }
                properties.preferredCategory?.let { put("preferred_category", it) }
                putAll(properties.customAttributes.mapValues { (_, v) -> v.toString() })
            }
            // privateAttributes are evaluated locally for gates/experiments but are
            // never sent to Statsig servers — safe for sensitive fields like email/phone.
            privateAttributes = buildMap {
                properties.email?.let { put("email", it) }
                properties.phone?.let { put("phone", it) }
            }
            // customIDs enable multi-dimensional targeting:
            //   "deviceId"       → device-based gates (stable across sign-ins, targets unauthed users)
            //   "advertisingId"  → cross-app experiments correlated with ad attribution
            // Each key maps to a separate Statsig targeting dimension in the dashboard.
            customIDs = buildMap {
                properties.deviceId?.let       { put("deviceId",       it) }
                properties.advertisingId?.let  { put("advertisingId",  it) }
                properties.appSetId?.let       { put("appSetId",       it) }
            }
        }
        Statsig.updateUserAsync(user, callback = object : IStatsigCallback {
            override fun onStatsigInitialize() {}
            override fun onStatsigUpdateUser() {}
        })
    }

    override fun reset() {
        Statsig.updateUserAsync(buildAnonymousUser(), callback = object : IStatsigCallback {
            override fun onStatsigInitialize() {}
            override fun onStatsigUpdateUser() {}
        })
    }

    override fun setAnalyticsConsent(enabled: Boolean) {
        consentEnabled = enabled
    }

    /**
     * Flush all queued Statsig events and shut down the SDK.
     * Called by [AnalyticsManager.shutdown] when the app is about to terminate.
     * Statsig.shutdown() is synchronous — safe to call from any thread.
     */
    override fun shutdown() {
        flushJob?.cancel()
        Statsig.shutdown()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Anonymous user sent at init and on reset. Locale and app version are always set
     *  so gate/experiment targeting rules can use them even before the user signs in. */
    private fun buildAnonymousUser() = StatsigUser(userID = "").apply {
        locale     = Locale.getDefault().toLanguageTag()
        appVersion = BuildConfig.VERSION_NAME
    }
}