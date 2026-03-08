package com.marketapp.analytics

/**
 * Contract every martech platform must implement.
 *
 * Each SDK gets its own [AnalyticsTracker] implementation.
 * [AnalyticsManager] fans out to all registered trackers.
 */
interface AnalyticsTracker {
    /** Human-readable name shown in debug logs (e.g. "Firebase", "Mixpanel") */
    val name: String

    /** Called once at app start. Use for SDK initialization if not done in Application. */
    suspend fun initialize() {}

    /** Track a discrete event with optional properties. */
    fun track(event: AnalyticsEvent)

    /** Set persistent user attributes (called after login or profile update). */
    fun identify(userId: String, properties: UserProperties) {}

    /** Alias two user identities (anonymous → identified). Supported by Mixpanel, Segment, etc. */
    fun alias(newId: String, oldId: String) {}

    /** Clear user identity on logout. */
    fun reset() {}

    /** Called when app receives a new FCM push token. */
    fun onNewPushToken(token: String) {}

    /**
     * Mark a specific view as sensitive — its content will be masked/blurred in session replay.
     * Use for static PII views (e.g. profile name, email) that are not EditText fields.
     * Default is a no-op; override in trackers that support per-view masking.
     */
    fun maskView(view: android.view.View) {}

    /**
     * Apply the user's analytics consent choice.
     * [enabled] = true → opt in (default); [enabled] = false → opt out.
     * Default is a no-op; override in trackers that expose an opt-out API.
     */
    fun setAnalyticsConsent(enabled: Boolean) {}

    /**
     * Called once after all trackers finish initializing, with a fresh session UUID.
     * Override in trackers that support super-properties or global event context.
     */
    fun onSessionStart(sessionId: String) {}

    /**
     * Flush pending events and release SDK resources.
     *
     * Call from [com.marketapp.MarketApplication] via [AnalyticsManager.shutdown]
     * when the application process is about to exit — for example, from a lifecycle
     * observer that detects when the app moves to background for an extended period,
     * or from a custom [android.app.Activity.onDestroy] in a single-activity app.
     *
     * Default is a no-op; override in SDKs that expose explicit flush/shutdown methods.
     */
    fun shutdown() {}
}

/**
 * Structured user profile sent to all trackers on [AnalyticsTracker.identify].
 * Add fields as your user model grows.
 */
data class UserProperties(
    val userId: String,
    val email: String?             = null,
    val name: String?              = null,
    val firstName: String?         = null,
    val lastName: String?          = null,
    val phone: String?             = null,
    val country: String?           = null,
    val currency: String?          = null,
    val loginMethod: String?       = null,  // "email" | "google"
    val hasPurchased: Boolean?     = null,  // true once the user completes any order
    val orderCount: Int?           = null,  // lifetime order count
    val lifetimeValue: Double?     = null,  // lifetime revenue in the app's base currency (IDR)
    val preferredCategory: String? = null,  // last purchased / most-browsed category
    // Device identifiers — collected at app level and forwarded to analytics platforms.
    // deviceId:      android.provider.Settings.Secure.ANDROID_ID (or SDK-managed ID)
    // appSetId:      com.google.android.gms.appset.AppSetIdClient (per-developer scope)
    // advertisingId: com.google.android.gms.ads.identifier.AdvertisingIdClient (null if LAT on)
    val deviceId: String?          = null,
    val appSetId: String?          = null,
    val advertisingId: String?     = null,
    val customAttributes: Map<String, Any> = emptyMap()
)
