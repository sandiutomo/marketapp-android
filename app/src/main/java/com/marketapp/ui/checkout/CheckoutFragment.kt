package com.marketapp.ui.checkout

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.braze.Braze
import com.braze.events.FeatureFlagsUpdatedEvent
import com.braze.events.IEventSubscriber
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.marketapp.R
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.config.BrazeFlag
import com.marketapp.config.ExperimentManager
import com.marketapp.config.FeatureFlag
import com.marketapp.config.PostHogFlag
import com.marketapp.databinding.FragmentCheckoutBinding
import com.marketapp.databinding.FragmentCheckoutPaymentBinding
import com.marketapp.databinding.FragmentOrderConfirmationBinding
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ── Step 1: Shipping ──────────────────────────────────────────────────────────

@AndroidEntryPoint
class CheckoutFragment : Fragment() {

    private var _binding: FragmentCheckoutBinding? = null
    private val binding get() = _binding!!

    // Shared across all checkout steps so perf trace + state spans the full funnel.
    private val viewModel: CheckoutViewModel by activityViewModels()

    @Inject lateinit var experiments: ExperimentManager
    @Inject lateinit var analyticsManager: AnalyticsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentCheckoutBinding.inflate(inflater, container, false).also { _binding = it }.root

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // Mask PII shipping fields in all session replay SDKs.
        analyticsManager.maskView(binding.etName)
        analyticsManager.maskView(binding.etAddress)
        analyticsManager.maskView(binding.etCity)
        analyticsManager.maskView(binding.etZip)
        if (experiments.isPostHogFlagEnabled(PostHogFlag.CHECKOUT_PROGRESS_BAR.key)) {
            binding.checkoutProgress.visibility = View.VISIBLE
        }

        // Prefill shipping address with dummy test data
        binding.etAddress.setText("Jl. Testing Dummy App")
        binding.etCity.setText("Jakarta")
        binding.etZip.setText("10620")

        // GA4: begin_checkout — fires on Step 1 entry.
        viewModel.onCheckoutStarted()

        binding.btnContinue.setOnClickListener {
            val name    = binding.etName.text?.toString()?.trim()    ?: ""
            val address = binding.etAddress.text?.toString()?.trim() ?: ""
            val city    = binding.etCity.text?.toString()?.trim()    ?: ""
            val zip     = binding.etZip.text?.toString()?.trim()     ?: ""

            if (name.isEmpty() || address.isEmpty() || city.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // GA4: add_shipping_info
            viewModel.onShippingConfirmed(name, address, city, zip)
            findNavController().navigate(R.id.action_checkout_to_payment)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Step 2: Payment ───────────────────────────────────────────────────────────

@AndroidEntryPoint
class PaymentFragment : Fragment() {

    private var _binding: FragmentCheckoutPaymentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CheckoutViewModel by activityViewModels()

    @Inject lateinit var experiments: ExperimentManager

    // Held in a field so Braze's WeakReference doesn't GC it before the callback fires.
    private var featureFlagsSubscriber: IEventSubscriber<FeatureFlagsUpdatedEvent>? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentCheckoutPaymentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        if (experiments.isPostHogFlagEnabled(PostHogFlag.CHECKOUT_PROGRESS_BAR.key)) {
            binding.checkoutProgress.visibility = View.VISIBLE
        }

        // COD kill switch — hidden by default when flag is off (e.g. region without COD).
        binding.rbCod.isVisible = experiments.isEnabled(FeatureFlag.PAYMENT_METHOD_COD_ENABLED)

        // Shipping estimate from Firebase Remote Config — changes per user segment / A/B test.
        val maxDays = experiments.getLong(FeatureFlag.MAX_SHIPPING_DAYS)
        binding.tvShippingDays.text = "🚚 Arrives in up to $maxDays days"

        // Override CTA text from Braze Feature Flag — targeted per user segment.
        // Braze flags are cached at session start; subscribe to updates so a flag
        // created after the last launch is picked up without requiring an app restart.
        applyBrazeCtaFlag()
        val braze = Braze.getInstance(requireContext())
        featureFlagsSubscriber = IEventSubscriber { _ ->
            Log.d(TAG, "FeatureFlagsUpdatedEvent received — re-applying CTA flag")
            activity?.runOnUiThread { if (_binding != null) applyBrazeCtaFlag() }
        }
        braze.subscribeToFeatureFlagsUpdates(featureFlagsSubscriber!!)
        Log.d(TAG, "Requesting Braze feature flags refresh…")
        braze.refreshFeatureFlags()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cart.collectLatest { cart ->
                    val shippingFee = viewModel.shippingFeeIdr()
                    val isFree = shippingFee == 0.0
                    binding.tvSubtotal.text = cart.formattedTotal
                    binding.tvShipping.text = viewModel.formattedShippingFee()
                    binding.tvShipping.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (isFree) R.color.success else R.color.text_primary
                        )
                    )
                    binding.tvTotal.text = viewModel.formattedGrandTotal()
                }
            }
        }

        binding.placeOrderBtn.setOnClickListener {
            val method = when (binding.rgPayment.checkedRadioButtonId) {
                binding.rbCard.id      -> "card"
                binding.rbPaypal.id   -> "paypal"
                binding.rbGooglepay.id -> "google_pay"
                binding.rbCod.id       -> "cod"
                else                   -> "card"
            }
            // GA4: add_payment_info — purchase fires on the confirmation screen.
            val order = viewModel.placeOrder(method)
            findNavController().navigate(
                PaymentFragmentDirections.actionPaymentToConfirmation(order.id)
            )
        }
    }

    private fun applyBrazeCtaFlag() {
        val flag = experiments.getBrazeFeatureFlag(BrazeFlag.CHECKOUT_CTA.key)
        val label = flag?.getStringProperty("label")
        Log.d(TAG, "applyBrazeCtaFlag: flag=${flag?.id ?: "null"} enabled=${flag?.enabled} label=$label")
        if (flag?.enabled == true) {
            label?.let { binding.placeOrderBtn.text = it }
            // Premium gold styling — targets VIP / high-value user segment via Braze.
            binding.placeOrderBtn.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.gold_premium)
            )
            binding.placeOrderBtn.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.white)
            )
            binding.placeOrderBtn.letterSpacing = 0.08f
        }
    }

    companion object {
        private const val TAG = "PaymentFragment"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        featureFlagsSubscriber?.let {
            Braze.getInstance(requireContext())
                .removeSingleSubscription(it, FeatureFlagsUpdatedEvent::class.java)
        }
        featureFlagsSubscriber = null
        _binding = null
    }
}

// ── Order Confirmation ────────────────────────────────────────────────────────

@AndroidEntryPoint
class OrderConfirmationFragment : Fragment() {

    private var _binding: FragmentOrderConfirmationBinding? = null
    private val binding get() = _binding!!
    private val args: OrderConfirmationFragmentArgs by navArgs()
    private val viewModel: CheckoutViewModel by activityViewModels()

    @Inject lateinit var analyticsManager: AnalyticsManager
    @Inject lateinit var remoteConfig: com.marketapp.config.RemoteConfigManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentOrderConfirmationBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvOrderId.text = args.orderId
        binding.btnRefund.isVisible = remoteConfig.isEnabled(FeatureFlag.REQUEST_REFUND_ENABLED)
        // Order ID is PII — mask it in every session replay SDK.
        // PostHog: adds "ph-no-capture" to contentDescription.
        // Clarity: calls Clarity.maskView(). Mixpanel: already masked globally via
        // AutoMaskedView.Text in its session replay config.
        analyticsManager.maskView(binding.tvOrderId)
        // GA4: purchase — fires here once the order is confirmed and visible to the user.
        viewModel.onOrderConfirmed()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.aiOrderMessage.collect { msg ->
                    if (msg.isNotEmpty()) {
                        binding.tvAiMessage.text = msg
                        binding.tvAiMessage.isVisible = true
                    }
                }
            }
        }

        binding.btnContinueShopping.setOnClickListener {
            findNavController().navigate(
                OrderConfirmationFragmentDirections.actionGlobalHomeFragment()
            )
        }

        binding.btnRefund.setOnClickListener {
            viewModel.onRefundRequested()
            Toast.makeText(requireContext(), "Refund request submitted", Toast.LENGTH_SHORT).show()
            binding.btnRefund.isEnabled = false
            binding.btnRefund.text = "Refund Requested"
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}