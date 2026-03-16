package com.marketapp.ui.profile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.braze.Braze
import com.braze.events.ContentCardsUpdatedEvent
import com.braze.events.IEventSubscriber
import com.braze.models.cards.Card
import com.braze.models.cards.CaptionedImageCard
import com.braze.models.cards.ShortNewsCard
import com.braze.models.cards.TextAnnouncementCard
import android.util.Log
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.marketapp.databinding.BottomSheetContentCardsBinding
import com.marketapp.databinding.ItemContentCardBinding

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

class ContentCardsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ContentCardsBottomSheet"
    }

    private var _binding: BottomSheetContentCardsBinding? = null
    private val binding get() = _binding!!
    private lateinit var cardAdapter: CardAdapter

    // Must be stored in a field — Braze holds a WeakReference internally, so a lambda
    // passed directly gets GC'd before the network response arrives and never fires.
    private var contentCardsSubscriber: IEventSubscriber<ContentCardsUpdatedEvent>? = null

    private val handler = Handler(Looper.getMainLooper())

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

        val braze = Braze.getInstance(requireContext())
        contentCardsSubscriber = IEventSubscriber { event ->
            val act = activity ?: return@IEventSubscriber
            act.runOnUiThread {
                if (_binding != null) {
                    logCards(event.allCards)
                    showCards(event.allCards)
                }
            }
        }
        braze.subscribeToContentCardsUpdates(contentCardsSubscriber!!)
        braze.requestContentCardsRefresh()

        // analyticsManager.track() is async — the trigger event reaches Braze's server
        // after this point. Retry the refresh after a delay so action-based delivery
        // has time to enqueue the card before we sync again.
        handler.postDelayed({
            if (_binding != null) braze.requestContentCardsRefresh()
        }, 3_500L)
    }

    private fun logCards(cards: List<Card>) {
        Log.d("ContentCards", "┌─── ${cards.size} card(s) received ──────────────────────────")
        cards.forEachIndexed { i, card ->
            val (type, title, desc, img) = when (card) {
                is CaptionedImageCard   -> Quad("CaptionedImage",    card.title, card.description, card.imageUrl)
                is ShortNewsCard        -> Quad("ShortNews",         card.title, card.description, card.imageUrl)
                is TextAnnouncementCard -> Quad("TextAnnouncement",  card.title, card.description, null)
                else                   -> Quad(card::class.simpleName ?: "Unknown", null, null, null)
            }
            Log.d("ContentCards", "│ [$i] type=$type")
            Log.d("ContentCards", "│     title=${title.orEmpty().ifEmpty { "<empty>" }}")
            Log.d("ContentCards", "│     desc=${desc.orEmpty().ifEmpty { "<empty>" }}")
            Log.d("ContentCards", "│     img=${img.orEmpty().ifEmpty { "<none>" }}")
        }
        Log.d("ContentCards", "└─────────────────────────────────────────────────────────")
    }

    private fun showCards(cards: List<Card>) {
        binding.progress.isVisible = false
        if (cards.isEmpty()) {
            binding.tvEmpty.isVisible = true
            binding.recyclerCards.isVisible = false
        } else {
            binding.tvEmpty.isVisible = false
            binding.recyclerCards.isVisible = true
            val visible = cards.take(5)
            cardAdapter.submitList(visible)
            visible.forEach { it.logImpression() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        contentCardsSubscriber?.let {
            Braze.getInstance(requireContext())
                .removeSingleSubscription(it, ContentCardsUpdatedEvent::class.java)
        }
        contentCardsSubscriber = null
        _binding = null
    }

    // Adapter ──────────────────────────────────────────────────────────────
    private inner class CardAdapter : ListAdapter<Card, CardAdapter.ViewHolder>(
        object : DiffUtil.ItemCallback<Card>() {
            override fun areItemsTheSame(a: Card, b: Card) = a.id == b.id
            override fun areContentsTheSame(a: Card, b: Card) = a.id == b.id
        }
    ) {
        inner class ViewHolder(private val b: ItemContentCardBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(card: Card) {
                val (type, title, description, imageUrl) = when (card) {
                    is CaptionedImageCard   -> Quad("Image",        card.title,  card.description, card.imageUrl)
                    is ShortNewsCard        -> Quad("News",         card.title,  card.description, null)
                    is TextAnnouncementCard -> Quad("Announcement", card.title,  card.description, null)
                    else                    -> Quad("Banner",       null,        null,              null)
                }
                b.tvCardType.text = type
                b.tvCardTitle.isVisible = !title.isNullOrEmpty()
                b.tvCardTitle.text = title
                b.tvCardDescription.isVisible = !description.isNullOrEmpty()
                b.tvCardDescription.text = description

                if (!imageUrl.isNullOrEmpty()) {
                    b.imgCard.isVisible = true
                    b.imgCard.load(imageUrl) {
                        crossfade(200)
                        memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    }
                } else {
                    b.imgCard.isVisible = false
                }

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