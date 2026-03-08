package com.marketapp.config

import android.content.Context
import android.util.Log
import com.amplitude.experiment.Experiment
import com.amplitude.experiment.ExperimentConfig
import com.braze.models.FeatureFlag as BrazeFeatureFlag
import com.marketapp.BuildConfig
import com.posthog.PostHog
import com.statsig.androidsdk.DynamicConfig
import com.statsig.androidsdk.Layer
import com.statsig.androidsdk.Statsig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified experiments and feature-flag manager.
 *
 * Wraps five platforms so the rest of the codebase has a single, stable interface
 * regardless of which tool owns a given flag:
 *
 * | Provider              | What it's best for                              |
 * |-----------------------|-------------------------------------------------|
 * | Firebase Remote Config| Kill switches, config values, % rollouts        |
 * | Statsig               | Feature gates, A/B experiments                  |
 * | PostHog               | Flags tied to PostHog cohorts / surveys         |
 * | Braze Feature Flags   | Personalised flags per Braze user segment       |
 * | Amplitude Experiment  | Product A/B tests linked to Amplitude metrics   |
 * | Mixpanel              | Experiment exposure events for Mixpanel reports |
 *
 * Usage:
 *   @Inject lateinit var experiments: ExperimentManager
 *
 *   // Firebase Remote Config boolean flag
 *   if (experiments.isEnabled(FeatureFlag.CHECKOUT_ENABLED)) { ... }
 *
 *   // Statsig feature gate
 *   if (experiments.isStatsigGateEnabled("new_checkout_flow")) { ... }
 *
 *   // Amplitude Experiment variant
 *   when (experiments.getAmplitudeVariant("homepage_layout")) {
 *       "grid"  -> showGridLayout()
 *       "list"  -> showListLayout()
 *       else    -> showGridLayout()   // control / fallback
 *   }
 *
 *   // Mixpanel experiment exposure (call once when the variant UI first appears)
 *   experiments.trackMixpanelExposure("homepage_layout", "grid")
 */
@Singleton
class ExperimentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigManager: RemoteConfigManager
) {

    /**
     * Amplitude Experiment client — initialised lazily and linked to the
     * Amplitude Analytics instance so user identity is shared automatically.
     */
    private val amplitudeExperiment by lazy {
        Experiment.initializeWithAmplitudeAnalytics(
            context.applicationContext as android.app.Application,
            BuildConfig.AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY,
            ExperimentConfig()
        )
    }

    /**
     * Call from [com.marketapp.MarketApplication.onCreate], after
     * [com.marketapp.analytics.AnalyticsManager.initialize], so that the
     * Amplitude Analytics instance is ready before the Experiment client starts.
     *
     * [Experiment.initializeWithAmplitudeAnalytics] fetches variants
     * asynchronously; cached values from the previous session are available
     * immediately via [getAmplitudeVariant].
     */
    fun initialize() {
        runCatching { amplitudeExperiment.fetch(null) }
            .onFailure { Log.w(TAG, "Amplitude Experiment fetch failed: ${it.message}") }
    }

    // ── Firebase Remote Config ────────────────────────────────────────────────

    fun isEnabled(flag: FeatureFlag): Boolean = remoteConfigManager.isEnabled(flag)
    fun getString(flag: FeatureFlag): String   = remoteConfigManager.getString(flag)
    fun getDouble(flag: FeatureFlag): Double   = remoteConfigManager.getDouble(flag)
    fun getLong(flag: FeatureFlag):   Long     = remoteConfigManager.getLong(flag)

    // ── Statsig Feature Gates & Experiments ──────────────────────────────────

    /**
     * Returns true if the Statsig feature gate is open for the current user.
     * Falls back to false if Statsig is not yet initialized.
     */
    fun isStatsigGateEnabled(gate: String): Boolean =
        runCatching { Statsig.checkGate(gate) }.getOrElse { false }

    /**
     * Returns the Statsig [DynamicConfig] for the named A/B experiment.
     * Read individual parameters via [DynamicConfig.getString],
     * [DynamicConfig.getBoolean], [DynamicConfig.getDouble], etc.
     *
     * Returns null if Statsig is not initialized or the experiment doesn't exist.
     */
    fun getStatsigExperiment(experimentName: String): DynamicConfig? =
        runCatching { Statsig.getExperiment(experimentName) }.getOrNull()

    /**
     * Returns the Statsig [DynamicConfig] for the named dynamic config object.
     *
     * Use this for server-driven config values that are NOT an A/B experiment
     * (e.g. feature rollout percentages, API endpoint overrides, UI copy).
     * Unlike experiments, dynamic configs don't split users into variant groups.
     *
     * Example:
     * ```kotlin
     * val config = experiments.getStatsigConfig("checkout_config")
     * val maxItems = config?.getInt("max_cart_items", 50) ?: 50
     * ```
     */
    fun getStatsigConfig(configName: String): DynamicConfig? =
        runCatching { Statsig.getConfig(configName) }.getOrNull()

    /**
     * Returns the Statsig [Layer] for the named layer.
     *
     * Layers allow multiple mutually exclusive experiments to share the same
     * parameter namespace — only one experiment within a layer is active for
     * any given user at a time, preventing parameter conflicts.
     *
     * Access typed values via [Layer.getString], [Layer.getBoolean],
     * [Layer.getDouble], [Layer.getInt], [Layer.getDictionary], etc.
     *
     * Example:
     * ```kotlin
     * val layer = experiments.getStatsigLayer("product_page_layer")
     * val showRatings = layer?.getBoolean("show_ratings", true) ?: true
     * val heroStyle   = layer?.getString("hero_style", "carousel") ?: "carousel"
     * ```
     */
    fun getStatsigLayer(layerName: String): Layer? =
        runCatching { Statsig.getLayer(layerName) }.getOrNull()

    /**
     * Returns the Statsig [Layer] without logging an exposure event.
     *
     * Use this for pre-fetching or introspection when you don't want the read
     * to count as a user exposure in Statsig's experiment metrics.
     */
    fun getStatsigLayerNoExposure(layerName: String): Layer? =
        runCatching { Statsig.getLayerWithExposureLoggingDisabled(layerName) }.getOrNull()

    // ── PostHog Feature Flags ─────────────────────────────────────────────────

    /**
     * Returns true if the PostHog feature flag is enabled for the current user.
     * Falls back to false if PostHog hasn't loaded flags yet.
     */
    fun isPostHogFlagEnabled(key: String): Boolean =
        runCatching { PostHog.isFeatureEnabled(key) == true }.getOrElse { false }

    /**
     * Returns the variant value of a PostHog multivariate flag, or null.
     * For boolean flags use [isPostHogFlagEnabled] instead.
     *
     * Calling this method also fires the `$feature_flag_called` exposure event,
     * which is required for PostHog experiment analysis.
     */
    fun getPostHogFlag(key: String): Any? =
        runCatching { PostHog.getFeatureFlag(key) }.getOrNull()

    /**
     * Returns the JSON payload attached to a PostHog flag/experiment variant, or null.
     *
     * Use this when the experiment variant carries server-driven config
     * (e.g. button color, copy, layout parameters).
     *
     * **Important**: `getFeatureFlagPayload` does NOT fire `$feature_flag_called` on its own.
     * Always pair with [getPostHogFlag] or [isPostHogFlagEnabled] so the exposure event
     * is recorded and PostHog can attribute the user to the correct experiment variant.
     *
     * Example:
     * ```kotlin
     * val variant = experiments.getPostHogFlag("homepage_layout")      // fires exposure
     * val payload = experiments.getPostHogFlagPayload("homepage_layout") // reads config
     * ```
     */
    fun getPostHogFlagPayload(key: String): Any? =
        runCatching { PostHog.getFeatureFlagPayload(key) }.getOrNull()

    /**
     * Re-fetches PostHog feature flags from the server.
     * Useful after [com.marketapp.analytics.AnalyticsManager.identify] so that
     * flags targeting specific user properties update in the same session.
     */
    fun reloadPostHogFlags(onComplete: (() -> Unit)? = null) {
        runCatching { PostHog.reloadFeatureFlags { onComplete?.invoke() } }
    }

    // ── Braze Feature Flags ───────────────────────────────────────────────────
    /**
     * Returns the Braze [BrazeFeatureFlag] for the given ID, or null if not found.
     * Access typed properties via [BrazeFeatureFlag.getStringProperty],
     * [BrazeFeatureFlag.getBooleanProperty], [BrazeFeatureFlag.getNumberProperty].
     */
    fun getBrazeFeatureFlag(flagId: String): BrazeFeatureFlag? =
        runCatching {
            com.braze.Braze.getInstance(context).getFeatureFlag(flagId)
        }.getOrNull()

    /** Returns true if the Braze feature flag is enabled for the current user. */
    fun isBrazeFlagEnabled(flagId: String): Boolean =
        getBrazeFeatureFlag(flagId)?.enabled == true

    /** Requests a fresh set of Braze feature flags from the server. */
    fun refreshBrazeFeatureFlags() {
        runCatching { com.braze.Braze.getInstance(context).refreshFeatureFlags() }
    }

    // ── Amplitude Experiment ──────────────────────────────────────────────────

    /**
     * Returns the variant key string assigned to the current user for the given
     * flag or experiment, or null if they are in the control group or the
     * experiment is inactive.
     *
     * Example: `"control"` | `"treatment"` | `"grid"` | `"list"`
     */
    fun getAmplitudeVariant(flagKey: String): String? =
        runCatching {
            amplitudeExperiment.variant(flagKey)?.value?.takeIf { it.isNotEmpty() }
        }.getOrNull()

    /**
     * Returns the JSON payload attached to the Amplitude variant, or null.
     * Use this for server-driven config values bundled into the variant definition.
     */
    fun getAmplitudeVariantPayload(flagKey: String): Any? =
        runCatching { amplitudeExperiment.variant(flagKey)?.payload }.getOrNull()

    // ── Mixpanel Experiment Exposure ──────────────────────────────────────────

    /**
     * Logs an `$experiment_started` event to Mixpanel.
     *
     * Mixpanel does not have a native A/B testing SDK — instead, record an
     * exposure event exactly once per session when the variant UI first appears.
     * Mixpanel will let you build a cohort from this event and compare goal
     * metrics between variants in Reports → Experiments.
     *
     * Call site example:
     *   experiments.trackMixpanelExposure("homepage_layout", "grid")
     */
    fun trackMixpanelExposure(experimentName: String, variantName: String) {
        runCatching {
            com.mixpanel.android.mpmetrics.MixpanelAPI
                .getInstance(context, BuildConfig.MIXPANEL_TOKEN, false)
                .track(
                    "\$experiment_started",
                    JSONObject().apply {
                        put("Experiment name", experimentName)
                        put("Variant name",    variantName)
                    }
                )
        }.onFailure { Log.w(TAG, "Mixpanel exposure track failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "ExperimentManager"
    }
}