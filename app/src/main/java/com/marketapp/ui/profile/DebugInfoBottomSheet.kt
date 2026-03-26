package com.marketapp.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.braze.Braze
import com.braze.events.BannersUpdatedEvent
import com.marketapp.BuildConfig
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.google.firebase.auth.FirebaseAuth
import com.braze.events.ContentCardsUpdatedEvent
import com.braze.events.IEventSubscriber
import com.braze.models.cards.CaptionedImageCard
import com.braze.models.cards.ShortNewsCard
import com.braze.models.cards.TextAnnouncementCard
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.messaging.FirebaseMessaging
import com.marketapp.R
import com.marketapp.analytics.AmplitudeTracker
import com.marketapp.config.ALL_BANNER_PLACEMENTS
import com.marketapp.config.AmplitudeExperimentFlag
import com.marketapp.config.ExperimentManager
import com.marketapp.config.BrazeFlag
import com.marketapp.config.FeatureFlag
import com.marketapp.config.FeatureGate
import com.marketapp.config.PostHogFlag
import com.marketapp.analytics.SessionReplayLogger
import com.marketapp.config.RemoteConfigManager
import com.marketapp.data.repository.UserStatsRepository
import com.marketapp.databinding.BottomSheetDebugInfoBinding
import com.marketapp.databinding.ItemDebugRowBinding
import com.onesignal.OneSignal
import com.posthog.PostHog
import com.statsig.androidsdk.Statsig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DebugInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDebugInfoBinding? = null
    private val binding get() = _binding!!
    private var bannerSubscriber: IEventSubscriber<BannersUpdatedEvent>? = null
    private var contentCardsSubscriber: IEventSubscriber<ContentCardsUpdatedEvent>? = null

    @Inject lateinit var remoteConfig: RemoteConfigManager
    @Inject lateinit var experiments: ExperimentManager
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var userStats: UserStatsRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = BottomSheetDebugInfoBinding.inflate(inflater, container, false)
        .also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateUser()
        populateTokens()
        populateSessionReplay()
        populate()
        populateBrazeCampaigns()
        populateBrazePushStatus()
        populateOneSignal()

        val braze = Braze.getInstance(requireContext())
        bannerSubscriber = IEventSubscriber { event ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                binding.containerBanners.removeAllViews()
                ALL_BANNER_PLACEMENTS.forEach { placement ->
                    val banner = event.getBanner(placement)
                    val hasContent = banner != null && !banner.html.isNullOrEmpty() && !banner.isControl && !banner.isExpired()
                    val label = if (hasContent) banner!!.trackingId.take(12) + "…" else "empty"
                    addRow(binding.containerBanners, placement, label, hasContent, monospace = true)
                }
            }
        }
        braze.subscribeToBannersUpdates(bannerSubscriber!!)
        braze.requestBannersRefresh(ALL_BANNER_PLACEMENTS)

        binding.btnRefresh.setOnClickListener {
            experiments.refreshBrazeFeatureFlags()
            experiments.reloadPostHogFlags {
                if (_binding != null) {
                    clearAndRepopulate()
                }
            }
            AmplitudeTracker.experimentClient?.fetch(null)
            braze.requestContentCardsRefresh()
            braze.requestBannersRefresh(ALL_BANNER_PLACEMENTS)
            binding.root.postDelayed({
                if (_binding != null) clearAndRepopulate()
            }, 2_000L)
        }
    }

    private fun clearAndRepopulate() {
        binding.containerUser.removeAllViews()
        binding.containerSessionReplay.removeAllViews()
        binding.containerSdks.removeAllViews()
        binding.containerRollouts.removeAllViews()
        binding.containerFlags.removeAllViews()
        binding.containerPosthog.removeAllViews()
        binding.containerBraze.removeAllViews()
        binding.containerAmplitude.removeAllViews()
        binding.containerContentCards.removeAllViews()
        binding.containerBanners.removeAllViews()
        binding.containerBrazePush.removeAllViews()
        binding.containerOnesignal.removeAllViews()
        binding.containerBrazeCanvas.removeAllViews()
        binding.containerMixpanel.removeAllViews()
        populateUser()
        populateSessionReplay()
        populate()
        populateBrazeCampaigns()
        populateBrazePushStatus()
        populateOneSignal()
    }

    private fun populate() {
        // Statsig — SDK kill switches
        val appMeta = requireContext().packageManager
            .getApplicationInfo(requireContext().packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData
        FeatureGate.entries
            .filter { it.category == FeatureGate.Category.SDK }
            .forEach { gate ->
                val active = Statsig.checkGate(gate.key)
                val label = when {
                    !active -> "Inactive"
                    gate == FeatureGate.SDK_FIREBASE -> {
                        val sgtmOn = appMeta?.getBoolean("google_analytics_sgtm_upload_enabled", false) ?: false
                        if (sgtmOn) "Active (sGTM)" else "Active (Direct SDK)"
                    }
                    else -> "Active"
                }
                addRow(binding.containerSdks, gate.label, label, active, monospace = false)
            }

        // Statsig — feature rollouts
        FeatureGate.entries
            .filter { it.category == FeatureGate.Category.ROLLOUT }
            .forEach { gate ->
                val active = Statsig.checkGate(gate.key)
                addRow(binding.containerRollouts, gate.label, if (active) "ACTIVE" else "OFF", active, monospace = false)
            }

        // Firebase Remote Config
        FeatureFlag.entries.forEach { flag ->
            val raw = remoteConfig.rawValue(flag)
            val positive = when {
                raw.equals("true",  ignoreCase = true) -> true
                raw.equals("false", ignoreCase = true) -> false
                else                                   -> null
            }
            addRow(binding.containerFlags, flag.key, raw, positive, monospace = true)
        }

        // PostHog feature flags
        val postHogFlags = PostHogFlag.entries
        if (postHogFlags.isEmpty()) {
            addRow(binding.containerPosthog, "No flags configured", "—", null, monospace = false)
        } else {
            postHogFlags.forEach { flag ->
                val raw     = PostHog.getFeatureFlag(flag.key)?.toString() ?: "unset"
                val enabled = PostHog.isFeatureEnabled(flag.key)
                addRow(binding.containerPosthog, flag.label, raw, enabled, monospace = false)
            }
        }

        // Braze feature flags
        val brazeFlags = BrazeFlag.entries
        if (brazeFlags.isEmpty()) {
            addRow(binding.containerBraze, "No flags configured", "—", null, monospace = false)
        } else {
            val braze = com.braze.Braze.getInstance(requireContext())
            brazeFlags.forEach { flag ->
                val featureFlag = braze.getFeatureFlag(flag.key)
                val enabled     = featureFlag?.enabled
                val display     = enabled?.toString() ?: "unset"
                addRow(binding.containerBraze, flag.label, display, enabled, monospace = false)
            }
        }

        // Amplitude Experiment feature flags
        val amplitudeFlags = AmplitudeExperimentFlag.entries
        if (amplitudeFlags.isEmpty()) {
            addRow(binding.containerAmplitude, "No flags configured", "—", null, monospace = false)
        } else {
            amplitudeFlags.forEach { flag ->
                val value   = AmplitudeTracker.experimentClient?.variant(flag.key)?.value
                val raw     = value ?: "unset"
                val positive: Boolean? = when {
                    value == null -> null
                    value.equals("off",   ignoreCase = true) ||
                    value.equals("false", ignoreCase = true) -> false
                    else -> true
                }
                addRow(binding.containerAmplitude, flag.label, raw, positive, monospace = false)
            }
        }

        // Mixpanel — no client-side flag query API in SDK 8.x
        addRow(binding.containerMixpanel, "Flags / A/B / Cohorts", "server-side only", null, monospace = false)
    }

    private fun populateUser() {
        val uid = auth.currentUser?.uid
        addRow(binding.containerUser, "user_id",
            uid ?: "null", positive = if (uid != null) null else false, monospace = true)

        val country = java.util.Locale.getDefault().country.ifEmpty { "—" }
        addRow(binding.containerUser, "country", country, positive = null, monospace = false)

        val ltv = userStats.lifetimeValue
        val bucket = when {
            ltv <= 0            -> "none"
            ltv < 1_000_000     -> "low"
            ltv < 5_000_000     -> "mid"
            ltv < 20_000_000    -> "high"
            else                -> "vip"
        }
        val ltvPositive: Boolean? = if (ltv <= 0) null else true
        addRow(binding.containerUser, "ltv_bucket", bucket, positive = ltvPositive, monospace = false)

        val mp = MixpanelAPI.getInstance(requireContext(), BuildConfig.MIXPANEL_TOKEN, true)
        addRow(binding.containerUser, "mp_distinct_id", (mp.distinctId ?: "—").take(24), null, monospace = true)
        val optedOut = mp.hasOptedOutTracking()
        addRow(binding.containerUser, "mixpanel_tracking", if (optedOut) "opted out" else "active", positive = if (optedOut) false else true, monospace = false)
    }

    private fun populateSessionReplay() {
        val decisions = SessionReplayLogger.getDecisions()
        if (decisions.isEmpty()) {
            addRow(binding.containerSessionReplay, "No data yet", "—", null, monospace = false)
            return
        }
        decisions.forEach { (platform, pair) ->
            val active    = pair.first
            val samplePct = pair.second
            val value = if (active) "recording  ($samplePct%)" else "inactive"
            addRow(binding.containerSessionReplay, platform, value, active, monospace = false)
        }
    }

    private fun populateBrazeCampaigns() {
        val braze = Braze.getInstance(requireContext())

        // Content Cards — use event subscriber (no synchronous cache getter in SDK).
        addRow(binding.containerContentCards, "requesting…", "—", null, monospace = false)
        addRow(binding.containerBrazeCanvas, "requesting…", "—", null, monospace = false)
        contentCardsSubscriber = IEventSubscriber { event ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                binding.containerContentCards.removeAllViews()
                binding.containerBrazeCanvas.removeAllViews()
                val cards = event.allCards
                if (cards.isEmpty()) {
                    addRow(binding.containerContentCards, "No cached cards", "—", null, monospace = false)
                    addRow(binding.containerBrazeCanvas, "No canvas cards", "—", null, monospace = false)
                } else {
                    cards.forEach { card ->
                        val title = when (card) {
                            is CaptionedImageCard   -> card.title
                            is ShortNewsCard        -> card.title
                            is TextAnnouncementCard -> card.title
                            else                    -> null
                        }
                        addRow(binding.containerContentCards, title ?: "Untitled", "active", true, monospace = false)
                    }
                    val canvasCards = cards.filter { it.extras.containsKey("canvas_id") }
                    if (canvasCards.isEmpty()) {
                        addRow(binding.containerBrazeCanvas, "No canvas cards", "add canvas_id to extras", null, monospace = false)
                    } else {
                        canvasCards.forEach { card ->
                            val title = when (card) {
                                is CaptionedImageCard   -> card.title
                                is ShortNewsCard        -> card.title
                                is TextAnnouncementCard -> card.title
                                else                    -> null
                            }
                            val canvasId = card.extras["canvas_id"]!!
                            addRow(binding.containerBrazeCanvas, title ?: "Untitled", canvasId.take(16), true, monospace = true)
                        }
                    }
                }
            }
        }
        braze.subscribeToContentCardsUpdates(contentCardsSubscriber!!)
        braze.requestContentCardsRefresh()

        // Banner placements — rows are populated/updated by the BannersUpdatedEvent subscriber.
        // Show placeholders until the subscriber fires.
        ALL_BANNER_PLACEMENTS.forEach { placement ->
            addRow(binding.containerBanners, placement, "loading…", null, monospace = true)
        }
    }

    private fun populateBrazePushStatus() {
        val pushEnabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        val brazeToken = Braze.getInstance(requireContext()).registeredPushToken
        addRow(binding.containerBrazePush, "Notifications",
            if (pushEnabled) "granted" else "denied", pushEnabled, monospace = false)
        addRow(binding.containerBrazePush, "Push token",
            if (brazeToken != null) "registered" else "none", brazeToken != null, monospace = false)
    }

    private fun populateOneSignal() {
        val subscription = OneSignal.User.pushSubscription
        val optedIn = subscription.optedIn
        val subId = subscription.id.takeIf { it.isNotEmpty() } ?: "—"
        // FCM token registered with OneSignal (separate from Braze's token)
        val token = subscription.token.takeIf { it.isNotEmpty() } ?: "—"
        addRow(binding.containerOnesignal, "Push opted in", optedIn.toString(), optedIn, monospace = false)
        addRow(binding.containerOnesignal, "Subscription ID", subId.take(20), null, monospace = true)
        addRow(binding.containerOnesignal, "Push token", token.take(20), null, monospace = true)
      }

    private fun populateTokens() {
        addRow(binding.containerTokens, "FCM Token", "loading…", null, monospace = false)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (_binding == null) return@addOnSuccessListener
            binding.containerTokens.removeAllViews()
            val row = ItemDebugRowBinding.inflate(layoutInflater, binding.containerTokens, false)
            row.tvKey.text = "FCM Token"
            row.tvValue.text = "${token.take(16)}…"
            row.tvValue.setTextColor(requireContext().getColor(R.color.text_tertiary))
            row.root.setOnClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", token))
                Toast.makeText(requireContext(), "FCM token copied", Toast.LENGTH_SHORT).show()
            }
            binding.containerTokens.addView(row.root)
        }
    }

    private fun addRow(
        container: LinearLayout,
        key: String,
        value: String,
        positive: Boolean?,
        monospace: Boolean
    ) {
        val row = ItemDebugRowBinding.inflate(layoutInflater, container, false)
        row.tvKey.text = key
        row.tvValue.text = value
        if (monospace) row.tvKey.typeface = Typeface.MONOSPACE
        row.tvValue.setTextColor(
            requireContext().getColor(
                when (positive) {
                    true  -> R.color.primary
                    false -> R.color.error
                    null  -> R.color.text_tertiary
                }
            )
        )
        container.addView(row.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val braze = Braze.getInstance(requireContext())
        bannerSubscriber?.let { braze.removeSingleSubscription(it, BannersUpdatedEvent::class.java) }
        contentCardsSubscriber?.let { braze.removeSingleSubscription(it, ContentCardsUpdatedEvent::class.java) }
        bannerSubscriber = null
        contentCardsSubscriber = null
        _binding = null
    }

    companion object {
        const val TAG = "DebugInfoBottomSheet"
    }
}