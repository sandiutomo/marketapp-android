package com.marketapp.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists per-user purchase stats locally via SharedPreferences.
 *
 * These feed [com.marketapp.analytics.UserProperties] after each order so that
 * Firebase, Mixpanel, and other trackers always have up-to-date values for:
 *   - has_purchased / hasPurchased
 *   - order_count_bucket / orderCount
 *   - ltv_bucket / lifetimeValue
 *
 * Stats are reset in [clear] on sign-out so a new user on the same device
 * starts from zero rather than inheriting a previous user's history.
 *
 * Note: these are local approximations. If the user installs on a new device or
 * clears app data the counts start from zero. For authoritative numbers persist
 * them in Firestore via the user document written in AuthRepository.
 */
@Singleton
class UserStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val orderCount: Int
        get() = prefs.getInt(KEY_ORDER_COUNT, 0)

    /** Lifetime order value in the app's base currency (IDR). */
    val lifetimeValue: Double
        get() = Double.fromBits(prefs.getLong(KEY_LTV_BITS, 0L))

    /** Returns true if the user has completed at least one order. */
    val hasPurchased: Boolean
        get() = orderCount > 0

    /** Atomically increments order count and adds [orderValue] to lifetime value. */
    fun recordOrder(orderValue: Double) {
        prefs.edit()
            .putInt(KEY_ORDER_COUNT, orderCount + 1)
            .putLong(KEY_LTV_BITS, (lifetimeValue + orderValue).toBits())
            .apply()
    }

    /** Call on sign-out so the next user's stats start clean. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME     = "user_stats"
        private const val KEY_ORDER_COUNT = "order_count"
        private const val KEY_LTV_BITS    = "lifetime_value_bits"
    }
}