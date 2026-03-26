package com.marketapp.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.braze.Braze
import com.braze.events.BannersUpdatedEvent
import com.braze.events.IEventSubscriber
import androidx.fragment.app.setFragmentResultListener
import com.google.android.material.snackbar.Snackbar
import com.marketapp.BuildConfig
import com.marketapp.R
import com.marketapp.analytics.AmplitudeTracker
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.config.ALL_BANNER_PLACEMENTS
import com.marketapp.config.BannerDismissManager
import com.marketapp.config.FeatureFlag
import com.marketapp.config.RemoteConfigManager
import com.marketapp.databinding.FragmentProfileBinding
import com.marketapp.ui.auth.AuthViewModel
import com.marketapp.ui.auth.LoginBottomSheet
import com.posthog.PostHog
import com.statsig.androidsdk.Statsig
import com.statsig.androidsdk.StatsigUser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var analyticsManager: AnalyticsManager
    @Inject lateinit var remoteConfig: RemoteConfigManager

    private val authViewModel: AuthViewModel by activityViewModels()
    private var bannerSubscriber: IEventSubscriber<BannersUpdatedEvent>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentProfileBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        // Braze profile banner — shown when profile_banner placement has active content.
        // Triggered in Braze by the trigger_for_banner custom event (fired by debug button below).
        val braze = Braze.getInstance(requireContext())
        bannerSubscriber = IEventSubscriber { event ->
            val banner = event.getBanner("profile_banner")
            val html = banner?.html
            val hasContent = banner != null && !html.isNullOrEmpty() && !banner.isControl && !banner.isExpired()
            val show = hasContent && BannerDismissManager.shouldShow("profile_banner", html!!)
            activity?.runOnUiThread {
                if (_binding != null) {
                    binding.bannerContainerProfile.visibility = if (show) View.VISIBLE else View.GONE
                }
            }
        }
        braze.subscribeToBannersUpdates(bannerSubscriber!!)
        braze.requestBannersRefresh(ALL_BANNER_PLACEMENTS)
        binding.btnCloseBannerProfile.setOnClickListener {
            BannerDismissManager.dismiss("profile_banner")
            binding.bannerContainerProfile.visibility = View.GONE
        }

        // Auto-open content cards when arriving via marketapp://content-cards deep link
        // (e.g. tapping a Braze "content card ready" push notification).
        if (requireActivity().intent?.data?.host == "content-cards") {
            ContentCardsBottomSheet().show(parentFragmentManager, ContentCardsBottomSheet.TAG)
            requireActivity().intent.data = null  // consume so rotation doesn't reopen
        }

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
            root.isVisible = remoteConfig.isEnabled(FeatureFlag.WISHLIST_ENABLED)
            root.setOnClickListener { findNavController().navigate(R.id.action_profile_to_wishlist) }
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
                BannerDismissManager.reset()
                Braze.getInstance(requireContext()).requestBannersRefresh(ALL_BANNER_PLACEMENTS)
            }
            binding.btnTriggerInapp.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerInAppTest)
            }
            binding.btnTriggerContentCard.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerContentCardTest)
                ContentCardsBottomSheet().show(parentFragmentManager, ContentCardsBottomSheet.TAG)
            }
            binding.btnClearBrazeCache.setOnClickListener {
                BannerDismissManager.reset()
                Braze.getInstance(requireContext()).requestBannersRefresh(ALL_BANNER_PLACEMENTS)
                Snackbar.make(binding.root, "Braze cache cleared", Snackbar.LENGTH_SHORT).show()
            }
            binding.btnTriggerGuide.setOnClickListener {
                analyticsManager.track(AnalyticsEvent.TriggerAmplitudeGuide)
            }
            binding.btnPreviewSurvey.setOnClickListener {
                AmplitudeTracker.handleLinkIntentWhenReady(
                    Intent(Intent.ACTION_VIEW, Uri.parse("amp-26647b0254432df8://gs/preview/41053"))
                )
            }
            binding.btnPreviewGuide.setOnClickListener {
                AmplitudeTracker.handleLinkIntentWhenReady(
                    Intent(Intent.ACTION_VIEW, Uri.parse("amp-26647b0254432df8://gs/preview/41685"))
                )
            }
            binding.cardForceRefresh.setOnClickListener {
                binding.cardForceRefresh.isEnabled = false
                binding.cardForceRefresh.alpha = 0.5f
                binding.tvForceRefreshLabel.text = "Refreshing…"

                val userId = authViewModel.authState.value?.uid ?: "anonymous"
                val braze = Braze.getInstance(requireContext())

                remoteConfig.forceRefresh()
                PostHog.reloadFeatureFlags()
                braze.refreshFeatureFlags()
                braze.requestBannersRefresh(ALL_BANNER_PLACEMENTS)
                braze.requestContentCardsRefresh()

                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { Statsig.updateUser(StatsigUser(userID = userId)) }
                    runCatching { AmplitudeTracker.experimentClient?.fetch(null)?.get() }
                    withContext(Dispatchers.Main) {
                        binding.cardForceRefresh.isEnabled = true
                        binding.cardForceRefresh.alpha = 1.0f
                        binding.tvForceRefreshLabel.setText(R.string.debug_force_refresh_flags)
                        Snackbar.make(
                            binding.root,
                            "All flags refreshed — navigate away and back to apply",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
            binding.cardDebugInfo.setOnClickListener {
                DebugInfoBottomSheet().show(parentFragmentManager, DebugInfoBottomSheet.TAG)
            }
        }

        setFragmentResultListener(EditNameBottomSheet.REQUEST_KEY) { _, bundle ->
            val newName = bundle.getString(EditNameBottomSheet.RESULT_NAME) ?: return@setFragmentResultListener
            val initial = newName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
            binding.tvAvatar.text = initial
            binding.tvUserName.text = newName.ifEmpty { getString(R.string.guest_user) }
        }

        binding.cardUser.setOnClickListener {
            val user = authViewModel.authState.value
            if (user == null) {
                LoginBottomSheet().show(parentFragmentManager, LoginBottomSheet.TAG)
            } else {
                EditNameBottomSheet.newInstance(user.displayName ?: "")
                    .show(parentFragmentManager, EditNameBottomSheet.TAG)
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

    override fun onDestroyView() {
        super.onDestroyView()
        authViewModel.resetUpdateNameState()
        bannerSubscriber?.let {
            Braze.getInstance(requireContext()).removeSingleSubscription(it, BannersUpdatedEvent::class.java)
        }
        bannerSubscriber = null
        _binding = null
    }
}
