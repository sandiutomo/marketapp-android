package com.marketapp.config

/**
 * All Statsig feature gates used in the app.
 *
 * Adding a new gate here is the only step needed to make it:
 *  - enforced in the relevant tracker's track() via Statsig.checkGate(gate.key)
 *  - visible in the debug panel (DebugInfoBottomSheet)
 *  - logged at startup in StatsigTracker.logGates()
 *
 * SETUP: After adding a gate here, create the matching gate key in Statsig Console
 * (Feature Gates → Create) and set it to ON (Pass% = 100) before deploying.
 * Gates that don't exist in Statsig Console default to false — all traffic blocked.
 *
 * Categories
 *  SDK      — kill switches for individual analytics SDKs
 *  ROLLOUT  — gradual feature rollouts and A/B experiments managed via Statsig
 */
enum class FeatureGate(val key: String, val label: String, val category: Category) {
    // SDK Kill Switches ───────────────────────────────────────────────────────────────────────────
    SDK_AMPLITUDE ("sdk_amplitude_enabled",  "Amplitude SDK",          Category.SDK),
    SDK_FACEBOOK  ("sdk_facebook_enabled",   "Facebook SDK",           Category.SDK),
    SDK_FIREBASE  ("sdk_firebase_enabled",   "Firebase Analytics SDK", Category.SDK),
    SDK_POSTHOG   ("sdk_posthog_enabled",    "PostHog SDK",            Category.SDK),
    SDK_MIXPANEL  ("sdk_mixpanel_enabled",   "Mixpanel SDK",           Category.SDK),
    SDK_APPSFLYER ("sdk_appsflyer_enabled",  "AppsFlyer SDK",          Category.SDK),
    SDK_CLARITY   ("sdk_clarity_enabled",    "Microsoft Clarity SDK",  Category.SDK),
    SDK_BRAZE     ("sdk_braze_enabled",      "Braze SDK",              Category.SDK),
    SDK_ONESIGNAL ("sdk_onesignal_enabled",  "OneSignal SDK",          Category.SDK),

    ;

    enum class Category { SDK, ROLLOUT }
}