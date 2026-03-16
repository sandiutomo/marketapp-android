package com.marketapp.ui.product

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.marketapp.R
import com.marketapp.config.ExperimentManager
import com.marketapp.config.FeatureFlag
import com.marketapp.data.model.Product
import com.marketapp.data.model.UiState
import com.marketapp.databinding.FragmentProductDetailBinding
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProductDetailViewModel by viewModels()
    private val args: ProductDetailFragmentArgs by navArgs()

    @Inject lateinit var experiments: ExperimentManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentProductDetailBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        binding.btnWishlist.isVisible = experiments.isEnabled(FeatureFlag.WISHLIST_ENABLED)
        val source = requireArguments().getString("source", "unknown")
        viewModel.loadProduct(args.productId, source)
        observeProduct()
        observeWishlist()
        observeCartAdded()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_share -> { shareProduct(); true }
                else -> false
            }
        }
    }

    private fun observeProduct() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.product.collectLatest { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.progress.visibility = View.VISIBLE
                        binding.scrollView.visibility = View.INVISIBLE
                        binding.bottomActions.visibility = View.GONE
                    }
                    is UiState.Success -> {
                        binding.progress.visibility = View.GONE
                        binding.scrollView.visibility = View.VISIBLE
                        binding.bottomActions.visibility = View.VISIBLE
                        bindProduct(state.data)
                    }
                    is UiState.Error -> {
                        binding.progress.visibility = View.GONE
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            }
        }
    }

    private fun bindProduct(product: Product) {
        binding.imgProduct.load(product.image) {
            crossfade(300)
            memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
        }
        binding.tvCategory.text    = product.category
        binding.tvTitle.text       = product.title
        binding.tvPrice.text       = product.formattedPrice
        binding.tvRating.text      = product.rating.rate.toString()
        binding.tvReviewCount.text = "(${product.rating.count})"
        binding.tvDescription.text = product.description

        binding.btnAddToCart.setOnClickListener { viewModel.addToCart() }
        binding.btnWishlist.setOnClickListener  { viewModel.toggleWishlist() }
    }

    private fun observeWishlist() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isWishlisted.collectLatest { wishlisted ->
                    binding.btnWishlist.setIconResource(
                        if (wishlisted) R.drawable.ic_heart_filled else R.drawable.ic_heart
                    )
                }
            }
        }
    }

    private fun observeCartAdded() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.addedToCart.collectLatest { added ->
                    if (added) {
                        Snackbar.make(binding.root, getString(R.string.added_to_cart), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun shareProduct() {
        viewModel.onShare()
        val product = (viewModel.product.value as? UiState.Success)?.data ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out ${product.title} for ${product.formattedPrice} on Market!")
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
