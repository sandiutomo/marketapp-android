package com.marketapp.ui.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.os.Bundle
import android.view.View
import coil.load
import com.braze.Braze
import com.braze.events.BannersUpdatedEvent
import com.braze.events.IEventSubscriber
import com.marketapp.config.ALL_BANNER_PLACEMENTS
import com.marketapp.config.BannerDismissManager
import com.marketapp.data.model.CartItem
import com.marketapp.databinding.FragmentCartBinding
import com.marketapp.databinding.ItemCartBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ── Fragment ──────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CartViewModel by viewModels()
    private lateinit var adapter: CartAdapter
    private var bannerSubscriber: IEventSubscriber<BannersUpdatedEvent>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentCartBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        observeCart()
        binding.btnCheckout.setOnClickListener {
            findNavController().navigate(
                CartFragmentDirections.actionCartToCheckout()
            )
        }
        viewModel.onCartViewed()

        val braze = Braze.getInstance(requireContext())
        bannerSubscriber = IEventSubscriber { event ->
            val banner = event.getBanner("cart_banner")
            val html = banner?.html
            val hasContent = banner != null && !html.isNullOrEmpty() && !banner.isControl && !banner.isExpired()
            val show = hasContent && BannerDismissManager.shouldShow("cart_banner", html!!)
            activity?.runOnUiThread {
                if (_binding != null) {
                    binding.cartBannerContainer.visibility = if (show) View.VISIBLE else View.GONE
                }
            }
        }
        braze.subscribeToBannersUpdates(bannerSubscriber!!)
        braze.requestBannersRefresh(ALL_BANNER_PLACEMENTS)
        binding.btnCloseCartBanner.setOnClickListener {
            BannerDismissManager.dismiss("cart_banner")
            binding.cartBannerContainer.visibility = View.GONE
        }
    }

    private fun setupRecycler() {
        adapter = CartAdapter(
            onIncrease = { viewModel.increaseQty(it) },
            onDecrease = { viewModel.decreaseQty(it) },
            onRemove   = { viewModel.removeItem(it) }
        )
        binding.recyclerCart.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CartFragment.adapter
            setHasFixedSize(false)
            setItemViewCacheSize(10)
        }
    }

    private fun observeCart() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.cart.collectLatest { cart ->
                adapter.submitList(cart.items)
                if (cart.isEmpty) {
                    binding.emptyView.visibility   = View.VISIBLE
                    binding.recyclerCart.visibility = View.GONE
                    binding.checkoutCard.visibility = View.GONE
                } else {
                    binding.emptyView.visibility   = View.GONE
                    binding.recyclerCart.visibility = View.VISIBLE
                    binding.checkoutCard.visibility = View.VISIBLE
                    binding.tvTotal.text = cart.formattedTotal
                }
            }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bannerSubscriber?.let {
            Braze.getInstance(requireContext()).removeSingleSubscription(it, BannersUpdatedEvent::class.java)
        }
        bannerSubscriber = null
        _binding = null
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class CartAdapter(
    private val onIncrease: (Int) -> Unit,
    private val onDecrease: (Int) -> Unit,
    private val onRemove:   (Int) -> Unit
) : ListAdapter<CartItem, CartAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CartItem) {
            b.imgProduct.load(item.product.image) {
                crossfade(200)
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
            }
            b.tvTitle.text    = item.product.shortTitle
            b.tvPrice.text    = item.formattedSubtotal
            b.tvQuantity.text = item.quantity.toString()
            b.btnIncrease.setOnClickListener { onIncrease(item.product.id) }
            b.btnDecrease.setOnClickListener { onDecrease(item.product.id) }
            b.btnRemove.setOnClickListener   { onRemove(item.product.id) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: ViewHolder, pos: Int) = holder.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(a: CartItem, b: CartItem) = a.product.id == b.product.id
            override fun areContentsTheSame(a: CartItem, b: CartItem) = a == b
        }
    }
}
