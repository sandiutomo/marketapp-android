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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.messaging.FirebaseMessaging
import com.marketapp.R
import com.marketapp.analytics.AmplitudeTracker
import com.marketapp.config.AmplitudeExperimentFlag
import com.marketapp.config.ExperimentManager
import com.marketapp.config.BrazeFlag
import com.marketapp.config.FeatureFlag
import com.marketapp.config.FeatureGate
import com.marketapp.config.PostHogFlag
import com.marketapp.config.RemoteConfigManager
import com.marketapp.databinding.BottomSheetDebugInfoBinding
import com.marketapp.databinding.ItemDebugRowBinding
import com.posthog.PostHog
import com.statsig.androidsdk.Statsig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DebugInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDebugInfoBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var remoteConfig: RemoteConfigManager
    @Inject lateinit var experiments: ExperimentManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = BottomSheetDebugInfoBinding.inflate(inflater, container, false)
        .also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populate()
        populateTokens()
        binding.btnRefresh.setOnClickListener {
            // Re-fetch live values before re-populating so variants aren't stale.
            experiments.refreshBrazeFeatureFlags()
            experiments.reloadPostHogFlags {
                if (_binding != null) {
                    binding.containerSdks.removeAllViews()
                    binding.containerRollouts.removeAllViews()
                    binding.containerFlags.removeAllViews()
                    binding.containerPosthog.removeAllViews()
                    binding.containerBraze.removeAllViews()
                    binding.containerAmplitude.removeAllViews()
                    populate()
                }
            }
            // Amplitude Experiment fetch is fire-and-forget; repopulate after a short delay.
            AmplitudeTracker.experimentClient?.fetch(null)
            binding.root.postDelayed({
                if (_binding != null) {
                    binding.containerSdks.removeAllViews()
                    binding.containerRollouts.removeAllViews()
                    binding.containerFlags.removeAllViews()
                    binding.containerPosthog.removeAllViews()
                    binding.containerBraze.removeAllViews()
                    binding.containerAmplitude.removeAllViews()
                    populate()
                }
            }, 2_000L)
        }
    }

    private fun populate() {
        // Statsig — SDK kill switches
        FeatureGate.entries
            .filter { it.category == FeatureGate.Category.SDK }
            .forEach { gate ->
                val active = Statsig.checkGate(gate.key)
                addRow(binding.containerSdks, gate.label, if (active) "ACTIVE" else "OFF", active, monospace = false)
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object {
        const val TAG = "DebugInfoBottomSheet"
    }
}