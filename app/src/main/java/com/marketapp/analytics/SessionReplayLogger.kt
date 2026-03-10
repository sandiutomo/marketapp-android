package com.marketapp.analytics

import android.util.Log

/**
 * Centralized session-replay status logger.
 *
 * Each SDK tracker calls [record] during its [AnalyticsTracker.initialize].
 * [AnalyticsManager] calls [printSummary] once all trackers are ready so
 * a single, formatted table appears in logcat on every app launch.
 *
 * Example output:
 * ```
 * I/SessionReplay: ┌─── Session Replay Status ─────────────────────────────
 * I/SessionReplay: │  ● ACTIVE    [Amplitude]          debug=100% / prod=40%
 * I/SessionReplay: │  ● ACTIVE    [Mixpanel]            debug= 20% / prod=40%
 * I/SessionReplay: │  ○ INACTIVE  [PostHog]             debug= 20% / prod=40%
 * I/SessionReplay: │  2 / 3 platforms recording this session
 * I/SessionReplay: └───────────────────────────────────────────────────────
 * ```
 *
 * Thread-safe: all three SDK trackers initialize in parallel on [kotlinx.coroutines.Dispatchers.IO].
 */
internal object SessionReplayLogger {

    private const val TAG = "SessionReplay"

    private data class Entry(
        val platform: String,
        val active: Boolean,
        val debugPct: Int,
        val prodPct: Int
    )

    private val lock    = Any()
    private val entries = mutableListOf<Entry>()

    /**
     * Record and immediately log a single platform's replay decision.
     *
     * @param platform  Human-readable SDK name, e.g. "Amplitude"
     * @param active    Whether this session will be recorded
     * @param debugPct  Configured sample rate for debug builds (0–100)
     * @param prodPct   Configured sample rate for production builds (0–100)
     */
    fun record(platform: String, active: Boolean, debugPct: Int, prodPct: Int) {
        val entry = Entry(platform, active, debugPct, prodPct)
        synchronized(lock) { entries.add(entry) }

        val icon   = if (active) "●" else "○"
        val status = if (active) "ACTIVE  " else "INACTIVE"
        Log.i(TAG, "$icon $status  [$platform]")
    }

    /**
     * Print a summary table once all trackers have finished initializing.
     * Clears the entry list so subsequent [printSummary] calls are no-ops
     * unless new [record] calls are made.
     */
    fun printSummary() {
        val snapshot = synchronized(lock) {
            val copy = entries.toList()
            entries.clear()
            copy
        }
        if (snapshot.isEmpty()) return

        val activeCount = snapshot.count { it.active }
        val col = snapshot.maxOf { it.platform.length }

        Log.i(TAG, "┌─── Session Replay Status ─────────────────────────────")
        snapshot.forEach { e ->
            val icon   = if (e.active) "●" else "○"
            val status = if (e.active) "ACTIVE  " else "INACTIVE"
            val name   = "[${e.platform}]".padEnd(col + 2)
            Log.i(TAG, "│  $icon $status  $name  debug=${e.debugPct.toString().padStart(3)}% / prod=${e.prodPct}%")
        }
        Log.i(TAG, "│  $activeCount / ${snapshot.size} platforms recording this session")
        Log.i(TAG, "└───────────────────────────────────────────────────────")
    }
}