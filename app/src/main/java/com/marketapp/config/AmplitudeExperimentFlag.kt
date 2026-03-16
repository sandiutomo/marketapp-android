package com.marketapp.config

/**
 * Amplitude Experiment flags used in the app.
 *
 * Adding a new flag here is the only step needed to make it:
 *  - readable via AmplitudeTracker.experimentClient?.variant(flag.key)
 *  - visible in the debug panel (DebugInfoBottomSheet)
 *
 * SETUP: Create the matching flag key in Amplitude Experiment Console
 * (Experiment → Feature Flags → Create Flag) before deploying.
 * Flags not created in Amplitude return a null variant (shown as "unset").
 *
 * Note: Amplitude Experiment is a separate SDK from Amplitude Analytics.
 * Variants are fetched once on app start and re-fetched after identify().
 */
enum class AmplitudeExperimentFlag(val key: String, val label: String) {
    // A/B test: control = light theme, treatment = dark theme.
    // Applied in MainActivity before layout inflation.
    // Measure: 7-day retention + session duration per variant.
    DARK_MODE ("dark-mode", "Dark Mode"),
    ;
}