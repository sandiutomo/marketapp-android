package com.marketapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.PerformanceMonitor
import com.marketapp.data.model.Product
import com.marketapp.data.model.UiState
import com.marketapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val analytics: AnalyticsManager,
    private val perf: PerformanceMonitor
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<UiState<List<Product>>?>(null)
    val results: StateFlow<UiState<List<Product>>?> = _results

    init {
        // Debounce search — 400ms after user stops typing
        viewModelScope.launch {
            _query
                .debounce(400)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query -> search(query) }
        }
    }

    fun onQueryChanged(q: String) {
        _query.value = q
        if (q.length < 2) _results.value = null
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _results.value = UiState.Loading
            perf.trace("search_load") { trace ->
                trace.putAttribute("query_length", query.length.toString())
                repository.searchProducts(query)
                    .onSuccess { list ->
                        trace.putAttribute("result_count", list.size.toString())
                        _results.value = UiState.Success(list)
                        analytics.track(AnalyticsEvent.SearchPerformed(query, list.size))
                    }
                    .onFailure {
                        trace.putAttribute("result_count", "0")
                        _results.value = UiState.Error(it.message ?: "Search failed")
                    }
            }
        }
    }

    fun onResultTapped(product: Product, position: Int) {
        analytics.track(AnalyticsEvent.SearchResultTapped(product.id.toString(), position))
    }
}
