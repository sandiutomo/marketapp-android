package com.marketapp.ui.checkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.marketapp.R
import com.marketapp.databinding.FragmentCheckoutBinding
import com.marketapp.databinding.FragmentCheckoutPaymentBinding
import com.marketapp.databinding.FragmentOrderConfirmationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ── Step 1: Shipping ──────────────────────────────────────────────────────────

@AndroidEntryPoint
class CheckoutFragment : Fragment() {

    private var _binding: FragmentCheckoutBinding? = null
    private val binding get() = _binding!!

    // Shared across all checkout steps so perf trace + state spans the full funnel.
    private val viewModel: CheckoutViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentCheckoutBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentCheckoutPaymentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cart.collectLatest { cart ->
                    binding.tvSubtotal.text = cart.formattedTotal
                    binding.tvTotal.text    = cart.formattedTotal
                }
            }
        }

        binding.placeOrderBtn.setOnClickListener {
            val method = when (binding.rgPayment.checkedRadioButtonId) {
                binding.rbCard.id      -> "card"
                binding.rbPaypal.id   -> "paypal"
                binding.rbGooglepay.id -> "google_pay"
                else                   -> "card"
            }
            // GA4: add_payment_info — purchase fires on the confirmation screen.
            val order = viewModel.placeOrder(method)
            findNavController().navigate(
                PaymentFragmentDirections.actionPaymentToConfirmation(order.id)
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Order Confirmation ────────────────────────────────────────────────────────

@AndroidEntryPoint
class OrderConfirmationFragment : Fragment() {

    private var _binding: FragmentOrderConfirmationBinding? = null
    private val binding get() = _binding!!
    private val args: OrderConfirmationFragmentArgs by navArgs()
    private val viewModel: CheckoutViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentOrderConfirmationBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvOrderId.text = args.orderId
        // GA4: purchase — fires here once the order is confirmed and visible to the user.
        viewModel.onOrderConfirmed()
        binding.btnContinueShopping.setOnClickListener {
            findNavController().navigate(
                OrderConfirmationFragmentDirections.actionGlobalHomeFragment()
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}