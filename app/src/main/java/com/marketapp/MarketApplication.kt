package com.marketapp

import android.app.Application
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.config.ExperimentManager
import com.marketapp.config.RemoteConfigManager
import com.marketapp.data.preferences.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MarketApplication : Application() {

    @Inject lateinit var analyticsManager: AnalyticsManager
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var remoteConfigManager: RemoteConfigManager
    @Inject lateinit var experimentManager: ExperimentManager

    override fun onCreate() {
        super.onCreate()

        // Initialize all registered analytics trackers
        analyticsManager.initialize()

        // Restore the user's saved analytics consent so every tracker (including
        // Facebook's setAutoLogAppEventsEnabled) reflects the correct state on
        // every launch — not just after the ConsentBottomSheet is shown again.
        analyticsManager.setAnalyticsConsent(appPreferences.analyticsEnabled)

        // Fetch fresh Remote Config values in the background; applied on next launch.
        remoteConfigManager.fetchAndActivate()

        // Fetch Amplitude Experiment variants in the background (fire-and-forget).
        // Must run after analyticsManager.initialize() so the Amplitude Analytics
        // instance is ready for ExperimentManager to link against.
        experimentManager.initialize()
    }
}
