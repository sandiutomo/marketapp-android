package com.marketapp.ui.consent

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.data.preferences.AppPreferences
import com.marketapp.databinding.BottomSheetConsentBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ConsentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetConsentBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var analyticsManager: AnalyticsManager

    // Result launcher for POST_NOTIFICATIONS (Android 13+). Saves the actual
    // grant result rather than the toggle state, since the user can deny the
    // system dialog even if they turned the toggle on.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        appPreferences.notificationsEnabled = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must not be cancellable — user has to make an explicit choice.
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BottomSheetConsentBinding.inflate(inflater, container, false)
        .also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnContinue.setOnClickListener { onContinue() }

        // Open privacy policy in browser.
        binding.tvPrivacy.setOnClickListener {
            runCatching {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(PRIVACY_POLICY_URL)
                    )
                )
            }
        }
    }

    private fun onContinue() {
        val analyticsOn   = binding.switchAnalytics.isChecked
        val notifyToggled = binding.switchNotifications.isChecked

        // Persist analytics choice and apply to all SDK trackers immediately.
        appPreferences.analyticsEnabled = analyticsOn
        analyticsManager.setAnalyticsConsent(analyticsOn)

        // Mark consent as seen so this sheet never appears again.
        appPreferences.consentShown = true

        // Track that the user completed onboarding/consent.
        analyticsManager.track(AnalyticsEvent.OnboardingCompleted("consent"))

        // Handle notification permission.
        when {
            !notifyToggled -> {
                appPreferences.notificationsEnabled = false
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // On Android 13+ POST_NOTIFICATIONS is a runtime permission.
                // The launcher callback stores the actual grant result.
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                // Below API 33 the permission is granted at install time.
                appPreferences.notificationsEnabled = true
            }
        }

        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ConsentBottomSheet"
        private const val PRIVACY_POLICY_URL = "https://YOUR_DOMAIN/privacy"
    }
}