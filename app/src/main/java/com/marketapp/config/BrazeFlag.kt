package com.marketapp.config

/**
 * All Braze Banner placement IDs registered in the app.
 * Always pass the full list to requestBannersRefresh() — the SDK REPLACES (not merges)
 * the placement list on each call, so passing a subset silently drops the others.
 */
val ALL_BANNER_PLACEMENTS = listOf("home_banner", "profile_banner", "cart_banner")

/**
 * Session-scoped banner dismiss tracker.
 *
 * Banners are placement-based (always-on while a campaign is Live in Braze Console), so the SDK
 * re-fires BannersUpdatedEvent every time requestBannersRefresh() is called — including on every
 * fragment resume. This means tapping X has no lasting effect across navigation.
 *
 * This object tracks both:
 *  - dismissed: placements the user explicitly closed this session.
 *  - seenContent: a hash of the last HTML shown per placement. If Braze sends genuinely new
 *    content (different HTML), the dismiss is reset automatically so the user sees the new banner.
 *
 * Both maps are process-scoped and reset on app restart.
 */
object BannerDismissManager {
    private val dismissed = mutableSetOf<String>()
    private val seenContent = mutableMapOf<String, Int>()   // placementId → html.hashCode()

    /** Call when the user taps the close button for a placement. */
    fun dismiss(placementId: String) {
        dismissed.add(placementId)
    }

    /**
     * Returns true if the banner should be shown.
     * Resets the dismiss state automatically when the content changes (new campaign).
     */
    fun shouldShow(placementId: String, html: String): Boolean {
        val contentHash = html.hashCode()
        if (seenContent[placementId] != contentHash) {
            // New content from Braze — clear the dismiss so it appears once
            dismissed.remove(placementId)
            seenContent[placementId] = contentHash
        }
        return placementId !in dismissed
    }

    /** Call on sign-out or explicit "reset banners" if needed. */
    fun reset() {
        dismissed.clear()
        seenContent.clear()
    }
}

/**
 * Braze Feature Flags used in the app. Adding a new flag here is the only step needed to make it:
 *  - readable via Braze.getInstance(context).getFeatureFlag(flag.key)
 *  - visible in the debug panel (DebugInfoBottomSheet)
 *
 * SETUP: Create the matching flag ID in Braze Console (Feature Flags → Create)
 * before deploying. Flags not created in Braze return null (shown as "unset").
 */
enum class BrazeFlag(val key: String, val label: String) {
    // Overrides the "Place Order" button label in PaymentFragment.
    // enabled=true: reads string property "label" from the Braze flag and applies it.
    // enabled=false / unset: default button text ("Place Order") is used.
    // Useful for personalizing CTA copy per Braze user segment (e.g. VIP, first-time buyer).
    CHECKOUT_CTA ("checkout-cta", "Checkout CTA Label"),
    ;
}