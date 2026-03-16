package com.marketapp.config

/**
 * PostHog feature flags used in the app.
 *
 * Adding a new flag here is the only step needed to make it:
 *  - readable via PostHog.getFeatureFlag(flag.key)
 *  - visible in the debug panel (DebugInfoBottomSheet)
 *
 * SETUP: Create the matching flag key in PostHog Console (Feature Flags → New)
 * before deploying. Flags not yet created return null (shown as "unset" in the panel).
 *
 * PostHog flags support boolean, string, and JSON payloads.
 * The debug panel shows the raw value returned by PostHog.getFeatureFlag().
 */
enum class PostHogFlag(val key: String, val label: String) {
    // Multivariate flag — controls the greeting copy on the home screen.
    // control / null : time-based ("Good morning" / "Good afternoon" / "Good evening")
    // "welcome-back" : randomly picks "Welcome back!" or "Hi again!"
    // "lets-shop"    : randomly picks one of 4 action-oriented messages
    HOME_GREETING_VARIANT ("home-greeting-variant", "Home Greeting Variant"),
    ;
}