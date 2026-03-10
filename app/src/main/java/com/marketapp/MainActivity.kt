package com.marketapp

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.google.firebase.messaging.FirebaseMessaging
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.AppsFlyerTracker
import com.marketapp.data.preferences.AppPreferences
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen — must be called before super.onCreate
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        if (!appPreferences.consentShown) {
            ConsentBottomSheet().show(supportFragmentManager, ConsentBottomSheet.TAG)
        }

        // On cold start, NavHostFragment automatically handles the deep link and builds
        // the correct back stack. We only extract UTM attribution — no manual navigation.
        intent?.let { trackDeepLinkAttribution(it) }
        handlePushIntent(intent, "cold")
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
                .setPopUpTo(navController.graph.startDestinationId, false)
                .setLaunchSingleTop(true)
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

        val imei = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                (getSystemService(TELEPHONY_SERVICE) as TelephonyManager).deviceId ?: "null"
            } catch (e: Exception) { "unavailable (${e.message})" }
        } else {
            "unavailable (API ${Build.VERSION.SDK_INT} ≥ 29)"
        }
        Log.d("Analytics", "[DeviceID] IMEI:          $imei")

        // OAID requires the MSA SDK — not integrated in this project
        Log.d("Analytics", "[DeviceID] OAID:          requires MSA SDK")

        // Google Advertising ID and Amazon Fire AID must be fetched off the main thread
        lifecycleScope.launch(Dispatchers.IO) {
            val gaid = try {
                AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id ?: "null"
            } catch (e: Exception) { "unavailable (${e.message})" }
            Log.d("Analytics", "[DeviceID] Google AID:    $gaid")

            val fireAid = try {
                Settings.Secure.getString(contentResolver, "advertising_id")
                    .takeIf { !it.isNullOrEmpty() } ?: "not a Fire device"
            } catch (e: Exception) { "unavailable (${e.message})" }
            Log.d("Analytics", "[DeviceID] Fire AID:      $fireAid")
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
                R.id.orderConfirmationFragment
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
        }
    }

    override fun onSupportNavigateUp() =
        navController.navigateUp() || super.onSupportNavigateUp()
}
