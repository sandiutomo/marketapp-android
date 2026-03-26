package com.marketapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.analytics.ktx.analytics
import com.amplitude.android.engagement.ui.theme.ThemeMode
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.AmplitudeTracker
import com.marketapp.analytics.AppsFlyerTracker
import com.marketapp.config.AmplitudeExperimentFlag
import com.marketapp.config.BrazeFlag
import com.marketapp.config.ExperimentManager
import com.marketapp.config.FeatureFlag
import com.marketapp.config.FeatureGate
import com.marketapp.config.PostHogFlag
import com.marketapp.config.RemoteConfigManager
import com.posthog.PostHog
import com.statsig.androidsdk.Statsig
import com.marketapp.data.preferences.AppPreferences
import com.marketapp.data.repository.CartManager
import com.marketapp.databinding.ActivityMainBinding
import com.marketapp.ui.consent.ConsentBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Tracks whether the Activity is currently in the resumed (visible) state.
    // Used to distinguish warm start (backgrounded → onNewIntent) from
    // hot start (already foregrounded → onNewIntent).
    private var isActivityResumed = false

    @Inject lateinit var analyticsManager: AnalyticsManager
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var experiments: ExperimentManager
    @Inject lateinit var remoteConfig: RemoteConfigManager
    @Inject lateinit var cartManager: CartManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen — must be called before super.onCreate
        installSplashScreen()

        // Hilt injection happens inside super.onCreate — experiments is available after this call.
        super.onCreate(savedInstanceState)

        // Apply dark mode from Amplitude Experiment before inflating the layout so the
        // correct theme is applied on the first draw. Cached variants from the previous
        // session are available immediately; first-launch falls back to control (light).
        val darkVariant = experiments.getAmplitudeVariant(AmplitudeExperimentFlag.DARK_MODE.key)
        val isDark = darkVariant == "treatment"
        if (BuildConfig.DEBUG && savedInstanceState == null) logAllFlags()
        val targetNightMode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }
        AmplitudeTracker.engagement?.setThemeMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupCartBadge()

        if (!appPreferences.consentShown) {
            ConsentBottomSheet().show(supportFragmentManager, ConsentBottomSheet.TAG)
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Returning user who already granted location — re-initialize geofences.
            // requestLocationInitialization() is a no-op after the first call per session.
            analyticsManager.requestLocationInitialization()
        }

        // On cold start, NavHostFragment automatically handles the deep link and builds
        // the correct back stack. We only extract UTM attribution — no manual navigation.
        intent?.let { trackDeepLinkAttribution(it) }
        handlePushIntent(intent, "cold")
        // Amplitude Guides & Surveys — cold-start preview link (ADB or push that opens a fresh process).
        // engagement may not be ready yet; handleLinkIntentWhenReady queues it until initialize() completes.
        if (intent?.data?.scheme == "marketapp-amp-preview") {
            AmplitudeTracker.handleLinkIntentWhenReady(intent)
        }
        setupAppsFlyerDeepLink()

        if (BuildConfig.DEBUG) {
            logFirebaseTokens()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        // Only call after sdkInitialize() — first-launch activation is handled inside
        // FacebookTracker.initialize(). Subsequent foreground transitions are covered here.
        if (FacebookSdk.isInitialized()) {
            AppEventsLogger.activateApp(application)
        }
        // Explicitly register in-app message manager so it is never missed due to the
        // async BrazeActivityLifecycleCallbackListener initialization race condition.
        com.braze.ui.inappmessage.BrazeInAppMessageManager
            .getInstance()
            .registerInAppMessageManager(this)
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        com.braze.ui.inappmessage.BrazeInAppMessageManager
            .getInstance()
            .unregisterInAppMessageManager(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppsFlyerTracker.onDeepLink = null
    }

    // Called when the activity is already running and receives a new deep link intent
    // (works because launchMode="singleTop" in AndroidManifest).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // isActivityResumed is true when the Activity is already visible (hot start),
        // false when it was backgrounded and this intent brings it to foreground (warm start).
        val launchState = if (isActivityResumed) "hot" else "warm"
        handleNativeDeepLink(intent, launchState)
        handlePushIntent(intent, launchState)
        AmplitudeTracker.handleLinkIntentWhenReady(intent)
    }

    /**
     * Extracts UTM attribution from a deep link intent and fires [AnalyticsEvent.CampaignOpened].
     *
     * Called on cold start (onCreate) only — NavHostFragment automatically handles navigation.
     * For warm start (onNewIntent), use [handleNativeDeepLink] which also navigates.
     */
    private fun trackDeepLinkAttribution(intent: Intent) {
        val uri = intent.data ?: return
        val host = uri.host ?: ""
        if (host.endsWith("onelink.me") || host.endsWith("appsflyer.com")) return

        val utmSource   = uri.getQueryParameter("utm_source") ?: return
        val utmMedium   = uri.getQueryParameter("utm_medium")
        val utmCampaign = uri.getQueryParameter("utm_campaign")
        val utmTerm     = uri.getQueryParameter("utm_term")
        val utmContent  = uri.getQueryParameter("utm_content")

        analyticsManager.track(
            AnalyticsEvent.CampaignOpened(
                source   = utmSource,
                medium   = utmMedium,
                campaign = utmCampaign,
                term     = utmTerm,
                content  = utmContent,
                deepLink = uri.toString()
            )
        )
        if (BuildConfig.DEBUG) {
            Log.d("Analytics", "[DeepLink] launch=cold source=native uri=$uri utm_source=$utmSource")
        }
    }

    /**
     * Handles native Android deep links on warm start (onNewIntent).
     *
     * Unlike cold start, NavHostFragment does NOT re-handle the intent when the activity is
     * already running, so we navigate manually using [NavDeepLinkRequest] + [NavOptions].
     * Using popUpTo(startDestination) ensures we never accumulate duplicate destinations
     * in the back stack.
     *
     * Example deep links:
     *   marketapp://profile?utm_source=email&utm_medium=newsletter&utm_campaign=winback
     *   https://YOUR_DOMAIN/profile?utm_source=social&utm_medium=instagram
     */
    private fun handleNativeDeepLink(intent: Intent, launchState: String) {
        val uri = intent.data ?: return
        // Amplitude Guides & Surveys preview links — handled exclusively by handleLinkIntent().
        // Letting routeDeepLink() process them navigates to Home (no matching destination).
        if (uri.scheme == "marketapp-amp-preview") return
        val host = uri.host ?: ""
        if (host.endsWith("onelink.me") || host.endsWith("appsflyer.com")) return

        val utmSource   = uri.getQueryParameter("utm_source")
        val utmMedium   = uri.getQueryParameter("utm_medium")
        val utmCampaign = uri.getQueryParameter("utm_campaign")
        val utmTerm     = uri.getQueryParameter("utm_term")
        val utmContent  = uri.getQueryParameter("utm_content")

        if (utmSource != null) {
            analyticsManager.track(
                AnalyticsEvent.CampaignOpened(
                    source   = utmSource,
                    medium   = utmMedium,
                    campaign = utmCampaign,
                    term     = utmTerm,
                    content  = utmContent,
                    deepLink = uri.toString()
                )
            )
        }
        if (BuildConfig.DEBUG) {
            Log.d("Analytics", "[DeepLink] launch=$launchState source=native uri=$uri utm_source=$utmSource")
        }
        routeDeepLink(uri, launchState)
    }

    /**
     * Handles push notification taps — fires PushTapped to all trackers, then
     * CampaignOpened if UTM params were embedded in the notification data payload.
     *
     * Works for both foreground pushes (showNotification puts extras explicitly)
     * and background/killed pushes from Firebase Console (data keys become extras
     * automatically when the notification is tapped).
     *
     * To attribute a push campaign, include these keys in the FCM data payload:
     *   utm_source, utm_medium, utm_campaign, utm_term (opt), utm_content (opt),
     *   deep_link (opt), campaign_id (opt)
     */
    private fun handlePushIntent(intent: Intent, launchState: String) {
        val campaignId = intent.getStringExtra("campaign_id")
        val deepLink   = intent.getStringExtra("deep_link")

        // No push data present — regular app launch, skip
        if (campaignId == null && deepLink == null) return

        analyticsManager.track(
            AnalyticsEvent.PushTapped(campaignId = campaignId, deepLink = deepLink)
        )

        // Only attribute if the sender explicitly set a UTM source
        val utmSource = intent.getStringExtra("utm_source") ?: return
        // fcm_analytics_label is the server-side FCM fcm_options.analytics_label value;
        // use it as campaign name fallback when utm_campaign isn't set in the payload.
        val fcmLabel = intent.getStringExtra("fcm_analytics_label")
        analyticsManager.track(
            AnalyticsEvent.CampaignOpened(
                source   = utmSource,
                medium   = intent.getStringExtra("utm_medium")   ?: "push",
                campaign = intent.getStringExtra("utm_campaign") ?: fcmLabel,
                term     = intent.getStringExtra("utm_term"),
                content  = intent.getStringExtra("utm_content"),
                deepLink = deepLink
            )
        )
        if (BuildConfig.DEBUG) {
            Log.d("Analytics", "[DeepLink] launch=$launchState source=push utm_source=$utmSource campaign_id=$campaignId deep_link=$deepLink")
        }
        deepLink?.let { routeDeepLink(it.toUri(), launchState) }
    }

    /**
     * Receives AppsFlyer OneLink attribution data and forwards it to all analytics
     * platforms via [AnalyticsEvent.CampaignOpened].
     * Cleared in [onDestroy] to avoid memory leaks.
     */
    private fun setupAppsFlyerDeepLink() {
        AppsFlyerTracker.onDeepLink = { source, medium, campaign, term, content, deepLink ->
            analyticsManager.track(
                AnalyticsEvent.CampaignOpened(
                    source   = source,
                    medium   = medium,
                    campaign = campaign,
                    term     = term,
                    content  = content,
                    deepLink = deepLink
                )
            )
            val launchState = if (isActivityResumed) "hot" else "warm"
            if (BuildConfig.DEBUG) {
                Log.d("Analytics", "[DeepLink] launch=$launchState source=appsflyer utm_source=$source medium=$medium campaign=$campaign deep_link=$deepLink")
            }
            deepLink?.let { runOnUiThread { routeDeepLink(it.toUri(), launchState) } }
        }
    }

    /**
     * Routes a deep link URI from warm-start native links, push notifications, or AppsFlyer.
     *
     * Uses [NavDeepLinkRequest] + [NavOptions] so NavController matches the URI against
     * <deepLink> entries cleanly without rebuilding the full task back stack.
     * popUpTo(startDestination, inclusive=false) + singleTop prevent back-stack accumulation
     * (e.g. tapping Home after a push deep link always returns to home, not a stale copy).
     *
     * Falls back to the global home action when the URI doesn't match any destination,
     * preventing a blank screen after a malformed or outdated deep link.
     */
    private fun routeDeepLink(uri: Uri, launchState: String) {
        if (BuildConfig.DEBUG) {
            Log.d("Analytics", "[DeepLink] routing launch=$launchState uri=$uri")
        }
        binding.root.post {
            val navOptions = NavOptions.Builder()
                // saveState=true matches setupWithNavController's per-tab state management.
                // Without it, repeated deep links corrupt the NavController saved-state map,
                // causing BottomNav tab taps to silently fail after 3+ navigations.
                .setPopUpTo(navController.graph.startDestinationId, false, true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
            try {
                val request = NavDeepLinkRequest.Builder.fromUri(uri).build()
                navController.navigate(request, navOptions)
            } catch (e: Exception) {
                Log.e("Analytics", "[DeepLink] no destination launch=$launchState uri=$uri — falling back to home", e)
                if (BuildConfig.DEBUG) {
                    Toast.makeText(this, "Deep link not found, going home", Toast.LENGTH_SHORT).show()
                }
                navController.navigate(R.id.action_global_homeFragment)
            }
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun logFirebaseTokens() {
        if (deviceIdsLogged) return
        deviceIdsLogged = true

        FirebaseInstallations.getInstance().id.addOnSuccessListener { fid ->
            Log.d("Analytics", "[FCM] Firebase Installation ID: $fid")
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d("Analytics", "[FCM] Registration Token: $token")
        }
        val afid = com.appsflyer.AppsFlyerLib.getInstance().getAppsFlyerUID(this)
        Log.d("Analytics", "[AF]       AppsFlyer UID: $afid")

        // ── Device identifiers (for AppsFlyer test device registration) ───────
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("Analytics", "[DeviceID] Android ID:    $androidId")

        val imeiIsFallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val imei = if (!imeiIsFallback) {
            try {
                @Suppress("DEPRECATION")
                (getSystemService(TELEPHONY_SERVICE) as TelephonyManager).deviceId ?: androidId
            } catch (e: Exception) { androidId }
        } else {
            androidId
        }
        val imeiNote = if (imeiIsFallback) " [fallback: Android ID, IMEI restricted API 29+]" else ""
        Log.d("Analytics", "[DeviceID] IMEI:          $imei$imeiNote")

        // Firebase Installation ID and Analytics App Instance ID — fetched async
        FirebaseInstallations.getInstance().id.addOnSuccessListener { fid ->
            Log.d("Analytics", "[DeviceID] Firebase FID:  $fid")
        }
        // Google Advertising ID, OAID, Firebase App Instance ID, and Amazon Fire AID — fetched off the main thread
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val iid = com.google.android.gms.tasks.Tasks.await(
                    Firebase.analytics.appInstanceId, 3, java.util.concurrent.TimeUnit.SECONDS
                )
                Log.d("Analytics", "[DeviceID] Firebase App Instance ID: $iid")
            }

            val gaid = try {
                AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id ?: "null"
            } catch (e: Exception) { "unavailable (${e.message})" }
            Log.d("Analytics", "[DeviceID] Google AID:    $gaid")

            val oaidIsFallback = gaid.startsWith("unavailable")
            val oaid = if (!oaidIsFallback) gaid else "N/A"
            val oaidNote = if (!oaidIsFallback) " [fallback: Google AID, MSA SDK not integrated]" else ""
            Log.d("Analytics", "[DeviceID] OAID:          $oaid$oaidNote")

            val fireAid = try {
                Settings.Secure.getString(contentResolver, "advertising_id")
                    .takeIf { !it.isNullOrEmpty() } ?: "not a Fire device"
            } catch (e: Exception) { "unavailable (${e.message})" }
            Log.d("Analytics", "[DeviceID] Fire AID:      $fireAid")
        }
    }

    private fun setupCartBadge() {
        lifecycleScope.launch {
            cartManager.cart.collect { cart ->
                val badge = binding.bottomNav.getOrCreateBadge(R.id.cartFragment)
                badge.isVisible = cart.totalItems > 0
                badge.number = cart.totalItems
            }
        }
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        // Show/hide bottom nav based on destination + fire manual screen_view
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideNavOn = setOf(
                R.id.productDetailFragment,
                R.id.checkoutFragment,
                R.id.orderConfirmationFragment,
                R.id.wishlistFragment
            )
            binding.bottomNav.visibility =
                if (destination.id in hideNavOn) android.view.View.GONE
                else android.view.View.VISIBLE
            binding.navDivider.visibility = binding.bottomNav.visibility

            // Manual screen_view — auto reporting is disabled in AndroidManifest.xml
            val screenName = destination.label?.toString() ?: "Unknown"
            val screenClass = when (destination.id) {
                R.id.homeFragment              -> "HomeFragment"
                R.id.searchFragment            -> "SearchFragment"
                R.id.productDetailFragment     -> "ProductDetailFragment"
                R.id.categoryFragment          -> "CategoryFragment"
                R.id.cartFragment              -> "CartFragment"
                R.id.checkoutFragment          -> "CheckoutFragment"
                R.id.orderConfirmationFragment -> "OrderConfirmationFragment"
                R.id.profileFragment           -> "ProfileFragment"
                else                           -> destination.label?.toString() ?: "Unknown"
            }
            analyticsManager.track(AnalyticsEvent.ScreenView(screenName, screenClass))
            val eng = AmplitudeTracker.engagement
            if (BuildConfig.DEBUG) Log.d("AmplitudeSurvey", "screen=$screenClass engagement=${if (eng != null) "ready" else "NULL"}")
            eng?.screen(screenClass)
        }
    }

    override fun onSupportNavigateUp() =
        navController.navigateUp() || super.onSupportNavigateUp()

    private fun logFlag(tag: String, key: String, value: Any?) {
        val label = when (value) {
            true, "true", "ACTIVE", "on" -> "ACTIVE"
            false, "false", "off"        -> "OFF"
            null, "unset"                -> "unset"
            else                         -> value.toString()
        }
        Log.d(tag, "│  ${key.padEnd(42)} $label")
    }

    private fun logAllFlags() {
        val tag = "FlagStatus"

        Log.d(tag, "┌─── Statsig ─────────────────────────────────────────────")
        FeatureGate.entries.forEach { gate ->
            val value = if (Statsig.checkGate(gate.key)) "ACTIVE" else "OFF"
            logFlag(tag, gate.key, value)
        }

        Log.d(tag, "├─── Remote Config ───────────────────────────────────────")
        FeatureFlag.entries.forEach { flag ->
            logFlag(tag, flag.key, remoteConfig.rawValue(flag))
        }

        Log.d(tag, "├─── PostHog ─────────────────────────────────────────────")
        PostHogFlag.entries.forEach { flag ->
            logFlag(tag, flag.key, PostHog.getFeatureFlag(flag.key))
        }

        Log.d(tag, "├─── Braze ───────────────────────────────────────────────")
        val braze = com.braze.Braze.getInstance(this)
        BrazeFlag.entries.forEach { flag ->
            logFlag(tag, flag.key, braze.getFeatureFlag(flag.key)?.enabled)
        }

        Log.d(tag, "├─── Amplitude Experiment ────────────────────────────────")
        AmplitudeExperimentFlag.entries.forEach { flag ->
            logFlag(tag, flag.key, experiments.getAmplitudeVariant(flag.key))
        }

        Log.d(tag, "└─────────────────────────────────────────────────────────")
    }

    companion object {
        // Process-scoped guard — prevents logFirebaseTokens() from re-running if the
        // Activity is recreated mid-session (e.g. theme/config change on first launch).
        private var deviceIdsLogged = false
    }
}