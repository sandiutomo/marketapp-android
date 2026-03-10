package com.marketapp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.braze.Braze
import com.braze.events.ContentCardsUpdatedEvent
import com.braze.models.cards.Card
import com.braze.models.cards.CaptionedImageCard
import com.braze.models.cards.ShortNewsCard
import com.braze.models.cards.TextAnnouncementCard
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.marketapp.databinding.BottomSheetContentCardsBinding
import com.marketapp.databinding.ItemContentCardBinding

class ContentCardsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ContentCardsBottomSheet"
    }

    private var _binding: BottomSheetContentCardsBinding? = null
    private val binding get() = _binding!!
    private lateinit var cardAdapter: CardAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetContentCardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cardAdapter = CardAdapter()
        binding.recyclerCards.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cardAdapter
            setHasFixedSize(false)
        }

        Braze.getInstance(requireContext()).also { braze ->
            braze.subscribeToContentCardsUpdates { event: ContentCardsUpdatedEvent ->
                requireActivity().runOnUiThread { showCards(event.allCards) }
            }
            braze.requestContentCardsRefresh()
        }
    }

    private fun showCards(cards: List<Card>) {
        binding.progress.isVisible = false
        if (cards.isEmpty()) {
            binding.tvEmpty.isVisible = true
            binding.recyclerCards.isVisible = false
        } else {
            binding.tvEmpty.isVisible = false
            binding.recyclerCards.isVisible = true
            cardAdapter.submitList(cards)
            cards.forEach { it.logImpression() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private inner class CardAdapter : ListAdapter<Card, CardAdapter.ViewHolder>(
        object : DiffUtil.ItemCallback<Card>() {
            override fun areItemsTheSame(a: Card, b: Card) = a.id == b.id
            override fun areContentsTheSame(a: Card, b: Card) = a.id == b.id
        }
    ) {
        inner class ViewHolder(private val b: ItemContentCardBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(card: Card) {
                val (type, title, description) = when (card) {
                    is TextAnnouncementCard -> Triple("Announcement", card.title, card.description)
                    is ShortNewsCard        -> Triple("News",         card.title, card.description)
                    is CaptionedImageCard   -> Triple("Image",        card.title, card.description)
                    else                    -> Triple("Banner",       null,       null)
                }
                b.tvCardType.text = type
                b.tvCardTitle.isVisible = title != null
                b.tvCardTitle.text = title
                b.tvCardDescription.isVisible = description != null
                b.tvCardDescription.text = description
                b.root.setOnClickListener { card.logClick() }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemContentCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(getItem(position))
    }
}
