package com.marketapp.di

import com.marketapp.analytics.AmplitudeTracker
import com.marketapp.analytics.AnalyticsTracker
import com.marketapp.analytics.AppsFlyerTracker
import com.marketapp.analytics.BrazeTracker
import com.marketapp.analytics.MicrosoftClarityTracker
import com.marketapp.analytics.FacebookTracker
import com.marketapp.analytics.FirebaseTracker
import com.marketapp.analytics.MixpanelTracker
import com.marketapp.analytics.OneSignalTracker
import com.marketapp.analytics.PostHogTracker
import com.marketapp.analytics.SegmentTracker
import com.marketapp.analytics.StatsigTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module that builds the Set<AnalyticsTracker> injected into [AnalyticsManager].
 *
 * TO ADD A NEW PLATFORM:
 *   1. Implement [AnalyticsTracker] (see Trackers.kt stubs)
 *   2. Add a @Binds @IntoSet binding here
 *   Done — no other wiring required.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    // ── Active ─────────────────────────────────────────────────────
    @Binds @IntoSet
    abstract fun bindFirebaseTracker(tracker: FirebaseTracker): AnalyticsTracker

    // ── Braze ──────────────────────────────────────────────────────
    @Binds @IntoSet
    abstract fun bindBrazeTracker(tracker: BrazeTracker): AnalyticsTracker

    // ── OneSignal ──────────────────────────────────────────────────
    @Binds @IntoSet
    abstract fun bindOneSignalTracker(tracker: OneSignalTracker): AnalyticsTracker

    // ── AppsFlyer ───────────────────────────────────────────────────
    @Binds @IntoSet
    abstract fun bindAppsFlyerTracker(tracker: AppsFlyerTracker): AnalyticsTracker

    // ── PostHog ─────────────────────────────────────────────────────
    @Binds @IntoSet
    abstract fun bindPostHogTracker(tracker: PostHogTracker): AnalyticsTracker

    // ── Amplitude ────────────────────────────────────────────────────
    // Session Replay plugin can be enabled in AmplitudeTracker.initialize() when ready
    @Binds @IntoSet
    abstract fun bindAmplitudeTracker(tracker: AmplitudeTracker): AnalyticsTracker

    // ── Segment ───────────────────────────────────────────────────────────────
    @Binds @IntoSet
    abstract fun bindSegmentTracker(tracker: SegmentTracker): AnalyticsTracker

    // ── Statsig ───────────────────────────────────────────────────────────────
    @Binds @IntoSet
    abstract fun bindStatsigTracker(tracker: StatsigTracker): AnalyticsTracker

    // ── Mixpanel ──────────────────────────────────────────────────────────────
    // Includes native Session Replay — configure sampling in Mixpanel dashboard
    @Binds @IntoSet
    abstract fun bindMixpanelTracker(tracker: MixpanelTracker): AnalyticsTracker

    // ── Microsoft Clarity ─────────────────────────────────────────
    // Session Replay + Heatmaps active on init. Mask PII via Clarity.maskView()
    // Clarity → Firebase custom dimension + Crashlytics key + Mixpanel super-prop
    // are wired in ClarityTracker.initialize() via setOnSessionStartedCallback.
    @Binds @IntoSet
    abstract fun bindClarityTracker(tracker: MicrosoftClarityTracker): AnalyticsTracker

    // ── Meta (Facebook) App Events ────────────────────────────────────────────
    // E-commerce events for Ads Manager + Meta Pixel conversion optimization.
    // Auto-logging disabled; all events sent explicitly. Requires FACEBOOK_CLIENT_TOKEN
    // in local.properties.
    @Binds @IntoSet
    abstract fun bindFacebookTracker(tracker: FacebookTracker): AnalyticsTracker

}
