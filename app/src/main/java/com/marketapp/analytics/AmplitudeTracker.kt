package com.marketapp.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.android.engagement.AmplitudeEngagement
import com.amplitude.android.engagement.AmplitudeInitOptions
import com.amplitude.android.engagement.AmplitudeLogLevel
import com.amplitude.android.DeadClickOptions
import com.amplitude.android.InteractionsOptions
import com.amplitude.android.RageClickOptions
import com.amplitude.android.events.Identify
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.common.Logger
import com.amplitude.experiment.Experiment
import com.amplitude.experiment.ExperimentClient
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.ExperimentUser
import com.marketapp.BuildConfig
import com.microsoft.clarity.Clarity
import com.statsig.androidsdk.Statsig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Amplitude Analytics tracker.
 * Standalone: session replay, autocapture, frustration detection.
 */
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

    private val experimentJob = SupervisorJob()
    private val experimentScope = CoroutineScope(experimentJob + Dispatchers.IO)

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
                        AutocaptureOption.ELEMENT_INTERACTIONS,
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

            // Guides & Surveys — plugin approach shares identity with Analytics automatically.
            val eng = AmplitudeEngagement(
                context = context,
                apiKey  = BuildConfig.AMPLITUDE_API_KEY,
                options = AmplitudeInitOptions(
                    logLevel = if (BuildConfig.DEBUG) AmplitudeLogLevel.DEBUG else AmplitudeLogLevel.WARN
                )
            )
            amplitude.add(eng.getPlugin())
            engagement = eng
            // Drain any preview intent that arrived before engagement was ready (cold start).
            pendingLinkIntent?.let { dispatch(eng, it) }
            pendingLinkIntent = null
        }
        if (sessionRecorded) {
            (context as Application).registerActivityLifecycleCallbacks(softwareLayerFix)
        }

        // Amplitude Experiment — use initializeWithAmplitudeAnalytics so this client and
        // ExperimentManager.amplitudeExperiment share the same SDK singleton (keyed by
        // deployment key). Using Experiment.initialize() here creates a SEPARATE client
        // that never receives the variants fetched by ExperimentManager, causing the
        // debug panel to always show "unset".
        val expClient = Experiment.initializeWithAmplitudeAnalytics(
            context.applicationContext as Application,
            BuildConfig.AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY,
            ExperimentConfig()
        )
        experimentScope.launch {
            try {
                expClient.fetch(null).get()
                if (BuildConfig.DEBUG) {
                    com.marketapp.config.AmplitudeExperimentFlag.entries.forEach { flag ->
                        Log.d("Experiments", "[Amplitude] fetch OK: ${flag.key}=${expClient.variant(flag.key)?.value ?: "null"}")
                    }
                }
            } catch (e: Exception) {
                Log.w("Experiments", "[Amplitude] fetch FAILED: ${e.message}")
            }
        }
        experimentClient = expClient
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
        if (!Statsig.checkGate("sdk_amplitude_enabled")) return
        if (event.isBrazeOnly) return
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
        // Guides & Surveys identity is synced automatically via the plugin added in
        // initialize() — amplitude.add(eng.getPlugin()) intercepts every identify call.

        // Re-fetch Experiment variants so flag targeting reflects the signed-in user.
        val expUser = ExperimentUser.Builder().userId(userId).build()
        experimentScope.launch {
            try { experimentClient?.fetch(expUser) } catch (_: Exception) {}
        }
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
        experimentJob.cancel()
    }

    companion object {
        /** Exposed for cross-SDK integrations (e.g. AppsFlyer setAdditionalData). */
        @Volatile var deviceId: String? = null
            private set
        @Volatile var sessionId: Long = -1L
            private set
        /** Exposed for DebugInfoBottomSheet to read Experiment variant values. */
        @Volatile var experimentClient: ExperimentClient? = null
            private set
        /** Exposed so MainActivity can call screen() and handleLinkIntent(). */
        @Volatile var engagement: AmplitudeEngagement? = null
            private set

        // Stores a preview intent received before engagement is ready (cold-start race).
        @Volatile private var pendingLinkIntent: android.content.Intent? = null

        /**
         * Passes [intent] to [AmplitudeEngagement.handleLinkIntent] immediately if
         * [engagement] is ready, or queues it to be delivered once [initialize] completes.
         * Handles both warm start (onNewIntent) and cold start (onCreate) safely.
         */
        fun handleLinkIntentWhenReady(intent: android.content.Intent) {
            val eng = engagement
            if (eng != null) {
                Log.d("AmplitudeSurvey", "handleLinkIntent: immediate scheme=${intent.data?.scheme}")
                dispatch(eng, intent)
            } else {
                Log.d("AmplitudeSurvey", "handleLinkIntent: queued (engagement not ready yet)")
                pendingLinkIntent = intent
            }
        }

        private fun dispatch(eng: com.amplitude.android.engagement.AmplitudeEngagement, intent: android.content.Intent) {
            // Preview links from the Amplitude dashboard must use handlePreviewLinkIntent.
            // handleLinkIntent routes to handleShareLinkIntent (collaboration/share flow) and
            // does NOT trigger the survey preview overlay.
            if (intent.data?.scheme?.startsWith("amp-") == true) {
                eng.handlePreviewLinkIntent(intent)
            } else {
                eng.handleLinkIntent(intent)
            }
        }
    }
}

/**
 * Centralized session-replay status logger.
 *
 * Each SDK tracker calls [record] during its [AnalyticsTracker.initialize].
 * [AnalyticsManager] calls [printSummary] once all trackers are ready so
 * a single, formatted table appears in logcat on every app launch.
 *
 * Example output:
 * ```
 * I/SessionReplay: ┌─── Session Replay Status ─────────────────────────────
 * I/SessionReplay: │  ● ACTIVE    [Amplitude]          debug=100% / prod=40%
 * I/SessionReplay: │  ● ACTIVE    [Mixpanel]            debug= 20% / prod=40%
 * I/SessionReplay: │  ○ INACTIVE  [PostHog]             debug= 20% / prod=40%
 * I/SessionReplay: │  2 / 3 platforms recording this session
 * I/SessionReplay: └───────────────────────────────────────────────────────
 * ```
 *
 * Thread-safe: all three SDK trackers initialize in parallel on [kotlinx.coroutines.Dispatchers.IO].
 */
internal object SessionReplayLogger {

    private const val TAG = "Analytics"

    private data class Entry(
        val platform: String,
        val active: Boolean,
        val debugPct: Int,
        val prodPct: Int
    )

    private val lock    = Any()
    private val entries = mutableListOf<Entry>()

    // Persistent map of every platform's replay decision for the current session.
    // Unlike `entries`, this is never cleared — DebugInfoBottomSheet reads it at any time.
    // Key = platform name, Value = (active, samplePct)
    private val decisions = linkedMapOf<String, Pair<Boolean, Int>>()

    /** Returns a snapshot of all session-replay decisions recorded this session. */
    fun getDecisions(): Map<String, Pair<Boolean, Int>> = synchronized(lock) { decisions.toMap() }

    /**
     * Record and immediately log a single platform's replay decision.
     *
     * @param platform  Human-readable SDK name, e.g. "Amplitude"
     * @param active    Whether this session will be recorded
     * @param debugPct  Configured sample rate for debug builds (0–100)
     * @param prodPct   Configured sample rate for production builds (0–100)
     */
    fun record(platform: String, active: Boolean, debugPct: Int, prodPct: Int) {
        val entry = Entry(platform, active, debugPct, prodPct)
        val samplePct = if (BuildConfig.DEBUG) debugPct else prodPct
        synchronized(lock) {
            entries.add(entry)
            decisions[platform] = Pair(active, samplePct)
        }

        val icon   = if (active) "●" else "○"
        val status = if (active) "ACTIVE  " else "INACTIVE"
        Log.i(TAG, "$icon $status  [$platform]")
    }

    /**
     * Print a summary table once all trackers have finished initializing.
     * Clears the entry list so subsequent [printSummary] calls are no-ops
     * unless new [record] calls are made.
     */
    fun printSummary() {
        val snapshot = synchronized(lock) {
            val copy = entries.toList()
            entries.clear()
            copy
        }
        if (snapshot.isEmpty()) return

        val activeCount = snapshot.count { it.active }
        val col = snapshot.maxOf { it.platform.length }

        Log.i(TAG, "┌─── Session Replay Status ─────────────────────────────")
        snapshot.forEach { e ->
            val icon   = if (e.active) "●" else "○"
            val status = if (e.active) "ACTIVE  " else "INACTIVE"
            val name   = "[${e.platform}]".padEnd(col + 2)
            Log.i(TAG, "│  $icon $status  $name  debug=${e.debugPct.toString().padStart(3)}% / prod=${e.prodPct}%")
        }
        Log.i(TAG, "│  $activeCount / ${snapshot.size} platforms recording this session")
        Log.i(TAG, "└───────────────────────────────────────────────────────")
    }
}
