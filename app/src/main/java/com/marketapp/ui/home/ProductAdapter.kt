package com.marketapp.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.marketapp.data.model.Product
import com.marketapp.databinding.ItemProductCardBinding

class ProductAdapter(
    private val isWishlistEnabled: () -> Boolean = { true },
    private val onClick: (Product, Int) -> Unit
) : ListAdapter<Product, ProductAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemProductCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product, position: Int) {
            binding.tvTitle.text    = product.shortTitle
            binding.tvCategory.text = product.category
            binding.tvPrice.text    = product.formattedPrice
            binding.tvRating.text   = "${product.rating.rate} (${product.rating.count})"

            binding.imgProduct.load(product.image) {
                crossfade(200)
                allowHardware(true)
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
            }

            // Re-read on every bind so RC fetch completing mid-session takes effect.
            binding.btnWishlist.isVisible = isWishlistEnabled()

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
