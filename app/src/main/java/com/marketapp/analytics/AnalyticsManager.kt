package com.marketapp.analytics

import android.util.Log
import com.marketapp.BuildConfig
import com.posthog.PostHog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor(
    private val trackers: Set<@JvmSuppressWildcards AnalyticsTracker>
) {
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initJob: Job? = null

    // ── Session ID ─────────────────────────────────────────────────────────────
    val sessionId: String = UUID.randomUUID().toString()

    // ── Event deduplication ───────────────────────────────────────────────────
    private data class EventKey(val name: String, val props: Map<String, Any>)
    private val dedupeMutex  = Mutex()
    private var lastEventKey: EventKey? = null
    private var lastEventTime            = 0L

    /**
     * Called once from [com.marketapp.MarketApplication.onCreate]. Returns immediately.
     *
     * All trackers initialize in parallel on [Dispatchers.IO] (SDK init is I/O-heavy:
     * SharedPreferences, SQLite, network setup). The [initJob] completes only after every
     * tracker is ready, guaranteeing no events are dispatched before initialization.
     */
    fun initialize() {
        initJob = scope.launch {
            withContext(Dispatchers.IO) {
                trackers.map { tracker ->
                    async {
                        runCatching { tracker.initialize() }
                            .onFailure { log("INIT FAILED [${tracker.name}]", it) }
                            .onSuccess  { log("Initialized [${tracker.name}]") }
                    }
                }.awaitAll()
            }
            // Broadcast session ID after ALL trackers are ready.
            trackers.map { tracker ->
                async {
                    runCatching { tracker.onSessionStart(sessionId) }
                        .onFailure { log("SESSION_ID FAILED [${tracker.name}]", it) }
                }
            }.awaitAll()
        }
    }

    fun track(event: AnalyticsEvent) {
        scope.launch {
            initJob?.join()

            // Compute once — reused for dedup key and debug logging.
            val props = event.toProperties()

            // 200 ms deduplication guard
            val key = EventKey(event.name, props)
            val now = System.currentTimeMillis()
            val isDuplicate = dedupeMutex.withLock {
                val dup = key == lastEventKey && now - lastEventTime < 200L
                if (!dup) { lastEventKey = key; lastEventTime = now }
                dup
            }
            if (isDuplicate) {
                if (BuildConfig.ENABLE_ANALYTICS_LOGGING) log("DEDUP_SKIP → ${event.name}")
                return@launch
            }

            if (BuildConfig.ENABLE_ANALYTICS_LOGGING) log("TRACK → ${event.name} | props=$props")

            trackers.map { tracker ->
                async {
                    runCatching { tracker.track(event) }
                        .onFailure { log("TRACK FAILED [${tracker.name}] ${event.name}", it) }
                }
            }.awaitAll()
        }
    }

    fun identify(userId: String, properties: UserProperties) {
        scope.launch {
            initJob?.join()
            if (BuildConfig.ENABLE_ANALYTICS_LOGGING) log("IDENTIFY → $userId")
            trackers.map { tracker ->
                async {
                    runCatching { tracker.identify(userId, properties) }
                        .onFailure { log("IDENTIFY FAILED [${tracker.name}]", it) }
                }
            }.awaitAll()
        }
    }

    fun alias(newId: String, oldId: String) {
        scope.launch {
            initJob?.join()
            trackers.map { tracker ->
                async {
                    runCatching { tracker.alias(newId, oldId) }
                        .onFailure { log("ALIAS FAILED [${tracker.name}]", it) }
                }
            }.awaitAll()
        }
    }

    fun reset() {
        scope.launch {
            initJob?.join()
            trackers.map { tracker ->
                async {
                    runCatching { tracker.reset() }
                        .onFailure { log("RESET FAILED [${tracker.name}]", it) }
                }
            }.awaitAll()
        }
    }

    fun onNewPushToken(token: String) {
        scope.launch {
            initJob?.join()
            trackers.map { tracker ->
                async {
                    runCatching { tracker.onNewPushToken(token) }
                        .onFailure { log("PUSH TOKEN FAILED [${tracker.name}]", it) }
                }
            }.awaitAll()
        }
    }

    fun trackError(screen: String, code: String, message: String) =
        track(AnalyticsEvent.ErrorOccurred(screen, code, message))

    /**
     * Flush all pending events and release SDK resources.
     *
     * Call this when the app is about to die — e.g. from a [androidx.lifecycle.ProcessLifecycleOwner]
     * observer (ON_STOP after the last activity stops) or from the Application's onTerminate().
     * Runs fire-and-forget; tracker failures are logged but do not block shutdown.
     */
    fun shutdown() {
        scope.launch {
            initJob?.join()
            trackers.forEach { tracker ->
                runCatching { tracker.shutdown() }
                    .onFailure { log("SHUTDOWN FAILED [${tracker.name}]", it) }
            }
        }
    }

    fun setAnalyticsConsent(enabled: Boolean) {
        scope.launch {
            initJob?.join()
            trackers.map { tracker ->
                async {
                    runCatching { tracker.setAnalyticsConsent(enabled) }
                        .onFailure { log("CONSENT FAILED [${tracker.name}]", it) }
                }
            }.awaitAll()
        }
    }

    /**
     * Masks a view in session replay SDKs. Must run on the calling (UI) thread because
     * PostHog and Clarity both modify [android.view.View.contentDescription] / SDK state
     * that is bound to the view hierarchy. No async dispatch.
     */
    fun maskView(view: android.view.View) {
        trackers.forEach { tracker ->
            runCatching { tracker.maskView(view) }
                .onFailure { log("MASK_VIEW FAILED [${tracker.name}]", it) }
        }
    }

    fun captureLog(level: String, message: String, tag: String? = null) {
        scope.launch {
            initJob?.join()
            val props = buildMap<String, Any> {
                put("level", level)
                put("message", message)
                if (tag != null) put("attributes", mapOf("tag" to tag))
            }
            runCatching { PostHog.capture(event = "\$log", properties = props) }
        }
    }

    private fun log(msg: String, throwable: Throwable? = null) {
        if (BuildConfig.ENABLE_ANALYTICS_LOGGING) {
            if (throwable != null) {
                Log.e(TAG, msg, throwable)
                runCatching {
                    PostHog.capture(
                        event = "\$log",
                        properties = mapOf(
                            "level"   to "error",
                            "message" to "$msg: ${throwable.message}",
                            "attributes" to mapOf("tag" to TAG)
                        )
                    )
                }
            } else {
                Log.d(TAG, msg)
            }
        }
    }

    companion object {
        private const val TAG = "Analytics"
    }
}