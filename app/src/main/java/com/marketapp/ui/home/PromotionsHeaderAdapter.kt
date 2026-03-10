package com.marketapp.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.marketapp.databinding.ItemPromotionsHeaderBinding

class PromotionsHeaderAdapter(
    private val promotionAdapter: PromotionAdapter
) : RecyclerView.Adapter<PromotionsHeaderAdapter.ViewHolder>() {

    private var visible = false

    class ViewHolder(binding: ItemPromotionsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPromotionsHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding.recyclerPromotions.apply {
            layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
            adapter = promotionAdapter
            setHasFixedSize(true)
        }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit

    fun setVisible(show: Boolean) {
        if (visible == show) return
        visible = show
        if (show) notifyItemInserted(0) else notifyItemRemoved(0)
    }
}