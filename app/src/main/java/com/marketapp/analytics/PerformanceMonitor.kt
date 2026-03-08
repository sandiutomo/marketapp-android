package com.marketapp.analytics

import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import com.google.firebase.perf.metrics.Trace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Firebase Performance for custom app traces.
 *
 * Firebase Performance auto-instruments:
 *   - HTTP/S requests via the OkHttp plugin (all Retrofit calls)
 *   - App cold/warm start time
 *   - Slow/frozen frame rendering
 *
 * This class adds custom traces for operations the plugin cannot see:
 *   - Firebase Auth (Task-based, not OkHttp)
 *   - Cache-vs-network load paths
 *   - Business-level flows like checkout duration
 *
 * Two usage patterns:
 *
 *   1. Scoped block (most common):
 *      perf.trace("product_detail_load") { trace ->
 *          val result = repo.getProduct(id)
 *          trace.putAttribute("from_cache", "true")
 *          result
 *      }
 *
 *   2. Manual start/stop (multistep flows):
 *      val t = perf.startTrace("checkout_complete")
 *      // ... user steps ...
 *      t.putAttribute("payment_method", "card")
 *      t.stop()
 */
@Singleton
class PerformanceMonitor @Inject constructor() {

    private val perf by lazy { Firebase.performance }

    /**
     * Starts a named trace and returns it for manual lifecycle management.
     * Caller is responsible for calling [Trace.stop] — failure to do so leaks
     * the trace (Firebase expires it server-side after 5 minutes).
     */
    fun startTrace(name: String): Trace = perf.newTrace(name).also { it.start() }

    /**
     * Wraps [block] with a Firebase Performance trace.
     *
     * - The [Trace] is passed into [block] so attributes can be added once
     *   the result is known (e.g. item count, cache status).
     * - A "success" attribute ("true"/"false") is added automatically.
     * - The trace is always stopped in the finally block, even on exception.
     */
    suspend fun <T> trace(name: String, block: suspend (Trace) -> T): T {
        val t = perf.newTrace(name).also { it.start() }
        return try {
            block(t).also { t.putAttribute("success", "true") }
        } catch (e: Exception) {
            t.putAttribute("success", "false")
            throw e
        } finally {
            t.stop()
        }
    }
}