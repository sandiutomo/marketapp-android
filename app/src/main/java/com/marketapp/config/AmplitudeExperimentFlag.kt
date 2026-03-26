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
 */
enum class AmplitudeExperimentFlag(val key: String, val label: String) {
    DARK_MODE ("dark-mode", "Dark Mode"),
    HOME_LAYOUT ("home_layout", "Home Layout"),
    ;
}