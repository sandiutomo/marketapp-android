package com.marketapp.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.marketapp.R
import com.marketapp.data.model.Product
import com.marketapp.databinding.ItemProductCardBinding

class ProductAdapter(
    private val isWishlistEnabled: () -> Boolean = { true },
    private val onWishlistToggle: ((Product) -> Unit)? = null,
    private val onClick: (Product, Int) -> Unit
) : ListAdapter<Product, ProductAdapter.ViewHolder>(DIFF) {

    var compact: Boolean = false
    var wishlistedIds: Set<Int> = emptySet()

    fun updateWishlistIds(ids: Set<Int>) {
        wishlistedIds = ids
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(private val binding: ItemProductCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product, position: Int) {
            binding.tvTitle.text    = product.shortTitle
            binding.tvCategory.text = product.category
            binding.tvPrice.text    = product.formattedPrice
            binding.tvRating.text   = "${product.rating.rate} (${product.rating.count})"

            binding.tvPrice.setTextAppearance(
                if (compact) R.style.TextAppearance_MarketApp_Callout
                else         R.style.TextAppearance_MarketApp_Title3
            )

            binding.imgProduct.load(product.image) {
                crossfade(200)
                allowHardware(true)
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
            }

            // Re-read on every bind so RC fetch completing mid-session takes effect.
            val wishlistVisible = isWishlistEnabled()
            binding.btnWishlist.isVisible = wishlistVisible
            if (wishlistVisible) {
                binding.btnWishlist.setImageResource(
                    if (wishlistedIds.contains(product.id)) R.drawable.ic_heart_filled
                    else R.drawable.ic_heart
                )
                binding.btnWishlist.setOnClickListener { onWishlistToggle?.invoke(product) }
            }

            binding.root.setOnClickListener { onClick(product, position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
            override fun areContentsTheSame(a: Product, b: Product) = a == b
        }
    }
}
