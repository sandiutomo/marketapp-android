package com.marketapp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.marketapp.BuildConfig
import com.marketapp.R
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.databinding.FragmentProfileBinding
import com.marketapp.ui.auth.AuthViewModel
import com.marketapp.ui.auth.LoginBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var analyticsManager: AnalyticsManager

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentProfileBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        // Session replay: mask PII views in Mixpanel + Clarity.
        // PostHog masking is handled by android:tag="ph-no-capture" set in the layout XML.
        analyticsManager.maskView(binding.tvAvatar)
        analyticsManager.maskView(binding.tvUserName)
        analyticsManager.maskView(binding.tvUserEmail)

        // Configure menu rows
        binding.rowOrders.apply {
            ivIcon.setImageResource(R.drawable.ic_bag)
            tvLabel.text = getString(R.string.orders)
        }
        binding.rowWishlist.apply {
            ivIcon.setImageResource(R.drawable.ic_heart)
            tvLabel.text = getString(R.string.wishlist)
        }
        binding.rowAddresses.apply {
            ivIcon.setImageResource(R.drawable.ic_location)
            tvLabel.text = getString(R.string.addresses)
        }
        binding.rowNotifications.apply {
            ivIcon.setImageResource(R.drawable.ic_bell)
            tvLabel.text = getString(R.string.notifications)
        }
        binding.rowSettings.apply {
            ivIcon.setImageResource(R.drawable.ic_settings)
            tvLabel.text = getString(R.string.settings)
        }

        if (BuildConfig.DEBUG) {
            binding.cardDebug.isVisible = true
            binding.btnTriggerPush.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerPushTest)
            }
            binding.btnTriggerBanner.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerBannerTest)
            }
            binding.btnTriggerInapp.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerInAppTest)
            }
            binding.btnTriggerContentCard.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerContentCardTest)
                ContentCardsBottomSheet().show(parentFragmentManager, ContentCardsBottomSheet.TAG)
            }
            binding.btnTriggerExperiment.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerExperimentTest)
            }
        }

        binding.cardUser.setOnClickListener {
            if (authViewModel.authState.value == null) {
                LoginBottomSheet().show(parentFragmentManager, LoginBottomSheet.TAG)
            }
        }

        binding.btnSignOut.setOnClickListener {
            authViewModel.signOut()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { user ->
                    if (user != null) {
                        val initial = user.displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                        binding.tvAvatar.text = initial
                        binding.tvUserName.text = user.displayName?.ifEmpty { getString(R.string.guest_user) } ?: getString(R.string.guest_user)
                        binding.tvUserEmail.text = user.email ?: ""
                        binding.btnSignOut.isVisible = true
                    } else {
                        binding.tvAvatar.text = "G"
                        binding.tvUserName.setText(R.string.guest_user)
                        binding.tvUserEmail.setText(R.string.tap_to_sign_in)
                        binding.btnSignOut.isVisible = false
                    }
                }
            }
        }

    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
