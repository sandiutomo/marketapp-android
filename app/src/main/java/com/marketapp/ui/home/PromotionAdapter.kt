package com.marketapp.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marketapp.R
import com.marketapp.data.model.PromotionItem
import com.marketapp.databinding.ItemPromotionBinding

class PromotionAdapter(
    private val onClick: (PromotionItem) -> Unit
) : ListAdapter<PromotionItem, PromotionAdapter.ViewHolder>(DIFF) {

    // Cycles through the app's semantic color palette so each banner is distinct.
    private val colorRes = listOf(
        R.color.error,    // red   — flash sale
        R.color.primary,  // blue  — new arrivals
        R.color.success,  // green — extra slot
        R.color.warning   // amber — extra slot
    )

    inner class ViewHolder(private val binding: ItemPromotionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(promo: PromotionItem, position: Int) {
            val color = ContextCompat.getColor(binding.root.context, colorRes[position % colorRes.size])
            binding.root.setCardBackgroundColor(color)
            binding.tvPromoLabel.text = promo.name
            binding.tvPromoName.text  = promo.creativeName
            binding.root.setOnClickListener { onClick(promo) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPromotionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PromotionItem>() {
            override fun areItemsTheSame(a: PromotionItem, b: PromotionItem) = a.id == b.id
            override fun areContentsTheSame(a: PromotionItem, b: PromotionItem) = a == b
        }
    }
}
